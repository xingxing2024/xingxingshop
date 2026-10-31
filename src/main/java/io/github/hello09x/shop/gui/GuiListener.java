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

/**
 * 拦截商店 GUI 的所有点击 / 拖拽。
 *
 * <p>创造模式下客户端拖拽走的是 {@link InventoryCreativeEvent}，
 * 且服务端 {@code handleSetCreativeModeSlot} 只看 {@code getResult()}，
 * 所以必须显式设 {@link Event.Result#DENY}，光 setCancelled 拦不住。</p>
 */
public final class GuiListener implements Listener {

    private final ShopPlugin plugin;

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
        if (view == null && menu == null && list == null && confirm == null) {
            return;
        }

        event.setResult(Event.Result.DENY);
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        int raw = event.getRawSlot();
        if (raw < 0 || raw >= event.getView().getTopInventory().getSize()) {
            return;
        }

        if (confirm != null) {
            confirm.onClick(raw);
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
                || ShopGuiManager.asConfirm(holder) != null;
    }

}
