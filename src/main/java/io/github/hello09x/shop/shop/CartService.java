package io.github.hello09x.shop.shop;

import io.github.hello09x.shop.ShopPlugin;
import io.github.hello09x.shop.util.Schedulers;
import io.github.hello09x.shop.util.Sounds;
import io.github.hello09x.shop.util.Text;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** 购物车结算：逐件、带间隔地走正常购买流程，最后汇总一条结果。 */
public final class CartService {

    /** 每件商品之间的间隔（tick），避免一次性并发扣款 */
    private static final long STEP_DELAY = 3L;

    private final ShopPlugin plugin;

    public CartService(@NotNull ShopPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 结算购物车。失败的商品会放回购物车。
     *
     * @return 提交的商品种数
     */
    public int checkout(@NotNull Player player) {
        var cart = plugin.cart().items(player.getUniqueId());
        if (cart.isEmpty()) {
            player.sendMessage(Text.color("&7购物车是空的"));
            return 0;
        }

        var uuid = player.getUniqueId();
        List<Line> lines = new ArrayList<>();
        for (var entry : cart.entrySet()) {
            int split = entry.getKey().indexOf(':');
            Shop shop = split > 0 ? plugin.shops().get(entry.getKey().substring(0, split)) : null;
            ShopItem item = shop == null ? null : shop.find(entry.getKey().substring(split + 1));
            if (item != null && shop.isOpen() && shop.type() == Shop.Type.BUY) {
                lines.add(new Line(shop, item, entry.getValue()));
            }
        }
        if (lines.isEmpty()) {
            plugin.cart().clear(uuid);
            player.sendMessage(Text.color("&c购物车里的商品都已下架或不能购买"));
            return 0;
        }

        // 安全锁：价格为 0 或无效时整单取消，避免 0 元结算
        for (Line line : lines) {
            double unit = line.item.buyPrice(player);
            double total = unit * line.quantity;
            if (!Double.isFinite(unit) || !Double.isFinite(total) || total <= 0) {
                player.sendMessage(Text.color("&c结算已取消：&f" + line.item.displayName()
                        + " &c的价格为 0 或无效，请联系管理员检查商店配置"));
                return 0;
            }
        }

        // 先清空，防止结算期间重复点击
        plugin.cart().clear(uuid);
        var transactions = new TransactionService(plugin);
        var failures = new ArrayList<String>();
        var index = new AtomicInteger();
        var success = new AtomicInteger();

        player.sendMessage(Text.color("&a购物车结算中…"));

        Runnable[] step = new Runnable[1];
        step[0] = () -> {
            if (!player.isOnline()) {
                return;
            }
            if (index.get() >= lines.size()) {
                finish(player, success.get(), failures);
                return;
            }
            Line line = lines.get(index.getAndIncrement());
            try {
                transactions.buy(player, line.shop, line.item, line.quantity, outcome -> {
                    if (outcome.result() == TransactionService.Result.SUCCESS) {
                        success.incrementAndGet();
                    } else {
                        failures.add(line.item.displayName() + "(" + reason(outcome.result()) + ")");
                        if (!plugin.cart().items(uuid).containsKey(line.item.key())) {
                            plugin.cart().add(uuid, line.item.key(), line.quantity);
                        }
                    }
                    Schedulers.entityLater(plugin, player, step[0], STEP_DELAY);
                });
            } catch (Throwable t) {
                failures.add(line.item.displayName() + "(失败)");
                if (!plugin.cart().items(uuid).containsKey(line.item.key())) {
                    plugin.cart().add(uuid, line.item.key(), line.quantity);
                }
                Schedulers.entityLater(plugin, player, step[0], STEP_DELAY);
            }
        };
        Schedulers.entityLater(plugin, player, step[0], 1L);
        return lines.size();
    }

    private void finish(Player player, int success, List<String> failures) {
        var currency = plugin.currencies().getOrDefault(null);
        String balance = currency == null ? ""
                : " &8| &7余额 &f" + currency.format(currency.balance(player));
        if (failures.isEmpty()) {
            player.sendMessage(Text.color("&a[商店] 结算完成：成功购买 &f" + success
                    + " &a件商品" + balance));
        } else {
            String detail = String.join("、", failures.subList(0, Math.min(3, failures.size())));
            if (failures.size() > 3) {
                detail += " 等 " + failures.size() + " 件";
            }
            player.sendMessage(Text.color("&e[商店] 结算完成：&a成功 " + success
                    + " &7/ &c失败 " + failures.size() + " &8- &f" + detail + balance));
        }
        Sounds.play(player, success > 0 ? "buy-success" : "error");
    }

    private static String reason(TransactionService.Result result) {
        return switch (result) {
            case NOT_ENOUGH_MONEY -> "余额不足";
            case OUT_OF_STOCK -> "已售罄";
            case LIMIT_REACHED -> "达到限购";
            case NO_PERMISSION -> "无权限";
            case NO_CURRENCY -> "货币不可用";
            case DB_UNAVAILABLE -> "数据库未连接";
            default -> "失败";
        };
    }

    private record Line(Shop shop, ShopItem item, int quantity) {
    }
}
