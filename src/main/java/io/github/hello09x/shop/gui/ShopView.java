package io.github.hello09x.shop.gui;

import io.github.hello09x.shop.ShopPlugin;
import io.github.hello09x.shop.currency.Currency;
import io.github.hello09x.shop.shop.Shop;
import io.github.hello09x.shop.shop.ShopItem;
import io.github.hello09x.shop.shop.TransactionService;
import io.github.hello09x.shop.util.ItemSerializer;
import io.github.hello09x.shop.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 商店界面。
 *
 * <pre>
 *  第 1 行  分类栏
 *  第 2~5 行 商品网格（4×9）
 *  第 6 行  操作条
 * </pre>
 *
 * <p>操作方式：</p>
 * <ul>
 *     <li><b>左键</b> —— 买/卖 1 个</li>
 *     <li><b>右键</b> —— 买/卖 1 组（64 个）</li>
 *     <li><b>Shift+左键</b> —— 打开数量选择界面</li>
 * </ul>
 */
public final class ShopView implements InventoryHolder {

    /** 商品区：第 2~5 行 */
    private static final int ITEM_START = 9;
    private static final int ITEM_AREA = 36;
    /** 操作条：第 6 行 */
    private static final int CONTROL = 45;

    private static final int TAB_CENTER = 4;
    private static final int TAB_LEFT_MAX = 4;

    private final ShopPlugin plugin;
    private final Shop shop;
    private final Player viewer;
    private final Inventory inventory;
    private final TransactionService transactions;

    private final Map<Integer, ShopItem> slotItems = new HashMap<>();

    /** 防止跨服异步交易期间重复点击 */
    private final AtomicBoolean trading = new AtomicBoolean();

    private int page = 1;
    private int totalPages = 1;

    public ShopView(@NotNull ShopPlugin plugin, @NotNull Shop shop, @NotNull Player viewer) {
        this.plugin = plugin;
        this.shop = shop;
        this.viewer = viewer;
        this.transactions = new TransactionService(plugin);

        boolean selling = shop.type() == Shop.Type.SELL;
        String raw = Text.color(plugin.messages().raw(selling ? "gui.title-sell" : "gui.title-buy"));
        this.inventory = Bukkit.createInventory(this, 54, raw.replace("{shop}", shop.name()));
        render();
    }

    // ================================================================
    //  渲染
    // ================================================================

    public void render() {
        inventory.clear();
        slotItems.clear();

        List<ShopItem> items = shop.sortedItems().stream()
                .filter(item -> item.permission() == null || viewer.hasPermission(item.permission()))
                .toList();
        this.totalPages = Math.max(1, (items.size() + ITEM_AREA - 1) / ITEM_AREA);
        this.page = Math.max(1, Math.min(page, totalPages));

        Material accent = shop.type() == Shop.Type.SELL
                ? Material.ORANGE_STAINED_GLASS_PANE
                : Material.LIME_STAINED_GLASS_PANE;
        GuiStyle.fill(inventory, ITEM_START, CONTROL);
        GuiStyle.frame(inventory, accent);

        renderItemGrid(items);
        renderHeader();
        renderControlBar();
    }

    /**
     * 原地刷新商品图标和底栏（不整体重绘，避免打断玩家点击）
     */
    public void refreshView() {
        if (viewer.getOpenInventory().getTopInventory().getHolder() != this) {
            return;
        }
        for (var entry : slotItems.entrySet()) {
            inventory.setItem(entry.getKey(), icon(entry.getValue()));
        }
        renderControlBar();
    }

    private void renderItemGrid(@NotNull List<ShopItem> items) {
        int from = (page - 1) * ITEM_AREA;
        int to = Math.min(items.size(), from + ITEM_AREA);

        for (int i = from; i < to; i++) {
            int slot = ITEM_START + (i - from);
            ShopItem item = items.get(i);
            slotItems.put(slot, item);
            inventory.setItem(slot, icon(item));
        }
    }

