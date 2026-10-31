package io.github.hello09x.shop.gui;

import io.github.hello09x.shop.ShopPlugin;
import io.github.hello09x.shop.shop.Shop;
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
 * 主菜单：只让玩家选「购买」还是「收购」，两类彻底分开。
 *
 * <pre>
 *  ┌───────────────────────┐
 *  │          ✦ 商店 ✦       │
 *  │   [购买商店]  [收购商店]  │
 *  │  余额   说明        关闭  │
 *  └───────────────────────┘
 * </pre>
 */
public final class MainMenu implements InventoryHolder {

    private static final int BUY_SLOT = 20;
    private static final int SELL_SLOT = 24;
    private static final int BALANCE_SLOT = 49;
    private static final int HELP_SLOT = 50;
    private static final int CLOSE_SLOT = 53;

    private final ShopPlugin plugin;
    private final Player viewer;
    private final Inventory inventory;

    public MainMenu(@NotNull ShopPlugin plugin, @NotNull Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, 54,
                Text.color(plugin.messages().raw("gui.title-menu")));
        render();
    }

    public void render() {
        inventory.clear();

        // 边框
        ItemStack edge = pane(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack accent = pane(Material.CYAN_STAINED_GLASS_PANE);
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, edge);
            inventory.setItem(45 + i, edge);
        }
        for (int row = 1; row <= 4; row++) {
            inventory.setItem(row * 9, edge);
            inventory.setItem(row * 9 + 8, edge);
        }
        inventory.setItem(0, accent);
        inventory.setItem(8, accent);
        inventory.setItem(45, accent);
        inventory.setItem(53, accent);

        // 标题
        inventory.setItem(4, icon(Material.NETHER_STAR,
                "&b&l✦ &f&l商店 &b&l✦",
                "",
                "&7欢迎，&f" + viewer.getName(),
                "&7请选择要进入的商店"));

        // 购买入口
        List<Shop> buys = visible(Shop.Type.BUY);
        inventory.setItem(BUY_SLOT, bigButton(Material.EMERALD,
                "&a&l购买商店",
                "&7用钱买物资、建材、装备……",
                "",
                "&7共 &f" + buys.size() + " &7个分类",
                "",
                "&a➤ 点击进入购买分类列表"));

        // 收购入口
        List<Shop> sells = visible(Shop.Type.SELL);
        inventory.setItem(SELL_SLOT, bigButton(Material.HOPPER,
                "&6&l收购商店",
                "&7把多余的材料卖成钱",
                "",
                "&7共 &f" + sells.size() + " &7个分类",
                "",
                "&6➤ 点击进入收购分类列表"));

        // 余额
        var currency = plugin.currencies().getOrDefault(null);
        if (currency != null) {
            inventory.setItem(BALANCE_SLOT, icon(Material.GOLD_INGOT,
                    "&6&l余额",
                    "",
                    "&7" + currency.displayName() + " &f" + currency.format(currency.balance(viewer)),
                    "",
                    "&8点击刷新"));
        } else {
            inventory.setItem(BALANCE_SLOT, icon(Material.BARRIER,
                    "&c&l无可用货币",
                    "",
                    "&7请安装 &fVault &7+ 经济插件"));
        }

        // 操作说明（与真实行为一致）
        inventory.setItem(HELP_SLOT, icon(Material.KNOWLEDGE_BOOK,
                "&e&l操作说明",
                "",
                "&f左键 &7» 买/卖 &f1 个",
                "&f右键 &7» 购买=买 &f1 组(64)&7，收购=&f全部卖出",
                "&fShift+左键 &7» 聊天框输入数量",
                "",
                "&8输入 /shop help 查看全部指令"));

        inventory.setItem(CLOSE_SLOT, icon(Material.BARRIER, "&c&l关闭", "&7点击关闭界面"));
    }

    private List<Shop> visible(Shop.Type type) {
        return plugin.shops().byType(type).stream()
                .filter(s -> s.isOpen())
                .filter(s -> s.permission() == null || viewer.hasPermission(s.permission()))
                .toList();
    }

    // ------------------------------------------------------------ 点击

    public void onClick(int slot) {
        switch (slot) {
            case BUY_SLOT -> plugin.gui().openList(viewer, Shop.Type.BUY);
            case SELL_SLOT -> plugin.gui().openList(viewer, Shop.Type.SELL);
            case BALANCE_SLOT, HELP_SLOT -> render();
            case CLOSE_SLOT -> viewer.closeInventory();
            default -> {
            }
        }
    }

    // ------------------------------------------------------------ 工具

    private static @NotNull ItemStack pane(@NotNull Material material) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static @NotNull ItemStack bigButton(@NotNull Material material,
                                                @NotNull String name, @NotNull String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(name));
            List<String> list = new ArrayList<>();
            for (String line : lore) {
                list.add(Text.color(line));
            }
            meta.setLore(list);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static @NotNull ItemStack icon(@NotNull Material material, @NotNull String name, @NotNull String... lore) {
        return bigButton(material, name, lore);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

}
