package io.github.hello09x.shop.currency;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * 一种货币的抽象。
 *
 * <p>实现必须是线程安全的（GUI 点击都在主线程，但行情刷新可能在异步线程）。</p>
 */
public interface Currency {

    /**
     * 货币唯一 ID（配置里引用它），例如 {@code vault} / {@code points}
     */
    @NotNull
    String id();

    /**
     * 显示名，用于消息与 lore，例如 {@code 金币}
     */
    @NotNull
    String displayName();

    /**
     * 该货币当前是否可用（依赖插件是否已加载）
     */
    boolean available();

    /**
     * 查询余额
     */
    double balance(@NotNull OfflinePlayer player);

    /**
     * 是否够扣
     */
    default boolean has(@NotNull OfflinePlayer player, double amount) {
        return balance(player) >= amount;
    }

    /**
     * 扣款，返回是否成功
     */
    boolean withdraw(@NotNull OfflinePlayer player, double amount);

    /**
     * 入账（出售时用）
     */
    boolean deposit(@NotNull OfflinePlayer player, double amount);

    /**
     * 把金额格式化成可读字符串
     */
    default @NotNull String format(double amount) {
        return formatNumber(amount) + " " + displayName();
    }

    static @NotNull String formatNumber(double amount) {
        if (amount == Math.floor(amount) && !Double.isInfinite(amount)) {
            return String.valueOf((long) amount);
        }
        return String.format(java.util.Locale.ROOT, "%.2f", amount);
    }

    /**
     * 该货币是否需要玩家在线（PAPI 变量类需要）
     */
    default boolean requiresOnline() {
        return false;
    }

    default boolean supportsDeposit() {
        return true;
    }

    @SuppressWarnings("unused")
    default void invalidate(@NotNull Player player) {
        // 可选：清理缓存
    }

}
