package io.github.hello09x.shop.storage;

import io.github.hello09x.shop.ShopPlugin;
import io.github.hello09x.shop.log.TransactionDatabase;
import io.github.hello09x.shop.shop.Shop;
import io.github.hello09x.shop.shop.ShopItem;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * 库存与销量持久化。
 *
 * <p>优先存数据库（{@code shop_stock} 表）；数据库不可用时退回 {@code stock.yml}。</p>
 *
 * <p>只有「限量供应」的商品才会被记录。</p>
 */
public final class StockStore {

    private final ShopPlugin plugin;
    private final File file;

    public StockStore(@NotNull ShopPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stock.yml");
    }

    // ================================================================
    //  载入
    // ================================================================

    public void load() {
        var db = plugin.transactionLog().database();
        if (db.available()) {
            loadFromDatabase();
        } else {
            loadFromFile();
        }
    }

    private void loadFromDatabase() {
        var rows = plugin.transactionLog().database().loadStock();
        if (rows.isEmpty()) {
            // 数据库还没有数据，尝试把旧的 stock.yml 迁进去
            migrateFromFile();
            return;
        }
        applyRows(rows);
        plugin.getLogger().info("已从数据库载入 " + rows.size() + " 条库存记录");
    }

    /**
     * 跨服时从数据库刷新内存缓存（只读，不写回）。
     */
    public void refresh() {
        var db = plugin.transactionLog().database();
        if (!db.shared()) {
            return;
        }
        applyRows(db.loadStock());
    }

    private void applyRows(@NotNull java.util.List<TransactionDatabase.StockRow> rows) {
        Map<String, ShopItem> byKey = buildIndex();
        for (var row : rows) {
            ShopItem item = byKey.get(row.shopId() + ":" + row.itemId());
            if (item != null) {
                applyReservation(item, row.stock(), row.sold());
            }
        }
    }

    /**
     * 把数据库返回的最新库存 / 销量同步到内存商品。
     */
    public void applyReservation(@NotNull ShopItem item, int stock, int sold) {
        item.stock(stock);
        item.sold(sold);
    }

    private void migrateFromFile() {
        if (!file.exists()) {
            return;
        }
        loadFromFile();
        saveToDatabase();
        plugin.getLogger().info("已把 stock.yml 迁移到数据库");
    }

    private void loadFromFile() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        Map<String, ShopItem> byKey = buildIndex();
        for (ShopItem item : byKey.values()) {
            if (yaml.contains(item.key() + ".stock")) {
                item.stock(yaml.getInt(item.key() + ".stock"));
            }
            if (yaml.contains(item.key() + ".sold")) {
                item.sold(yaml.getInt(item.key() + ".sold"));
            }
        }
    }

    private @NotNull Map<String, ShopItem> buildIndex() {
        Map<String, ShopItem> byKey = new HashMap<>();
        for (Shop shop : plugin.shops().all().values()) {
            for (ShopItem item : shop.items()) {
                byKey.put(item.key(), item);
            }
        }
        return byKey;
    }

    // ================================================================
    //  保存
    // ================================================================

    public void save() {
        var db = plugin.transactionLog().database();
        // 跨服时数据库才是唯一数据源，不能用本服内存覆盖别的服
        if (db.shared()) {
            return;
        }
        if (db.available()) {
            saveToDatabase();
        } else {
            saveToFile();
        }
    }

    /**
     * 立即持久化单个商品（交易后调用，保证库存实时进数据库）
     */
    public void saveItem(@NotNull ShopItem item) {
        var db = plugin.transactionLog().database();
        if (db.available()) {
            db.upsertStock(item.shopId(), item.itemId(), item.stock(), item.sold());
        }
        // 数据库不可用时靠定时任务 / 关服时写文件，避免每次交易都写 YAML
    }

    private void saveToDatabase() {
        var db = plugin.transactionLog().database();
        for (Shop shop : plugin.shops().all().values()) {
            for (ShopItem item : shop.items()) {
                if (item.isLimitedStock() || item.sold() > 0) {
                    db.upsertStock(item.shopId(), item.itemId(), item.stock(), item.sold());
                }
            }
        }
    }

    private void saveToFile() {
        YamlConfiguration yaml = new YamlConfiguration();
        boolean any = false;
        for (Shop shop : plugin.shops().all().values()) {
            for (ShopItem item : shop.items()) {
                if (item.isLimitedStock()) {
                    yaml.set(item.key() + ".stock", item.stock());
                    any = true;
                }
                if (item.sold() > 0) {
                    yaml.set(item.key() + ".sold", item.sold());
                    any = true;
                }
            }
        }
        if (!any) {
            return;
        }
        try {
            yaml.save(file);
        } catch (Exception e) {
            plugin.getLogger().warning("保存 stock.yml 失败：" + e.getMessage());
        }
    }

}
