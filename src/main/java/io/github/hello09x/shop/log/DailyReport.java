package io.github.hello09x.shop.log;

import io.github.hello09x.shop.ShopPlugin;
import io.github.hello09x.shop.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 每日报表。
 *
 * <p>两种：</p>
 * <ul>
 *     <li><b>全服日报</b> —— 给管理员看，写文件 + 发给在线管理员</li>
 *     <li><b>个人日报</b> —— 给每个玩家看自己的交易汇总，可推送 + 命令查询</li>
 * </ul>
 */
public final class DailyReport {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 统计结果（全服） */
    public record Summary(
            @NotNull LocalDate from,
            @NotNull LocalDate to,
            int totalCount,
            int buyCount,
            int sellCount,
            double buyAmount,
            double sellAmount,
            @NotNull Map<String, PlayerStat> players,
            @NotNull Map<String, ItemStat> items,
            @NotNull Map<String, Map<String, ItemStat>> playerItems
    ) {
        public double netOutflow() {
            return buyAmount - sellAmount;
        }

        /**
         * 这份报表代表的日期。
         *
         * <p>{@code to} 是「含头不含尾」的结束边界，所以实际业务日期是 {@code to - 1 天}。</p>
         */
        public @NotNull LocalDate reportDate() {
            return to.minusDays(1);
        }
    }

    /** 玩家统计 */
    public record PlayerStat(
            @NotNull String name,
            int buyTimes,
            int sellTimes,
            int buyCount,
            int sellCount,
            double buyAmount,
            double sellAmount
    ) {
        public double total() {
            return buyAmount + sellAmount;
        }

        public int totalTimes() {
            return buyTimes + sellTimes;
        }
    }

    /** 商品统计 */
    public record ItemStat(
            @NotNull String name,
            int buyCount,
            int sellCount
    ) {
        public int total() {
            return buyCount + sellCount;
        }
    }

    /** 单个玩家的日报 */
    public record PlayerSummary(
            @NotNull String playerName,
            @NotNull LocalDate from,
            @NotNull LocalDate to,
            int buyTimes,
            int sellTimes,
            int buyCount,
            int sellCount,
            double buyAmount,
            double sellAmount,
            @NotNull List<ItemStat> topBought,
            @NotNull List<ItemStat> topSold,
            int rank,
            int totalPlayers
    ) {
        /** 净收益（卖出 − 买入） */
        public double net() {
            return sellAmount - buyAmount;
        }

        public int totalTimes() {
            return buyTimes + sellTimes;
        }

        public boolean empty() {
            return buyTimes == 0 && sellTimes == 0;
        }
    }

    private final ShopPlugin plugin;

