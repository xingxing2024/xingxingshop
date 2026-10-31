package io.github.hello09x.shop.gui;

import io.github.hello09x.shop.ShopPlugin;
import io.github.hello09x.shop.shop.Shop;
import io.github.hello09x.shop.shop.ShopItem;
import io.github.hello09x.shop.util.Schedulers;
import io.github.hello09x.shop.util.Text;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 聊天框输入：商品数量、购物车数量、搜索关键词。 */
public final class ChatInputHandler implements Listener {

    private static final long TIMEOUT_TICKS = 20L * 30;

    private enum Mode { SHOP, CART, SEARCH }

    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    private final ShopPlugin plugin;

    public ChatInputHandler(@NotNull ShopPlugin plugin) {
        this.plugin = plugin;
    }

    private static final class Pending {
        final Mode mode;
        @Nullable final Shop shop;
        @Nullable final ShopItem item;
        final long createdAt = System.currentTimeMillis();

        Pending(@NotNull Mode mode, @Nullable Shop shop, @Nullable ShopItem item) {
            this.mode = mode;
            this.shop = shop;
            this.item = item;
        }
    }

    /** 商店里 Shift+左键：输入购买 / 出售数量 */
    public void request(@NotNull Player viewer, @NotNull Shop shop, @NotNull ShopItem item) {
        boolean selling = shop.type() == Shop.Type.SELL;
        viewer.closeInventory();
        pending.put(viewer.getUniqueId(), new Pending(Mode.SHOP, shop, item));
        plugin.messages().send(viewer, "chat-input.prompt",
                item.displayName(), selling ? "卖出" : "购买");
        scheduleTimeout(viewer);
    }

    /** 购物车里设置数量 */
    public void requestCartQuantity(@NotNull Player viewer, @NotNull Shop shop, @NotNull ShopItem item) {
        viewer.closeInventory();
        pending.put(viewer.getUniqueId(), new Pending(Mode.CART, shop, item));
        viewer.sendMessage(Text.color("&e请在聊天框输入要设置的 &f" + item.displayName()
                + " &e数量 &8(输入 cancel 取消)"));
        scheduleTimeout(viewer);
    }

    /** 搜索关键词 */
    public void requestSearch(@NotNull Player viewer) {
        requestSearch(viewer, null);
    }

    /** 搜索关键词；从商店里打开时，取消后回到该商店 */
    public void requestSearch(@NotNull Player viewer, @Nullable Shop returnShop) {
        viewer.closeInventory();
        pending.put(viewer.getUniqueId(), new Pending(Mode.SEARCH, returnShop, null));
        viewer.sendMessage(Text.color("&e请在聊天框输入搜索关键词 &8(输入 cancel 取消)"));
        scheduleTimeout(viewer);
    }

    private void scheduleTimeout(@NotNull Player viewer) {
        Schedulers.entityLater(plugin, viewer, () -> {
            Pending p = pending.get(viewer.getUniqueId());
            if (p != null && System.currentTimeMillis() - p.createdAt >= TIMEOUT_TICKS * 50) {
                pending.remove(viewer.getUniqueId());
                if (viewer.isOnline() && p.mode == Mode.SHOP
                        && !(viewer.getOpenInventory().getTopInventory().getHolder() instanceof ShopView)) {
                    plugin.messages().send(viewer, "chat-input.timeout");
                }
            }
        }, TIMEOUT_TICKS);
    }

    public void cancel(@NotNull Player viewer) {
        pending.remove(viewer.getUniqueId());
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }

    public boolean isWaiting(@NotNull Player viewer) {
        return pending.containsKey(viewer.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(@NotNull AsyncChatEvent event) {
        Player player = event.getPlayer();
        Pending p = pending.remove(player.getUniqueId());
        if (p == null) {
            return;
        }
        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Schedulers.entity(plugin, player, () -> apply(player, p, input));
    }

    private void apply(@NotNull Player player, @NotNull Pending p, @NotNull String input) {
        if (!player.isOnline()) {
            return;
        }
        boolean cancel = input.equalsIgnoreCase("cancel") || input.equals("取消") || input.equals("q");
        if (p.mode == Mode.SEARCH) {
            if (cancel || input.isBlank()) {
                if (p.shop != null) {
                    plugin.gui().open(player, p.shop);
                } else {
                    plugin.gui().openEntry(player);
                }
            } else {
                plugin.gui().openSearch(player, input);
            }
            return;
        }

        if (cancel) {
            plugin.messages().send(player, "chat-input.cancelled");
            back(player, p);
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(input);
        } catch (NumberFormatException e) {
            plugin.messages().send(player, "chat-input.invalid");
            back(player, p);
            return;
        }
        if (amount <= 0) {
            plugin.messages().send(player, "chat-input.invalid");
            back(player, p);
            return;
        }
        amount = Math.min(amount, 100_000);

        if (p.mode == Mode.CART) {
            assert p.item != null;
            plugin.cart().set(player.getUniqueId(), p.item.key(), amount);
            player.sendMessage(Text.color("&a已设置 &f" + p.item.displayName() + " &a数量为 &f" + amount));
            plugin.gui().openCart(player);
            return;
        }

        assert p.shop != null && p.item != null;
        plugin.gui().openConfirm(player, p.shop, p.item, amount, p.shop.type() == Shop.Type.SELL);
    }

    private void back(@NotNull Player player, @NotNull Pending p) {
        if (p.mode == Mode.CART) {
            plugin.gui().openCart(player);
        } else if (p.shop != null) {
            plugin.gui().open(player, p.shop);
        } else {
            plugin.gui().openEntry(player);
        }
    }

    public static @NotNull String promptHint(@NotNull ShopItem item) {
        return Text.color("&e请输入数量");
    }
}
