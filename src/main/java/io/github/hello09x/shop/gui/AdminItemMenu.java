package io.github.hello09x.shop.gui;

import io.github.hello09x.shop.ShopPlugin;
import io.github.hello09x.shop.shop.Shop;
import io.github.hello09x.shop.shop.ShopItem;
import io.github.hello09x.shop.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 管理员商品编辑面板。 */
public final class AdminItemMenu implements InventoryHolder {

    private final ShopPlugin plugin;
    private final Shop shop;
    private final ShopItem item;
    private final Player viewer;
    private final Inventory inventory;

    public AdminItemMenu(@NotNull ShopPlugin plugin, @NotNull Shop shop,
                         @NotNull ShopItem item, @NotNull Player viewer) {
        this.plugin = plugin;
        this.shop = shop;
        this.item = item;
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, 54,
                Text.color("&8编辑 &7» &f" + item.itemId()));
        render();
    }

    public void render() {
        inventory.clear();
        GuiStyle.fill(inventory, 9, 45);
        GuiStyle.frame(inventory, Material.CYAN_STAINED_GLASS_PANE);
        ItemStack icon = item.display();
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setLore(List.of(
                    Text.color("&7商店 &f" + shop.id()),
                    Text.color("&7商品 ID &f" + item.itemId()),
                    Text.color("&7买价 &f" + item.baseBuyPrice()),
                    Text.color("&7卖价 &f" + item.sellPrice()),
                    Text.color("&7库存 &f" + item.stock() + " &7/ &f" + item.maxStock()),
                    Text.color("&8点击下方按钮修改")
            ));
            icon.setItemMeta(meta);
        }
        inventory.setItem(13, GuiStyle.glow(icon));

        inventory.setItem(20, button(Material.RED_CONCRETE, "&c价格 -10"));
        inventory.setItem(21, button(Material.RED_STAINED_GLASS_PANE, "&c价格 -1"));
        inventory.setItem(22, button(Material.LIME_STAINED_GLASS_PANE, "&a价格 +1"));
        inventory.setItem(23, button(Material.LIME_CONCRETE, "&a价格 +10"));

        inventory.setItem(29, button(Material.RED_CONCRETE, "&c库存 -10"));
        inventory.setItem(30, button(Material.RED_STAINED_GLASS_PANE, "&c库存 -1"));
        inventory.setItem(31, button(Material.LIME_STAINED_GLASS_PANE, "&a库存 +1"));
        inventory.setItem(32, button(Material.LIME_CONCRETE, "&a库存 +10"));

        inventory.setItem(49, button(Material.BARRIER, "&c关闭"));
    }

    public void onClick(int slot) {
        switch (slot) {
            case 20 -> changePrice(-10);
            case 21 -> changePrice(-1);
            case 22 -> changePrice(1);
            case 23 -> changePrice(10);
            case 29 -> changeStock(-10);
            case 30 -> changeStock(-1);
            case 31 -> changeStock(1);
            case 32 -> changeStock(10);
            case 49 -> viewer.closeInventory();
            default -> {
            }
        }
    }

    private void changePrice(double delta) {
        if (shop.type() == Shop.Type.SELL) {
            item.setSellPrice(Math.max(0, item.sellPrice() + delta));
        } else {
            item.setBaseBuyPrice(Math.max(0, item.baseBuyPrice() + delta));
        }
        writePrice();
        render();
    }

    private void changeStock(int delta) {
        int max = item.maxStock();
        int value = Math.max(0, item.stock() + delta);
        if (max > 0) {
            value = Math.min(max, value);
        }
        item.stock(value);
        plugin.stocks().saveItem(item);
        render();
    }

    private void writePrice() {
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(plugin.shops().fileOf(shop.id()));
            String key = shop.type() == Shop.Type.SELL ? "sell" : "buy";
            yaml.set("items." + item.itemId() + "." + key,
                    shop.type() == Shop.Type.SELL ? item.sellPrice() : item.baseBuyPrice());
            yaml.save(plugin.shops().fileOf(shop.id()));
        } catch (Exception e) {
            plugin.messages().send(viewer, "failed");
            plugin.getLogger().warning("保存商品价格失败：" + e.getMessage());
        }
    }

    private static @NotNull ItemStack button(@NotNull Material material, @NotNull String name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(name));
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
}
