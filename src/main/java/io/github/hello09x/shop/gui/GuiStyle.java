package io.github.hello09x.shop.gui;

import io.github.hello09x.shop.util.Text;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** 各菜单共用的视觉样式：渐变边框、分隔线、进度条。 */
public final class GuiStyle {

    public static final String SEP = "&8▬▬▬▬▬▬▬▬▬▬▬▬";

    private static final Material DARK = Material.BLACK_STAINED_GLASS_PANE;
    private static final Material MID = Material.GRAY_STAINED_GLASS_PANE;

    private GuiStyle() {
    }

    /** 54 / 45 格菜单的整体边框：深浅交替，四角用主题色。 */
    public static void frame(@NotNull Inventory inventory, @NotNull Material accent) {
        int rows = inventory.getSize() / 9;
        int end = inventory.getSize();
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, pane(i % 2 == 0 ? DARK : MID));
            inventory.setItem(end - 9 + i, pane(i % 2 == 0 ? MID : DARK));
        }
        for (int row = 1; row < rows - 1; row++) {
            inventory.setItem(row * 9, pane(row % 2 == 0 ? DARK : MID));
            inventory.setItem(row * 9 + 8, pane(row % 2 == 0 ? MID : DARK));
        }
        inventory.setItem(0, pane(accent));
        inventory.setItem(8, pane(accent));
        inventory.setItem(end - 9, pane(accent));
        inventory.setItem(end - 1, pane(accent));
    }

    /** 只画左右两条边框，保留上下两行给标题和操作条。 */
    public static void sides(@NotNull Inventory inventory, @NotNull Material accent) {
        int rows = inventory.getSize() / 9;
        for (int row = 1; row < rows - 1; row++) {
            inventory.setItem(row * 9, pane(row % 2 == 0 ? DARK : MID));
            inventory.setItem(row * 9 + 8, pane(row % 2 == 0 ? MID : DARK));
        }
    }

    /** 把一段槽位铺满深色底，用于商品区和操作条。 */
    public static void fill(@NotNull Inventory inventory, int from, int to) {
        for (int slot = from; slot < to; slot++) {
            inventory.setItem(slot, pane(DARK));
        }
    }

    /** 生成 `▰▰▰▱▱` 进度条。 */
    public static @NotNull String bar(int value, int max, int length) {
        int safeMax = Math.max(1, max);
        int filled = (int) Math.round((double) Math.max(0, Math.min(value, safeMax)) / safeMax * length);
        return "▰".repeat(filled) + "▱".repeat(Math.max(0, length - filled));
    }

    /** 分页进度条。 */
    public static @NotNull String pageBar(int page, int pages) {
        return bar(page, Math.max(1, pages), 5);
    }

    public static @NotNull ItemStack pane(@NotNull Material material) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            meta.setLore(List.of());
            meta.addItemFlags(ItemFlag.values());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static @NotNull ItemStack button(@NotNull Material material, @NotNull String name,
                                            @NotNull String... lore) {
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

    /** 让物品带附魔光效，但不显示附魔文字。 */
    public static @NotNull ItemStack glow(@NotNull ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setEnchantmentGlintOverride(true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
