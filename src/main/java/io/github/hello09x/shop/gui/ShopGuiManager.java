package io.github.hello09x.shop.gui;

import io.github.hello09x.shop.ShopPlugin;
import io.github.hello09x.shop.shop.Shop;
import io.github.hello09x.shop.shop.ShopItem;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * GUI 打开入口。
 */
public final class ShopGuiManager {

    private final ShopPlugin plugin;

    public ShopGuiManager(@NotNull ShopPlugin plugin) {
        this.plugin = plugin;
    }

    /** 打开某个商店 */
    public void open(@NotNull Player viewer, @NotNull Shop shop) {
        if (!shop.isOpen()) {
            plugin.messages().send(viewer, "shop-closed");
            return;
        }
        io.github.hello09x.shop.util.Sounds.play(viewer, "open");
        ShopView view = new ShopView(plugin, shop, viewer);
        viewer.openInventory(view.getInventory());
    }

    public void open(@NotNull Player viewer, @NotNull Shop shop, int page) {
        if (!shop.isOpen()) {
            plugin.messages().send(viewer, "shop-closed");
            return;
        }
        io.github.hello09x.shop.util.Sounds.play(viewer, "open");
        ShopView view = new ShopView(plugin, shop, viewer);
        view.page(page);
        view.render();
        viewer.openInventory(view.getInventory());
    }


    /**
     * 打开交易确认界面
     */
    public void openConfirm(@NotNull Player viewer, @NotNull Shop shop, @NotNull ShopItem item,
                            int quantity, boolean selling) {
        plugin.stocks().refreshItem(item);
        ConfirmView view = new ConfirmView(plugin, shop, item, viewer, quantity, selling);
        viewer.openInventory(view.getInventory());
    }

    /** 管理员交易记录面板 */
    public void openTransactions(@NotNull Player viewer, int page) {
        io.github.hello09x.shop.util.Sounds.play(viewer, "open");
        TransactionMenu menu = new TransactionMenu(plugin, viewer, page);
        viewer.openInventory(menu.getInventory());
    }

    /** 管理员商品编辑面板 */
    public void openEdit(@NotNull Player viewer, @NotNull Shop shop, @NotNull ShopItem item) {
        io.github.hello09x.shop.util.Sounds.play(viewer, "open");
        AdminItemMenu menu = new AdminItemMenu(plugin, shop, item, viewer);
        viewer.openInventory(menu.getInventory());
    }

    public void openSearch(@NotNull Player viewer, @NotNull String keyword) {
        io.github.hello09x.shop.util.Sounds.play(viewer, "open");
        SearchMenu menu = new SearchMenu(plugin, viewer, keyword);
        viewer.openInventory(menu.getInventory());
    }

    public void openFavorites(@NotNull Player viewer) {
        io.github.hello09x.shop.util.Sounds.play(viewer, "open");
        FavoriteMenu menu = new FavoriteMenu(plugin, viewer);
        viewer.openInventory(menu.getInventory());
    }

    public void openEntry(@NotNull Player viewer) {
        var shops = plugin.shops().all();
        if (shops.isEmpty()) {
            plugin.messages().send(viewer, "no-shops");
            return;
        }
        io.github.hello09x.shop.util.Sounds.play(viewer, "open");
        MainMenu menu = new MainMenu(plugin, viewer);
        viewer.openInventory(menu.getInventory());
    }

    /** 直接打开某一类（购买 / 收购）的分类列表 */
    public void openType(@NotNull Player viewer, @NotNull Shop.Type type) {
        var shops = plugin.shops().byType(type).stream()
                .filter(s -> s.isOpen())
                .filter(s -> s.permission() == null || viewer.hasPermission(s.permission()))
                .toList();
        if (shops.isEmpty()) {
            plugin.messages().send(viewer, "no-shops");
            return;
        }
        if (shops.size() == 1) {
            open(viewer, shops.get(0));
            return;
        }
        ShopListMenu menu = new ShopListMenu(plugin, viewer, type);
        viewer.openInventory(menu.getInventory());
        io.github.hello09x.shop.util.Sounds.play(viewer, "open");
    }

    /** 打开某一类的分类列表 */
    public void openList(@NotNull Player viewer, @NotNull Shop.Type type) {
        ShopListMenu menu = new ShopListMenu(plugin, viewer, type);
        viewer.openInventory(menu.getInventory());
        io.github.hello09x.shop.util.Sounds.play(viewer, "open");
    }

    public static @Nullable ShopListMenu asList(@Nullable InventoryHolder holder) {
        return holder instanceof ShopListMenu m ? m : null;
    }

    public static @Nullable ShopView asView(@Nullable InventoryHolder holder) {
        return holder instanceof ShopView v ? v : null;
    }

    public static @Nullable MainMenu asMenu(@Nullable InventoryHolder holder) {
        return holder instanceof MainMenu m ? m : null;
    }

    public static @Nullable ConfirmView asConfirm(@Nullable InventoryHolder holder) {
        return holder instanceof ConfirmView c ? c : null;
    }

    public static @Nullable TransactionMenu asTransactions(@Nullable InventoryHolder holder) {
        return holder instanceof TransactionMenu m ? m : null;
    }

    public static @Nullable AdminItemMenu asEdit(@Nullable InventoryHolder holder) {
        return holder instanceof AdminItemMenu m ? m : null;
    }

    public static @Nullable SearchMenu asSearch(@Nullable InventoryHolder holder) {
        return holder instanceof SearchMenu m ? m : null;
    }

    public static @Nullable FavoriteMenu asFavorites(@Nullable InventoryHolder holder) {
        return holder instanceof FavoriteMenu m ? m : null;
    }


}
