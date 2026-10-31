package io.github.hello09x.shop.shop;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 一个商店（对应 {@code shops/某个文件.yml}）。
 */
public final class Shop {

    /**
     * 商店类型：购买（玩家花钱买） / 收购（玩家卖东西换钱）
     */
    public enum Type {
        BUY,
        SELL
    }

    private final String id;
    private final String name;
    private final Material icon;
    private final List<String> desc;
    private final String permission;
    private final Type type;

    private final List<ShopItem> items = new ArrayList<>();

    public Shop(
            @NotNull String id,
            @NotNull String name,
            @NotNull Material icon,
            @NotNull List<String> desc,
            @Nullable String permission,
            @NotNull Type type
    ) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.desc = desc;
        this.permission = permission;
        this.type = type;
    }

    public void addItem(@NotNull ShopItem item) {
        items.add(item);
        items.sort(Comparator.comparingInt(ShopItem::slot));
    }

    public void removeItem(@NotNull String itemId) {
        items.removeIf(i -> i.itemId().equalsIgnoreCase(itemId));
    }

    public @Nullable ShopItem find(@NotNull String itemId) {
        return items.stream()
                .filter(i -> i.itemId().equalsIgnoreCase(itemId))
                .findFirst()
                .orElse(null);
    }

    public @Nullable ShopItem findByMaterial(@NotNull Material material) {
        return items.stream()
                .filter(i -> i.material() == material)
                .findFirst()
                .orElse(null);
    }

    public @NotNull String id() {
        return id;
    }

    public @NotNull String name() {
        return name;
    }

    public @NotNull Material icon() {
        return icon;
    }

    public @NotNull List<String> desc() {
        return desc;
    }

    public @Nullable String permission() {
        return permission;
    }

    public @NotNull Type type() {
        return type;
    }

    public @NotNull List<ShopItem> items() {
        return items;
    }

    /**
     * 按槽位顺序排列（GUI 用）
     */
    public @NotNull List<ShopItem> sortedItems() {
        List<ShopItem> copy = new ArrayList<>(items);
        copy.sort(Comparator.comparingInt(ShopItem::slot));
        return copy;
    }

}
