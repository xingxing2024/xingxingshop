package io.github.hello09x.shop.hook;

import io.github.hello09x.shop.ShopPlugin;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Pattern;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/** Records Vault balance changes made while a player is using a TrMenu with money actions. */
public final class TrMenuTradeMonitor implements AutoCloseable {
    private record Button(String id, String name) {}
    private record Pending(String itemId, String itemName, double balance, long expiresAt) {}

    private static final Pattern MONEY_ACTION = Pattern.compile("(?i)^\\s*(?:take|give)-money\\s*:\\s*.+$");
    private final ShopPlugin plugin;
    private final Map<String, Map<Integer, Button>> moneyButtons = new HashMap<>();
    private final Map<UUID, Pending> pending = new HashMap<>();
    private Economy economy;
    private java.lang.reflect.Method sessionMethod;
    private Object clickListener;
    private java.lang.reflect.Method cancelListener;
    private BukkitTask task;
    private boolean failed;

    public TrMenuTradeMonitor(ShopPlugin plugin) { this.plugin = plugin; }

    public void start() {
        if (!plugin.getConfig().getBoolean("external-trades.trmenu.enabled", true)) return;
        Plugin trMenu = Bukkit.getPluginManager().getPlugin("TrMenu");
        Plugin vault = Bukkit.getPluginManager().getPlugin("Vault");
        if (trMenu == null || !trMenu.isEnabled() || vault == null || !vault.isEnabled()) return;
        var registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (registration == null) {
            plugin.getLogger().warning("TrMenu 菜单收支记录未启动：没有可用的 Vault 经济服务。");
            return;
        }
        economy = registration.getProvider();
        try {
            Class<?> sessions = Class.forName("me.arasple.mc.trmenu.module.display.MenuSessionKt", true,
                    trMenu.getClass().getClassLoader());
            sessionMethod = sessions.getMethod("session", Player.class);
            loadMoneyButtons(new File(trMenu.getDataFolder(), "menus"));
            if (moneyButtons.isEmpty()) {
                plugin.getLogger().info("TrMenu 菜单收支记录未发现包含 take-money/give-money 的菜单。");
                return;
            }
            listenForClicks(trMenu.getClass().getClassLoader());
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
            plugin.getLogger().info("TrMenu 菜单收支记录已启动，监控 "
                    + moneyButtons.values().stream().mapToInt(Map::size).sum() + " 个交易按钮。");
        } catch (Exception exception) {
            close();
            plugin.getLogger().warning("TrMenu 菜单收支记录启动失败：" + exception);
        }
    }

    private void loadMoneyButtons(File folder) throws java.io.IOException {
        if (!folder.isDirectory()) return;
        try (var paths = Files.walk(folder.toPath())) {
            for (var path : paths.filter(p -> p.toString().endsWith(".yml")).toList()) {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(path.toFile());
                ConfigurationSection buttons = yaml.getConfigurationSection("Buttons");
                List<String> layout = yaml.getStringList("Layout");
                if (buttons == null || layout.isEmpty()) continue;
                Map<Character, Button> bySymbol = new HashMap<>();
                for (String button : buttons.getKeys(false)) {
                    if (!containsMoneyAction(buttons.get(button + ".actions"))) continue;
                    Object rawName = buttons.get(button + ".display.name");
                    String name = displayName(rawName);
                    if (name.isBlank()) name = materialName(buttons.getString(button + ".display.material",
                            buttons.getString(button + ".display.mat", "")), button);
                    bySymbol.put(button.charAt(0), new Button(button, name));
                }
                if (bySymbol.isEmpty()) continue;
                Map<Integer, Button> slots = new HashMap<>();
                int slot = 0;
                for (String row : layout) {
                    for (int column = 0; column < row.length(); column++) {
                        Button button = bySymbol.get(row.charAt(column));
                        if (button != null) slots.put(slot, button);
                        slot++;
                    }
                }
                if (!slots.isEmpty()) moneyButtons.put(path.getFileName().toString().replaceFirst("\\.yml$", ""), slots);
            }
        }
    }

    private String displayName(Object raw) {
        if (raw instanceof List<?> names) raw = names.stream().filter(Objects::nonNull).findFirst().orElse(null);
        String name = raw == null ? "" : String.valueOf(raw);
        name = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', name));
        return name == null ? "" : name.trim();
    }

    private String materialName(String material, String fallback) {
        return switch (material.toLowerCase(Locale.ROOT)) {
            case "carrot" -> "胡萝卜";
            case "potato" -> "马铃薯";
            case "wheat" -> "小麦";
            case "melon" -> "西瓜";
            case "pumpkin" -> "南瓜";
            case "beetroot" -> "甜菜根";
            case "cocoa_beans" -> "可可豆";
            case "sweet_berries" -> "甜浆果";
            case "sugar_cane" -> "甘蔗";
            default -> fallback;
        };
    }

