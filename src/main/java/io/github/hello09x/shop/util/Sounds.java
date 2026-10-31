package io.github.hello09x.shop.util;

import io.github.hello09x.shop.ShopPlugin;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 音效播放工具。
 *
 * <p>音效名字写在 {@code config.yml} 的 {@code sound} 段里，
 * 用原版音效 key（例如 {@code entity.experience_orb.pickup}），
 * 也可以填资源包自定义音效。</p>
 *
 * <p>用 {@code Player#playSound(Location, String, float, float)} 这个字符串重载，
 * 不依赖 {@code org.bukkit.Sound} 枚举，跨版本最稳。</p>
 *
 * <p>带一个防重复保护：同一玩家在 {@value #DEDUP_MS} 毫秒内，
 * 同一个音效只播一次，避免一次操作因为代码路径重叠而响两遍。</p>
 */
public final class Sounds {

    /** 防重复窗口（毫秒） */
    private static final long DEDUP_MS = 150L;

    /** player -> (音效名 -> 上次播放时间) */
    private static final Map<UUID, Map<String, Long>> LAST = new ConcurrentHashMap<>();

    private Sounds() {
    }

    /**
     * 播放配置里的某个音效
     *
     * @param key config.yml 里 {@code sound.<key>} 的键名
     */
    public static void play(@NotNull Player player, @NotNull String key) {
        ShopPlugin plugin = ShopPlugin.getInstance();
        if (plugin == null || !plugin.config().soundEnabled()) {
            return;
        }
        String name = plugin.config().sound(key);
        if (name == null || name.isBlank()) {
            return;
        }
        playRaw(player, name, plugin.config().soundVolume(), plugin.config().soundPitch());
    }

    /**
     * 直接播放一个音效（带防重复）
     */
    public static void playRaw(@NotNull Player player, @NotNull String name,
                               float volume, float pitch) {
        long now = System.currentTimeMillis();
        Map<String, Long> mine = LAST.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>());
        Long last = mine.get(name);
        if (last != null && now - last < DEDUP_MS) {
            return;   // 太近了，忽略这条重复
        }
        mine.put(name, now);

        // 清理：只保留最近 2 秒的记录，防止内存膨胀
        if (mine.size() > 16) {
            mine.entrySet().removeIf(e -> now - e.getValue() > 2000L);
        }

        try {
            player.playSound(player.getLocation(), name, volume, pitch);
        } catch (Throwable ignored) {
            // 名字写错或客户端不支持 —— 静默跳过，不影响交易
        }
    }

}