    public DailyReport(@NotNull ShopPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 汇总指定时间段
     *
     * @param from 起始（含）
     * @param to   结束（不含）
     */
    public @NotNull Summary summarize(@NotNull LocalDate from, @NotNull LocalDate to) {
        ZoneId zone = ZoneId.systemDefault();
        long start = from.atStartOfDay(zone).toInstant().toEpochMilli();
        long end = to.atStartOfDay(zone).toInstant().toEpochMilli();

        int total = 0, buyCount = 0, sellCount = 0;
        double buyAmount = 0, sellAmount = 0;
        Map<String, PlayerStat> players = new LinkedHashMap<>();
        Map<String, ItemStat> items = new LinkedHashMap<>();
        Map<String, Map<String, ItemStat>> playerItems = new LinkedHashMap<>();

        // 从数据库读（排除管理员交易）
        var db = plugin.transactionLog().database();
        List<TransactionLog.Record> records;
        if (db.available()) {
            records = queryRange(db, start, end);
        } else {
            records = plugin.transactionLog().query(null, 1, Integer.MAX_VALUE);
        }

        for (var r : records) {
            if (r.timestamp() < start || r.timestamp() >= end) {
                continue;
            }
            // 管理员交易不计入统计
            if (r.admin()) {
                continue;
            }
            total++;
            if (r.buying()) {
                buyCount += r.quantity();
                buyAmount += r.total();
            } else {
                sellCount += r.quantity();
                sellAmount += r.total();
            }

            // 玩家累计
            var ps = players.getOrDefault(r.playerName(),
                    new PlayerStat(r.playerName(), 0, 0, 0, 0, 0, 0));
            players.put(r.playerName(), new PlayerStat(
                    ps.name(),
                    ps.buyTimes() + (r.buying() ? 1 : 0),
                    ps.sellTimes() + (r.buying() ? 0 : 1),
                    ps.buyCount() + (r.buying() ? r.quantity() : 0),
                    ps.sellCount() + (r.buying() ? 0 : r.quantity()),
                    ps.buyAmount() + (r.buying() ? r.total() : 0),
                    ps.sellAmount() + (r.buying() ? 0 : r.total())
            ));

            // 全服商品累计
            var is = items.getOrDefault(r.itemName(), new ItemStat(r.itemName(), 0, 0));
            items.put(r.itemName(), new ItemStat(
                    is.name(),
                    is.buyCount() + (r.buying() ? r.quantity() : 0),
                    is.sellCount() + (r.buying() ? 0 : r.quantity())
            ));

            // 每个玩家的商品累计
            var perPlayer = playerItems.computeIfAbsent(r.playerName(), k -> new LinkedHashMap<>());
            var pis = perPlayer.getOrDefault(r.itemName(), new ItemStat(r.itemName(), 0, 0));
            perPlayer.put(r.itemName(), new ItemStat(
                    pis.name(),
                    pis.buyCount() + (r.buying() ? r.quantity() : 0),
                    pis.sellCount() + (r.buying() ? 0 : r.quantity())
            ));
        }

        return new Summary(from, to, total, buyCount, sellCount,
                buyAmount, sellAmount, players, items, playerItems);
    }

    private @NotNull List<TransactionLog.Record> queryRange(
            @NotNull TransactionDatabase db, long start, long end) {
        // 直接查全部再过滤（月度数据量不大）；这里用分页把数据拉全
        List<TransactionLog.Record> all = new ArrayList<>();
        int page = 1;
        while (true) {
            var batch = db.query(new TransactionDatabase.Query(null, null, start, true), page, 500);
            if (batch.isEmpty()) {
                break;
            }
            for (var r : batch) {
                if (r.timestamp() < end) {
                    all.add(r);
                }
            }
            if (batch.size() < 500) {
                break;
            }
            page++;
            if (page > 200) {
                break;   // 安全上限 10 万条
            }
        }
        return all;
    }

    // ================================================================
    //  全服日报
    // ================================================================

    /**
     * 生成并发送全服日报（写文件 + 发给在线管理员 + 推送个人日报）
     */
    public void send(@NotNull Summary s) {
        var cfg = plugin.config();
        List<String> lines = format(s);

        // 1) 写文件（独立的日报文件夹，每天一份）
        if (cfg.logReportFile()) {
            File dir = new File(plugin.getDataFolder(), "reports");
            if (!dir.exists() && !dir.mkdirs()) {
                plugin.getLogger().warning("无法创建日报目录：" + dir.getAbsolutePath());
            }
            File file = new File(dir, s.reportDate().format(DATE) + ".log");
            try {
                Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
            } catch (IOException e) {
                plugin.getLogger().warning("写日报失败：" + e.getMessage());
            }
        }

        // 2) 发给在线管理员
        if (cfg.logReportNotify()) {
            for (var player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission("xshop.admin")) {
                    lines.forEach(l -> player.sendMessage(Text.color(l)));
                }
            }
        }

        // 3) 控制台
        lines.forEach(l -> plugin.getLogger().info(Text.strip(l)));

        // 4) 个人日报推送给在线玩家
        sendPlayerReports(s);
    }

    /**
     * 把每个人的个人日报推送给在线玩家
     */
    public void sendPlayerReports(@NotNull Summary s) {
        if (!plugin.config().logReportPlayer() || !plugin.config().logReportPlayerNotify()) {
            return;
        }
        for (var player : Bukkit.getOnlinePlayers()) {
            // 管理员也可以看自己的个人日报
            var ps = playerSummary(s, player.getName());
            if (ps != null && !ps.empty()) {
                formatPlayer(ps).forEach(l -> player.sendMessage(Text.color(l)));
            }
            // 不管有没有数据，都记下已投递到这一天，避免下次登录重复补发
            plugin.reportDelivery().mark(player.getUniqueId(), s.reportDate());
        }
    }

    // ================================================================
    //  离线补发
    // ================================================================

