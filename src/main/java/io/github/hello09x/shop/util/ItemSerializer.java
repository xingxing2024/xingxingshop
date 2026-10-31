package io.github.hello09x.shop.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 物品构建工具。
 */
public final class ItemSerializer {

    private ItemSerializer() {
    }

    public static @NotNull ItemStack buildDisplay(
            @NotNull Material material,
            int amount,
            @Nullable String name,
            @Nullable List<String> lore,
            int customModelData,
            boolean glow
    ) {
        ItemStack stack = new ItemStack(material, Math.max(1, Math.min(amount, material.getMaxStackSize())));
        return decorate(stack, name, lore, customModelData, glow);
    }

    /**
     * 给现成的物品（包括 ItemsAdder / Nexo / MMOItems 物品）加显示名和 lore。
     */
    public static @NotNull ItemStack decorate(
            @NotNull ItemStack source,
            @Nullable String name,
            @Nullable List<String> lore,
            int customModelData,
            boolean glow
    ) {
        ItemStack stack = source.clone();
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        if (name != null && !name.isBlank()) {
            meta.setDisplayName(Text.color(name));
        }
        if (lore != null && !lore.isEmpty()) {
            List<String> colored = new ArrayList<>(lore.size());
            for (String line : lore) {
                colored.add(Text.color(line));
            }
            meta.setLore(colored);
        }
        if (customModelData > 0) {
            try {
                meta.setCustomModelData(customModelData);
            } catch (Throwable ignored) {
                // 老版本不支持就算了
            }
        }
        if (glow) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        if (meta instanceof SkullMeta skull) {
            // 需要指定头颅主人时由调用方另行设置
            skull.setOwningPlayer(Bukkit.getOfflinePlayer("Notch"));
        }
        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * DIAMOND_SWORD -> Diamond Sword
     */
    public static @NotNull String prettyName(@NotNull Material material) {
        String[] parts = material.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    @SuppressWarnings("unused")
    private static @NotNull String unused(OfflinePlayer p) {
        return p.getName() == null ? "" : p.getName();
    }

}
