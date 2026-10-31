package io.github.hello09x.shop.shop;

import io.github.hello09x.shop.ShopPlugin;
import io.github.hello09x.shop.util.Text;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/** 购物车结算：逐件走正常购买流程。 */
public final class CartService {

    private final ShopPlugin plugin;

    public CartService(@NotNull ShopPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 结算购物车。失败的商品会放回购物车，成功后清空。
     *
     * @return 实际提交的商品种数
     */
    public int checkout(@NotNull Player player) {
        var cart = plugin.cart().items(player.getUniqueId());
        if (cart.isEmpty()) {
            player.sendMessage(Text.color("&7购物车是空的"));
            return 0;
        }

        var uuid = player.getUniqueId();
        var lines = new ArrayList<>(cart.entrySet());
        // 先清空，避免玩家在异步结算期间重复点结算
        plugin.cart().clear(uuid);

        var transactions = new TransactionService(plugin);
        int queued = 0;
        for (var entry : lines) {
            int split = entry.getKey().indexOf(':');
            Shop shop = split > 0 ? plugin.shops().get(entry.getKey().substring(0, split)) : null;
            ShopItem item = shop == null ? null : shop.find(entry.getKey().substring(split + 1));
            // 收购商店的商品不能买，只允许从购买商店结算
            if (item == null || !shop.isOpen() || shop.type() != Shop.Type.BUY) {
                continue;
            }
            int quantity = entry.getValue();
            transactions.buy(player, shop, item, quantity, outcome -> {
                transactions.notify(player, outcome, item, false);
                if (outcome.result() != TransactionService.Result.SUCCESS
                        && !plugin.cart().items(uuid).containsKey(item.key())) {
                    plugin.cart().add(uuid, item.key(), quantity);
                }
            });
            queued++;
        }
        if (queued > 0) {
            player.sendMessage(Text.color("&a购物车结算中…"));
        } else {
            player.sendMessage(Text.color("&c购物车里的商品都已下架或不能购买"));
        }
        return queued;
    }
}
