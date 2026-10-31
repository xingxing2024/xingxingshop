package io.github.hello09x.shop.shop;

import io.github.hello09x.shop.ShopPlugin;
import io.github.hello09x.shop.currency.Currency;
import io.github.hello09x.shop.util.ChineseNames;
import io.github.hello09x.shop.util.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 交易引擎。所有买入 / 卖出都走这里。
 *
 * <p><b>数量单位统一是「个」</b>，不是「份」。</p>
 */
public final class TransactionService {

    public enum Result {
        SUCCESS,
        NO_PERMISSION,
        NOT_ENOUGH_MONEY,
        NOT_ENOUGH_ITEMS,
        OUT_OF_STOCK,
        LIMIT_REACHED,
        SELL_LIMIT_REACHED,
        NO_CURRENCY,
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
     * @param quantity 买几个
     */
    public @NotNull Outcome buy(@NotNull Player player, @NotNull Shop shop, @NotNull ShopItem item, int quantity) {
        if (quantity <= 0) {
            return new Outcome(Result.ERROR, 0, name(item));
        }
        if (!allowed(player, shop, item)) {
            return new Outcome(Result.NO_PERMISSION, 0, name(item));
        }

        Currency currency = plugin.currencies().getOrDefault(item.currencyId());
        if (currency == null || !currency.available()) {
            return new Outcome(Result.NO_CURRENCY, 0, name(item));
        }

        // 每日限购
        if (item.isLimited()) {
            long left = plugin.limits().remaining(player.getUniqueId(), item);
            if (left <= 0) {
                return new Outcome(Result.LIMIT_REACHED, item.limit(), name(item));
            }
            quantity = (int) Math.min(quantity, left);
        }

        boolean shared = plugin.transactionLog().database().shared();

        // 库存：跨服时以数据库事务为准，本服缓存可能慢几秒，不在这里拦
        if (!shared && item.isLimitedStock() && item.stock() < quantity) {
            if (item.stock() <= 0) {
                return new Outcome(Result.OUT_OF_STOCK, 0, name(item));
            }
            quantity = item.stock();
        }

        // 买得起的最大数量（钱不够就自动缩量）
        double unit = item.buyPrice();
        double balance = currency.balance(player);
        if (unit > 0) {
            int affordable = (int) Math.floor(balance / unit);
            if (affordable <= 0) {
                return new Outcome(Result.NOT_ENOUGH_MONEY, balance, name(item));
            }
            quantity = Math.min(quantity, affordable);
        }

        double total = round(unit * quantity);

        if (!currency.has(player, total) || !currency.withdraw(player, total)) {
            return new Outcome(Result.NOT_ENOUGH_MONEY, currency.balance(player), name(item));
        }

        if (shared) {
            var db = plugin.transactionLog().database();
            var reserved = db.reserveBuy(item.shopId(), item.itemId(), item.maxStock(),
                    player.getUniqueId().toString(), java.time.LocalDate.now().toString(), item.key(),
                    item.isLimited() ? item.limit() : 0, quantity);
            if (reserved.granted() < 0) {
                currency.deposit(player, total);
                return new Outcome(Result.ERROR, 0, name(item));
            }
            if (reserved.granted() <= 0) {
                currency.deposit(player, total);
                Result result = item.isLimitedStock() && reserved.stock() <= 0
                        ? Result.OUT_OF_STOCK : Result.LIMIT_REACHED;
                return new Outcome(result, total, name(item));
            }
            if (reserved.granted() < quantity) {
                // 别的服先把库存/限购买走了，退还多扣的钱
                currency.deposit(player, round(unit * (quantity - reserved.granted())));
                quantity = reserved.granted();
                total = round(unit * quantity);
            }
            plugin.stocks().applyReservation(item, reserved.stock(), reserved.stockSold());
            if (item.isLimited()) {
                plugin.limits().applyBought(player.getUniqueId(), item, reserved.limitValue());
            }
        } else {
            if (!item.takeStock(quantity)) {
                currency.deposit(player, total);
                return new Outcome(Result.OUT_OF_STOCK, 0, name(item));
            }
            item.addSold(quantity);
            plugin.limits().add(player.getUniqueId(), item, quantity);
            plugin.stocks().saveItem(item);
        }

        give(player, item, quantity);
        // 记录交易日志
        plugin.transactionLog().record(player, shop.id(), item, name(item),
                item.currencyId(), true, quantity, unit, total);
        return new Outcome(Result.SUCCESS, total, name(item));
    }

    // ================================================================
    //  卖出
    // ================================================================

