package io.github.hello09x.shop.shop;

import io.github.hello09x.shop.ShopPlugin;
import io.github.hello09x.shop.currency.Currency;
import io.github.hello09x.shop.util.Messages;
import io.github.hello09x.shop.util.Schedulers;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 交易引擎。所有买入 / 卖出都走这里。
 *
 * <p><b>数量单位统一是「个」</b>，不是「份」。</p>
 */
public final class TransactionService {

    /** 单次交易最大数量，防止异常数量拖垮服务器 */
    private static final int MAX_QUANTITY = 100_000;

    public enum Result {
        SUCCESS,
        NO_PERMISSION,
        NOT_ENOUGH_MONEY,
        NOT_ENOUGH_ITEMS,
        OUT_OF_STOCK,
        LIMIT_REACHED,
        SELL_LIMIT_REACHED,
        NO_CURRENCY,
        DB_UNAVAILABLE,
        ERROR
    }

    public record Outcome(@NotNull Result result, double amount, @NotNull String itemName) {
    }

    private final ShopPlugin plugin;
    /** 防刷屏：失败提示 1 秒内不重复 */
    private final Map<String, Long> lastNotify = new ConcurrentHashMap<>();

    public TransactionService(@NotNull ShopPlugin plugin) {
        this.plugin = plugin;
    }

    // ================================================================
    //  买入
    // ================================================================

    /**
     * 买入。
     *
     * <p>SQLite 直接当前线程完成；MySQL 跨服把库存事务丢到异步线程，
     * 结果回到主线程后再发货 / 提示，避免远程数据库卡主线程。</p>
     */
    public void buy(
            @NotNull Player player,
            @NotNull Shop shop,
            @NotNull ShopItem item,
            int quantity,
            @NotNull Consumer<Outcome> callback
    ) {
        if (quantity <= 0) {
            callback.accept(new Outcome(Result.ERROR, 0, name(item)));
            return;
        }
        if (quantity > MAX_QUANTITY) {
            callback.accept(new Outcome(Result.ERROR, 0, name(item)));
            return;
        }
        // 收购商店只能卖，不能反向购买（否则 0 价商品会被免费刷出）
        if (shop.type() != Shop.Type.BUY) {
            callback.accept(new Outcome(Result.ERROR, 0, name(item)));
            return;
        }
        if (plugin.isDisabling()) {
            callback.accept(new Outcome(Result.ERROR, 0, name(item)));
            return;
        }
        if (!allowed(player, shop, item)) {
            callback.accept(new Outcome(Result.NO_PERMISSION, 0, name(item)));
            return;
        }

        Currency currency = plugin.currencies().resolve(item.currencyId());
        if (currency == null || !currency.available()) {
            callback.accept(new Outcome(Result.NO_CURRENCY, 0, name(item)));
            return;
        }

        boolean shared = plugin.transactionLog().database().shared();
        var db = plugin.transactionLog().database();
        if (db.mysqlConfigured() && !db.available()) {
            callback.accept(new Outcome(Result.DB_UNAVAILABLE, 0, name(item)));
            return;
        }

        // 每日限购
        if (item.isLimited()) {
            long left = plugin.limits().remaining(player.getUniqueId(), item);
            if (left <= 0) {
                callback.accept(new Outcome(Result.LIMIT_REACHED, item.limit(), name(item)));
                return;
            }
            quantity = (int) Math.min(quantity, left);
        }

        // 库存：跨服时以数据库事务为准，本服缓存可能慢几秒，不在这里拦
        if (!shared && item.isLimitedStock() && item.stock() < quantity) {
            if (item.stock() <= 0) {
                callback.accept(new Outcome(Result.OUT_OF_STOCK, 0, name(item)));
                return;
            }
            quantity = item.stock();
        }

        // 买得起的最大数量（钱不够就自动缩量）
        double unit = item.buyPrice(player);
        if (!Double.isFinite(unit) || unit < 0) {
            callback.accept(new Outcome(Result.ERROR, 0, name(item)));
            return;
        }
        double balance = currency.balance(player);
        if (unit > 0) {
            int affordable = (int) Math.floor(balance / unit);
            if (affordable <= 0) {
                callback.accept(new Outcome(Result.NOT_ENOUGH_MONEY, balance, name(item)));
                return;
            }
            quantity = Math.min(quantity, affordable);
        }

        double total = round(unit * quantity);
        if (!Double.isFinite(total) || total < 0) {
            callback.accept(new Outcome(Result.ERROR, 0, name(item)));
            return;
        }

        if (!currency.has(player, total) || !currency.withdraw(player, total)) {
            callback.accept(new Outcome(Result.NOT_ENOUGH_MONEY, currency.balance(player), name(item)));
            return;
        }

        if (!shared) {
            if (!item.takeStock(quantity)) {
                currency.deposit(player, total);
                callback.accept(new Outcome(Result.OUT_OF_STOCK, 0, name(item)));
                return;
            }
            item.addSold(quantity);
            plugin.limits().add(player.getUniqueId(), item, quantity);
            plugin.stocks().saveItem(item);
            if (item.isLimitedStock() && item.stock() <= 0) {
                plugin.stocks().notifySoldOut(item);
            }

            give(player, item, quantity);
            plugin.transactionLog().record(player, shop.id(), item, name(item),
                    item.currencyId(), true, quantity, unit, total);
            callback.accept(new Outcome(Result.SUCCESS, total, name(item)));
            return;
        }

        final int wanted = quantity;
        final double unitPrice = unit;
        final double paid = total;
        var uuid = player.getUniqueId().toString();
        var day = java.time.LocalDate.now().toString();
        var limit = item.isLimited() ? item.limit() : 0;

        plugin.tradeStarted();
        try {
            plugin.tradeExecutor().execute(() -> {
                var reserved = db.reserveBuy(item.shopId(), item.itemId(), item.maxStock(),
                        uuid, day, item.key(), limit, wanted);

                runSync(player, () -> {
                    int granted = reserved.granted();
                    if (granted < 0) {
                        currency.deposit(player, paid);
                        callback.accept(new Outcome(Result.ERROR, 0, name(item)));
                        return;
                    }
                    if (granted <= 0) {
                        currency.deposit(player, paid);
                        Result result = item.isLimitedStock() && reserved.stock() <= 0
                                ? Result.OUT_OF_STOCK : Result.LIMIT_REACHED;
                        callback.accept(new Outcome(result, paid, name(item)));
                        return;
                    }

                    double finalTotal = paid;
                    if (granted < wanted) {
                        // 别的服先把库存/限购买走了，退还多扣的钱（按差额退，避免四舍五入误差）
                        finalTotal = round(unitPrice * granted);
                        currency.deposit(player, paid - finalTotal);
                    }

                    plugin.stocks().applyReservation(item, reserved.stock(), reserved.stockSold());
                    if (item.isLimitedStock() && reserved.stock() <= 0) {
                        plugin.stocks().notifySoldOut(item);
                    }
                    if (item.isLimited()) {
                        plugin.limits().applyBought(player.getUniqueId(), item, reserved.limitValue());
                    }

                    give(player, item, granted);
                    plugin.transactionLog().record(player, shop.id(), item, name(item),
                            item.currencyId(), true, granted, unitPrice, finalTotal);
                    callback.accept(new Outcome(Result.SUCCESS, finalTotal, name(item)));
                });
            });
        } catch (Throwable t) {
            plugin.tradeFinished();
            currency.deposit(player, paid);
            callback.accept(new Outcome(Result.ERROR, 0, name(item)));
        }
    }