    private void renderHeader() {
        boolean selling = shop.type() == Shop.Type.SELL;

        // 0：返回同类的分类列表
        inventory.setItem(0, button(Material.COMPASS,
                "&e« 返回分类",
                "&7回到" + (selling ? "收购" : "购买") + "分类列表"));

        // 2：余额
        Currency currency = plugin.currencies().getOrDefault(null);
        inventory.setItem(2, button(Material.GOLD_INGOT,
                "&6余额",
                "&f" + (currency == null ? "无可用货币" : currency.format(currency.balance(viewer)))));

        // 4：当前分类
        long visibleItems = shop.sortedItems().stream()
                .filter(item -> item.permission() == null || viewer.hasPermission(item.permission()))
                .count();
        inventory.setItem(4, button(shop.icon(),
                (selling ? "&6&l" : "&a&l") + shop.name(),
                "&7类型 &8| &f" + (selling ? "收购" : "购买"),
                "&7商品 &8| &f" + visibleItems + " &7件"));

        // 6：搜索
        inventory.setItem(6, button(Material.COMPASS,
                "&e搜索商品",
                "&7输入关键词搜索全服商品"));

        // 8：回主菜单
        inventory.setItem(8, button(Material.NETHER_STAR,
                "&e主菜单",
                "&7回到商店首页"));
    }

    private void renderControlBar() {
        boolean selling = shop.type() == Shop.Type.SELL;

        inventory.setItem(CONTROL, button(Material.COMPASS,
                "&e« 返回分类",
                "&7回到" + (selling ? "收购" : "购买") + "分类列表"));

        inventory.setItem(CONTROL + 1, button(Material.NETHER_STAR,
                "&e主菜单", "&7回到商店首页"));

        int cartKinds = plugin.cart().items(viewer.getUniqueId()).size();
        if (!selling) {
            inventory.setItem(CONTROL + 2, GuiStyle.glow(button(Material.CHEST_MINECART,
                    "&b&l购物车",
                    "&7已选 &f" + cartKinds + " &7种商品",
                    "",
                    "&b➤ 点击查看并结算")));
        }

        if (page > 1) {
            inventory.setItem(CONTROL + 3, button(Material.ARROW,
                    "&e« 上一页", "&7第 &f" + (page - 1) + " &7页"));
        }
        inventory.setItem(CONTROL + 4, button(Material.PAPER,
                "&f第 &a" + page + " &f/ &a" + totalPages + " &f页",
                "&a" + GuiStyle.pageBar(page, totalPages) + " &7翻页",
                "&7共 &f" + shop.items().size() + " &7件商品"));
        if (page < totalPages) {
            inventory.setItem(CONTROL + 5, button(Material.ARROW,
                    "&e下一页 »", "&7第 &f" + (page + 1) + " &7页"));
        }

        inventory.setItem(CONTROL + 6, button(Material.HEART_OF_THE_SEA,
                "&b收藏夹",
                "&7查看收藏的商品"));

        inventory.setItem(CONTROL + 7, button(Material.KNOWLEDGE_BOOK,
                selling ? "&b收购说明" : "&b购买说明",
                selling ? "&e左键 &7直接卖 1 个" : "&e左键 &7直接买 1 个",
                selling ? "&e右键 &7全部卖出 &8[需确认]" : "&e右键 &7买 1 组 &8[需确认]",
                "&eShift+左键 &7输入数量",
                selling ? "&eShift+右键 &7收藏 / 取消收藏" : "&eF 键 &7加入购物车",
                "",
                "&8点击查看完整说明"));

        inventory.setItem(CONTROL + 8, button(Material.BARRIER, "&c关闭", "&7点击关闭界面"));
    }

    // ================================================================
    //  图标
    // ================================================================

    private @NotNull ItemStack icon(@NotNull ShopItem item) {
        boolean selling = shop.type() == Shop.Type.SELL;
        Currency currency = plugin.currencies().resolve(item.currencyId());

        ItemStack stack = item.display();
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }

        // 图标上显示中文名（发给玩家的仍是原版物品）
        if (!meta.hasDisplayName()) {
            meta.setDisplayName(Text.color("&f" + item.displayName()));
        }

        List<String> lore = new ArrayList<>();
        if (meta.hasLore() && meta.getLore() != null) {
            lore.addAll(meta.getLore());
            lore.add(Text.color(GuiStyle.SEP));
        }

