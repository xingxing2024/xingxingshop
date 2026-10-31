package io.github.hello09x.shop.currency;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;

/**
 * Vault 经济货币（EssentialsX / CMI / XConomy 等）。
 *
 * <p>通过 {@link RegisteredServiceProvider} 获取 {@link Economy}，
 * 插件缺失或没有经济实现时 {@link #available()} 返回 {@code false}。</p>
 */
public final class VaultCurrency implements Currency {

    private final String id;
    private final String displayName;
    private Economy economy;

    public VaultCurrency(@NotNull String id, @NotNull String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    /**
     * 探测 Vault 是否可用，返回是否成功
     */
    public boolean detect() {
        this.economy = null;
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        try {
            RegisteredServiceProvider<Economy> rsp =
                    Bukkit.getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                this.economy = rsp.getProvider();
            }
        } catch (Throwable ignored) {
            // Vault 未安装或没有经济实现
        }
        return this.economy != null;
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
        return economy != null;
    }

    @Override
    public double balance(@NotNull OfflinePlayer player) {
        return economy == null ? 0D : economy.getBalance(player);
    }

    @Override
    public boolean withdraw(@NotNull OfflinePlayer player, double amount) {
        if (economy == null) {
            return false;
        }
        try {
            return economy.withdrawPlayer(player, amount).transactionSuccess();
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean deposit(@NotNull OfflinePlayer player, double amount) {
        if (economy == null) {
            return false;
        }
        try {
            return economy.depositPlayer(player, amount).transactionSuccess();
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public @NotNull String format(double amount) {
        if (economy != null) {
            try {
                return economy.format(amount);
            } catch (Throwable ignored) {
                // 退回默认格式
            }
        }
        return Currency.super.format(amount);
    }

}
