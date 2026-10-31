package io.github.hello09x.shop.hook;

import io.github.hello09x.shop.ShopPlugin;
import io.github.hello09x.shop.currency.Currency;
import io.github.hello09x.shop.log.TransactionDatabase;
import io.github.hello09x.shop.log.TransactionLog;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.util.Locale;

/**
 * PlaceholderAPI 变量扩展。
 *
 * <p>服务器没装 PAPI 时，主类会**先判断再加载**本类，
 * 所以不会抛 {@code NoClassDefFoundError}。</p>
 *
 * <h3>玩家变量</h3>
 * <pre>
 * %xingsxingshop_balance%            余额（Vault 经济）
 * %xingsxingshop_balance_vault%      指定货币的余额
 * %xingsxingshop_balance_points%     PlayerPoints 点券余额
 * %xingsxingshop_buy_today%          今天买了多少个
 * %xingsxingshop_buy_today_times%    今天买了多少笔
 * %xingsxingshop_buy_today_money%    今天花了多少钱
 * %xingsxingshop_sell_today%         今天卖了多少个
 * %xingsxingshop_sell_today_times%   今天卖了多少笔
 * %xingsxingshop_sell_today_money%   今天赚了多少钱
 * %xingsxingshop_total_today%        今天交易总笔数
 * %xingsxingshop_shops%              商店分类数
 * %xingsxingshop_items%              商品总数
 * </pre>
 *
 * <h3>全局变量（不需要玩家上下文）</h3>
 * <pre>
 * %xingsxingshop_global_buy_today%   全服今天买入总数
 * %xingsxingshop_global_sell_today%  全服今天卖出总数
 * %xingsxingshop_global_buy_money%   全服今天玩家支出
 * %xingsxingshop_global_sell_money%  全服今天玩家收入
 * %xingsxingshop_currencies%         可用货币数量
 * </pre>
 */
public class ShopPlaceholderExpansion extends PlaceholderExpansion {

    private final ShopPlugin plugin;

    public ShopPlaceholderExpansion(@NotNull ShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "xingsxingshop";
    }

