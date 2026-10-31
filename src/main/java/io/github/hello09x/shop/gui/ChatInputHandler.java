package io.github.hello09x.shop.gui;

import io.github.hello09x.shop.ShopPlugin;
import io.github.hello09x.shop.shop.Shop;
import io.github.hello09x.shop.shop.ShopItem;
import io.github.hello09x.shop.shop.TransactionService;
import io.github.hello09x.shop.util.Schedulers;
import io.github.hello09x.shop.util.Text;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「聊天框输入数量」的等待队列。
 *
 * <p>玩家在商店里 Shift+左键点商品后，关闭界面、进入等待状态，
 * 下一条聊天内容会被当成数量解析。</p>
 */
public final class ChatInputHandler implements Listener {

    private static final long TIMEOUT_TICKS = 20L * 30;

    /** 等待输入中的玩家 */
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    private final ShopPlugin plugin;

    public ChatInputHandler(@NotNull ShopPlugin plugin) {
        this.plugin = plugin;
    }

    private static final class Pending {
        final Shop shop;
        final ShopItem item;
        final long createdAt = System.currentTimeMillis();

        Pending(Shop shop, ShopItem item) {
            this.shop = shop;
            this.item = item;
        }
    }

    /**
     * 进入等待输入状态
     */
    public void request(@NotNull Player viewer, @NotNull Shop shop, @NotNull ShopItem item) {
        boolean selling = shop.type() == Shop.Type.SELL;

        viewer.closeInventory();
        pending.put(viewer.getUniqueId(), new Pending(shop, item));

        plugin.messages().send(viewer, "chat-input.prompt",
                item.displayName(),
                selling ? "卖出" : "购买");

        // 超时清理
        Schedulers.entityLater(plugin, viewer, () -> {
            Pending p = pending.get(viewer.getUniqueId());
            if (p != null && System.currentTimeMillis() - p.createdAt >= TIMEOUT_TICKS * 50) {
                pending.remove(viewer.getUniqueId());
                if (viewer.isOnline() && !(viewer.getOpenInventory().getTopInventory().getHolder()
                        instanceof ShopView)) {
                    plugin.messages().send(viewer, "chat-input.timeout");
                }
            }
        }, TIMEOUT_TICKS);
    }

    /**
     * 取消等待
     */
    public void cancel(@NotNull Player viewer) {
        pending.remove(viewer.getUniqueId());
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }

    public boolean isWaiting(@NotNull Player viewer) {
        return pending.containsKey(viewer.getUniqueId());
    }

    /**
     * 拦截聊天内容
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(@NotNull AsyncChatEvent event) {
        Player player = event.getPlayer();
        Pending p = pending.remove(player.getUniqueId());
        if (p == null) {
            return;
        }

        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText()
                .serialize(event.message()).trim();

        Schedulers.entity(plugin, player, () -> apply(player, p, input));
    }

    private void apply(@NotNull Player player, @NotNull Pending p, @NotNull String input) {
        if (!player.isOnline()) {
            return;
        }

        // 取消
        if (input.equalsIgnoreCase("cancel") || input.equals("取消") || input.equals("q")) {
            plugin.messages().send(player, "chat-input.cancelled");
            plugin.gui().open(player, p.shop);
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(input);
        } catch (NumberFormatException e) {
            plugin.messages().send(player, "chat-input.invalid");
            plugin.gui().open(player, p.shop);
            return;
        }

        if (amount <= 0) {
            plugin.messages().send(player, "chat-input.invalid");
            plugin.gui().open(player, p.shop);
            return;
        }

        // 上限保护
        if (amount > 100_000) {
            amount = 100_000;
        }

        // 走确认界面（防止误触）
        plugin.gui().openConfirm(player, p.shop, p.item, amount, p.shop.type() == Shop.Type.SELL);
    }

    /**
     * 界面标题：提示玩家正在输入
     */
    public static @NotNull String promptHint(@NotNull ShopItem item) {
        return Text.color("&e请输入数量");
    }

    @SuppressWarnings("unused")
    private static @Nullable String unused(String s) {
        return s;
    }

}