    /**
     * 玩家上线时补发错过的个人日报。
     *
     * <p>规则：</p>
     * <ul>
     *     <li>从上一次收到日报的次日开始补，直到「昨天」</li>
     *     <li>最多回溯 {@link ReportDeliveryStore#RETENTION_DAYS} 天，更早的丢弃</li>
     *     <li>一天都没有数据就不发消息，只记账</li>
     * </ul>
     */
    public void deliverPending(@NotNull Player player) {
        if (!plugin.config().logReport() || !plugin.config().logReportPlayer()) {
            return;
        }
        var store = plugin.reportDelivery();
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate floor = today.minusDays(ReportDeliveryStore.RETENTION_DAYS);

        LocalDate last = store.last(player.getUniqueId());
        LocalDate start = last == null ? floor : last.plusDays(1);
        if (start.isBefore(floor)) {
            start = floor;
        }
        if (start.isAfter(yesterday)) {
            return;   // 已经投递到昨天，无需补发
        }

        // 逐天统计
        List<PlayerSummary> days = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(yesterday); d = d.plusDays(1)) {
            var ps = playerReport(player.getName(), d, d.plusDays(1));
            if (!ps.empty()) {
                days.add(ps);
            }
        }

        if (!days.isEmpty()) {
            if (days.size() == 1) {
                formatPlayer(days.get(0)).forEach(l -> player.sendMessage(Text.color(l)));
            } else {
                var agg = playerReport(player.getName(), start, yesterday.plusDays(1));
                formatMissed(agg, days).forEach(l -> player.sendMessage(Text.color(l)));
            }
        }

