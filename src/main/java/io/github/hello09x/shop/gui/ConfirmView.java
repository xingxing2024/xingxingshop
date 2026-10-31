package io.github.hello09x.shop.gui;

import io.github.hello09x.shop.ShopPlugin;
import io.github.hello09x.shop.currency.Currency;
import io.github.hello09x.shop.shop.Shop;
import io.github.hello09x.shop.shop.ShopItem;
import io.github.hello09x.shop.shop.TransactionService;
import io.github.hello09x.shop.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 交易确认界面。
 *
 * <pre>
 *  第 1 行  ████████  标题  ████████
 *  第 2 行  █       商品预览        █
 *  第 3 行  █  数量   单价   总价    █
 *  第 4 行  █  [确认]              [取消]  █
 *  第 5 行  ████████  ████████
 * </pre>
 *
 * <p>目的是防止误触 —— 尤其是收购侧的「全部卖出」。</p>
 */
public final class ConfirmView implements InventoryHolder {

    private static final int CONFIRM_SLOT = 29;
    private static final int CANCEL_SLOT = 33;
    private static final int INFO_SLOT = 13;

    private final ShopPlugin plugin;
    private final Shop shop;
    private final ShopItem item;
    private final Player viewer;
    private final int quantity;
    private final boolean selling;

    private final Inventory inventory;

    public ConfirmView(@NotNull ShopPlugin plugin, @NotNull Shop shop, @NotNull ShopItem item,
                       @NotNull Player viewer, int quantity, boolean selling) {
        this.plugin = plugin;
        this.shop = shop;
        this.item = item;
        this.viewer = viewer;
        this.quantity = quantity;
        this.selling = selling;
        this.inventory = Bukkit.createInventory(this, 45,
                Text.color(plugin.messages().raw("gui.title-confirm")));
        render();
    }

    public void render() {
        inventory.clear();

        // 边框
        ItemStack edge = pane(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack accent = pane(selling
                ? Material.ORANGE_STAINED_GLASS_PANE
                : Material.LIME_STAINED_GLASS_PANE);
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, edge);
            inventory.setItem(36 + i, edge);
        }
        for (int row = 1; row <= 3; row++) {
            inventory.setItem(row * 9, edge);
            inventory.setItem(row * 9 + 8, edge);
        }
        inventory.setItem(0, accent);
        inventory.setItem(8, accent);
        inventory.setItem(36, accent);
        inventory.setItem(44, accent);

        // 中间：商品预览 + 明细
        inventory.setItem(INFO_SLOT, infoIcon());

        // 确认 / 取消
        Currency currency = plugin.currencies().getOrDefault(item.currencyId());
        double unit = selling ? item.sellPrice() : item.buyPrice();
        double total = unit * quantity;

        inventory.setItem(CONFIRM_SLOT, confirmIcon(currency, total));
        inventory.setItem(CANCEL_SLOT, cancelIcon());
    }

    /**
     * 中间那个大图标：显示买/卖什么、多少、多少钱
     */
    private @NotNull ItemStack infoIcon() {
        ItemStack stack = item.display();
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }

        Currency currency = plugin.currencies().getOrDefault(item.currencyId());
        double unit = selling ? item.sellPrice() : item.buyPrice();
        double total = unit * quantity;

        meta.setDisplayName(Text.color("&6&l" + item.displayName()));

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(Text.color("&7操作 &f" + (selling ? "出售给商店" : "从商店购买")));
        lore.add(Text.color("&7数量 &f" + quantity + " &7个"));
        lore.add(Text.color("&7单价 &f" + money(currency, unit)));
        lore.add("");
        lore.add(Text.color("&7总额 &e" + money(currency, total)));
        lore.add("");
        lore.add(Text.color("&8请确认后再操作"));

        meta.setLore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private @NotNull ItemStack confirmIcon(@NotNull Currency currency, double total) {
        ItemStack stack = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.setDisplayName(Text.color("&a&l✔ 确认"));

        List<String> lore = new ArrayList<>();
        lore.add(Text.color("&7" + (selling ? "确定卖出" : "确定购买")
                + " &f" + quantity + " &7个"));
        lore.add(Text.color("&7总额 &e" + money(currency, total)));

        // 余额预检
        if (!selling && currency != null) {
            double balance = currency.balance(viewer);
            if (balance < total) {
                lore.add("");
                lore.add(Text.color("&c余额不足！"));
                lore.add(Text.color("&7需要 &e" + money(currency, total)
                        + " &7当前 &f" + money(currency, balance)));
            }
        }

        lore.add("");
        lore.add(Text.color("&a➤ 点击确认"));
        meta.setLore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private @NotNull ItemStack cancelIcon() {
        ItemStack stack = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.setDisplayName(Text.color("&c&l✘ 取消"));
        meta.setLore(List.of(
                Text.color("&7放弃这次操作"),
                Text.color("&7回到商店界面"),
                "",
                Text.color("&c➤ 点击取消")
        ));
        stack.setItemMeta(meta);
        return stack;
    }

    private @NotNull String money(Currency currency, double value) {
        return currency == null ? Currency.formatNumber(value) : currency.format(value);
    }

    private static @NotNull ItemStack pane(@NotNull Material material) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            stack.setItemMeta(meta);
        }
        return stack;
    }

    // ================================================================

    public void onClick(int slot) {
        if (slot == CONFIRM_SLOT) {
            // 不再单独播「确认」音效：紧接着的成交音效就是反馈，避免两个音效同一刻叠在一起
            // 回到商店界面并执行
            plugin.gui().open(viewer, shop);
            var holder = viewer.getOpenInventory().getTopInventory().getHolder();
            ShopView view = ShopGuiManager.asView(holder);
            if (view != null) {
                view.trade(item, quantity, selling);
            }
            return;
        }
        if (slot == CANCEL_SLOT) {
            io.github.hello09x.shop.util.Sounds.play(viewer, "cancel");
            plugin.gui().open(viewer, shop);
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public @NotNull Shop shop() {
        return shop;
    }

    public @NotNull ShopItem item() {
        return item;
    }

    public int quantity() {
        return quantity;
    }

    public boolean selling() {
        return selling;
    }

    @SuppressWarnings("unused")
    private static @NotNull String unused(TransactionService t) {
        return "";
    }

}
