package io.github.hello09x.shop.shop;

import io.github.hello09x.shop.ShopPlugin;
import io.github.hello09x.shop.util.Text;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** 购物车结算：逐件走正常购买流程。 */
public final class CartService {

    private final ShopPlugin plugin;

    public CartService(@NotNull ShopPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 结算购物车。
     *
     * @return 实际提交的商品种数
     */
    public int checkout(@NotNull Player player) {
        var cart = plugin.cart().items(player.getUniqueId());
        if (cart.isEmpty()) {
            player.sendMessage(Text.color("&7购物车是空的"));
            return 0;
        }

        var transactions = new TransactionService(plugin);
        int queued = 0;
        for (var entry : cart.entrySet()) {
            int split = entry.getKey().indexOf(':');
            Shop shop = split > 0 ? plugin.shops().get(entry.getKey().substring(0, split)) : null;
            ShopItem item = shop == null ? null : shop.find(entry.getKey().substring(split + 1));
            if (item == null || !shop.isOpen()) {
                continue;
            }
            transactions.buy(player, shop, item, entry.getValue(),
                    outcome -> transactions.notify(player, outcome, item, false));
            queued++;
        }
        plugin.cart().clear(player.getUniqueId());
        if (queued > 0) {
            player.sendMessage(Text.color("&a购物车结算中…"));
        } else {
            player.sendMessage(Text.color("&c购物车里的商品都已下架或关闭"));
        }
        return queued;
    }
}
