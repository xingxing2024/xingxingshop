package io.github.hello09x.shop.log;

import io.github.hello09x.shop.ShopPlugin;
import io.github.hello09x.shop.shop.ShopItem;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 交易日志。
 *
 * <p>记录每一笔买卖，用于管理员排查刷钱。</p>
 *
 * <p>输出：</p>
 * <ul>
 *     <li>{@code logs/transactions-YYYY-MM-DD.log} —— 按天分文件的文本日志</li>
 *     <li>内存里保留最近 N 条，供 {@code /shop log} 查询</li>
 * </ul>
 */
public final class TransactionLog {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 内存里保留的最近交易条数 */
    private static final int MEMORY_LIMIT = 2000;

    private final ShopPlugin plugin;
    private final Path logDir;
    private final TransactionDatabase database;

    /** 最近交易（新的在前） */
    private final Deque<Record> recent = new ConcurrentLinkedDeque<>();

    /** 玩家 → 最近一次交易时间戳（用于刷屏/高频检测） */
    private final Map<UUID, Deque<Long>> rateWindow = new ConcurrentHashMap<>();

    public TransactionLog(@NotNull ShopPlugin plugin) {
        this.plugin = plugin;
        this.logDir = plugin.getDataFolder().toPath().resolve("logs");
        try {
            Files.createDirectories(logDir);
        } catch (IOException e) {
            plugin.getLogger().warning("无法创建日志目录：" + e.getMessage());
        }
        this.database = new TransactionDatabase(plugin);
    }

    /** 数据库实例 */
    public @NotNull TransactionDatabase database() {
        return database;
    }

    /**
     * 初始化数据库（同步调用，SQLite 很快；MySQL 稍慢但可接受）
     */
    public void initDatabase() {
        database.init();
    }

    /**
     * 一条交易记录
     */
    public record Record(
            long timestamp,
            @NotNull String playerName,
            @NotNull UUID playerUuid,
            @NotNull String shopId,
            @NotNull String itemId,
            @NotNull String itemName,
            @NotNull String currency,
            boolean buying,
            int quantity,
            double unitPrice,
            double total,
            /** 是否管理员操作（管理员交易不计入日报统计） */
            boolean admin
    ) {
        public @NotNull String toLogLine() {
            LocalDateTime t = LocalDateTime.from(
                    java.time.Instant.ofEpochMilli(timestamp).atZone(java.time.ZoneId.systemDefault()));
            return String.format("%s | %-16s | %-4s | %-12s | %-20s | %5d 个 | 单价 %10.4f | 总价 %10.2f%s",
                    t.format(TIME),
                    playerName,
                    buying ? "购买" : "出售",
                    shopId,
                    itemName,
                    quantity,
                    unitPrice,
                    total,
                    (admin ? "  [管理员]" : "") + " | UUID=" + playerUuid + " | 商品ID=" + itemId + " | 货币=" + currency);
        }
    }

    /**
     * 记一笔交易
     */
    public void record(
            @NotNull Player player,
            @NotNull String shopId,
            @NotNull ShopItem item,
            @NotNull String itemName,
            @Nullable String currencyId,
            boolean buying,
            int quantity,
            double unitPrice,
            double total
    ) {
        Record rec = new Record(
                System.currentTimeMillis(),
                player.getName(),
                player.getUniqueId(),
                shopId,
                item.itemId(),
                itemName,
                currencyId == null ? plugin.config().currencyDefault() : currencyId,
                buying,
                quantity,
                unitPrice,
                total,
                // 管理员交易会被标记，日报统计时跳过
                player.hasPermission("xshop.admin")
        );

        // 1) 内存
        recent.addFirst(rec);
        while (recent.size() > MEMORY_LIMIT) {
            recent.removeLast();
        }

        // 2) 文件 + 数据库
        if (plugin.config().logEnabled()) {
            writeToFile(rec);
            // 数据库写入内部是单线程队列，立即返回，不阻塞主线程
            database.insert(rec);
        }

        // 3) 异常检测
        checkAnomaly(player, rec);
    }

    /** 登记已完成的外部交易，不扣款、不发货；由调用者在主线程确认成交后调用。 */
    public void recordExternal(Player player, String source, String itemId, String itemName,
                               String currency, boolean buying, int quantity, double total) {
        if (!source.matches("[A-Za-z0-9_-]{1,48}") || itemId.isBlank() || itemId.length() > 64
                || itemName.isBlank() || itemName.length() > 64 || currency.isBlank() || currency.length() > 32
                || quantity <= 0 || !Double.isFinite(total) || total < 0) {
            throw new IllegalArgumentException("外部交易参数无效");
        }
        Record rec = new Record(System.currentTimeMillis(), player.getName(), player.getUniqueId(),
                "external:" + source, itemId, itemName, currency, buying, quantity, total / quantity, total, false);
        recent.addFirst(rec);
        while (recent.size() > MEMORY_LIMIT) recent.pollLast();
        writeToFile(rec);
        database.insert(rec);
        checkAnomaly(player, rec);
    }

