package io.github.hello09x.shop.limit;

import io.github.hello09x.shop.ShopPlugin;
import io.github.hello09x.shop.log.TransactionDatabase;
import io.github.hello09x.shop.shop.ShopItem;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 限购 / 限卖记录。
 *
 * <p>优先存数据库（{@code shop_limit} 表）；数据库不可用时退回 {@code limits.yml}。</p>
 *
 * <p>限购简化为「每人每天」一个数字。</p>
 */
public final class LimitStore {

    private final ShopPlugin plugin;
    private final File file;

    /** date -> (uuid|itemKey 或 uuid|sell|itemKey → 数量) */
    private final Map<String, Map<String, Integer>> data = new ConcurrentHashMap<>();

    public LimitStore(@NotNull ShopPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "limits.yml");
    }

    private static @NotNull String today() {
        return LocalDate.now().toString();
    }

    // ================================================================
    //  载入
    // ================================================================

    public void load() {
        data.clear();
        var db = plugin.transactionLog().database();
        if (db.available()) {
            loadFromDatabase();
        } else {
            loadFromFile();
        }
    }

    private void loadFromDatabase() {
        var rows = plugin.transactionLog().database().loadLimits(today());
        applyRows(rows);
        if (!rows.isEmpty()) {
            plugin.getLogger().info("已从数据库载入 " + rows.size() + " 条限购记录");
        } else if (!plugin.transactionLog().database().hasAnyLimit()) {
            // 数据库里从来没有限购数据，尝试迁移旧的 limits.yml
            migrateFromFile();
        }
    }

    private void applyRows(@NotNull java.util.List<TransactionDatabase.LimitRow> rows) {
        Map<String, Integer> rowMap = new ConcurrentHashMap<>();
        for (var row : rows) {
            if (row.bought() > 0) {
                rowMap.put(row.playerUuid() + "|" + row.itemKey(), row.bought());
            }
            if (row.sold() > 0) {
                rowMap.put(row.playerUuid() + "|sell|" + row.itemKey(), row.sold());
            }
        }
        data.put(today(), rowMap);
    }

    /**
     * 跨服时从数据库刷新今天的限购缓存（只读，不写回）。
     */
    public void refresh() {
        var db = plugin.transactionLog().database();
        if (!db.shared()) {
            return;
        }
        applyRows(db.loadLimits(today()));
    }

    /** 把数据库返回的最新「今日已买」同步到内存 */
    public void applyBought(@NotNull UUID uuid, @NotNull ShopItem item, int bought) {
        data.computeIfAbsent(today(), k -> new ConcurrentHashMap<>())
                .put(uuid + "|" + item.key(), bought);
    }

    /** 把数据库返回的最新「今日已卖」同步到内存 */
    public void applySold(@NotNull UUID uuid, @NotNull ShopItem item, int sold) {
        data.computeIfAbsent(today(), k -> new ConcurrentHashMap<>())
                .put(uuid + "|sell|" + item.key(), sold);
    }

    private void migrateFromFile() {
        if (!file.exists()) {
            return;
        }
        loadFromFile();
        saveToDatabase(today());
        plugin.getLogger().info("已把 limits.yml 迁移到数据库");
    }

    private void loadFromFile() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String date : yaml.getKeys(false)) {
            var dateSection = yaml.getConfigurationSection(date);
            if (dateSection == null) {
                continue;
            }
            Map<String, Integer> row = new ConcurrentHashMap<>();
            for (String uuid : dateSection.getKeys(false)) {
                var section = yaml.getConfigurationSection(date + "." + uuid);
                if (section == null) {
                    continue;
                }
                for (String itemKey : section.getKeys(false)) {
                    int value = section.getInt(itemKey, 0);
                    if (value > 0) {
                        row.put(uuid + "|" + itemKey, value);
                    }
                }
            }
            if (!row.isEmpty()) {
                data.put(date, row);
            }
        }
    }

    private void saveToDatabase(@NotNull String day) {
        Map<String, Integer> row = data.get(day);
        if (row == null) {
            return;
        }
        var db = plugin.transactionLog().database();
        for (var entry : row.entrySet()) {
            int split = entry.getKey().indexOf('|');
            if (split <= 0) {
                continue;
            }
            String uuid = entry.getKey().substring(0, split);
            String rest = entry.getKey().substring(split + 1);
            boolean sell = rest.startsWith("sell|");
            String itemKey = sell ? rest.substring("sell|".length()) : rest;
            int bought = sell ? 0 : entry.getValue();
            int sold = sell ? entry.getValue() : 0;
            db.upsertLimit(day, uuid, itemKey, bought, sold);
        }
    }

    /** 把某一天的一条记录写进数据库（读旧值合并） */
    private void persistRow(@NotNull String day, @NotNull UUID uuid,
                            @NotNull String itemKey, boolean sell) {
        var db = plugin.transactionLog().database();
        // 跨服写入由 TransactionDatabase 的原子预留负责，这里不再整行覆盖
        if (db.shared()) {
            return;
        }
        if (!db.available()) {
            saveToFile();
            return;
        }
        // 始终把「买入 + 卖出」两个值一起写，避免覆盖
        Map<String, Integer> row = data.getOrDefault(day, Map.of());
        int bought = row.getOrDefault(uuid + "|" + itemKey, 0);
        int sold = row.getOrDefault(uuid + "|sell|" + itemKey, 0);
        db.upsertLimit(day, uuid.toString(), itemKey, bought, sold);
    }

    // ================================================================
    //  保存（文件兜底）
    // ================================================================

    public void save() {
        var db = plugin.transactionLog().database();
        // 跨服时数据库才是唯一数据源，不能用本服内存覆盖别的服
        if (db.shared()) {
            return;
        }
        if (db.available()) {
            saveToDatabase(today());
            return;
        }
        saveToFile();
    }

    private void saveToFile() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (var dateEntry : data.entrySet()) {
            for (var entry : dateEntry.getValue().entrySet()) {
                int split = entry.getKey().indexOf('|');
                if (split <= 0) {
                    continue;
                }
                String uuid = entry.getKey().substring(0, split);
                String itemKey = entry.getKey().substring(split + 1);
                yaml.set(dateEntry.getKey() + "." + uuid + "." + itemKey, entry.getValue());
            }
        }
        try {
            yaml.save(file);
        } catch (Exception e) {
            plugin.getLogger().warning("保存 limits.yml 失败：" + e.getMessage());
        }
    }

    // ================================================================
    //  查询
    // ================================================================

    /**
     * 今天已经买了多少
     */
    public long bought(@NotNull UUID uuid, @NotNull ShopItem item) {
        Map<String, Integer> row = data.get(today());
        if (row == null) {
            return 0;
        }
        return row.getOrDefault(uuid + "|" + item.key(), 0);
    }

    /**
     * 今天还能买多少
     */
    public long remaining(@NotNull UUID uuid, @NotNull ShopItem item) {
        if (!item.isLimited()) {
            return Long.MAX_VALUE;
        }
        return Math.max(0, item.limit() - bought(uuid, item));
    }

    /**
     * 今天已经卖了多少（收购菜单）
     */
    public long sold(@NotNull UUID uuid, @NotNull ShopItem item) {
        Map<String, Integer> row = data.get(today());
        if (row == null) {
            return 0;
        }
        return row.getOrDefault(uuid + "|sell|" + item.key(), 0);
    }

    /**
     * 今天还能卖多少
     */
    public long sellRemaining(@NotNull UUID uuid, @NotNull ShopItem item) {
        if (!item.isSellLimited()) {
            return Long.MAX_VALUE;
        }
        return Math.max(0, item.sellLimit() - sold(uuid, item));
    }

    /**
     * 记录一次出售
     */
    public void addSell(@NotNull UUID uuid, @NotNull ShopItem item, int quantity) {
        if (!item.isSellLimited()) {
            return;
        }
        String day = today();
        Map<String, Integer> row = data.computeIfAbsent(day, k -> new ConcurrentHashMap<>());
        row.merge(uuid + "|sell|" + item.key(), quantity, Integer::sum);
        persistRow(day, uuid, item.key(), true);
    }

    /**
     * 记录一次购买（立即落盘，防止重启丢数据）
     */
    public void add(@NotNull UUID uuid, @NotNull ShopItem item, int quantity) {
        if (!item.isLimited()) {
            return;
        }
        String day = today();
        Map<String, Integer> row = data.computeIfAbsent(day, k -> new ConcurrentHashMap<>());
        row.merge(uuid + "|" + item.key(), quantity, Integer::sum);
        persistRow(day, uuid, item.key(), false);
    }

    /**
     * 清理历史日期（不是今天的都删掉），返回清理条数
     */
    public int cleanup() {
        String today = today();
        int removed = 0;
        var it = data.entrySet().iterator();
        while (it.hasNext()) {
            if (!it.next().getKey().equals(today)) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            var db = plugin.transactionLog().database();
            if (db.available()) {
                db.deleteLimitsBefore(today);
            } else {
                saveToFile();
            }
        }
        return removed;
    }

    @SuppressWarnings("unused")
    private static void unused(Player p, Bukkit ignored) {
    }

}