    private boolean containsMoneyAction(Object node) {
        if (node instanceof ConfigurationSection section) return containsMoneyAction(section.getValues(false));
        if (node instanceof Map<?, ?> map) return map.values().stream().anyMatch(this::containsMoneyAction);
        if (node instanceof Iterable<?> iterable) {
            for (Object value : iterable) {
                if (value instanceof String text && MONEY_ACTION.matcher(text).matches()) return true;
                if (!(value instanceof String) && containsMoneyAction(value)) return true;
            }
        }
        return false;
    }

    /** TrMenu fires this internal event for both packet menus and static inventories. */
    private void listenForClicks(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> eventType = Class.forName("me.arasple.mc.trmenu.api.receptacle.ReceptacleInteractEvent", true, loader);
        Class<?> busType = Class.forName("me.arasple.mc.trmenu.taboolib.common.event.InternalEventBus", true, loader);
        Class<?> functionType = Class.forName("kotlin210.jvm.functions.Function1", true, loader);
        Object unit = Class.forName("kotlin210.Unit", true, loader).getField("INSTANCE").get(null);
        var getPlayer = eventType.getMethod("getPlayer");
        var getSlot = eventType.getMethod("getSlot");
        Object callback = java.lang.reflect.Proxy.newProxyInstance(loader, new Class<?>[]{functionType}, (proxy, method, args) -> {
            if (method.getName().equals("invoke")) {
                Player player = (Player) getPlayer.invoke(args[0]);
                int slot = ((Number) getSlot.invoke(args[0])).intValue();
                // Packet clicks arrive off-thread. Queue before TrMenu queues its money actions.
                if (Bukkit.isPrimaryThread()) onMenuClick(player, slot);
                else if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> {
                    if (clickListener != null && player.isOnline()) onMenuClick(player, slot);
                });
                return unit;
            }
            return switch (method.getName()) {
                case "toString" -> "XingXingShop TrMenu click listener";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> null;
            };
        });
        Object bus = busType.getField("Companion").get(null);
        clickListener = bus.getClass().getMethod("listen", Class.class, int.class, boolean.class, functionType)
                .invoke(bus, eventType, -100, true, callback);
        cancelListener = Class.forName("me.arasple.mc.trmenu.taboolib.common.event.InternalListener", true, loader)
                .getMethod("cancel");
    }

    private void onMenuClick(Player player, int slot) {
        if (slot < 0) return;
        try {
            Object session = sessionMethod.invoke(null, player);
            if (session == null) return;
            Object menu = session.getClass().getMethod("getMenu").invoke(session);
            if (menu == null) return;
            String menuId = String.valueOf(menu.getClass().getMethod("getId").invoke(menu));
            Button button = moneyButtons.getOrDefault(menuId, Map.of()).get(slot);
            if (button == null) return;
            double balance = economy.getBalance(player);
            String safeId = button.id().replaceAll("[^A-Za-z0-9_-]", "_");
            pending.put(player.getUniqueId(), new Pending("menu_button_" + safeId, button.name(), balance,
                    System.currentTimeMillis() + 5000L));
        } catch (Exception exception) {
            plugin.getLogger().fine("读取 TrMenu 点击商品名失败：" + exception.getMessage());
        }
    }

    private void tick() {
        if (failed) return;
        try {
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();
                Pending click = pending.get(uuid);
                if (click == null) continue;
                double balance = economy.getBalance(player);
                double change = balance - click.balance();
                if (Double.isFinite(change) && Math.abs(change) > 0.000001) {
                    boolean buying = change < 0;
                    plugin.transactionLog().recordExternal(player, "TrMenu", click.itemId(), click.itemName(),
                            "vault", buying, 1, Math.abs(change));
                    pending.remove(uuid);
                } else if (System.currentTimeMillis() >= click.expiresAt()) {
                    pending.remove(uuid);
                }
            }
            Set<UUID> online = new HashSet<>();
            for (Player player : Bukkit.getOnlinePlayers()) online.add(player.getUniqueId());
            pending.keySet().retainAll(online);
        } catch (Exception exception) {
            failed = true;
            plugin.getLogger().warning("TrMenu 菜单收支观察异常，已停止记录：" + exception);
        }
    }

    @Override public void close() {
        if (clickListener != null && cancelListener != null) {
            try {
                cancelListener.invoke(clickListener);
            } catch (ReflectiveOperationException exception) {
                plugin.getLogger().warning("停止 TrMenu 点击监听失败：" + exception.getMessage());
            }
        }
        clickListener = null;
        if (task != null) task.cancel();
        pending.clear();
    }
}