    @Override
    public @NotNull String getAuthor() {
        return "xingxing";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    /** 不随 /papi reload 掉线 */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public @Nullable String onRequest(@Nullable OfflinePlayer player, @NotNull String params) {
        String key = params.toLowerCase(Locale.ROOT);

        // ==================== 全局变量（不需要玩家）====================
        switch (key) {
            case "shops":
            case "shop_count":
                return String.valueOf(plugin.shops().all().size());
            case "items":
            case "item_count":
                return String.valueOf(plugin.shops().totalItems());
            case "currencies":
            case "currency_count":
                return String.valueOf(plugin.currencies().all().size());
            case "database":
            case "db_type":
                return plugin.transactionLog().database().type().name().toLowerCase(Locale.ROOT);
            default:
                // 继续往下走玩家变量
        }

        // 全局统计
        if (key.startsWith("global_")) {
            return globalStat(key.substring("global_".length()));
        }

        // ==================== 玩家变量 ====================
        if (player == null) {
            return "";
        }

        // 余额
        if (key.equals("balance") || key.startsWith("balance_")) {
            String currencyId = key.equals("balance")
                    ? null
                    : key.substring("balance_".length());
            Currency currency = plugin.currencies().getOrDefault(currencyId);
            if (currency == null) {
                return "0";
            }
            double bal = currency.balance(player);
            return io.github.hello09x.shop.currency.Currency.formatNumber(
                    Math.round(bal * 100D) / 100D);
        }

        // 玩家今日统计
        return playerStat(player, key);
    }

    /**
     * 玩家今日交易统计
     */
    private @NotNull String playerStat(@NotNull OfflinePlayer player, @NotNull String key) {
        long dayStart = LocalDate.now()
                .atStartOfDay(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli();

        // 只取今天的记录（走数据库时用 since 过滤，避免全表扫描）
        var records = recordsSince(player.getName(), dayStart);

        int buyCount = 0, sellCount = 0, buyTimes = 0, sellTimes = 0;
        double buyMoney = 0, sellMoney = 0;

        for (TransactionLog.Record r : records) {
            if (r.timestamp() < dayStart) {
                continue;
            }
            // 管理员交易不计入统计
            if (r.admin()) {
                continue;
            }
            if (!r.playerUuid().equals(player.getUniqueId())) {
                continue;
            }
            if (r.buying()) {
                buyCount += r.quantity();
                buyTimes++;
                buyMoney += r.total();
            } else {
                sellCount += r.quantity();
                sellTimes++;
                sellMoney += r.total();
            }
        }

        return switch (key) {
            case "buy_today", "buy_today_count" -> String.valueOf(buyCount);
            case "buy_today_times" -> String.valueOf(buyTimes);
            case "buy_today_money", "spent_today" -> money(buyMoney);
            case "sell_today", "sell_today_count" -> String.valueOf(sellCount);
            case "sell_today_times" -> String.valueOf(sellTimes);
            case "sell_today_money", "earned_today" -> money(sellMoney);
            case "total_today", "total_today_times" -> String.valueOf(buyTimes + sellTimes);
            case "total_today_count" -> String.valueOf(buyCount + sellCount);
            case "net_today" -> money(sellMoney - buyMoney);
            default -> null;
        };
    }

    /**
     * 全服今日统计
     */
    private @NotNull String globalStat(@NotNull String key) {
        long dayStart = LocalDate.now()
                .atStartOfDay(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli();

        int dbTotal = 0;
        if (plugin.transactionLog().database().available()) {
            dbTotal = plugin.transactionLog().database().count(
                    new TransactionDatabase.Query(null, null, 0, true));
        }
        var records = recordsSince(null, dayStart);

        int buyCount = 0, sellCount = 0, buyTimes = 0, sellTimes = 0;
        double buyMoney = 0, sellMoney = 0;

        for (TransactionLog.Record r : records) {
            if (r.timestamp() < dayStart) {
                continue;
            }
            // 管理员交易不计入统计
            if (r.admin()) {
                continue;
            }
            if (r.buying()) {
                buyCount += r.quantity();
                buyTimes++;
                buyMoney += r.total();
            } else {
                sellCount += r.quantity();
                sellTimes++;
                sellMoney += r.total();
            }
        }

        return switch (key) {
            case "buy_today", "buy_today_count" -> String.valueOf(buyCount);
            case "buy_today_times" -> String.valueOf(buyTimes);
            case "buy_money", "buy_today_money" -> money(buyMoney);
            case "sell_today", "sell_today_count" -> String.valueOf(sellCount);
            case "sell_today_times" -> String.valueOf(sellTimes);
            case "sell_money", "sell_today_money" -> money(sellMoney);
            case "total_today", "total_today_times" -> String.valueOf(buyTimes + sellTimes);
            case "net_today" -> money(buyMoney - sellMoney);
            case "total_records" -> String.valueOf(dbTotal);
            default -> null;
        };
    }

    /**
     * 取某个时间之后的记录（走数据库就用 since 过滤；否则退回内存缓存再筛）。
     */
    private @NotNull java.util.List<TransactionLog.Record> recordsSince(
            @Nullable String playerName, long since) {
        var db = plugin.transactionLog().database();
        if (db.available()) {
            java.util.List<TransactionLog.Record> all = new java.util.ArrayList<>();
            int page = 1;
            while (true) {
                var batch = db.query(new TransactionDatabase.Query(playerName, null, since), page, 1000);
                all.addAll(batch);
                if (batch.size() < 1000 || page > 20) {
                    break;
                }
                page++;
            }
            return all;
        }
        java.util.List<TransactionLog.Record> out = new java.util.ArrayList<>();
        for (var r : plugin.transactionLog().query(playerName, 1, Integer.MAX_VALUE)) {
            if (r.timestamp() >= since) {
                out.add(r);
            }
        }
        return out;
    }

    private @NotNull String money(double v) {
        return io.github.hello09x.shop.currency.Currency.formatNumber(
                Math.round(v * 100D) / 100D);
    }

    @SuppressWarnings("unused")
    private static @Nullable String unused(Player p) {
        return p.getName();
    }

}
