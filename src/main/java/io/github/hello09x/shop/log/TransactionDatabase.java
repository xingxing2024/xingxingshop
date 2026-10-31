package io.github.hello09x.shop.log;

import io.github.hello09x.shop.ShopPlugin;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 交易记录数据库。
 *
 * <p>支持两种后端：</p>
 * <ul>
 *     <li>{@code sqlite} —— 零配置，数据存在 {@code plugins/XingXingShop/transactions.db}</li>
 *     <li>{@code mysql} —— 跨服共享，需要填连接信息</li>
 * </ul>
 *
 * <p><b>写入</b>：所有写操作都丢进一个<b>单线程</b>队列串行执行，
 * 既不会阻塞主线程，也不会因为多线程抢 SQLite 文件锁而卡住。</p>
 *
 * <p><b>读取</b>：每次查询临时开连接。SQLite 开启了 WAL 模式，读写可以并存。</p>
 */
public final class TransactionDatabase {

    public enum Type {
        SQLITE,
        MYSQL,
        NONE
    }

    private final ShopPlugin plugin;
    private final Type type;
    /** 表名前缀（多插件共库时用） */
    private final String prefix;

    /** SQLite 连接串 */
    private String sqliteUrl;
    /** MySQL 连接池（跨服时复用连接，避免每笔交易重新握手） */
    private HikariDataSource mysqlPool;
    /** 下载/加载的 JDBC 驱动 */
    private DriverLoader driverLoader;
    private volatile boolean available;