        double unit = selling ? item.sellPrice(viewer) : item.buyPrice(viewer);
        String price = currency == null ? Currency.formatNumber(unit) : currency.format(unit);

        lore.add(Text.color("&7单价 &f" + price + " &8/ 个"));
        if (item.hasDiscount()) {
            double base = selling ? item.baseSellPrice() : item.baseBuyPrice();
            String old = currency == null ? Currency.formatNumber(base) : currency.format(base);
            lore.add(Text.color("&7原价 &8&m" + old + "&r &a限时折扣中"));
        }
        if (item.isVipPlayer(viewer)) {
            lore.add(Text.color("&6VIP 专享价"));
        }
        lore.add(Text.color(plugin.favorites().contains(viewer.getUniqueId(), item.key())
                ? "&b❤ 已收藏"
                : "&8❤ 未收藏"));
        int inCart = plugin.cart().items(viewer.getUniqueId()).getOrDefault(item.key(), 0);        if (inCart > 0) {
            lore.add(Text.color("&b购物车中 &f" + inCart + " &7件"));
        }
        lore.add(Text.color(GuiStyle.SEP));

        if (selling) {
            // 收购：显示 1 个 / 手上全部 能拿多少
            int have = TransactionService.count(viewer, item);
            lore.add(Text.color("&7卖 1 个 &8» &a" + price));
            if (have > 0) {
                // 受每日限卖限制，实际能卖的个数
                int sellable = have;
                if (item.isSellLimited()) {
                    long left = plugin.limits().sellRemaining(viewer.getUniqueId(), item);
                    sellable = (int) Math.min(have, Math.max(0, left));
                }
                double total = unit * sellable;
                String totalText = currency == null ? Currency.formatNumber(total) : currency.format(total);
                lore.add(Text.color("&7全部卖出 &8(&f" + sellable + "&7个) &8» &a" + totalText));
            } else {
                lore.add(Text.color("&8背包里没有这种东西"));
            }
        } else {
            // 购买：显示 1 个 / 1 组 各要多少钱
            double p64 = unit * 64;
            String p64s = currency == null ? Currency.formatNumber(p64) : currency.format(p64);
            lore.add(Text.color("&7买 1 个 &8» &e" + price));
            lore.add(Text.color("&7买 1 组 &8» &e" + p64s));
        }

        if (item.isLimitedStock()) {
            int stock = item.stock();
            int max = Math.max(1, item.maxStock());
            String c = stock <= 0 ? "&c" : stock <= max / 4 ? "&e" : "&a";
            lore.add(Text.color("&7库存 " + c + GuiStyle.bar(stock, max, 10)
                    + " &f" + stock + "&7/&f" + item.maxStock()));
        }
        if (item.hasRestock() && item.stock() < item.maxStock()) {
            long left = item.nextRestockAt() - System.currentTimeMillis();
            lore.add(Text.color("&7下次补货 &f" + (left <= 0 ? "即将" : formatRestock(left))));
        }
        if (item.isDynamicActive()) {
            // 动态价格提示按商店类型区分
            if (selling) {
                lore.add(Text.color(item.isStockDown()
                        ? "&a▼ 卖的人多了，收购价下跌"
                        : "&7价随行情浮动"));
            } else {
                int max = Math.max(1, item.maxStock());
                double ratio = (double) (max - item.stock()) / max;
                lore.add(Text.color(ratio > 0.1
                        ? "&c▲ 库存不足，价格已上涨"
                        : "&a▼ 库存充足，价格平稳"));
            }
        }
        if (!selling && item.isLimited()) {
            long left = plugin.limits().remaining(viewer.getUniqueId(), item);
            lore.add(left <= 0
                    ? Text.color("&c今日已买满")
                    : Text.color("&7今日可买 &f" + left + " &7个"));
        }
        if (selling && item.isSellLimited()) {
            long left = plugin.limits().sellRemaining(viewer.getUniqueId(), item);
            lore.add(left <= 0
                    ? Text.color("&c今日已卖满 &7(" + item.sellLimit() + " 个)")
                    : Text.color("&7今日可卖 &f" + left + " &7个"));
        }

