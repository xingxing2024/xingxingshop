package io.github.hello09x.shop.gui;

import io.github.hello09x.shop.ShopPlugin;
import io.github.hello09x.shop.shop.Shop;
import io.github.hello09x.shop.shop.ShopItem;
import io.github.hello09x.shop.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 分类列表：只显示同一类（购买 或 收购）的商店。
 *
 * <p>购买和收购彻底分在两个菜单里，不再混在一起。</p>
 */
public final class ShopListMenu implements InventoryHolder {

    /** 卡片可用槽位（避开边框和第 6 行控制条） */
    private static final int[] CARD_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final ShopPlugin plugin;
    private final Player viewer;
    private final Shop.Type type;
    private final Inventory inventory;
    private final Map<Integer, Shop> slotShops = new HashMap<>();

    private int page = 1;
    private int totalPages = 1;

    public ShopListMenu(@NotNull ShopPlugin plugin, @NotNull Player viewer, @NotNull Shop.Type type) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.type = type;
        boolean selling = type == Shop.Type.SELL;
        this.inventory = Bukkit.createInventory(this, 54,
                Text.color(selling ? "&8收购 &7» &6&l分类列表" : "&8购买 &7» &a&l分类列表"));
        render();
    }

    public void render() {
        inventory.clear();
        slotShops.clear();

        List<Shop> shops = visible();
        this.totalPages = Math.max(1, (shops.size() + CARD_SLOTS.length - 1) / CARD_SLOTS.length);
        this.page = Math.max(1, Math.min(page, totalPages));

        renderFrame();
        renderCards(shops);
        renderFooter(shops.size());
    }

    private void renderFrame() {
        boolean selling = type == Shop.Type.SELL;
        ItemStack edge = pane(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack accent = pane(selling ? Material.ORANGE_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE);
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, edge);
            inventory.setItem(45 + i, edge);
        }
        for (int row = 1; row <= 4; row++) {
            inventory.setItem(row * 9, edge);
            inventory.setItem(row * 9 + 8, edge);
        }
        inventory.setItem(0, accent);
        inventory.setItem(8, accent);
        inventory.setItem(45, accent);
        inventory.setItem(53, accent);

        inventory.setItem(4, icon(selling ? Material.HOPPER : Material.EMERALD,
                selling ? "&6&l收购分类" : "&a&l购买分类",
                "",
                "&7共 &f" + visible().size() + " &7个分类",
                "&7点击卡片进入"));
    }

    private void renderCards(@NotNull List<Shop> shops) {
        int from = (page - 1) * CARD_SLOTS.length;
        int to = Math.min(shops.size(), from + CARD_SLOTS.length);
        for (int i = from; i < to; i++) {
            int slot = CARD_SLOTS[i - from];
            Shop shop = shops.get(i);
            slotShops.put(slot, shop);
            inventory.setItem(slot, card(shop));
        }
    }

    private void renderFooter(int shopCount) {
        var currency = plugin.currencies().getOrDefault(null);
        if (currency != null) {
            inventory.setItem(45 + 0, icon(Material.GOLD_INGOT,
                    "&6&l余额",
                    "",
                    "&7" + currency.displayName() + " &f" + currency.format(currency.balance(viewer))));
        }

        inventory.setItem(45 + 1, icon(Material.COMPASS, "&e« 返回主菜单", "&7回到商店首页"));

        if (page > 1) {
            inventory.setItem(45 + 3, icon(Material.ARROW, "&e« 上一页", "&7第 &f" + (page - 1) + " &7页"));
        }
        inventory.setItem(45 + 4, icon(Material.PAPER,
                "&f第 &a" + page + " &f/ &a" + totalPages + " &f页",
                "&7共 &f" + shopCount + " &7个分类"));
        if (page < totalPages) {
            inventory.setItem(45 + 5, icon(Material.ARROW, "&e下一页 »", "&7第 &f" + (page + 1) + " &7页"));
        }

        inventory.setItem(45 + 8, icon(Material.BARRIER, "&c关闭", "&7点击关闭界面"));
    }

    // ------------------------------------------------------------ 卡片

    private @NotNull ItemStack card(@NotNull Shop shop) {
        boolean selling = shop.type() == Shop.Type.SELL;
        ItemStack stack = new ItemStack(shop.icon());
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.setDisplayName(Text.color((selling ? "&6&l" : "&a&l") + shop.name()));

        List<String> lore = new ArrayList<>();
        for (String line : shop.desc()) {
            lore.add(Text.color(line));
        }
        lore.add("");

        lore.add(Text.color("&8&m     &r &7商品预览 &8&m     "));
        List<ShopItem> items = shop.sortedItems();
        int preview = Math.min(4, items.size());
        for (int i = 0; i < preview; i++) {
            ShopItem item = items.get(i);
            double price = selling ? item.sellPrice(viewer) : item.buyPrice(viewer);
            lore.add(Text.color("&8▪ &f" + displayNameOf(item) + " &7" + fmt(price)));
        }
        if (items.size() > preview) {
            lore.add(Text.color("&8▪ &7... 还有 &f" + (items.size() - preview) + " &7件"));
        }
        lore.add("");

        if (!items.isEmpty()) {
            double min = Double.MAX_VALUE, max = -1;
            for (ShopItem item : items) {
                double p = selling ? item.sellPrice(viewer) : item.buyPrice(viewer);
                min = Math.min(min, p);
                max = Math.max(max, p);
            }
            var currency = plugin.currencies().getOrDefault(null);
            lore.add(Text.color("&7价位 &f" + (currency == null ? fmt(min) : currency.format(min))
                    + " &7~ &f" + (currency == null ? fmt(max) : currency.format(max))));
        }

        lore.add("");
        lore.add(Text.color(selling ? "&6➤ 点击进入收购" : "&a➤ 点击进入购买"));

        meta.setLore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private @NotNull String displayNameOf(@NotNull ShopItem item) {
        return item.displayName();
    }

    private static @NotNull String fmt(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) {
            return String.valueOf((long) v);
        }
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    private List<Shop> visible() {
        return plugin.shops().byType(type).stream()
                .filter(s -> s.isOpen())
                .filter(s -> s.permission() == null || viewer.hasPermission(s.permission()))
                .toList();
    }

    // ------------------------------------------------------------ 点击

    public void onClick(int slot) {
        if (slot == 45 + 8) {
            viewer.closeInventory();
            return;
        }
        if (slot == 45 + 1) {
            plugin.gui().openEntry(viewer);
            return;
        }
        if (slot == 45 + 3 && page > 1) {
            page--;
            render();
            return;
        }
        if (slot == 45 + 5 && page < totalPages) {
            page++;
            render();
            return;
        }
        if (slot == 45 + 0 || slot == 45 + 4) {
            render();
            return;
        }
        Shop shop = slotShops.get(slot);
        if (shop != null) {
            plugin.gui().open(viewer, shop);
        }
    }

    // ------------------------------------------------------------ 工具

    private static @NotNull ItemStack pane(@NotNull Material material) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static @NotNull ItemStack icon(@NotNull Material material, @NotNull String name, @NotNull String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(name));
            if (lore.length > 0) {
                List<String> list = new ArrayList<>();
                for (String line : lore) {
                    list.add(Text.color(line));
                }
                meta.setLore(list);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public @NotNull Shop.Type type() {
        return type;
    }

}
