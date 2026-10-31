package io.github.hello09x.shop.listener;

import io.github.hello09x.shop.ShopPlugin;
import io.github.hello09x.shop.util.Schedulers;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jetbrains.annotations.NotNull;

/**
 * 玩家上线时补发错过的个人日报。
 */
public final class PlayerJoinListener implements Listener {

    private final ShopPlugin plugin;

    public PlayerJoinListener(@NotNull ShopPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(@NotNull PlayerJoinEvent event) {
        var player = event.getPlayer();
        // 延后 2 秒，等玩家完全进服再发，避免被登录消息刷掉
        Schedulers.entityLater(plugin, player, () -> {
            if (!player.isOnline()) {
                return;
            }
            try {
                plugin.dailyReport().deliverPending(player);
            } catch (Throwable t) {
                plugin.getLogger().warning("补发个人日报失败：" + t.getMessage());
            }
        }, 40L);
    }

}
