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

    /** 是否启用每日限卖（收购菜单） */
    private boolean sellLimits = true;

    /** 交易记录数据库 */
    private String dbType = "sqlite";
    private String dbHost = "localhost";
    private int dbPort = 3306;
    private String dbName = "minecraft";
    private String dbUrl = "";
    private String dbUser = "";
    private String dbPassword = "";

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
        this.sellRatio = f.getDouble("sell-ratio", 0.4);
        this.limits = f.getBoolean("limits", true);
        this.dynamicPricing = f.getBoolean("dynamic-pricing", true);
        this.dynamicCoefficient = f.getDouble("dynamic.coefficient", 0.5);
        this.dynamicMin = f.getDouble("dynamic.min", 0.3);
        this.dynamicMax = f.getDouble("dynamic.max", 3.0);
        this.sellLimits = f.getBoolean("sell-limits", true);
        this.dbType = f.getString("database.type", "sqlite");
        this.dbHost = f.getString("database.mysql.host", "localhost");
        this.dbPort = f.getInt("database.mysql.port", 3306);
        this.dbName = f.getString("database.mysql.database", "minecraft");
        this.dbUser = f.getString("database.mysql.username", "");
        this.dbPassword = f.getString("database.mysql.password", "");
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
        this.sellDynamicLimit = f.getDouble("sell-dynamic.limit", 2000);
        this.sellDynamicMax = f.getDouble("sell-dynamic.max-drop", 0.6);
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
