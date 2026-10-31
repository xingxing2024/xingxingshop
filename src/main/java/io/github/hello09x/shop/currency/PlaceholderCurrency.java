package io.github.hello09x.shop.currency;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * 基于 PlaceholderAPI 变量的货币。
 *
 * <p><b>只读</b>：PAPI 变量只能读余额，无法扣款，因此这类货币默认
 * 只能作为「展示 / 校验」用途。配置里把 {@code payable: false} 时
 * 扣款会直接失败并提示管理员改用可扣款的货币（Vault / PlayerPoints）。</p>
 *
 * <p>如果同时装了 PlayerPoints 等可扣款插件，建议直接使用对应货币 ID。</p>
 */
public final class PlaceholderCurrency implements Currency {

    private final String id;
    private final String displayName;
    private final String balanceExpression;
    private final boolean payable;
    private final double ratio;
    private boolean available;

    /**
     * @param balanceExpression 形如 {@code %playerpoints_points%}
     * @param payable           是否允许扣款
     * @param ratio             变量数值换算成货币单位的比例
     */
    public PlaceholderCurrency(
            @NotNull String id,
            @NotNull String displayName,
            @NotNull String balanceExpression,
            boolean payable,
            double ratio
    ) {
        this.id = id;
        this.displayName = displayName;
        this.balanceExpression = balanceExpression;
        this.payable = payable;
        this.ratio = ratio <= 0 ? 1D : ratio;
    }

    public boolean detect() {
        this.available = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null
                && balanceExpression.contains("%");
        return this.available;
    }

    @Override
    public @NotNull String id() {
        return id;
    }

    @Override
    public @NotNull String displayName() {
        return displayName;
    }

    @Override
    public boolean available() {
        return available;
    }

    @Override
    public double balance(@NotNull OfflinePlayer player) {
        if (!available || !(player instanceof Player online)) {
            return 0D;
        }
        try {
            String raw = PlaceholderAPI.setPlaceholders(online, balanceExpression);
            if (raw == null || raw.isEmpty()) {
                return 0D;
            }
            // 变量可能返回 "1,234" 或 "1.2k" 这类文本，这里只解析纯数字
            String cleaned = raw.replace(",", "").replace(" ", "").trim();
            StringBuilder sb = new StringBuilder();
            for (char c : cleaned.toCharArray()) {
                if (Character.isDigit(c) || c == '.' || c == '-') {
                    sb.append(c);
                } else {
                    break;
                }
            }
            if (sb.isEmpty()) {
                return 0D;
            }
            return Double.parseDouble(sb.toString()) * ratio;
        } catch (Throwable t) {
            return 0D;
        }
    }

    @Override
    public boolean withdraw(@NotNull OfflinePlayer player, double amount) {
        // PAPI 变量不可写
        return false;
    }

    @Override
    public boolean deposit(@NotNull OfflinePlayer player, double amount) {
        return false;
    }

    @Override
    public boolean requiresOnline() {
        return true;
    }

    @Override
    public boolean supportsDeposit() {
        return false;
    }

    public boolean isPayable() {
        return payable;
    }

    public @NotNull String getBalanceExpression() {
        return balanceExpression;
    }

}
