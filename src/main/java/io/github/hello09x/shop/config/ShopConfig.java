package io.github.hello09x.shop.config;

import io.github.hello09x.shop.ShopPlugin;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * 主配置（config.yml）。
 *
 * <p>只保留最必要的项，其余全部走内置默认值。</p>
 */
public final class ShopConfig {

    /** 旧版模板里的示例 url，忽略它，避免升级后盖掉新填的 host/database */
    private static final String LEGACY_DEFAULT_URL =
            "jdbc:mysql://localhost:3306/minecraft?useSSL=false&characterEncoding=utf8";

    private final ShopPlugin plugin;

    private String currencyDefault = "vault";
    private double sellRatio = 0.4;
    /** 是否启用每日限购 */
    private boolean limits = true;
    /** 是否启用动态定价 */
    private boolean dynamicPricing = true;
    private double dynamicCoefficient = 0.5;
    private double dynamicMin = 0.3;
    private double dynamicMax = 3.0;
    private String dynamicFormula = "linear";
    private int dynamicSteps = 5;

    /** 是否启用每日限卖（收购菜单） */
    private boolean sellLimits = true;

    /** 收购价高于购买价时自动压价，防止低买高卖刷钱 */
    private boolean antiArbitrage = true;

    /** bStats 统计 */
    private boolean metrics = true;

    /** GitHub 新版本检查 */
    private boolean updateCheck = true;

    /** 跨服商店配置同步 */
    private boolean shopSync = true;

    /** 交易记录数据库 */
    private String dbType = "sqlite";
    private String dbHost = "localhost";
    private int dbPort = 3306;
    private String dbName = "minecraft";
    private String dbUrl = "";
    private String dbUser = "";
    private String dbPassword = "";
    private int dbPoolSize = 4;
    private int dbConnectionTimeout = 5000;
    private String dbTablePrefix = "";
    private boolean dbDriverDownload = true;
    private String dbDriverRepo = "https://maven.aliyun.com/repository/public";

    /** 交易日志 */
    private boolean logEnabled = true;
    private boolean logAlert = true;
    private int logAlertRate = 20;
    private double logAlertAmount = 10000;

    /** 动态收购：卖得越多、单价越低 */
    private boolean sellDynamic = true;
    private double sellDynamicLimit = 2000;
    private double sellDynamicMax = 0.6;

    public ShopConfig(ShopPlugin plugin) {
        this.plugin = plugin;
        // 默认配置由 ShopPlugin#runConfigMerge 统一释放，
        // 这里不能调 saveDefaultConfig()，否则会跟它抢着写 config.yml
    }

    public void load() {
        // 文件不存在时先补一份（正常情况下 ShopPlugin 已经释放过了）
        if (!new java.io.File(plugin.getDataFolder(), "config.yml").isFile()) {
            plugin.saveResource("config.yml", false);
        }
        plugin.reloadConfig();
        FileConfiguration f = plugin.getConfig();

        this.currencyDefault = f.getString("currency", "vault");
        this.sellRatio = clampNonNegative(f.getDouble("sell-ratio", 0.4), 0.4);
        this.limits = f.getBoolean("limits", true);
        this.dynamicPricing = f.getBoolean("dynamic-pricing", true);
        this.dynamicCoefficient = clampNonNegative(f.getDouble("dynamic.coefficient", 0.5), 0.5);
        this.dynamicMin = clampNonNegative(f.getDouble("dynamic.min", 0.3), 0.3);
        this.dynamicMax = Math.max(1, Math.max(dynamicMin,
                clampNonNegative(f.getDouble("dynamic.max", 3.0), 3.0)));
        this.dynamicFormula = f.getString("dynamic.formula", "linear");
        this.dynamicSteps = Math.max(1, Math.min(100, f.getInt("dynamic.steps", 5)));
        this.sellLimits = f.getBoolean("sell-limits", true);
        this.antiArbitrage = f.getBoolean("anti-arbitrage", true);
        this.metrics = f.getBoolean("metrics.enabled", true);
        this.updateCheck = f.getBoolean("update-check", true);
        this.shopSync = f.getBoolean("database.shop-sync", true);
        this.dbType = f.getString("database.type", "sqlite");
        this.dbHost = f.getString("database.mysql.host", "localhost");
        this.dbPort = f.getInt("database.mysql.port", 3306);
        this.dbName = f.getString("database.mysql.database", "minecraft");
        this.dbUser = f.getString("database.mysql.username", "");
        this.dbPassword = f.getString("database.mysql.password", "");
        this.dbPoolSize = Math.max(1, Math.min(32, f.getInt("database.mysql.pool-size", 4)));
        this.dbConnectionTimeout = Math.max(1000, Math.min(30000,
                f.getInt("database.mysql.connection-timeout", 5000)));
        String prefix = f.getString("database.table-prefix", "");
        this.dbTablePrefix = prefix != null && prefix.matches("[A-Za-z0-9_]{0,32}") ? prefix : "";
        this.dbDriverDownload = f.getBoolean("database.driver-download", true);
        String repo = f.getString("database.driver-repo", "https://maven.aliyun.com/repository/public");
        this.dbDriverRepo = repo == null || repo.isBlank()
                ? "https://maven.aliyun.com/repository/public" : repo.trim();
        // 老配置里写了完整 url 就继续用；没写就按 host/port/database 自动拼
        String explicitUrl = f.getString("database.mysql.url", "");
        this.dbUrl = explicitUrl == null || explicitUrl.isBlank()
                || explicitUrl.trim().equals(LEGACY_DEFAULT_URL)
                ? mysqlUrl(this.dbHost, this.dbPort, this.dbName)
                : explicitUrl.trim();
        this.logEnabled = f.getBoolean("log.enabled", true);
        this.logAlert = f.getBoolean("log.alert.enabled", true);
        this.logAlertRate = f.getInt("log.alert.rate-limit", 20);
        this.logAlertAmount = f.getDouble("log.alert.amount", 10000);
        this.sellDynamic = f.getBoolean("sell-dynamic.enabled", true);
        this.sellDynamicLimit = Math.max(1,
                clampNonNegative(f.getDouble("sell-dynamic.limit", 2000), 2000));
        this.sellDynamicMax = clampNonNegative(f.getDouble("sell-dynamic.max-drop", 0.6), 0.6);
        if (this.sellDynamicMax > 1) {
            this.sellDynamicMax = 1;
        }
    }