        lore.add(Text.color(GuiStyle.SEP));
        lore.add(Text.color("&e左键 &7直接" + (selling ? "卖 1 个" : "买 1 个")));
        if (selling) {
            lore.add(Text.color("&e右键 &7全部卖出 &8[需确认]"));
        } else {
            lore.add(Text.color("&e右键 &7买 1 组 (64) &8[需确认]"));
        }
        lore.add(Text.color("&eShift+左键 &7聊天框输入数量"));
        if (!selling) {
            lore.add(Text.color("&eF 键 &7加入购物车"));
        }
        lore.add(Text.color("&eShift+右键 &7收藏 / 取消收藏"));

        meta.setLore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private @NotNull ItemStack tab(@NotNull Shop s, @NotNull Material border, @NotNull String kind) {
        boolean current = s.id().equalsIgnoreCase(shop.id());
        ItemStack stack = new ItemStack(current ? border : s.icon());
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.setDisplayName(Text.color((current ? "&a&l▶ " : "&7") + s.name()));
        List<String> lore = new ArrayList<>();
        lore.add(Text.color("&8" + kind));
        if (current) {
            lore.add(Text.color("&7当前分类"));
        } else {
            lore.add(Text.color("&7商品 &f" + s.items().size() + " &7件"));
            lore.add(Text.color("&e点击切换"));
        }
        meta.setLore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private @NotNull ItemStack titleTag() {
        ItemStack stack = new ItemStack(shop.icon());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color("&6&l" + shop.name()));
            List<String> lore = new ArrayList<>();
            for (String line : shop.desc()) {
                lore.add(Text.color(line));
            }
            lore.add(Text.color("&7商品 &f" + shop.items().size() + " &7件"));
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static @NotNull ItemStack button(@NotNull Material material, @NotNull String name, @NotNull String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(name));
            if (lore.length > 0) {
                List<String> list = new ArrayList<>();
                for (String line : lore) {
                    list.add(Text.color(line));
                }
                meta.setLore(list);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private List<Shop> visible(Shop.Type type) {
        return plugin.shops().byType(type).stream()
                .filter(s -> s.permission() == null || viewer.hasPermission(s.permission()))
                .toList();
    }

    // ================================================================
    //  点击
    // ================================================================

    public void onClick(int slot, @NotNull ClickType click) {
        if (slot < 9) {
            handleHeader(slot);
            return;
        }
        if (slot >= CONTROL) {
            handleControl(slot);
            return;
        }
        ShopItem item = slotItems.get(slot);
        if (item != null) {
            handleItem(item, click);
        }
    }

    private void handleHeader(int slot) {
        if (slot == 0) {
            plugin.gui().openList(viewer, shop.type());
        } else if (slot == 2) {
            render();
        } else if (slot == 6) {
            plugin.chatInput().requestSearch(viewer, shop);
        } else if (slot == 8) {
            plugin.gui().openEntry(viewer);
        }
    }

    private void handleControl(int slot) {
        switch (slot) {
            case CONTROL -> plugin.gui().openList(viewer, shop.type());
            case CONTROL + 1 -> plugin.gui().openEntry(viewer);
            case CONTROL + 2 -> {
                if (shop.type() == Shop.Type.BUY) {
                    plugin.gui().openCart(viewer);
                }
            }
            case CONTROL + 3 -> {
                if (page > 1) {
                    page--;
                    io.github.hello09x.shop.util.Sounds.play(viewer, "page");
                    render();
                }
            }
            case CONTROL + 4 -> render();
            case CONTROL + 5 -> {
                if (page < totalPages) {
                    page++;
                    io.github.hello09x.shop.util.Sounds.play(viewer, "page");
                    render();
                }
            }
            case CONTROL + 6 -> plugin.gui().openFavorites(viewer);
            case CONTROL + 7 -> plugin.gui().openHelp(viewer);
            case CONTROL + 8 -> viewer.closeInventory();
            default -> {
            }
        }
    }

    private void handleItem(@NotNull ShopItem item, @NotNull ClickType click) {
        if (!viewer.hasPermission("xshop.use")) {
            plugin.messages().send(viewer, "no-permission");
            return;
        }
        boolean selling = shop.type() == Shop.Type.SELL;
        if (selling && !viewer.hasPermission("xshop.sell")) {
            plugin.messages().send(viewer, "no-permission");
            return;
        }

        // 点击音效
        io.github.hello09x.shop.util.Sounds.play(viewer, "click");

        switch (click) {
            // 左键：直接买 / 卖 1 个（不再弹确认）
            case LEFT -> trade(item, 1, selling);
            // 右键：收购 = 全部卖出；购买 = 1 组，都先确认
            case RIGHT -> {
                if (selling) {
                    int all = TransactionService.count(viewer, item);
                    if (all <= 0) {
                        plugin.messages().send(viewer, "sell.not-enough-items", item.displayName());
                        return;
                    }
                    plugin.gui().openConfirm(viewer, shop, item, all, true);
                } else {
                    plugin.gui().openConfirm(viewer, shop, item, 64, false);
                }
            }
            // Shift+左键 / 中键：聊天框输入数量
            case SHIFT_LEFT, MIDDLE, NUMBER_KEY, DROP ->
                    plugin.chatInput().request(viewer, shop, item);
            // F 键：仅购买商店支持加入购物车
            case SWAP_OFFHAND -> {
                if (!selling) {
                    addToCart(item);
                }
            }
            // Shift+右键：收藏 / 取消收藏
            case SHIFT_RIGHT -> toggleFavorite(item);
            default -> trade(item, 1, selling);
        }
    }

    private void addToCart(@NotNull ShopItem item) {
        if (shop.type() != Shop.Type.BUY) {
            return;
        }
        if (item.isLimitedStock() && item.stock() <= 0) {
            io.github.hello09x.shop.util.Sounds.play(viewer, "out-of-stock");
            viewer.sendMessage(Text.color("&c[商店] &f" + item.displayName() + " &c已售罄"));
            return;
        }
        var uuid = viewer.getUniqueId();
        plugin.cart().add(uuid, item.key(), 1);
        int now = plugin.cart().items(uuid).getOrDefault(item.key(), 0);
        io.github.hello09x.shop.util.Sounds.play(viewer, "click");
        viewer.sendMessage(Text.color("&b🛒 已加入购物车 &f" + item.displayName()
                + " &7×1 &8(该项共 " + now + " 件)"));
        render();
    }

    private void toggleFavorite(@NotNull ShopItem item) {
        var favorites = plugin.favorites();
        var uuid = viewer.getUniqueId();
        boolean now;
        if (favorites.contains(uuid, item.key())) {
            favorites.remove(uuid, item.key());
            now = false;
        } else {
            favorites.add(uuid, item.key());
            now = true;
        }
        io.github.hello09x.shop.util.Sounds.play(viewer, "click");
        viewer.sendMessage(Text.color(now
                ? "&b❤ 已收藏 &f" + item.displayName()
                : "&7已取消收藏 &f" + item.displayName()));
        render();
    }

    /**
     * 真正执行交易并刷新界面
     */
    public void trade(@NotNull ShopItem item, int quantity, boolean selling) {
        if (!trading.compareAndSet(false, true)) {
            return;
        }

        java.util.function.Consumer<TransactionService.Outcome> done = outcome -> {
            try {
                transactions.notify(viewer, outcome, item, selling);
                plugin.stocks().save();
                if (viewer.getOpenInventory().getTopInventory().getHolder() == this) {
                    render();
                }
            } finally {
                trading.set(false);
            }
        };

        try {
            if (selling) {
                transactions.sell(viewer, shop, item, quantity, done);
            } else {
                transactions.buy(viewer, shop, item, quantity, done);
            }
        } catch (Throwable t) {
            trading.set(false);
            plugin.getLogger().warning("执行交易失败：" + t.getMessage());
        }
    }

    // ================================================================

    private static @NotNull String formatRestock(long millis) {
        long seconds = Math.max(1, (millis + 999) / 1000);
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) {
            return h + "h " + m + "m";
        }
        return String.format("%02d:%02d", m, s);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public @NotNull Shop shop() {
        return shop;
    }

    public @NotNull Player viewer() {
        return viewer;
    }

    public void page(int page) {
        this.page = page;
    }

    @SuppressWarnings("unused")
    private static @NotNull String unused(ItemSerializer s) {
        return "";
    }

}
