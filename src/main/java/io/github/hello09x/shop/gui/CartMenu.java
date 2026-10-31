package io.github.hello09x.shop.gui;

import io.github.hello09x.shop.ShopPlugin;
import io.github.hello09x.shop.currency.Currency;
import io.github.hello09x.shop.shop.CartService;
import io.github.hello09x.shop.shop.Shop;
import io.github.hello09x.shop.shop.ShopItem;
import io.github.hello09x.shop.util.Sounds;
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
import java.util.List;

/** 购物车：查看、加减数量、结算。 */
public final class CartMenu implements InventoryHolder {

    private static final int PAGE_SIZE = 36;
    private static final int ITEM_START = 9;

    private final ShopPlugin plugin;
    private final Player viewer;
    private final Inventory inventory;
    private final List<Line> lines = new ArrayList<>();
    private int page = 1;

    public CartMenu(@NotNull ShopPlugin plugin, @NotNull Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, 54, Text.color("&8我的购物车"));
        render();
    }

    private void collect() {
        lines.clear();
        List<String> stale = new ArrayList<>();
        for (var entry : plugin.cart().items(viewer.getUniqueId()).entrySet()) {
            int split = entry.getKey().indexOf(':');
            Shop shop = split > 0 ? plugin.shops().get(entry.getKey().substring(0, split)) : null;
            ShopItem item = shop == null ? null : shop.find(entry.getKey().substring(split + 1));
            if (item != null && shop.isOpen()) {
                lines.add(new Line(shop, item, entry.getValue()));
            } else {
                stale.add(entry.getKey());
            }
        }
        if (!stale.isEmpty()) {
            for (String key : stale) {
                plugin.cart().remove(viewer.getUniqueId(), key);
            }
            viewer.sendMessage(Text.color("&7已从购物车移除 &f" + stale.size() + " &7件已下架商品"));
        }
    }

    public void render() {
        inventory.clear();
        collect();
        GuiStyle.fill(inventory, ITEM_START, ITEM_START + PAGE_SIZE);
        GuiStyle.frame(inventory, Material.CYAN_STAINED_GLASS_PANE);

        int totalQuantity = 0;
        for (Line line : lines) {
            totalQuantity += line.quantity;
        }
        inventory.setItem(4, GuiStyle.button(Material.CHEST_MINECART,
                "&b&l我的购物车",
                "&8" + "▬".repeat(10),
                "&7共 &f" + lines.size() + " &7种 &f" + totalQuantity + " &7件",
                "&7点击商品可移除"));

        int pages = Math.max(1, (lines.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(1, Math.min(page, pages));
        int from = (page - 1) * PAGE_SIZE;
        int to = Math.min(lines.size(), from + PAGE_SIZE);
        for (int i = from; i < to; i++) {
            inventory.setItem(ITEM_START + i - from, icon(lines.get(i)));
        }

        if (page > 1) {
            inventory.setItem(45, GuiStyle.button(Material.ARROW, "&e« 上一页"));
        }
        inventory.setItem(48, GuiStyle.button(Material.BARRIER, "&c关闭", "&7点击关闭界面"));
        inventory.setItem(49, GuiStyle.glow(GuiStyle.button(Material.LIME_CONCRETE,
                "&a&l✔ 结算",
                "&8" + "▬".repeat(10),
                lines.isEmpty() ? "&7购物车是空的" : "&7共 &f" + lines.size() + " &7种商品",
                "",
                "&a➤ 点击全部购买")));
        inventory.setItem(50, GuiStyle.button(Material.BUCKET,
                "&c清空购物车",
                "&7移除全部商品",
                "",
                "&c➤ 点击清空"));
        if (page < pages) {
            inventory.setItem(53, GuiStyle.button(Material.ARROW, "&e下一页 »"));
        }
    }

    private @NotNull ItemStack icon(@NotNull Line line) {
        Currency currency = plugin.currencies().getOrDefault(line.item.currencyId());
        double unit = line.item.buyPrice(viewer);
        double total = unit * line.quantity;

        ItemStack stack = line.item.display();
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color("&f" + line.item.displayName()));
            meta.setLore(List.of(
                    Text.color("&7商店 &f" + line.shop.name()),
                    Text.color("&8" + "▬".repeat(10)),
                    Text.color("&7单价 &f" + money(currency, unit)),
                    Text.color("&7数量 &f" + line.quantity + " &8(右键 -1)"),
                    Text.color("&7小计 &e" + money(currency, total)),
                    Text.color("&8" + "▬".repeat(10)),
                    Text.color("&c左键 &7移除这一项")
            ));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private String money(Currency currency, double value) {
        return currency == null ? Currency.formatNumber(value) : currency.format(value);
    }

    public void onClick(int slot, @NotNull ClickType click) {
        if (slot == 45 && page > 1) {
            page--;
            Sounds.play(viewer, "page");
            render();
            return;
        }
        if (slot == 53) {
            page++;
            Sounds.play(viewer, "page");
            render();
            return;
        }
        if (slot == 48) {
            viewer.closeInventory();
            return;
        }
        if (slot == 49) {
            int queued = new CartService(plugin).checkout(viewer);
            if (queued > 0) {
                viewer.closeInventory();
            } else {
                render();
            }
            return;
        }
        if (slot == 50) {
            plugin.cart().clear(viewer.getUniqueId());
            Sounds.play(viewer, "click");
            render();
            return;
        }
        if (slot >= ITEM_START && slot < ITEM_START + PAGE_SIZE) {
            int index = (page - 1) * PAGE_SIZE + (slot - ITEM_START);
            if (index >= lines.size()) {
                return;
            }
            Line line = lines.get(index);
            if (click.isRightClick()) {
                plugin.cart().remove(viewer.getUniqueId(), line.item.key(), 1);
            } else {
                plugin.cart().remove(viewer.getUniqueId(), line.item.key());
            }
            Sounds.play(viewer, "click");
            render();
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public @NotNull Player viewer() {
        return viewer;
    }

    private record Line(Shop shop, ShopItem item, int quantity) {
    }
}
