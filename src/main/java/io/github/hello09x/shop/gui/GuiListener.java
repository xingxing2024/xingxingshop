package io.github.hello09x.shop.gui;

import io.github.hello09x.shop.ShopPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 拦截商店 GUI 的所有点击 / 拖拽。
 *
 * <p>创造模式下客户端拖拽走的是 {@link InventoryCreativeEvent}，
 * 且服务端 {@code handleSetCreativeModeSlot} 只看 {@code getResult()}，
 * 所以必须显式设 {@link Event.Result#DENY}，光 setCancelled 拦不住。</p>
 */
public final class GuiListener implements Listener {

    /** 同一玩家两次有效点击的最小间隔，防连点 */
    private static final long CLICK_COOLDOWN_MS = 250;

    private final ShopPlugin plugin;
    private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();

    public GuiListener(@NotNull ShopPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onClick(@NotNull InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        ShopView view = ShopGuiManager.asView(holder);
        MainMenu menu = ShopGuiManager.asMenu(holder);
        ShopListMenu list = ShopGuiManager.asList(holder);
        ConfirmView confirm = ShopGuiManager.asConfirm(holder);
        TransactionMenu transactions = ShopGuiManager.asTransactions(holder);
        AdminItemMenu edit = ShopGuiManager.asEdit(holder);
        SearchMenu search = ShopGuiManager.asSearch(holder);
        FavoriteMenu favorites = ShopGuiManager.asFavorites(holder);
        CartMenu cart = ShopGuiManager.asCart(holder);
        if (view == null && menu == null && list == null && confirm == null && transactions == null
                && edit == null && search == null && favorites == null && cart == null) {
            return;
        }

        event.setResult(Event.Result.DENY);
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        long now = System.currentTimeMillis();
        Long last = lastClick.put(event.getWhoClicked().getUniqueId(), now);
        if (last != null && now - last < CLICK_COOLDOWN_MS) {
            return;
        }
        if (lastClick.size() > 200) {
            lastClick.entrySet().removeIf(e -> now - e.getValue() > 10_000L);
        }

        int raw = event.getRawSlot();
        if (raw < 0 || raw >= event.getView().getTopInventory().getSize()) {
            return;
        }

        if (confirm != null) {
            confirm.onClick(raw);
        } else if (transactions != null) {
            transactions.onClick(raw);
        } else if (edit != null) {
            edit.onClick(raw);
        } else if (search != null) {
            search.onClick(raw);
        } else if (favorites != null) {
            favorites.onClick(raw);
        } else if (cart != null) {
            cart.onClick(raw, event.getClick());
        } else if (view != null) {
            view.onClick(raw, event.getClick());
        } else if (list != null) {
            list.onClick(raw);
        } else {
            menu.onClick(raw);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrag(@NotNull InventoryDragEvent event) {
        if (isShop(event.getView().getTopInventory().getHolder())) {
            event.setResult(Event.Result.DENY);
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCreative(@NotNull InventoryCreativeEvent event) {
        if (isShop(event.getView().getTopInventory().getHolder())) {
            event.setResult(Event.Result.DENY);
            event.setCancelled(true);
        }
    }

    private boolean isShop(InventoryHolder holder) {
        return ShopGuiManager.asView(holder) != null
                || ShopGuiManager.asMenu(holder) != null
                || ShopGuiManager.asList(holder) != null
                || ShopGuiManager.asConfirm(holder) != null
                || ShopGuiManager.asTransactions(holder) != null
                || ShopGuiManager.asEdit(holder) != null
                || ShopGuiManager.asSearch(holder) != null
                || ShopGuiManager.asFavorites(holder) != null
                || ShopGuiManager.asCart(holder) != null;
    }

}