    /**
     * 卖出。跨服库存事务同样异步执行。
     */
    public void sell(
            @NotNull Player player,
            @NotNull Shop shop,
            @NotNull ShopItem item,
            int quantity,
            @NotNull Consumer<Outcome> callback
    ) {
        if (quantity <= 0) {
            callback.accept(new Outcome(Result.ERROR, 0, name(item)));
            return;
        }
        if (quantity > MAX_QUANTITY) {
            callback.accept(new Outcome(Result.ERROR, 0, name(item)));
            return;
        }
        if (plugin.isDisabling()) {
            callback.accept(new Outcome(Result.ERROR, 0, name(item)));
            return;
        }
        if (!allowed(player, shop, item)) {
            callback.accept(new Outcome(Result.NO_PERMISSION, 0, name(item)));
            return;
        }

        Currency currency = plugin.currencies().resolve(item.currencyId());
        if (currency == null || !currency.available() || !currency.supportsDeposit()) {
            callback.accept(new Outcome(Result.NO_CURRENCY, 0, name(item)));
            return;
        }

        boolean shared = plugin.transactionLog().database().shared();
        var db = plugin.transactionLog().database();
        if (db.mysqlConfigured() && !db.available()) {
            callback.accept(new Outcome(Result.DB_UNAVAILABLE, 0, name(item)));
            return;
        }

        // 每日限卖（按价格自动分档）
        if (item.isSellLimited()) {
            long left = plugin.limits().sellRemaining(player.getUniqueId(), item);
            if (left <= 0) {
                callback.accept(new Outcome(Result.SELL_LIMIT_REACHED, item.sellLimit(), name(item)));
                return;
            }
            quantity = (int) Math.min(quantity, left);
        }

        // 背包够不够（不够就按有的卖）
        int has = count(player, item);
        if (has < quantity) {
            if (has <= 0) {
                callback.accept(new Outcome(Result.NOT_ENOUGH_ITEMS, 0, name(item)));
                return;
            }
            quantity = has;
        }

        double unit = item.sellPrice(player);
        if (!Double.isFinite(unit) || unit < 0) {
            callback.accept(new Outcome(Result.ERROR, 0, name(item)));
            return;
        }
        double total = round(unit * quantity);
        if (!Double.isFinite(total) || total < 0) {
            callback.accept(new Outcome(Result.ERROR, 0, name(item)));
            return;
        }

        int removed = take(player, item, quantity);
        if (removed < quantity) {
            give(player, item, removed);
            callback.accept(new Outcome(Result.NOT_ENOUGH_ITEMS, has, name(item)));
            return;
        }

        if (!shared) {
            if (!currency.deposit(player, total)) {
                give(player, item, quantity);
                callback.accept(new Outcome(Result.ERROR, 0, name(item)));
                return;
            }
            item.giveStock(quantity);
            item.addSold(quantity);
            plugin.limits().addSell(player.getUniqueId(), item, quantity);
            plugin.stocks().saveItem(item);
            plugin.transactionLog().record(player, shop.id(), item, name(item),
                    item.currencyId(), false, quantity, unit, total);
            callback.accept(new Outcome(Result.SUCCESS, total, name(item)));
            return;
        }

        final int wanted = quantity;
        final double unitPrice = unit;
        final double paid = total;
        var uuid = player.getUniqueId().toString();
        var day = java.time.LocalDate.now().toString();
        var sellLimit = item.isSellLimited() ? item.sellLimit() : 0;

        plugin.tradeStarted();
        try {
            plugin.tradeExecutor().execute(() -> {
                var reserved = db.reserveSell(item.shopId(), item.itemId(), item.maxStock(),
                        uuid, day, item.key(), sellLimit, wanted);

                runSync(player, () -> {
                    int granted = reserved.granted();
                    if (granted < 0) {
                        give(player, item, wanted);
                        callback.accept(new Outcome(Result.ERROR, 0, name(item)));
                        return;
                    }
                    if (granted <= 0) {
                        give(player, item, wanted);
                        callback.accept(new Outcome(Result.SELL_LIMIT_REACHED, item.sellLimit(), name(item)));
                        return;
                    }

                    double finalTotal = paid;
                    if (granted < wanted) {
                        // 别的服先把限卖额度用掉了，把多扣的物品还回去
                        give(player, item, wanted - granted);
                        finalTotal = round(unitPrice * granted);
                    }

                    // 库存额度确认成功后才到账，避免「先到账、事务失败、钱已被花掉」
                    if (!currency.deposit(player, finalTotal)) {
                        give(player, item, granted);
                        db.releaseSell(item.shopId(), item.itemId(), uuid, day, item.key(),
                                sellLimit, granted, Math.max(0, reserved.stock() - reserved.stockBefore()));
                        callback.accept(new Outcome(Result.ERROR, 0, name(item)));
                        return;
                    }

                    plugin.stocks().applyReservation(item, reserved.stock(), reserved.stockSold());
                    if (item.isSellLimited()) {
                        plugin.limits().applySold(player.getUniqueId(), item, reserved.limitValue());
                    }

                    plugin.transactionLog().record(player, shop.id(), item, name(item),
                            item.currencyId(), false, granted, unitPrice, finalTotal);
                    callback.accept(new Outcome(Result.SUCCESS, finalTotal, name(item)));
                });
            });
        } catch (Throwable t) {
            plugin.tradeFinished();
            give(player, item, wanted);
            callback.accept(new Outcome(Result.ERROR, 0, name(item)));
        }
    }

