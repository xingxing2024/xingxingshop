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

/** 收藏夹界面。 */
public final class FavoriteMenu implements InventoryHolder {

    private static final int PAGE_SIZE = 36;
    private static final int ITEM_START = 9;
    private final ShopPlugin plugin;
    private final Player viewer;
    private final List<Hit> hits = new ArrayList<>();
    private final Inventory inventory;
    private int page = 1;

    public FavoriteMenu(@NotNull ShopPlugin plugin, @NotNull Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
        collect();
        this.inventory = Bukkit.createInventory(this, 54, Text.color("&8我的收藏"));
        render();
    }

    private void collect() {
        for (String key : plugin.favorites().list(viewer.getUniqueId())) {
            int split = key.indexOf(':');
            if (split <= 0) continue;
            Shop shop = plugin.shops().get(key.substring(0, split));
            ShopItem item = shop == null ? null : shop.find(key.substring(split + 1));
            if (shop != null && item != null && shop.isOpen()
                    && (shop.permission() == null || viewer.hasPermission(shop.permission()))
                    && (item.permission() == null || viewer.hasPermission(item.permission()))) {
                hits.add(new Hit(shop, item));
            }
        }
    }

    public void render() {
        inventory.clear();
        GuiStyle.fill(inventory, ITEM_START, ITEM_START + PAGE_SIZE);
        GuiStyle.frame(inventory, Material.CYAN_STAINED_GLASS_PANE);
        inventory.setItem(4, button(Material.HEART_OF_THE_SEA,
                "&b&l我的收藏",
                "&8" + "▬".repeat(10),
                "&7已收藏 &f" + hits.size() + " &7件"));
        int pages = Math.max(1, (hits.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(1, Math.min(page, pages));
        int from = (page - 1) * PAGE_SIZE;
        int to = Math.min(hits.size(), from + PAGE_SIZE);
        for (int i = from; i < to; i++) {
            Hit hit = hits.get(i);
            inventory.setItem(ITEM_START + i - from, icon(hit));
        }
        if (hits.isEmpty()) {
            inventory.setItem(22, button(Material.BOOK, "&7收藏夹是空的",
                    "&7在商店里 Shift+右键 商品即可收藏"));
        }
        if (page > 1) inventory.setItem(45, button(Material.ARROW, "&e« 上一页"));
        inventory.setItem(49, button(Material.PAPER,
                "&f第 &a" + page + " &f/ &a" + pages + " &f页",
                "&b" + GuiStyle.pageBar(page, pages) + " &7翻页",
                "&7共 &f" + hits.size() + " &7个收藏"));
        if (page < pages) inventory.setItem(53, button(Material.ARROW, "&e下一页 »"));
        inventory.setItem(48, button(Material.BARRIER, "&c关闭"));
        inventory.setItem(50, button(Material.COMPASS, "&e返回主菜单", "&7回到商店首页"));
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
            lore.add(Text.color("&8Shift+右键 在商店里取消收藏"));
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static ItemStack button(Material material, String name, String... lore) {
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
