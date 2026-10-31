package io.github.hello09x.shop.gui;

import io.github.hello09x.shop.ShopPlugin;
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

/** 操作说明界面。 */
public final class HelpMenu implements InventoryHolder {

    private final Inventory inventory;
    private final Player viewer;
    private final ShopPlugin plugin;

    public HelpMenu(@NotNull ShopPlugin plugin, @NotNull Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, 54, Text.color("&8操作说明"));
        render();
    }

    private void render() {
        inventory.clear();
        GuiStyle.fill(inventory, 9, 45);
        GuiStyle.frame(inventory, Material.CYAN_STAINED_GLASS_PANE);

        inventory.setItem(4, button(Material.KNOWLEDGE_BOOK, "&b&l操作说明", "&7点击下方图标查看对应操作"));

        inventory.setItem(10, button(Material.EMERALD,
                "&a&l购买商店",
                "&e左键 &7直接买 1 个",
                "&e右键 &7买 1 组 (64) &8[需确认]",
                "&eShift+左键 &7输入数量",
                "&eF 键 &7加入购物车"));

        inventory.setItem(12, button(Material.HOPPER,
                "&6&l收购商店",
                "&e左键 &7直接卖 1 个",
                "&e右键 &7全部卖出 &8[需确认]",
                "&eShift+左键 &7输入数量",
                "&eShift+右键 &7收藏 / 取消收藏"));

        inventory.setItem(14, button(Material.CHEST_MINECART,
                "&b&l购物车",
                "&e左键商品 &7设置数量",
                "&e右键商品 &7数量 -1",
                "&eQ 键 &7移除这一项",
                "&e底部 &7可一键结算或清空"));

        inventory.setItem(16, button(Material.COMPASS,
                "&e&l搜索",
                "&7点击主菜单或商店里的指南针",
                "&7输入关键词即可搜索全服商品",
                "&8也可以直接 /shop search <关键词>"));

        inventory.setItem(22, button(Material.ARROW, "&e« 返回主菜单", "&7回到商店首页"));
        inventory.setItem(49, button(Material.BARRIER, "&c关闭", "&7点击关闭界面"));
    }

    public void onClick(int slot) {
        if (slot == 22) {
            plugin.gui().openEntry(viewer);
        } else if (slot == 49) {
            viewer.closeInventory();
        }
    }

    private static @NotNull ItemStack button(Material material, String name, String... lore) {
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

    public @NotNull Player viewer() {
        return viewer;
    }
}