    /** 把异步结果切回玩家所属线程；关服时排队，由 onDisable 执行 */
    private void runSync(@NotNull Player player, @NotNull Runnable task) {
        java.util.concurrent.atomic.AtomicBoolean started = new java.util.concurrent.atomic.AtomicBoolean();
        Runnable wrapped = () -> {
            if (!started.compareAndSet(false, true)) {
                return;
            }
            try {
                task.run();
            } finally {
                plugin.tradeFinished();
            }
        };
        if (plugin.isDisabling()) {
            plugin.enqueueTradeCompletion(wrapped);
            return;
        }
        try {
            Schedulers.entity(plugin, player, wrapped);
        } catch (Throwable t) {
            // 只有任务还没开始执行时才补投递，避免已经发过货又执行一遍
            if (!started.get()) {
                plugin.enqueueTradeCompletion(wrapped);
            }
        }
    }

    // ================================================================
    //  内部
    // ================================================================

    private boolean allowed(@NotNull Player player, @NotNull Shop shop, @NotNull ShopItem item) {
        if (!shop.isOpen()) {
            return false;
        }
        if (!player.hasPermission("xshop.use")) {
            return false;
        }
        if (shop.type() == Shop.Type.SELL && !player.hasPermission("xshop.sell")) {
            return false;
        }
        if (shop.permission() != null && !player.hasPermission(shop.permission())) {
            return false;
        }
        return item.permission() == null || player.hasPermission(item.permission());
    }

