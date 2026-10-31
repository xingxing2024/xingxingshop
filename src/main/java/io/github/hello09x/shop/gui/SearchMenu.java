package io.github.hello09x.shop.gui;

import io.github.hello09x.shop.ShopPlugin;
import io.github.hello09x.shop.currency.Currency;
import io.github.hello09x.shop.shop.Shop;
import io.github.hello09x.shop.shop.ShopItem;
import io.github.hello09x.shop.util.Sounds;
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
import java.util.List;

/** 搜索结果界面。 */
public final class SearchMenu implements InventoryHolder {

    private static final int PAGE_SIZE = 36;
    private static final int ITEM_START = 9;
    private final ShopPlugin plugin;
    private final Player viewer;
    private final String keyword;
    private final List<Hit> hits = new ArrayList<>();
    private final Inventory inventory;
    private int page = 1;

    public SearchMenu(@NotNull ShopPlugin plugin, @NotNull Player viewer, @NotNull String keyword) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.keyword = keyword;
        collect();
        this.inventory = Bukkit.createInventory(this, 54,
                Text.color("&8搜索 &7» &f" + keyword));
        render();
    }

    public SearchMenu(@NotNull ShopPlugin plugin, @NotNull Player viewer) {
        this(plugin, viewer, "*");
    }

    private void collect() {
        String lower = keyword.toLowerCase(java.util.Locale.ROOT);
        for (Shop shop : plugin.shops().all().values()) {
            if (!shop.isOpen()) continue;
            if (shop.permission() != null && !viewer.hasPermission(shop.permission())) continue;
            for (ShopItem item : shop.sortedItems()) {
                if (item.permission() != null && !viewer.hasPermission(item.permission())) continue;
                String hay = (item.itemId() + " " + item.material().name() + " "
                        + item.displayName()).toLowerCase(java.util.Locale.ROOT);
                if (keyword.equals("*") || hay.contains(lower)) {
                    hits.add(new Hit(shop, item));
                }
            }
        }
    }

    public void render() {
        inventory.clear();
        GuiStyle.fill(inventory, ITEM_START, ITEM_START + PAGE_SIZE);
        GuiStyle.frame(inventory, Material.CYAN_STAINED_GLASS_PANE);
        inventory.setItem(4, button(Material.COMPASS,
                "&b&l搜索 &f" + keyword,
                "&8" + "▬".repeat(10),
                "&7找到 &f" + hits.size() + " &7个商品"));
        int pages = Math.max(1, (hits.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(1, Math.min(page, pages));
        int from = (page - 1) * PAGE_SIZE;
        int to = Math.min(hits.size(), from + PAGE_SIZE);
        for (int i = from; i < to; i++) {
            Hit hit = hits.get(i);
            inventory.setItem(ITEM_START + i - from, icon(hit));
        }
        if (hits.isEmpty()) {
            inventory.setItem(22, button(Material.COMPASS, "&7没有找到商品", "&7换个关键词再试试"));
        }
        if (page > 1) inventory.setItem(45, button(Material.ARROW, "&e« 上一页"));
        inventory.setItem(49, button(Material.PAPER,
                "&f第 &a" + page + " &f/ &a" + pages + " &f页",
                "&a" + GuiStyle.pageBar(page, pages) + " &7翻页",
                "&7共 &f" + hits.size() + " &7个结果"));
        if (page < pages) inventory.setItem(53, button(Material.ARROW, "&e下一页 »"));
        inventory.setItem(48, button(Material.BARRIER, "&c关闭"));
        inventory.setItem(50, button(Material.COMPASS, "&e« 返回主菜单", "&7回到商店首页"));
    }

    private @NotNull ItemStack icon(@NotNull Hit hit) {
        ItemStack stack = hit.item.display();
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            boolean selling = hit.shop.type() == Shop.Type.SELL;
            Currency currency = plugin.currencies().resolve(hit.item.currencyId());
            double price = selling ? hit.item.sellPrice(viewer) : hit.item.buyPrice(viewer);
            String priceText = currency == null
                    ? Currency.formatNumber(price) : currency.format(price);
            List<String> lore = new ArrayList<>();
            meta.setDisplayName(Text.color("&f" + hit.item.displayName()));
            lore.add(Text.color("&7商店 &f" + hit.shop.name()));
            lore.add(Text.color("&7类型 &f" + (selling ? "收购" : "购买")));
            lore.add(Text.color("&7价格 &e" + priceText));
            if (hit.item.isLimitedStock()) {
                int stock = hit.item.stock();
                String color = stock <= 0 ? "&c" : stock <= Math.max(1, hit.item.maxStock()) / 4 ? "&e" : "&a";
                lore.add(Text.color("&7库存 " + color + stock + "&7/&f" + hit.item.maxStock()));
            }
            lore.add(Text.color(GuiStyle.SEP));
            lore.add(Text.color("&a➤ 点击进入商店"));
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public void onClick(int slot) {
        if (slot == 45 && page > 1) {
            page--;
            Sounds.play(viewer, "page");
            render();
        } else if (slot == 53) {
            page++;
            Sounds.play(viewer, "page");
            render();
        } else if (slot == 48) {
            viewer.closeInventory();
        } else if (slot == 50) {
            plugin.gui().openEntry(viewer);
        } else if (slot >= ITEM_START && slot < ITEM_START + PAGE_SIZE) {
            int index = (page - 1) * PAGE_SIZE + (slot - ITEM_START);
            if (index < hits.size()) {
                plugin.gui().open(viewer, hits.get(index).shop);
            }
        }
    }

    private static @NotNull ItemStack button(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(name));
            if (lore.length > 0) {
                meta.setLore(java.util.Arrays.stream(lore).map(Text::color).toList());
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public @NotNull Player viewer() {
        return viewer;
    }

    private record Hit(Shop shop, ShopItem item) {
    }
}
