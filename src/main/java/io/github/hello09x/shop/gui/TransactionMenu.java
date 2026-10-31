package io.github.hello09x.shop.gui;

import io.github.hello09x.shop.ShopPlugin;
import io.github.hello09x.shop.log.TransactionLog;
import io.github.hello09x.shop.util.Sounds;
import io.github.hello09x.shop.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** 管理员交易记录面板。 */
public final class TransactionMenu implements InventoryHolder {

    private static final int PAGE_SIZE = 36;
    private static final int ITEM_START = 9;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");

    private final ShopPlugin plugin;
    private final Player viewer;
    private final Inventory inventory;
    private int page;

    public TransactionMenu(@NotNull ShopPlugin plugin, @NotNull Player viewer, int page) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.page = Math.max(1, page);
        this.inventory = Bukkit.createInventory(this, 54,
                Text.color("&8交易记录 &7» &f第 " + this.page + " 页"));
        render();
    }

    public void render() {
        inventory.clear();
        GuiStyle.fill(inventory, ITEM_START, ITEM_START + PAGE_SIZE);
        GuiStyle.frame(inventory, Material.CYAN_STAINED_GLASS_PANE);
        List<TransactionLog.Record> records = plugin.transactionLog().query(null, page, PAGE_SIZE);
        for (int i = 0; i < records.size(); i++) {
            inventory.setItem(ITEM_START + i, icon(records.get(i)));
        }
        int total = plugin.transactionLog().count(null);
        int pages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page > pages) {
            page = pages;
        }
        inventory.setItem(4, button(Material.WRITABLE_BOOK,
                "&b&l交易记录",
                "&7共 &f" + total + " &7条"));

        if (page > 1) {
            inventory.setItem(45, button(Material.ARROW, "&e« 上一页", "&7第 " + (page - 1) + " 页"));
        }
        inventory.setItem(49, button(Material.PAPER,
                "&f第 &a" + page + " &f/ &a" + pages + " &f页",
                "&7共 &f" + total + " &7条记录"));
        if (page < pages) {
            inventory.setItem(53, button(Material.ARROW, "&e下一页 »", "&7第 " + (page + 1) + " 页"));
        }
        inventory.setItem(48, button(Material.COMPASS, "&e刷新", "&7重新读取数据库"));
        inventory.setItem(50, button(Material.BARRIER, "&c关闭", "&7点击关闭界面"));
    }

    private @NotNull ItemStack icon(@NotNull TransactionLog.Record rec) {
        ItemStack stack = new ItemStack(rec.buying() ? Material.EMERALD : Material.GOLD_INGOT);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.setDisplayName(Text.color((rec.buying() ? "&a买入 &f" : "&6卖出 &f") + rec.itemName()));
        LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochMilli(rec.timestamp()), ZoneId.systemDefault());
        List<String> lore = new ArrayList<>();
        lore.add(Text.color("&7玩家 &f" + rec.playerName()));
        lore.add(Text.color("&7时间 &f" + time.format(TIME)));
        lore.add(Text.color("&7商店 &f" + rec.shopId()));
        lore.add(Text.color("&7数量 &f" + rec.quantity()
                + " &7单价 &f" + rec.unitPrice() + " &7总价 &f" + rec.total()));
        if (rec.buying()) {
            lore.add(Text.color("&7玩家支出 &f" + rec.total()));
        } else {
            lore.add(Text.color("&7玩家收入 &f" + rec.total()));
        }
        if (rec.admin()) {
            lore.add(Text.color("&c[管理员交易]"));
        }
        meta.setLore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private static @NotNull ItemStack button(@NotNull Material material, @NotNull String name, @NotNull String lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(name));
            meta.setLore(List.of(Text.color(lore)));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public void onClick(int slot) {
        if (slot == 45 && page > 1) {
            page--;
            Sounds.play(viewer, "page");
            render();
        } else if (slot == 53) {
            page++;
            Sounds.play(viewer, "page");
            render();
        } else if (slot == 48) {
            render();
        } else if (slot == 50) {
            viewer.closeInventory();
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public @NotNull Player viewer() {
        return viewer;
    }
}