    /**
     * @param quantity 卖几个
     */
    public @NotNull Outcome sell(@NotNull Player player, @NotNull Shop shop, @NotNull ShopItem item, int quantity) {
        if (quantity <= 0) {
            return new Outcome(Result.ERROR, 0, name(item));
        }
        if (!allowed(player, shop, item)) {
            return new Outcome(Result.NO_PERMISSION, 0, name(item));
        }

        Currency currency = plugin.currencies().getOrDefault(item.currencyId());
        if (currency == null || !currency.available() || !currency.supportsDeposit()) {
            return new Outcome(Result.NO_CURRENCY, 0, name(item));
        }

        // 每日限卖（按价格自动分档）
        if (item.isSellLimited()) {
            long left = plugin.limits().sellRemaining(player.getUniqueId(), item);
            if (left <= 0) {
                return new Outcome(Result.SELL_LIMIT_REACHED, item.sellLimit(), name(item));
            }
            quantity = (int) Math.min(quantity, left);
        }

        // 背包够不够（不够就按有的卖）
        int has = count(player, item.material());
        if (has < quantity) {
            if (has <= 0) {
                return new Outcome(Result.NOT_ENOUGH_ITEMS, 0, name(item));
            }
            quantity = has;
        }

        double unit = item.sellPrice();
        double total = round(unit * quantity);

        int removed = take(player, item.material(), quantity);
        if (removed < quantity) {
            giveRaw(player, new ItemStack(item.material(), removed));
            return new Outcome(Result.NOT_ENOUGH_ITEMS, has, name(item));
        }

        boolean shared = plugin.transactionLog().database().shared();
        if (shared) {
            // 先到账，再原子占用限卖额度；失败就把钱扣回来、物品还给玩家
            if (!currency.deposit(player, total)) {
                giveRaw(player, new ItemStack(item.material(), quantity));
                return new Outcome(Result.ERROR, 0, name(item));
            }
            var db = plugin.transactionLog().database();
            var reserved = db.reserveSell(item.shopId(), item.itemId(), item.maxStock(),
                    player.getUniqueId().toString(), java.time.LocalDate.now().toString(), item.key(),
                    item.isSellLimited() ? item.sellLimit() : 0, quantity);
            if (reserved.granted() < 0) {
                giveRaw(player, new ItemStack(item.material(), quantity));
                currency.withdraw(player, total);
                return new Outcome(Result.ERROR, 0, name(item));
            }
            if (reserved.granted() <= 0) {
                giveRaw(player, new ItemStack(item.material(), quantity));
                currency.withdraw(player, total);
                return new Outcome(Result.SELL_LIMIT_REACHED, item.sellLimit(), name(item));
            }
            if (reserved.granted() < quantity) {
                // 别的服先把限卖额度用掉了，把多扣的物品和钱还回去
                int unused = quantity - reserved.granted();
                giveRaw(player, new ItemStack(item.material(), unused));
                currency.withdraw(player, round(unit * unused));
                quantity = reserved.granted();
                total = round(unit * quantity);
            }
            plugin.stocks().applyReservation(item, reserved.stock(), reserved.stockSold());
            if (item.isSellLimited()) {
                plugin.limits().applySold(player.getUniqueId(), item, reserved.limitValue());
            }
        } else {
            if (!currency.deposit(player, total)) {
                giveRaw(player, new ItemStack(item.material(), quantity));
                return new Outcome(Result.ERROR, 0, name(item));
            }
            item.giveStock(quantity);
            item.addSold(quantity);
            plugin.limits().addSell(player.getUniqueId(), item, quantity);
            plugin.stocks().saveItem(item);
        }
        // 记录交易日志
        plugin.transactionLog().record(player, shop.id(), item, name(item),
                item.currencyId(), false, quantity, unit, total);
        return new Outcome(Result.SUCCESS, total, name(item));
    }

    // ================================================================
    //  内部
    // ================================================================

    private boolean allowed(@NotNull Player player, @NotNull Shop shop, @NotNull ShopItem item) {
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
            int batch = Math.min(remaining, item.material().getMaxStackSize());
            giveRaw(player, item.reward(batch));
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

    public static int count(@NotNull Player player, @NotNull Material material) {
        int n = 0;
        for (ItemStack s : player.getInventory().getStorageContents()) {
            if (s != null && s.getType() == material) {
                n += s.getAmount();
            }
        }
        return n;
    }

    public static int take(@NotNull Player player, @NotNull Material material, int amount) {
        int removed = 0;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && removed < amount; i++) {
            ItemStack s = contents[i];
            if (s == null || s.getType() != material) {
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
        // 直接查中文名表，不依赖 GUI 运行时给图标设的名字
        return ChineseNames.of(item.material());
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