    private void writeToFile(@NotNull Record rec) {
        LocalDateTime t = LocalDateTime.from(
                java.time.Instant.ofEpochMilli(rec.timestamp()).atZone(java.time.ZoneId.systemDefault()));
        Path file = logDir.resolve("transactions-" + t.format(DATE) + ".log");
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            w.write(rec.toLogLine());
            w.newLine();
        } catch (IOException e) {
            plugin.getLogger().warning("写交易日志失败：" + e.getMessage());
        }
    }

    // ================================================================
    //  异常检测
    // ================================================================

    /**
     * 检测可疑行为并提醒管理员
     *
     * <p>规则：</p>
     * <ol>
     *     <li>10 秒内交易超过 {@code alert.rate-limit} 笔 —— 高频刷单</li>
     *     <li>单笔金额超过 {@code alert.amount} —— 大额交易</li>
     * </ol>
     */
    private void checkAnomaly(@NotNull Player player, @NotNull Record rec) {
        var cfg = plugin.config();
        if (!cfg.logAlert()) {
            return;
        }

        // ---- 规则 1：高频 ----
        long now = System.currentTimeMillis();
        Deque<Long> window = rateWindow.computeIfAbsent(player.getUniqueId(),
                k -> new ConcurrentLinkedDeque<>());
        window.addLast(now);
        // 只保留 10 秒内的
        while (!window.isEmpty() && now - window.peekFirst() > 10_000L) {
            window.pollFirst();
        }
        int limit = cfg.logAlertRate();
        if (limit > 0 && window.size() == limit) {
            alert(player, rec, String.format("10 秒内交易 %d 笔", window.size()));
        }

        // ---- 规则 2：大额 ----
        double amount = cfg.logAlertAmount();
        if (amount > 0 && rec.total() >= amount) {
            alert(player, rec, String.format("单笔金额 %.2f", rec.total()));
        }
    }

    private void alert(@NotNull Player player, @NotNull Record rec, @NotNull String reason) {
        String msg = plugin.messages().raw("log.alert")
                .replace("{player}", player.getName())
                .replace("{reason}", reason)
                .replace("{detail}", rec.toLogLine());

        for (var online : plugin.getServer().getOnlinePlayers()) {
            if (online.hasPermission("xshop.admin")) {
                online.sendMessage(io.github.hello09x.shop.util.Text.color(msg));
            }
        }
        plugin.getLogger().warning("[可疑交易] " + reason + " -> " + rec.toLogLine());
    }

    // ================================================================
    //  查询
    // ================================================================

    /**
     * 查某个玩家的最近交易
     *
     * <p>优先走数据库；数据库不可用时退回内存缓存。</p>
     *
     * @param playerName 玩家名（null 表示所有人）
     * @param page       第几页（从 1 开始）
     * @param pageSize   每页条数
     */
    public @NotNull List<Record> query(@Nullable String playerName, int page, int pageSize) {
        if (database.available()) {
            return database.query(new TransactionDatabase.Query(playerName, null, 0), page, pageSize);
        }
        List<Record> all = new ArrayList<>();
        for (Record r : recent) {
            if (playerName == null || r.playerName().equalsIgnoreCase(playerName)) {
                all.add(r);
            }
        }
        int from = Math.max(0, (page - 1) * pageSize);
        if (from >= all.size()) {
            return List.of();
        }
        int to = Math.min(all.size(), from + pageSize);
        return all.subList(from, to);
    }

    /**
     * 某玩家在日志里的总条数
     */
    public int count(@Nullable String playerName) {
        if (database.available()) {
            return database.count(new TransactionDatabase.Query(playerName, null, 0));
        }
        int n = 0;
        for (Record r : recent) {
            if (playerName == null || r.playerName().equalsIgnoreCase(playerName)) {
                n++;
            }
        }
        return n;
    }

    /**
     * 某玩家今天买/卖了多少个某种物品（辅助判断刷钱）
     */
    public int countToday(@NotNull UUID uuid, @NotNull String itemId, boolean buying) {
        if (database.available()) {
            return database.countToday(uuid, itemId, buying);
        }
        long dayStart = java.time.LocalDate.now()
                .atStartOfDay(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli();
        int n = 0;
        for (Record r : recent) {
            if (r.timestamp() < dayStart) {
                continue;
            }
            if (r.playerUuid().equals(uuid) && r.itemId().equals(itemId) && r.buying() == buying) {
                n += r.quantity();
            }
        }
        return n;
    }

    /** 关闭数据库连接 */
    public void close() {
        database.close();
    }

    /** 日志目录 */
    public @NotNull File folder() {
        return logDir.toFile();
    }

}