        store.mark(player.getUniqueId(), yesterday);
    }

    /**
     * 补发多天时的合并报表
     */
    public @NotNull List<String> formatMissed(@NotNull PlayerSummary agg, @NotNull List<PlayerSummary> days) {
        List<String> out = new ArrayList<>();
        out.add("");
        out.add(Text.color("&8&m      &r &6&l我的商店日报 &7(补发 " + days.size() + " 天) &8&m      "));
        out.add("");
        out.add(Text.color("  &7你有一段时间没上线，这些天的日报一起补给你："));
        out.add("");

        int shown = 0;
        for (var d : days) {
            if (shown >= 15) {
                out.add(Text.color("    &8…还有 " + (days.size() - shown) + " 天未显示"));
                break;
            }
            String net = (d.net() >= 0 ? "&a+" : "&c") + money(d.net());
            out.add(Text.color("    &f" + d.from().format(java.time.format.DateTimeFormatter.ofPattern("MM-dd"))
                    + " &8| 买 &f" + d.buyCount() + " &8| 卖 &f" + d.sellCount()
                    + " &8| 净 " + net));
            shown++;
        }
        out.add("");

        out.add(Text.color("  &a&l❯ 合计"));
        out.add(Text.color("    买入 &f" + agg.buyTimes() + " 笔 / &f" + agg.buyCount() + " 个"
                + "  &8|  花费 &c" + money(agg.buyAmount())));
        out.add(Text.color("    卖出 &f" + agg.sellTimes() + " 笔 / &f" + agg.sellCount() + " 个"
                + "  &8|  收入 &a" + money(agg.sellAmount())));
        out.add(Text.color("    净收益 &f" + (agg.net() >= 0 ? "&a+" : "&c") + money(agg.net())));
        out.add(Text.color("    全服活跃排名 &e#" + agg.rank() + " &7/ &f" + agg.totalPlayers()));
        out.add("");
        out.add(Text.color("  &8查询更多: &7/shop myreport [today|yesterday|week|month]"));
        out.add("");
        return out;
    }

    /**
     * 全服日报文本
     */
    public @NotNull List<String> format(@NotNull Summary s) {
        List<String> out = new ArrayList<>();
        String title = "&8&m      &r &6&l商店日报 &7(" + s.from().format(DATE)
                + (s.from().equals(s.to().minusDays(1)) ? "" : " ~ " + s.to().minusDays(1).format(DATE))
                + ") &8&m      ";

        out.add("");
        out.add(Text.color(title));
        out.add("");
        out.add(Text.color("  &7交易笔数 &f" + s.totalCount()
                + "  &8|  &7买入 &f" + s.buyCount() + " 个"
                + "  &8|  &7卖出 &f" + s.sellCount() + " 个"));
        out.add(Text.color("  &7玩家支出 &c" + money(s.buyAmount())
                + "  &8|  &7玩家收入 &a" + money(s.sellAmount())
                + "  &8|  &7净流入 &f" + money(s.netOutflow())));
        out.add("");

        // Top 玩家（按交易额）
        var topPlayers = s.players().values().stream()
                .sorted(Comparator.comparingDouble(PlayerStat::total).reversed())
                .limit(5)
                .toList();
        if (!topPlayers.isEmpty()) {
            out.add(Text.color("  &b&l❯ 最活跃玩家"));
            for (var p : topPlayers) {
                out.add(Text.color("    &f" + p.name()
                        + " &8| 买 &f" + p.buyCount() + " &8| 卖 &f" + p.sellCount()
                        + " &8| 支出 &c" + money(p.buyAmount())
                        + " &8| 收入 &a" + money(p.sellAmount())));
            }
            out.add("");
        }

        // Top 商品
        var topItems = s.items().values().stream()
                .sorted(Comparator.comparingInt(ItemStat::total).reversed())
                .limit(5)
                .toList();
        if (!topItems.isEmpty()) {
            out.add(Text.color("  &b&l❯ 热门商品"));
            for (var i : topItems) {
                out.add(Text.color("    &f" + i.name()
                        + " &8| 买入 &f" + i.buyCount()
                        + " &8| 卖出 &f" + i.sellCount()));
            }
            out.add("");
        }

        // 可疑提示
        if (s.totalCount() == 0) {
            out.add(Text.color("  &7昨天没有任何交易"));
            out.add("");
        } else if (!topPlayers.isEmpty()) {
            var top = topPlayers.get(0);
            if (top.total() > 0 && top.buyAmount() + top.sellAmount()
                    > s.buyAmount() + s.sellAmount() * 0.8
                    && s.players().size() > 1) {
                out.add(Text.color("  &e⚠ &7" + top.name() + " 的交易额占了绝大部分，建议查看 &f/shop log " + top.name()));
                out.add("");
            }
        }

        out.add(Text.color("  &8日报文件: &7plugins/XingXingShop/reports/"
                + s.reportDate().format(DATE) + ".log"));
        out.add(Text.color("  &8查询详情: &7/shop log [玩家名]"));
        out.add("");
        return out;
    }

    // ================================================================
    //  个人日报
    // ================================================================

    /**
     * 从全服汇总里取出某个玩家的个人汇总
     *
     * @return 玩家没有数据时返回 {@code null}
     */
    public @Nullable PlayerSummary playerSummary(@NotNull Summary s, @NotNull String playerName) {
        PlayerStat ps = null;
        for (var entry : s.players().entrySet()) {
            if (entry.getKey().equalsIgnoreCase(playerName)) {
                ps = entry.getValue();
                break;
            }
        }
        if (ps == null) {
            return null;
        }

        // 排名（按交易额）
        var ranked = s.players().values().stream()
                .sorted(Comparator.comparingDouble(PlayerStat::total).reversed())
                .toList();
        int rank = 1;
        for (int i = 0; i < ranked.size(); i++) {
            if (ranked.get(i).name().equalsIgnoreCase(ps.name())) {
                rank = i + 1;
                break;
            }
        }

        // 个人商品
        Map<String, ItemStat> mine = s.playerItems().getOrDefault(ps.name(), Map.of());
        List<ItemStat> topBought = mine.values().stream()
                .filter(i -> i.buyCount() > 0)
                .sorted(Comparator.comparingInt(ItemStat::buyCount).reversed())
                .limit(3)
                .toList();
        List<ItemStat> topSold = mine.values().stream()
                .filter(i -> i.sellCount() > 0)
                .sorted(Comparator.comparingInt(ItemStat::sellCount).reversed())
                .limit(3)
                .toList();

        return new PlayerSummary(ps.name(), s.from(), s.to(),
                ps.buyTimes(), ps.sellTimes(), ps.buyCount(), ps.sellCount(),
                ps.buyAmount(), ps.sellAmount(), topBought, topSold,
                rank, ranked.size());
    }

    /**
     * 个人日报文本
     */
    public @NotNull List<String> formatPlayer(@NotNull PlayerSummary p) {
        List<String> out = new ArrayList<>();
        String range = p.from().format(DATE)
                + (p.from().equals(p.to().minusDays(1)) ? "" : " ~ " + p.to().minusDays(1).format(DATE));

        out.add("");
        out.add(Text.color("&8&m      &r &6&l我的商店日报 &7(" + range + ") &8&m      "));
        out.add("");

        if (p.empty()) {
            out.add(Text.color("  &7这段时间你没有交易记录，去 &f/shop &7逛逛吧～"));
            out.add("");
            return out;
        }

        out.add(Text.color("  &a&l❯ 你的数据"));
        out.add(Text.color("    &7买入 &f" + p.buyTimes() + " 笔 &8/ &f" + p.buyCount() + " 个"
                + "  &8|  花费 &c" + money(p.buyAmount())));
        out.add(Text.color("    &7卖出 &f" + p.sellTimes() + " 笔 &8/ &f" + p.sellCount() + " 个"
                + "  &8|  收入 &a" + money(p.sellAmount())));
        out.add(Text.color("    &7净收益 &f" + (p.net() >= 0 ? "&a+" : "&c") + money(p.net())));
        out.add("");

        out.add(Text.color("  &b&l❯ 活跃排名"));
        out.add(Text.color("    全服第 &e#" + p.rank() + " &7/ 共 &f" + p.totalPlayers() + " &7位交易玩家"));
        out.add("");

        if (!p.topBought().isEmpty()) {
            out.add(Text.color("  &b&l❯ 你买得最多"));
            for (var i : p.topBought()) {
                out.add(Text.color("    &f" + i.name() + " &8x &f" + i.buyCount()));
            }
            out.add("");
        }
        if (!p.topSold().isEmpty()) {
            out.add(Text.color("  &b&l❯ 你卖得最多"));
            for (var i : p.topSold()) {
                out.add(Text.color("    &f" + i.name() + " &8x &f" + i.sellCount()));
            }
            out.add("");
        }

        out.add(Text.color("  &8查询更多: &7/shop myreport [today|yesterday|week|month]"));
        out.add("");
        return out;
    }

    /**
     * 生成某个玩家的个人日报
     */
    public @NotNull PlayerSummary playerReport(@NotNull String playerName,
                                               @NotNull LocalDate from, @NotNull LocalDate to) {
        Summary s = summarize(from, to);
        PlayerSummary ps = playerSummary(s, playerName);
        if (ps == null) {
            // 没有交易记录，返回一份空报告
            return new PlayerSummary(playerName, from, to, 0, 0, 0, 0, 0, 0,
                    List.of(), List.of(), 0, s.players().size());
        }
        return ps;
    }

    private @NotNull String money(double v) {
        return io.github.hello09x.shop.currency.Currency.formatNumber(
                Math.round(v * 100D) / 100D);
    }

    // ================================================================
    //  定时
    // ================================================================

    /**
     * 启动定时任务：每天 {@code hour} 点发前一天的报表
     */
    public void startScheduler() {
        if (!plugin.config().logReport()) {
            return;
        }
        int hour = Math.max(0, Math.min(23, plugin.config().logReportHour()));

        // 算出距离下一个整点还有多久（tick）
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = now.toLocalDate().atTime(hour, 0);
        if (!next.isAfter(now)) {
            next = next.plusDays(1);
        }
        long delaySeconds = java.time.Duration.between(now, next).getSeconds();
        long delayTicks = Math.max(20L, delaySeconds * 20L);
        long periodTicks = 20L * 60 * 60 * 24;   // 24 小时

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // 汇总「昨天」
            LocalDate today = LocalDate.now();
            LocalDate from = today.minusDays(1);
            var summary = summarize(from, today);
            send(summary);
        }, delayTicks, periodTicks);
    }

    /**
     * 手动生成报表（供命令调用）
     */
    public @NotNull Summary today() {
        LocalDate today = LocalDate.now();
        return summarize(today, today.plusDays(1));
    }

    /**
     * 昨天的报表
     */
    public @NotNull Summary yesterday() {
        LocalDate today = LocalDate.now();
        return summarize(today.minusDays(1), today);
    }

    @SuppressWarnings("unused")
    private static @NotNull String unused(Instant i) {
        return i.toString();
    }

}