    /**
     * 分批发物品（一次最多 64 个一摞）
     */
    private void give(@NotNull Player player, @NotNull ShopItem item, int quantity) {
        int remaining = quantity;
        while (remaining > 0) {
            int batch = Math.min(remaining, item.maxStackSize());
            ItemStack stack = item.reward(batch);
            if (player.isOnline()) {
                giveRaw(player, stack);
            } else {
                // 玩家在异步交易期间退出：先进待领取，上线补发
                plugin.pendingDelivery().add(player.getUniqueId(), stack);
            }
            remaining -= batch;
        }
    }

    private static void giveRaw(@NotNull Player player, @NotNull ItemStack stack) {
        if (stack.getType() == Material.AIR || stack.getAmount() <= 0) {
            return;
        }
        var leftover = player.getInventory().addItem(stack);
        for (ItemStack rest : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }
    }

    public static int count(@NotNull Player player, @NotNull ShopItem item) {
        int n = 0;
        for (ItemStack s : player.getInventory().getStorageContents()) {
            if (item.matches(s)) {
                n += s.getAmount();
            }
        }
        return n;
    }

    public static int take(@NotNull Player player, @NotNull ShopItem item, int amount) {
        int removed = 0;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && removed < amount; i++) {
            ItemStack s = contents[i];
            if (!item.matches(s)) {
                continue;
            }
            int take = Math.min(s.getAmount(), amount - removed);
            s.setAmount(s.getAmount() - take);
            if (s.getAmount() <= 0) {
                contents[i] = null;
            }
            removed += take;
        }
        player.getInventory().setStorageContents(contents);
        return removed;
    }

    private static String name(@NotNull ShopItem item) {
        return item.displayName();
    }

    private static double round(double v) {
        return Math.round(v * 100D) / 100D;
    }

    // ================================================================
    //  消息
    // ================================================================

    public void notify(@NotNull Player player, @NotNull Outcome outcome, @NotNull ShopItem item, boolean selling) {
        // 成功一定提示；失败 1 秒内不重复
        if (outcome.result() != Result.SUCCESS) {
            String key = player.getUniqueId() + "|" + outcome.result() + "|" + item.key();
            long now = System.currentTimeMillis();
            Long last = lastNotify.put(key, now);
            if (last != null && now - last < 1000L) {
                return;
            }
        }

        Messages msg = plugin.messages();
        Currency currency = plugin.currencies().getOrDefault(item.currencyId());
        String money = currency == null
                ? Currency.formatNumber(outcome.amount())
                : currency.format(outcome.amount());

        switch (outcome.result()) {
            case SUCCESS -> {
                msg.send(player, selling ? "sell.success" : "buy.success",
                        outcome.itemName(), money);
                io.github.hello09x.shop.util.Sounds.play(player,
                        selling ? "sell-success" : "buy-success");
            }
            case NO_PERMISSION -> {
                msg.send(player, "no-permission");
                io.github.hello09x.shop.util.Sounds.play(player, "error");
            }
            case NO_CURRENCY -> {
                msg.send(player, "no-currency");
                io.github.hello09x.shop.util.Sounds.play(player, "error");
            }
            case DB_UNAVAILABLE -> {
                msg.send(player, "database-unavailable");
                io.github.hello09x.shop.util.Sounds.play(player, "error");
            }
            case NOT_ENOUGH_MONEY -> {
                msg.send(player, "buy.not-enough-money", money, outcome.itemName());
                io.github.hello09x.shop.util.Sounds.play(player, "not-enough-money");
            }
            case NOT_ENOUGH_ITEMS -> {
                msg.send(player, "sell.not-enough-items", outcome.itemName());
                io.github.hello09x.shop.util.Sounds.play(player, "not-enough-items");
            }
            case OUT_OF_STOCK -> {
                msg.send(player, "buy.out-of-stock", outcome.itemName());
                io.github.hello09x.shop.util.Sounds.play(player, "out-of-stock");
            }
            case LIMIT_REACHED -> {
                msg.send(player, "buy.limit-reached", item.limit());
                io.github.hello09x.shop.util.Sounds.play(player, "limit-reached");
            }
            case SELL_LIMIT_REACHED -> {
                msg.send(player, "sell.limit-reached", item.sellLimit());
                io.github.hello09x.shop.util.Sounds.play(player, "limit-reached");
            }
            default -> {
                msg.send(player, "failed");
                io.github.hello09x.shop.util.Sounds.play(player, "error");
            }
        }
    }

    /** 供 GUI 判断要不要提示 */
    public void silentNotify(@NotNull Player player, @NotNull Outcome outcome, @NotNull ShopItem item, boolean selling) {
        notify(player, outcome, item, selling);
    }

}
