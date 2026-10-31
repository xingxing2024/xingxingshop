package io.github.hello09x.shop.log;

import io.github.hello09x.shop.ShopPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 个人日报「投递记录」。
 *
 * <p>记录每个玩家最后一次收到个人日报是哪一天，用来判断下次上线要不要补发。</p>
 *
 * <p>数据存在数据库 {@code shop_report_seen} 表；数据库不可用时退回内存
 * （重启会丢，但只影响补发，不影响交易数据）。</p>
 */
public final class ReportDeliveryStore {

    /** 补发最多回溯多少天（超过就丢弃） */
    public static final int RETENTION_DAYS = 30;

    private final ShopPlugin plugin;
    /** playerUUID -> 最后收到日报的日期 */
    private final Map<UUID, LocalDate> lastDelivered = new ConcurrentHashMap<>();

    public ReportDeliveryStore(@NotNull ShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        lastDelivered.clear();
        var db = plugin.transactionLog().database();
        if (!db.available()) {
            return;
        }
        Map<String, String> rows = db.loadReportSeen();
        for (var e : rows.entrySet()) {
            try {
                lastDelivered.put(UUID.fromString(e.getKey()), LocalDate.parse(e.getValue()));
            } catch (Exception ignored) {
                // 脏数据跳过
            }
        }
        if (!rows.isEmpty()) {
            plugin.getLogger().info("已载入 " + rows.size() + " 条日报投递记录");
        }
    }

    /** 最后一次收到日报的日期，可能为 null（从没收到过） */
    public @Nullable LocalDate last(@NotNull UUID uuid) {
        return lastDelivered.get(uuid);
    }

    /** 记下已经投递到哪一天 */
    public void mark(@NotNull UUID uuid, @NotNull LocalDate day) {
        LocalDate old = lastDelivered.get(uuid);
        if (old != null && !day.isAfter(old)) {
            return;   // 不回退
        }
        lastDelivered.put(uuid, day);
        plugin.transactionLog().database().upsertReportSeen(uuid.toString(), day.toString());
    }

}
