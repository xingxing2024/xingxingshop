package io.github.hello09x.shop.currency;

import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * PlayerPoints 点券货币。
 *
 * <p>运行时用反射拿 {@code PlayerPointsAPI}，避免插件缺失时抛
 * {@code NoClassDefFoundError}。</p>
 */
public final class PlayerPointsCurrency implements Currency {

    private final String id;
    private final String displayName;
    private Object api;

    public PlayerPointsCurrency(@NotNull String id, @NotNull String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public boolean detect() {
        this.api = null;
        Plugin plugin = Bukkit.getPluginManager().getPlugin("PlayerPoints");
        if (plugin == null || !plugin.isEnabled()) {
            return false;
        }
        try {
            Object instance = PlayerPoints.class.getMethod("getInstance").invoke(null);
            if (instance == null) {
                return false;
            }
            this.api = instance.getClass().getMethod("getAPI").invoke(instance);
        } catch (Throwable ignored) {
            this.api = null;
        }
        return this.api != null;
    }

    private int points(@NotNull OfflinePlayer player) {
        if (api == null) {
            return 0;
        }
        try {
            Object result = api.getClass().getMethod("look", UUID.class).invoke(api, player.getUniqueId());
            return result instanceof Integer i ? i : 0;
        } catch (Throwable t) {
            return 0;
        }
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
        return api != null;
    }

    @Override
    public double balance(@NotNull OfflinePlayer player) {
        return points(player);
    }

    @Override
    public boolean withdraw(@NotNull OfflinePlayer player, double amount) {
        if (api == null) {
            return false;
        }
        int cost = (int) Math.ceil(amount);
        try {
            Object result = api.getClass()
                    .getMethod("take", UUID.class, int.class)
                    .invoke(api, player.getUniqueId(), cost);
            return result instanceof Boolean b && b;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean deposit(@NotNull OfflinePlayer player, double amount) {
        if (api == null) {
            return false;
        }
        int gain = (int) Math.floor(amount);
        if (gain <= 0) {
            return true;
        }
        try {
            Object result = api.getClass()
                    .getMethod("give", UUID.class, int.class)
                    .invoke(api, player.getUniqueId(), gain);
            return result instanceof Boolean b && b;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public @NotNull String format(double amount) {
        return Currency.formatNumber(amount) + " " + displayName();
    }

}
