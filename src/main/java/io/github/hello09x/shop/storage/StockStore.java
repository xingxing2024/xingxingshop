package io.github.hello09x.shop.storage;

import io.github.hello09x.shop.ShopPlugin;
import io.github.hello09x.shop.log.TransactionDatabase;
import io.github.hello09x.shop.shop.Shop;
import io.github.hello09x.shop.shop.ShopItem;
import io.github.hello09x.shop.util.Schedulers;
import io.github.hello09x.shop.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
    /** 单服模式下每个商品的下一次补货时间 */
    private final Map<String, Long> localNextRestock = new ConcurrentHashMap<>();

    /** 每日限额刷新的提醒记录：玩家 -> 已提醒的日期 */
    private final Map<UUID, LocalDate> limitNoticeDay = new ConcurrentHashMap<>();

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

    /**
     * 跨服时下单 / 打开确认界面前刷新单个商品，避免用 5 秒前的缓存价格。
     */
    public void refreshItem(@NotNull ShopItem item) {
        var db = plugin.transactionLog().database();
        if (!db.shared()) {
            return;
        }
        var row = db.loadStockRow(item.shopId(), item.itemId());
        if (row != null) {
            applyReservation(item, row.stock(), row.sold(), row.lastRestock());
        }
    }

    /**
     * 自动补货。跨服用数据库时间戳去重，单服用本服时间。
     */
    public void restockAll() {
        var db = plugin.transactionLog().database();
        long now = System.currentTimeMillis();
        for (Shop shop : plugin.shops().all().values()) {
            for (ShopItem item : shop.items()) {
                if (!item.hasRestock()) {
                    continue;
                }
                if (db.shared()) {
                    int before = item.stock();
                    int after = db.restock(item.shopId(), item.itemId(), item.maxStock(),
                            item.restockAmount(), item.restockInterval() * 1000L);
                    if (after >= 0) {
                        applyReservation(item, after, item.sold());
                        item.nextRestockAt(now + item.restockInterval() * 1000L);
                        notifyRestock(item, before, after);
                    }
                } else {
                    if (item.stock() >= item.maxStock()) {
                        continue;
                    }
                    long next = localNextRestock.getOrDefault(item.key(), 0L);
                    if (now < next) {
                        continue;
                    }
                    int before = item.stock();
                    item.stock(Math.min(item.maxStock(), item.stock() + item.restockAmount()));
                    localNextRestock.put(item.key(), now + item.restockInterval() * 1000L);
                    item.nextRestockAt(now + item.restockInterval() * 1000L);
                    saveItem(item);
                    notifyRestock(item, before, item.stock());
                }
            }
        }
    }

    private void notifyRestock(@NotNull ShopItem item, int before, int after) {
        if (after <= before) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.favorites().contains(player.getUniqueId(), item.key())) {
                continue;
            }
            Schedulers.entity(plugin, player, () -> player.sendMessage(Text.color(
                    "&a[商店] &f" + item.displayName() + " &a已补货，当前库存 &f" + after)));
        }
    }

    /** 商品刚售罄时提醒收藏了它的在线玩家 */
    public void notifySoldOut(@NotNull ShopItem item) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.favorites().contains(player.getUniqueId(), item.key())) {
                continue;
            }
            Schedulers.entity(plugin, player, () -> player.sendMessage(Text.color(
                    "&c[商店] &f" + item.displayName() + " &c已售罄")));
        }
    }

    /**
     * 新的一天：提醒收藏里有限购 / 限卖的商品额度已刷新。
     * 每天每人只提醒一次，跨天上线也会补发。
     */
    public void notifyDailyLimitRefresh(@NotNull Player player) {
        var uuid = player.getUniqueId();
        LocalDate today = LocalDate.now();
        if (today.equals(limitNoticeDay.get(uuid))) {
            return;
        }

        Schedulers.entity(plugin, player, () -> {
            if (!player.isOnline() || today.equals(limitNoticeDay.get(uuid))) {
                return;
            }
            List<String> names = new ArrayList<>();
            for (String key : plugin.favorites().list(uuid)) {
                int split = key.indexOf(':');
                if (split <= 0) {
                    continue;
                }
                Shop shop = plugin.shops().get(key.substring(0, split));
                ShopItem item = shop == null ? null : shop.find(key.substring(split + 1));
                if (item == null || !shop.isOpen()) {
                    continue;
                }
                if (item.isLimited() || item.isSellLimited()) {
                    names.add(item.displayName());
                }
                if (names.size() >= 5) {
                    break;
                }
            }
            limitNoticeDay.put(uuid, today);
            if (names.isEmpty()) {
                return;
            }
            player.sendMessage(Text.color("&a[商店] &7新的一天，收藏的 &f"
                    + String.join("、", names) + " &7限购 / 限卖额度已刷新"));
        });
    }

    /** 玩家下线后清掉提醒记录，避免长期占用内存 */
    public void forgetDailyNotice(@NotNull Player player) {
        limitNoticeDay.remove(player.getUniqueId());
    }

    private void applyRows(@NotNull java.util.List<TransactionDatabase.StockRow> rows) {        Map<String, ShopItem> byKey = buildIndex();
        for (var row : rows) {
            ShopItem item = byKey.get(row.shopId() + ":" + row.itemId());
            if (item != null) {
                applyReservation(item, row.stock(), row.sold(), row.lastRestock());
            }
        }
    }

    /**
     * 把数据库返回的最新库存 / 销量同步到内存商品。
     */
    public void applyReservation(@NotNull ShopItem item, int stock, int sold) {
        int safeStock = Math.max(0, stock);
        if (item.maxStock() > 0) {
            safeStock = Math.min(item.maxStock(), safeStock);
        }
        item.stock(safeStock);
        item.sold(Math.max(0, sold));
    }

    public void applyReservation(@NotNull ShopItem item, int stock, int sold, long lastRestock) {
        applyReservation(item, stock, sold);
        if (item.hasRestock()) {
            item.nextRestockAt(lastRestock > 0
                    ? lastRestock + item.restockInterval() * 1000L
                    : System.currentTimeMillis());
        }
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
                int value = yaml.getInt(item.key() + ".stock");
                if (item.maxStock() > 0) {
                    value = Math.min(item.maxStock(), value);
                }
                item.stock(Math.max(0, value));
            }
            if (yaml.contains(item.key() + ".sold")) {
                item.sold(Math.max(0, yaml.getInt(item.key() + ".sold")));
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
            // 跨服时管理员改库存要立刻落库，否则别的服可能按旧库存/默认库存处理
            if (db.shared()) {
                db.flush();
            }
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
