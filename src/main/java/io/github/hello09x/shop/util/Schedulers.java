package io.github.hello09x.shop.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * 调度器兼容层：同一套代码同时跑 Paper/Purpur 普通端和 Folia。
 *
 * <p>Folia 没有全局主线程，玩家相关操作必须调度到玩家所属区域。</p>
 */
public final class Schedulers {

    private static final boolean FOLIA = detectFolia();

    private Schedulers() {
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void global(@NotNull Plugin plugin, @NotNull Runnable task) {
        if (FOLIA) {
            Bukkit.getGlobalRegionScheduler().run(plugin, t -> task.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public static void globalLater(@NotNull Plugin plugin, @NotNull Runnable task, long delayTicks) {
        if (FOLIA) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> task.run(), Math.max(1, delayTicks));
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, Math.max(1, delayTicks));
        }
    }

    public static void globalTimer(@NotNull Plugin plugin, @NotNull Runnable task,
                                   long delayTicks, long periodTicks) {
        if (FOLIA) {
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> task.run(),
                    Math.max(1, delayTicks), Math.max(1, periodTicks));
        } else {
            Bukkit.getScheduler().runTaskTimer(plugin, task,
                    Math.max(1, delayTicks), Math.max(1, periodTicks));
        }
    }

    public static void async(@NotNull Plugin plugin, @NotNull Runnable task) {
        if (FOLIA) {
            Bukkit.getAsyncScheduler().runNow(plugin, t -> task.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    public static void asyncTimer(@NotNull Plugin plugin, @NotNull Runnable task,
                                  long delayTicks, long periodTicks) {
        if (FOLIA) {
            Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> task.run(),
                    Math.max(1, delayTicks) * 50L, Math.max(1, periodTicks) * 50L,
                    java.util.concurrent.TimeUnit.MILLISECONDS);
        } else {
            Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task,
                    Math.max(1, delayTicks), Math.max(1, periodTicks));
        }
    }

    /**
     * 在实体所属线程执行。Folia 会对玩家所在区域调度；普通端回主线程。
     */
    public static void entity(@NotNull Plugin plugin, @NotNull Entity entity, @NotNull Runnable task) {
        if (FOLIA) {
            entity.getScheduler().run(plugin, t -> task.run(), null);
        } else if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public static void entityLater(@NotNull Plugin plugin, @NotNull Entity entity,
                                   @NotNull Runnable task, long delayTicks) {
        if (FOLIA) {
            entity.getScheduler().runDelayed(plugin, t -> task.run(), null, Math.max(1, delayTicks));
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, Math.max(1, delayTicks));
        }
    }

}