    /** 单线程写入队列 */
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "XingXingShop-TransactionWriter");
        t.setDaemon(true);
        return t;
    });

    /** 已排队 + 已写入的记录数（供测试/统计用） */
    private final AtomicLong written = new AtomicLong();

    public TransactionDatabase(@NotNull ShopPlugin plugin) {
        this.plugin = plugin;
        String raw = plugin.config().dbType();
        this.type = switch (raw == null ? "sqlite" : raw.toLowerCase(java.util.Locale.ROOT)) {
            case "mysql" -> Type.MYSQL;
            case "none", "off", "disabled" -> Type.NONE;
            default -> Type.SQLITE;
        };
        this.prefix = plugin.config().dbTablePrefix();
    }

    public @NotNull Type type() {
        return type;
    }

    public boolean available() {
        return available && type != Type.NONE;
    }

    /** 配置的是 MySQL（不管当前连没连上） */
    public boolean mysqlConfigured() {
        return type == Type.MYSQL;
    }

    /**
     * 是否多服共享同一个数据库。
     *
     * <p>只有 MySQL 才算跨服；SQLite 是单服本地文件。</p>
     */
    public boolean shared() {
        return available() && type == Type.MYSQL;
    }

    // ================================================================
    //  初始化
    // ================================================================

    /**
     * 建库建表（异步调用）
     */
    public synchronized void init() {
        if (type == Type.NONE) {
            return;
        }
        available = false;
        if (type == Type.MYSQL) {
            closeMysql();
        }
        try {
            if (driverLoader == null) {
                driverLoader = DriverLoader.load(plugin, type == Type.MYSQL);
            }
            if (type == Type.SQLITE) {
                initSqlite();
            } else if (type == Type.MYSQL) {
                initMysql();
            }
            createTable();
            available = true;
            plugin.getLogger().info("交易记录数据库已就绪（" + type.name().toLowerCase() + "）");
        } catch (Throwable t) {
            available = false;
            closeMysql();
            plugin.getLogger().warning("交易记录数据库初始化失败（" + type.name().toLowerCase()
                    + "）：" + t.getMessage() + "，将只使用文本日志");
        }
    }

    private void initMysql() {
        var cfg = plugin.config();
        HikariConfig hc = new HikariConfig();
        Properties props = new Properties();
        props.setProperty("user", cfg.dbUser());
        props.setProperty("password", cfg.dbPassword());
        hc.setDataSource(new DriverLoader.DriverDataSource(driverLoader.driver(), cfg.dbUrl(), props));
        hc.setPoolName("XingXingShop-DB");
        hc.setMaximumPoolSize(plugin.config().dbPoolSize());
        hc.setMinimumIdle(1);
        hc.setConnectionTimeout(plugin.config().dbConnectionTimeout());
        hc.setValidationTimeout(3000);
        hc.setMaxLifetime(1_800_000);
        hc.setIdleTimeout(600_000);
        hc.setInitializationFailTimeout(1);
        hc.addDataSourceProperty("cachePrepStmts", "true");
        hc.addDataSourceProperty("prepStmtCacheSize", "250");
        hc.addDataSourceProperty("useServerPrepStmts", "true");
        mysqlPool = new HikariDataSource(hc);
    }

    private void closeMysql() {
        if (mysqlPool != null) {
            try {
                mysqlPool.close();
            } catch (Throwable ignored) {
                // 忽略
            }
            mysqlPool = null;
        }
    }

    private void initSqlite() {
        try {
            File file = new File(plugin.getDataFolder(), "transactions.db");
            sqliteUrl = "jdbc:sqlite:" + file.getAbsolutePath();
            // WAL 模式：读写并存；busy_timeout 防止偶发锁冲突直接报错
            try (Connection c = sqliteDriver().connect(sqliteUrl, new java.util.Properties());
                 Statement st = c.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA synchronous=NORMAL");
                st.execute("PRAGMA busy_timeout=5000");
            }
        } catch (Exception e) {
            throw new IllegalStateException("SQLite 初始化失败：" + e.getMessage(), e);
        }
    }

    private java.sql.Driver sqliteDriver() {
        if (driverLoader == null) {
            throw new IllegalStateException("SQLite 驱动未加载");
        }
        return driverLoader.driver();
    }

    /**
     * 取一条新连接（调用方负责关闭）
     */
    private @NotNull Connection connection() throws SQLException {
        if (type == Type.SQLITE) {
            if (sqliteUrl == null) {
                initSqlite();
            }
            Connection c = sqliteDriver().connect(sqliteUrl, new java.util.Properties());
            if (c == null) {
                throw new SQLException("SQLite 驱动拒绝连接");
            }
            c.setAutoCommit(true);
            try (Statement st = c.createStatement()) {
                st.execute("PRAGMA busy_timeout=5000");
            }
            return c;
        }
        // MySQL
        if (mysqlPool == null) {
            throw new SQLException("MySQL 连接池未初始化");
        }
        Connection c = mysqlPool.getConnection();
        c.setAutoCommit(true);
        return c;
    }

    private static void closeQuietly(@Nullable Connection conn) {
        if (conn == null) {
            return;
        }
        try {
            conn.close();
        } catch (SQLException ignored) {
            // 忽略
        }
    }

    /** 加表名前缀 */
    private @NotNull String table(@NotNull String name) {
        return prefix + name;
    }

    /** 加索引名前缀 */
    private @NotNull String index(@NotNull String name) {
        return prefix + name;
    }

    private void createTable() throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS %s (
                    id          %s,
                    time        BIGINT      NOT NULL,
                    player_name VARCHAR(32) NOT NULL,
                    player_uuid VARCHAR(36) NOT NULL,
                    shop_id     VARCHAR(64) NOT NULL,
                    item_id     VARCHAR(64) NOT NULL,
                    item_name   VARCHAR(64) NOT NULL,
                    currency    VARCHAR(32),
                    buying      %s          NOT NULL,
                    quantity    INT         NOT NULL,
                    unit_price  DOUBLE      NOT NULL,
                    total       DOUBLE      NOT NULL,
                    admin       %s          NOT NULL DEFAULT 0,
                    external_id VARCHAR(128)
                )
                """.formatted(
                table("shop_transaction"),
                type == Type.MYSQL
                        ? "BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY"
                        : "INTEGER PRIMARY KEY AUTOINCREMENT",
                type == Type.MYSQL ? "TINYINT(1)" : "INTEGER",
                type == Type.MYSQL ? "TINYINT(1)" : "INTEGER"
        );
        Connection conn = null;
        try {
            conn = connection();
            try (Statement st = conn.createStatement()) {
                st.execute(sql);
                // 索引（加快按玩家/时间查询）
                safeIndex(st, "idx_shop_tr_player", "player_name");
                safeIndex(st, "idx_shop_tr_time", "time");
                safeIndex(st, "idx_shop_tr_item", "item_id");

                // 库存表
                st.execute("""
                        CREATE TABLE IF NOT EXISTS %s (
                            shop_id VARCHAR(64) NOT NULL,
                            item_id VARCHAR(64) NOT NULL,
                            stock   INT         NOT NULL,
                            sold    INT         NOT NULL,
                            last_restock BIGINT NOT NULL DEFAULT 0,
                            PRIMARY KEY (shop_id, item_id)
                        )
                        """.formatted(table("shop_stock")));

                // 每日限购 / 限卖表
                st.execute("""
                        CREATE TABLE IF NOT EXISTS %s (
                            day         VARCHAR(10) NOT NULL,
                            player_uuid VARCHAR(36) NOT NULL,
                            item_key    VARCHAR(160) NOT NULL,
                            bought      INT         NOT NULL DEFAULT 0,
                            sold        INT         NOT NULL DEFAULT 0,
                            PRIMARY KEY (day, player_uuid, item_key)
                        )
                        """.formatted(table("shop_limit")));

                // 个人日报投递记录（记录每个玩家最后一次收到日报的日期）
                st.execute("""
                        CREATE TABLE IF NOT EXISTS %s (
                            player_uuid VARCHAR(36) NOT NULL PRIMARY KEY,
                            last_day    VARCHAR(10) NOT NULL
                        )
                        """.formatted(table("shop_report_seen")));

                // 跨服商店配置同步
                st.execute("""
                        CREATE TABLE IF NOT EXISTS %s (
                            shop_id    VARCHAR(128) NOT NULL PRIMARY KEY,
                            content    LONGTEXT     NOT NULL,
                            updated_at BIGINT       NOT NULL
                        )
                        """.formatted(table("shop_config")));
                st.execute("""
                        CREATE TABLE IF NOT EXISTS %s (
                            k VARCHAR(32)  NOT NULL PRIMARY KEY,
                            v VARCHAR(255) NOT NULL
                        )
                        """.formatted(table("shop_meta")));

                // 旧库自动补列
                ensureColumn(st, table("shop_stock"), "last_restock", "BIGINT NOT NULL DEFAULT 0");
                ensureColumn(st, table("shop_transaction"), "external_id", "VARCHAR(128)");
                safeUniqueIndex(st, "idx_shop_tr_external", table("shop_transaction"), "external_id");
            }
        } finally {
            closeQuietly(conn);
        }
    }

    private void safeIndex(@NotNull Statement st, @NotNull String name, @NotNull String column) {
        try {
            st.execute("CREATE INDEX " + index(name) + " ON " + table("shop_transaction") + "(" + column + ")");
        } catch (SQLException ignored) {
            // 索引已存在
        }
    }

    private void safeUniqueIndex(@NotNull Statement st, @NotNull String name,
                                 @NotNull String table, @NotNull String column) {
        try {
            st.execute("CREATE UNIQUE INDEX " + index(name) + " ON " + table + "(" + column + ")");
        } catch (SQLException ignored) {
            // 索引已存在
        }
    }

    private void ensureColumn(@NotNull Statement st, @NotNull String table,
                             @NotNull String column, @NotNull String definition) {
        try {
            st.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        } catch (SQLException ignored) {
            // 列已存在
        }
    }

    // ================================================================
    //  写入
    // ================================================================

    /**
     * 插入一条记录。
     *
     * <p>只是丢进单线程队列马上返回，不阻塞调用方（主线程）。</p>
     */
    public void insert(@NotNull TransactionLog.Record rec) {
        insert(rec, null);
    }

    /**
     * 插入一条记录；externalId 非空时做幂等（重复 ID 只记一次）。
     */
    public void insert(@NotNull TransactionLog.Record rec, @Nullable String externalId) {
        if (!available()) {
            return;
        }
        writer.execute(() -> {
            try {
                doInsert(rec, externalId);
                written.incrementAndGet();
            } catch (Throwable t) {
                plugin.getLogger().warning("写入交易记录失败：" + t.getMessage());
            }
        });
    }

    private void doInsert(@NotNull TransactionLog.Record rec, @Nullable String externalId) throws SQLException {
        String table = table("shop_transaction");
        String sql = externalId == null
                ? "INSERT INTO " + table + " (time, player_name, player_uuid, shop_id, item_id, item_name,"
                + " currency, buying, quantity, unit_price, total, admin, external_id)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)"
                : (type == Type.MYSQL
                ? "INSERT INTO " + table + " (time, player_name, player_uuid, shop_id, item_id, item_name,"
                + " currency, buying, quantity, unit_price, total, admin, external_id)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                + " ON DUPLICATE KEY UPDATE id = id"
                : "INSERT OR IGNORE INTO " + table + " (time, player_name, player_uuid, shop_id, item_id, item_name,"
                + " currency, buying, quantity, unit_price, total, admin, external_id)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        Connection conn = null;
        try {
            conn = connection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, rec.timestamp());
                ps.setString(2, rec.playerName());
                ps.setString(3, rec.playerUuid().toString());
                ps.setString(4, rec.shopId());
                ps.setString(5, rec.itemId());
                ps.setString(6, rec.itemName());
                ps.setString(7, rec.currency());
                ps.setInt(8, rec.buying() ? 1 : 0);
                ps.setInt(9, rec.quantity());
                ps.setDouble(10, rec.unitPrice());
                ps.setDouble(11, rec.total());
                ps.setInt(12, rec.admin() ? 1 : 0);
                if (externalId != null) {
                    ps.setString(13, externalId);
                }
                ps.executeUpdate();
            }
        } finally {
            closeQuietly(conn);
        }
    }

    public boolean externalExists(@NotNull String externalId) {
        if (!available()) {
            return false;
        }
        Connection conn = null;
        try {
            conn = connection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM " + table("shop_transaction") + " WHERE external_id = ? LIMIT 1")) {
                ps.setString(1, externalId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (SQLException e) {
            return false;
        } finally {
            closeQuietly(conn);
        }
    }

    /** 已写入数据库的记录数 */
    public long writtenCount() {
        return written.get();
    }

    /**
     * 等待队列里的写入全部落库。
     *
     * <p>查询前调用，保证刚发生的交易能被查到。</p>
     */
    public void flush() {
        if (!available()) {
            return;
        }
        try {
            writer.submit(() -> {
            }).get(5, TimeUnit.SECONDS);
        } catch (Throwable ignored) {
            // 超时就放弃等待
        }
    }

    // ================================================================
    //  查询
    // ================================================================

    /**
     * 查询条件
     *
     * @param playerName 玩家名（null = 不限）
     * @param buying     true 只看买入 / false 只看卖出 / null 都看
     * @param since      起始时间戳（0 = 不限）
     */
    public record Query(
            @Nullable String playerName,
            @Nullable Boolean buying,
            long since,
            /** 是否排除管理员交易 */
            boolean excludeAdmin
    ) {
        public Query(@Nullable String playerName, @Nullable Boolean buying, long since) {
            this(playerName, buying, since, false);
        }
    }

    /**
     * 分页查询
     */
    public @NotNull List<TransactionLog.Record> query(@NotNull Query q, int page, int pageSize) {
        if (!available()) {
            return List.of();
        }
        flush();
        StringBuilder sql = new StringBuilder(
                "SELECT * FROM " + table("shop_transaction") + " WHERE 1=1");
        List<Object> params = new ArrayList<>();
        buildWhere(sql, params, q);
        sql.append(" ORDER BY time DESC LIMIT ? OFFSET ?");
        params.add(pageSize);
        params.add(Math.max(0, (page - 1) * pageSize));

        List<TransactionLog.Record> out = new ArrayList<>();
        Connection conn = null;
        try {
            conn = connection();
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                bind(ps, params);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(read(rs));
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("查询交易记录失败：" + e.getMessage());
        } finally {
            closeQuietly(conn);
        }
        return out;
    }

    /**
     * 查询总数
     */
    public int count(@NotNull Query q) {
        if (!available()) {
            return 0;
        }
        flush();
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM " + table("shop_transaction") + " WHERE 1=1");
        List<Object> params = new ArrayList<>();
        buildWhere(sql, params, q);
        Connection conn = null;
        try {
            conn = connection();
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                bind(ps, params);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("统计交易记录失败：" + e.getMessage());
            return 0;
        } finally {
            closeQuietly(conn);
        }
    }

    /**
     * 某个玩家今天买/卖了多少个某种物品（用于刷钱排查）
     */
    public int countToday(@NotNull UUID uuid, @NotNull String itemId, boolean buying) {
        if (!available()) {
            return 0;
        }
        long dayStart = java.time.LocalDate.now()
                .atStartOfDay(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli();
        String sql = """
                SELECT COALESCE(SUM(quantity), 0) FROM %s
                WHERE player_uuid = ? AND item_id = ? AND buying = ? AND time >= ? AND admin = 0
                """.formatted(table("shop_transaction"));
        Connection conn = null;
        try {
            conn = connection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, itemId);
                ps.setInt(3, buying ? 1 : 0);
                ps.setLong(4, dayStart);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        } catch (SQLException e) {
            return 0;
        } finally {
            closeQuietly(conn);
        }
    }

    // ----------------------------------------------------------------

    private void buildWhere(@NotNull StringBuilder sql, @NotNull List<Object> params, @NotNull Query q) {
        if (q.playerName() != null && !q.playerName().isBlank()) {
            sql.append(" AND LOWER(player_name) = ?");
            params.add(q.playerName().toLowerCase(java.util.Locale.ROOT));
        }
        if (q.buying() != null) {
            sql.append(" AND buying = ?");
            params.add(q.buying() ? 1 : 0);
        }
        if (q.since() > 0) {
            sql.append(" AND time >= ?");
            params.add(q.since());
        }
        if (q.excludeAdmin()) {
            sql.append(" AND admin = 0");
        }
    }

    private void bind(@NotNull PreparedStatement ps, @NotNull List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }

    private @NotNull TransactionLog.Record read(@NotNull ResultSet rs) throws SQLException {
        return new TransactionLog.Record(
                rs.getLong("time"),
                rs.getString("player_name"),
                UUID.fromString(rs.getString("player_uuid")),
                rs.getString("shop_id"),
                rs.getString("item_id"),
                rs.getString("item_name"),
                rs.getString("currency"),
                rs.getInt("buying") == 1,
                rs.getInt("quantity"),
                rs.getDouble("unit_price"),
                rs.getDouble("total"),
                rs.getInt("admin") == 1
        );
    }

    // ================================================================
    //  库存持久化
    // ================================================================

    /** 一行库存数据 */
    public record StockRow(@NotNull String shopId, @NotNull String itemId, int stock, int sold, long lastRestock) {
    }

    /**
     * 写入 / 更新一条库存（异步队列）
     */
    public void upsertStock(@NotNull String shopId, @NotNull String itemId, int stock, int sold) {
        if (!available()) {
            return;
        }
        writer.execute(() -> {
            String sql = upsertSql(
                    table("shop_stock"),
                    "shop_id, item_id, stock, sold",
                    "?, ?, ?, ?",
                    "stock = ?, sold = ?",
                    "(shop_id, item_id)"
            );
            Connection conn = null;
            try {
                conn = connection();
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, shopId);
                    ps.setString(2, itemId);
                    ps.setInt(3, stock);
                    ps.setInt(4, sold);
                    ps.setInt(5, stock);
                    ps.setInt(6, sold);
                    ps.executeUpdate();
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("写入库存失败：" + t.getMessage());
            } finally {
                closeQuietly(conn);
            }
        });
    }

    /**
     * 读出全部库存
     */
    public @NotNull List<StockRow> loadStock() {
        if (!available()) {
            return List.of();
        }
        flush();
        List<StockRow> out = new ArrayList<>();
        Connection conn = null;
        try {
            conn = connection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT shop_id, item_id, stock, sold, last_restock FROM " + table("shop_stock"));
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new StockRow(
                            rs.getString("shop_id"),
                            rs.getString("item_id"),
                            rs.getInt("stock"),
                            rs.getInt("sold"),
                            rs.getLong("last_restock")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("读取库存失败：" + e.getMessage());
        } finally {
            closeQuietly(conn);
        }
        return out;
    }

    /**
     * 读单个商品的库存（确认界面 / 下单前刷新价格用）
     */
    public @Nullable StockRow loadStockRow(@NotNull String shopId, @NotNull String itemId) {
        if (!available()) {
            return null;
        }
        Connection conn = null;
        try {
            conn = connection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT shop_id, item_id, stock, sold, last_restock FROM " + table("shop_stock") + " WHERE shop_id = ? AND item_id = ?")) {
                ps.setString(1, shopId);
                ps.setString(2, itemId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new StockRow(rs.getString("shop_id"), rs.getString("item_id"),
                                rs.getInt("stock"), rs.getInt("sold"), rs.getLong("last_restock"));
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("读取单个库存失败：" + e.getMessage());
        } finally {
            closeQuietly(conn);
        }
        return null;
    }

    // ================================================================
    //  跨服原子库存 / 限购
    // ================================================================

    /** 一次原子预留的结果；{@code granted < 0} 表示数据库出错 */
    public record Reservation(int granted, int stock, int stockSold, int limitValue, int stockBefore) {
    }

    /**
     * 原子地扣库存、加限购计数（MySQL 跨服）。
     *
     * <p>库存和限购在同一个事务里用 {@code SELECT ... FOR UPDATE} 锁行，
     * 多个服务器同时购买也不会超卖。</p>
     *
     * @param limit      每日限购，{@code <= 0} 表示不限
     * @param want       想买的数量
     * @return 实际允许购买的数量；可能因为库存 / 限购变少
     */
    public @NotNull Reservation reserveBuy(
            @NotNull String shopId,
            @NotNull String itemId,
            int maxStock,
            @NotNull String playerUuid,
            @NotNull String day,
            @NotNull String itemKey,
            int limit,
            int want
    ) {
        Connection conn = null;
        try {
            conn = connection();
            conn.setAutoCommit(false);

            ensureStockRow(conn, shopId, itemId, maxStock);
            int stock;
            int stockSold;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT stock, sold FROM " + table("shop_stock") + " WHERE shop_id = ? AND item_id = ? FOR UPDATE")) {
                ps.setString(1, shopId);
                ps.setString(2, itemId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    stock = rs.getInt(1);
                    stockSold = rs.getInt(2);
                }
            }
            int stockBefore = stock;
            if (maxStock > 0) {
                stock = Math.min(stock, maxStock);
            }

            int bought = 0;
            if (limit > 0) {
                ensureLimitRow(conn, day, playerUuid, itemKey);
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT bought FROM " + table("shop_limit") + " WHERE day = ? AND player_uuid = ? AND item_key = ? FOR UPDATE")) {
                    ps.setString(1, day);
                    ps.setString(2, playerUuid);
                    ps.setString(3, itemKey);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            bought = rs.getInt(1);
                        }
                    }
                }
            }

            int qty = Math.max(0, want);
            if (maxStock > 0) {
                qty = Math.min(qty, stock);
            }
            if (limit > 0) {
                qty = Math.min(qty, Math.max(0, limit - bought));
            }

            if (qty > 0) {
                int stockAfter = maxStock > 0 ? stock - qty : stock;
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE " + table("shop_stock") + " SET stock = ?, sold = ? WHERE shop_id = ? AND item_id = ?")) {
                    ps.setInt(1, stockAfter);
                    ps.setInt(2, stockSold + qty);
                    ps.setString(3, shopId);
                    ps.setString(4, itemId);
                    ps.executeUpdate();
                }
                if (limit > 0) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE " + table("shop_limit") + " SET bought = bought + ? WHERE day = ? AND player_uuid = ? AND item_key = ?")) {
                        ps.setInt(1, qty);
                        ps.setString(2, day);
                        ps.setString(3, playerUuid);
                        ps.setString(4, itemKey);
                        ps.executeUpdate();
                    }
                }
                stock = stockAfter;
                stockSold += qty;
                bought += qty;
            }

            conn.commit();
            return new Reservation(qty, stock, stockSold, bought, stockBefore);
        } catch (Throwable t) {
            rollbackQuietly(conn);
            plugin.getLogger().warning("跨服扣库存失败：" + t.getMessage());
            return new Reservation(-1, 0, 0, 0, 0);
        } finally {
            closeQuietly(conn);
        }
    }

    /**
     * 原子地加库存、加限卖计数（MySQL 跨服）。
     */
    public @NotNull Reservation reserveSell(
            @NotNull String shopId,
            @NotNull String itemId,
            int maxStock,
            @NotNull String playerUuid,
            @NotNull String day,
            @NotNull String itemKey,
            int sellLimit,
            int want
    ) {
        Connection conn = null;
        try {
            conn = connection();
            conn.setAutoCommit(false);

            ensureStockRow(conn, shopId, itemId, maxStock);
            int stock;
            int stockSold;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT stock, sold FROM " + table("shop_stock") + " WHERE shop_id = ? AND item_id = ? FOR UPDATE")) {
                ps.setString(1, shopId);
                ps.setString(2, itemId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    stock = rs.getInt(1);
                    stockSold = rs.getInt(2);
                }
            }
            int stockBefore = stock;
            if (maxStock > 0) {
                stock = Math.min(stock, maxStock);
            }

            int sold = 0;
            if (sellLimit > 0) {
                ensureLimitRow(conn, day, playerUuid, itemKey);
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT sold FROM " + table("shop_limit") + " WHERE day = ? AND player_uuid = ? AND item_key = ? FOR UPDATE")) {
                    ps.setString(1, day);
                    ps.setString(2, playerUuid);
                    ps.setString(3, itemKey);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            sold = rs.getInt(1);
                        }
                    }
                }
            }

            int qty = Math.max(0, want);
            if (sellLimit > 0) {
                qty = Math.min(qty, Math.max(0, sellLimit - sold));
            }

            if (qty > 0) {
                int stockAfter = maxStock > 0 ? Math.min(maxStock, stock + qty) : stock;
                int soldAfter = stockSold + qty;
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE " + table("shop_stock") + " SET stock = ?, sold = ? WHERE shop_id = ? AND item_id = ?")) {
                    ps.setInt(1, stockAfter);
                    ps.setInt(2, soldAfter);
                    ps.setString(3, shopId);
                    ps.setString(4, itemId);
                    ps.executeUpdate();
                }
                if (sellLimit > 0) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE " + table("shop_limit") + " SET sold = sold + ? WHERE day = ? AND player_uuid = ? AND item_key = ?")) {
                        ps.setInt(1, qty);
                        ps.setString(2, day);
                        ps.setString(3, playerUuid);
                        ps.setString(4, itemKey);
                        ps.executeUpdate();
                    }
                }
                stock = stockAfter;
                stockSold = soldAfter;
                sold += qty;
            }

            conn.commit();
            return new Reservation(qty, stock, stockSold, sold, stockBefore);
        } catch (Throwable t) {
            rollbackQuietly(conn);
            plugin.getLogger().warning("跨服加库存失败：" + t.getMessage());
            return new Reservation(-1, 0, 0, 0, 0);
        } finally {
            closeQuietly(conn);
        }
    }

    /**
     * 回滚一次 {@link #reserveBuy}（买入时扣款失败）。
     *
     * @param stockDelta 这次买入实际扣掉的库存数（可能因为库存上限而为 0）
     */
    public void releaseBuy(
            @NotNull String shopId,
            @NotNull String itemId,
            @NotNull String playerUuid,
            @NotNull String day,
            @NotNull String itemKey,
            int limit,
            int quantity,
            int stockDelta
    ) {
        if (quantity <= 0) {
            return;
        }
        Connection conn = null;
        try {
            conn = connection();
            conn.setAutoCommit(false);
            if (stockDelta > 0) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE " + table("shop_stock")
                                + " SET stock = stock + ?, sold = GREATEST(0, sold - ?) "
                                + "WHERE shop_id = ? AND item_id = ?")) {
                    ps.setInt(1, stockDelta);
                    ps.setInt(2, quantity);
                    ps.setString(3, shopId);
                    ps.setString(4, itemId);
                    ps.executeUpdate();
                }
            }
            if (limit > 0) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE " + table("shop_limit") + " SET bought = GREATEST(0, bought - ?) "
                                + "WHERE day = ? AND player_uuid = ? AND item_key = ?")) {
                    ps.setInt(1, quantity);
                    ps.setString(2, day);
                    ps.setString(3, playerUuid);
                    ps.setString(4, itemKey);
                    ps.executeUpdate();
                }
            }
            conn.commit();
        } catch (Throwable t) {
            rollbackQuietly(conn);
            plugin.getLogger().warning("回滚跨服限购失败：" + t.getMessage());
        } finally {
            closeQuietly(conn);
        }
    }

    /**
     * 回滚一次 {@link #reserveSell}（卖出时钱没能到账）。
     *
     * @param stockAdded 这次卖出实际增加的库存数（可能因为库存已满而为 0）
     */
    public void releaseSell(
            @NotNull String shopId,
            @NotNull String itemId,
            @NotNull String playerUuid,
            @NotNull String day,
            @NotNull String itemKey,
            int sellLimit,
            int quantity,
            int stockAdded
    ) {
        if (quantity <= 0) {
            return;
        }
        Connection conn = null;
        try {
            conn = connection();
            conn.setAutoCommit(false);
            if (stockAdded > 0) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE " + table("shop_stock") + " SET stock = GREATEST(0, stock - ?) "
                                + "WHERE shop_id = ? AND item_id = ?")) {
                    ps.setInt(1, stockAdded);
                    ps.setString(2, shopId);
                    ps.setString(3, itemId);
                    ps.executeUpdate();
                }
            }
            if (sellLimit > 0) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE " + table("shop_limit") + " SET sold = GREATEST(0, sold - ?) "
                                + "WHERE day = ? AND player_uuid = ? AND item_key = ?")) {
                    ps.setInt(1, quantity);
                    ps.setString(2, day);
                    ps.setString(3, playerUuid);
                    ps.setString(4, itemKey);
                    ps.executeUpdate();
                }
            }
            conn.commit();
        } catch (Throwable t) {
            rollbackQuietly(conn);
            plugin.getLogger().warning("回滚跨服限卖失败：" + t.getMessage());
        } finally {
            closeQuietly(conn);
        }
    }

    /** 库存行不存在时先建一条初始库存 */
    private void ensureStockRow(@NotNull Connection conn, @NotNull String shopId,
                                @NotNull String itemId, int maxStock) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO " + table("shop_stock") + " (shop_id, item_id, stock, sold) VALUES (?, ?, ?, 0) "
                        + "ON DUPLICATE KEY UPDATE sold = sold")) {
            ps.setString(1, shopId);
            ps.setString(2, itemId);
            ps.setInt(3, Math.max(0, maxStock));
            ps.executeUpdate();
        }
    }

    /** 限购行不存在时先建一条空记录 */
    private void ensureLimitRow(@NotNull Connection conn, @NotNull String day,
                                @NotNull String playerUuid, @NotNull String itemKey) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO " + table("shop_limit") + " (day, player_uuid, item_key, bought, sold) VALUES (?, ?, ?, 0, 0) "
                        + "ON DUPLICATE KEY UPDATE bought = bought")) {
            ps.setString(1, day);
            ps.setString(2, playerUuid);
            ps.setString(3, itemKey);
            ps.executeUpdate();
        }
    }

    private static void rollbackQuietly(@Nullable Connection conn) {
        if (conn == null) {
            return;
        }
        try {
            conn.rollback();
        } catch (SQLException ignored) {
            // 忽略
        }
    }

    /**
     * 跨服自动补货：同一商品在 intervalMillis 内只会补一次。
     *
     * @return 补货后的库存；没补返回 -1
     */
    public int restock(@NotNull String shopId, @NotNull String itemId, int maxStock,
                       int amount, long intervalMillis) {
        if (maxStock <= 0 || amount <= 0 || intervalMillis <= 0) {
            return -1;
        }
        Connection conn = null;
        try {
            conn = connection();
            conn.setAutoCommit(false);
            ensureStockRow(conn, shopId, itemId, maxStock);
            String sql = "SELECT stock, last_restock FROM " + table("shop_stock")
                    + " WHERE shop_id = ? AND item_id = ?" + (type == Type.MYSQL ? " FOR UPDATE" : "");
            int stock;
            long last;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, shopId);
                ps.setString(2, itemId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    stock = rs.getInt(1);
                    last = rs.getLong(2);
                }
            }
            long now = System.currentTimeMillis();
            if (stock >= maxStock || now - last < intervalMillis) {
                conn.rollback();
                return -1;
            }
            int after = Math.min(maxStock, stock + amount);
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE " + table("shop_stock") + " SET stock = ?, last_restock = ? "
                            + "WHERE shop_id = ? AND item_id = ?")) {
                ps.setInt(1, after);
                ps.setLong(2, now);
                ps.setString(3, shopId);
                ps.setString(4, itemId);
                ps.executeUpdate();
            }
            conn.commit();
            return after;
        } catch (Throwable t) {
            rollbackQuietly(conn);
            plugin.getLogger().warning("自动补货失败：" + t.getMessage());
            return -1;
        } finally {
            closeQuietly(conn);
        }
    }

    // ================================================================
    //  每日限购 / 限卖持久化
    // ================================================================

    /** 一行限购数据 */
    public record LimitRow(
            @NotNull String day,
            @NotNull String playerUuid,
            @NotNull String itemKey,
            int bought,
            int sold
    ) {
    }

    /**
     * 写入 / 更新一条限购限卖（异步队列）
     */
    public void upsertLimit(
            @NotNull String day,
            @NotNull String playerUuid,
            @NotNull String itemKey,
            int bought,
            int sold
    ) {
        if (!available()) {
            return;
        }
        writer.execute(() -> {
            String sql = upsertSql(
                    table("shop_limit"),
                    "day, player_uuid, item_key, bought, sold",
                    "?, ?, ?, ?, ?",
                    "bought = ?, sold = ?",
                    "(day, player_uuid, item_key)"
            );
            Connection conn = null;
            try {
                conn = connection();
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, day);
                    ps.setString(2, playerUuid);
                    ps.setString(3, itemKey);
                    ps.setInt(4, bought);
                    ps.setInt(5, sold);
                    ps.setInt(6, bought);
                    ps.setInt(7, sold);
                    ps.executeUpdate();
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("写入限购失败：" + t.getMessage());
            } finally {
                closeQuietly(conn);
            }
        });
    }

    /**
     * 读出某天的全部限购数据
     */
    public @NotNull List<LimitRow> loadLimits(@NotNull String day) {
        if (!available()) {
            return List.of();
        }
        flush();
        List<LimitRow> out = new ArrayList<>();
        Connection conn = null;
        try {
            conn = connection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT day, player_uuid, item_key, bought, sold FROM " + table("shop_limit") + " WHERE day = ?")) {
                ps.setString(1, day);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new LimitRow(
                                rs.getString("day"),
                                rs.getString("player_uuid"),
                                rs.getString("item_key"),
                                rs.getInt("bought"),
                                rs.getInt("sold")));
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("读取限购失败：" + e.getMessage());
        } finally {
            closeQuietly(conn);
        }
        return out;
    }

    /**
     * 数据库里是否有任何限购记录（用于判断要不要从旧文件迁移）
     */
    public boolean hasAnyLimit() {
        if (!available()) {
            return false;
        }
        flush();
        Connection conn = null;
        try {
            conn = connection();
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table("shop_limit"))) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            return false;
        } finally {
            closeQuietly(conn);
        }
    }

    /**
     * 删除某天以前的限购记录
     */
    public void deleteLimitsBefore(@NotNull String day) {
        if (!available()) {
            return;
        }
        writer.execute(() -> {
            Connection conn = null;
            try {
                conn = connection();
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM " + table("shop_limit") + " WHERE day < ?")) {
                    ps.setString(1, day);
                    ps.executeUpdate();
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("清理限购失败：" + t.getMessage());
            } finally {
                closeQuietly(conn);
            }
        });
    }

    // ================================================================
    //  个人日报投递记录
    // ================================================================

    /**
     * 读出所有玩家的「最后收到日报的日期」
     *
     * @return playerUUID -> yyyy-MM-dd
     */
    public @NotNull Map<String, String> loadReportSeen() {
        if (!available()) {
            return Map.of();
        }
        flush();
        Map<String, String> out = new java.util.HashMap<>();
        Connection conn = null;
        try {
            conn = connection();
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT player_uuid, last_day FROM " + table("shop_report_seen"))) {
                while (rs.next()) {
                    out.put(rs.getString("player_uuid"), rs.getString("last_day"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("读取日报投递记录失败：" + e.getMessage());
        } finally {
            closeQuietly(conn);
        }
        return out;
    }

    /**
     * 写入 / 更新某个玩家的日报投递日期（异步队列）
     */
    public void upsertReportSeen(@NotNull String playerUuid, @NotNull String day) {
        if (!available()) {
            return;
        }
        writer.execute(() -> {
            String sql = upsertSql(
                    table("shop_report_seen"),
                    "player_uuid, last_day",
                    "?, ?",
                    "last_day = ?",
                    "(player_uuid)"
            );
            Connection conn = null;
            try {
                conn = connection();
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, playerUuid);
                    ps.setString(2, day);
                    ps.setString(3, day);
                    ps.executeUpdate();
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("写入日报投递记录失败：" + t.getMessage());
            } finally {
                closeQuietly(conn);
            }
        });
    }

    // ================================================================
    //  跨服商店配置同步
    // ================================================================

    public @NotNull Map<String, String> loadShopConfigs() {
        Map<String, String> out = new java.util.HashMap<>();
        if (!available()) {
            return out;
        }
        Connection conn = null;
        try {
            conn = connection();
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT shop_id, content FROM " + table("shop_config"))) {
                while (rs.next()) {
                    out.put(rs.getString(1), rs.getString(2));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("读取商店配置失败：" + e.getMessage());
        } finally {
            closeQuietly(conn);
        }
        return out;
    }

    public long loadShopConfigVersion() {
        if (!available()) {
            return 0;
        }
        Connection conn = null;
        try {
            conn = connection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT v FROM " + table("shop_meta") + " WHERE k = 'version'");
                 ResultSet rs = ps.executeQuery()) {
                return rs.next() ? parseLong(rs.getString(1)) : 0;
            }
        } catch (SQLException e) {
            return 0;
        } finally {
            closeQuietly(conn);
        }
    }

    public void replaceShopConfigs(@NotNull Map<String, String> configs, long version) {
        if (!available()) {
            return;
        }
        Connection conn = null;
        try {
            conn = connection();
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("DELETE FROM " + table("shop_config"));
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO " + table("shop_config") + " (shop_id, content, updated_at) VALUES (?, ?, ?)")) {
                long now = System.currentTimeMillis();
                for (var entry : configs.entrySet()) {
                    ps.setString(1, entry.getKey());
                    ps.setString(2, entry.getValue());
                    ps.setLong(3, now);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            String upsert = upsertSql(table("shop_meta"), "k, v", "?, ?", "v = ?", "(k)");
            try (PreparedStatement ps = conn.prepareStatement(upsert)) {
                ps.setString(1, "version");
                ps.setString(2, Long.toString(version));
                ps.setString(3, Long.toString(version));
                ps.executeUpdate();
            }
            conn.commit();
        } catch (Throwable t) {
            rollbackQuietly(conn);
            plugin.getLogger().warning("保存商店配置失败：" + t.getMessage());
        } finally {
            closeQuietly(conn);
        }
    }

    private static long parseLong(@Nullable String raw) {
        try {
            return raw == null ? 0 : Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 生成 upsert 语句：SQLite 用 ON CONFLICT，MySQL 用 ON DUPLICATE KEY。
     */
    private @NotNull String upsertSql(
            @NotNull String table,
            @NotNull String columns,
            @NotNull String placeholders,
            @NotNull String updates,
            @NotNull String conflict
    ) {
        if (type == Type.MYSQL) {
            return "INSERT INTO " + table + " (" + columns + ") VALUES (" + placeholders
                    + ") ON DUPLICATE KEY UPDATE " + updates;
        }
        return "INSERT INTO " + table + " (" + columns + ") VALUES (" + placeholders
                + ") ON CONFLICT" + conflict + " DO UPDATE SET " + updates;
    }

    // ================================================================

    /**
     * 关闭：等待写入队列清空后关闭。
     */
    public void close() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(5, TimeUnit.SECONDS)) {
                writer.shutdownNow();
            }
        } catch (InterruptedException e) {
            writer.shutdownNow();
            Thread.currentThread().interrupt();
        }
        closeMysql();
        if (driverLoader != null) {
            driverLoader.close();
            driverLoader = null;
        }
    }

}