    private static double clampNonNegative(double value, double fallback) {
        return !Double.isFinite(value) || value < 0 ? fallback : value;
    }

    private static String mysqlUrl(String host, int port, String database) {
        String h = host == null || host.isBlank() ? "localhost" : host.trim();
        String n = database == null || database.isBlank() ? "minecraft" : database.trim();
        int p = port > 0 && port <= 65535 ? port : 3306;
        return "jdbc:mysql://" + h + ":" + p + "/" + n
                + "?useSSL=false&characterEncoding=utf8&allowPublicKeyRetrieval=true";
    }

    public String dbType() {
        return dbType;
    }

    public String dbUrl() {
        return dbUrl;
    }

    public String dbUser() {
        return dbUser;
    }

    public String dbPassword() {
        return dbPassword;
    }

    public int dbPoolSize() {
        return dbPoolSize;
    }

    public int dbConnectionTimeout() {
        return dbConnectionTimeout;
    }

    public String dbTablePrefix() {
        return dbTablePrefix;
    }

    public boolean dbDriverDownload() {
        return dbDriverDownload;
    }

    public String dbDriverRepo() {
        return dbDriverRepo;
    }

    public boolean logEnabled() {
        return logEnabled;
    }

    // ---------------------------------------------------------------- 日报

    public boolean logReport() {
        return plugin.getConfig().getBoolean("log.report.enabled", true);
    }

    public int logReportHour() {
        return plugin.getConfig().getInt("log.report.hour", 0);
    }

    public String logReportRange() {
        return plugin.getConfig().getString("log.report.range", "yesterday");
    }

    public boolean logReportNotify() {
        return plugin.getConfig().getBoolean("log.report.notify-in-game", true);
    }

    public boolean logReportFile() {
        return plugin.getConfig().getBoolean("log.report.write-file", true);
    }

    /** 是否生成 / 允许查看个人日报 */
    public boolean logReportPlayer() {
        return plugin.getConfig().getBoolean("log.report.player.enabled", true);
    }

    /** 个人日报是否每天推送给在线玩家 */
    public boolean logReportPlayerNotify() {
        return plugin.getConfig().getBoolean("log.report.player.notify-in-game", true);
    }

    public boolean logAlert() {
        return logAlert;
    }

    public int logAlertRate() {
        return logAlertRate;
    }

    public double logAlertAmount() {
        return logAlertAmount;
    }

    public boolean sellLimits() {
        return sellLimits;
    }

    public boolean antiArbitrage() {
        return antiArbitrage;
    }

    public boolean metrics() {
        return metrics;
    }

    public boolean updateCheck() {
        return updateCheck;
    }

    public boolean shopSync() {
        return shopSync;
    }

    public boolean sellDynamic() {
        return sellDynamic;
    }

    public double sellDynamicLimit() {
        return sellDynamicLimit;
    }

    public double sellDynamicMax() {
        return sellDynamicMax;
    }

    public String currencyDefault() {
        return currencyDefault;
    }

    public double sellRatio() {
        return sellRatio;
    }

    public boolean limits() {
        return limits;
    }

    public boolean dynamicPricing() {
        return dynamicPricing;
    }

    public double dynamicCoefficient() {
        return dynamicCoefficient;
    }

    public double dynamicMin() {
        return dynamicMin;
    }

    public double dynamicMax() {
        return dynamicMax;
    }

    public String dynamicFormula() {
        return dynamicFormula;
    }

    public int dynamicSteps() {
        return dynamicSteps;
    }

    public int rows() {
        return plugin.getConfig().getInt("gui.rows", 6);
    }

    public int size() {
        return rows() * 9;
    }

    public String title() {
        return plugin.getConfig().getString("gui.buy-title", "&8购买 &7» &a{shop}");
    }

    public String sellTitle() {
        return plugin.getConfig().getString("gui.sell-title", "&8收购 &7» &a{shop}");
    }

    // ---------------------------------------------------------------- 音效

    public boolean soundEnabled() {
        return plugin.getConfig().getBoolean("sound.enabled", true);
    }

    /** 取某个音效的名字（空字符串 = 不播放） */
    public String sound(@org.jetbrains.annotations.NotNull String key) {
        return plugin.getConfig().getString("sound." + key, "");
    }

    public float soundVolume() {
        return (float) plugin.getConfig().getDouble("sound.volume", 1.0);
    }

    public float soundPitch() {
        return (float) plugin.getConfig().getDouble("sound.pitch", 1.0);
    }

}
