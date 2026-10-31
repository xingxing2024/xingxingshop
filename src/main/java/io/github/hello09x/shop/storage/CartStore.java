package io.github.hello09x.shop.storage;

import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 玩家购物车（内存，重启清空）。 */
public final class CartStore {

    private final Map<UUID, Map<String, Integer>> carts = new ConcurrentHashMap<>();

    public void add(@NotNull UUID player, @NotNull String itemKey, int quantity) {
        carts.computeIfAbsent(player, k -> new LinkedHashMap<>())
                .merge(itemKey, Math.min(100_000, Math.max(1, quantity)),
                        (a, b) -> Math.min(100_000, a + b));
    }

    public @NotNull Map<String, Integer> items(@NotNull UUID player) {
        Map<String, Integer> cart = carts.get(player);
        return cart == null ? Map.of() : new LinkedHashMap<>(cart);
    }

    public void clear(@NotNull UUID player) {
        carts.remove(player);
    }

    public void remove(@NotNull UUID player, @NotNull String itemKey) {
        Map<String, Integer> cart = carts.get(player);
        if (cart != null) {
            cart.remove(itemKey);
        }
    }

    /** 减少数量，减到 0 或以下就整项移除。 */
    public void remove(@NotNull UUID player, @NotNull String itemKey, int quantity) {
        Map<String, Integer> cart = carts.get(player);
        if (cart == null) {
            return;
        }
        Integer current = cart.get(itemKey);
        if (current == null) {
            return;
        }
        int left = current - Math.max(1, quantity);
        if (left <= 0) {
            cart.remove(itemKey);
        } else {
            cart.put(itemKey, left);
        }
    }

    public @NotNull List<String> keys(@NotNull UUID player) {
        return List.copyOf(items(player).keySet());
    }
}
