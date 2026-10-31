package io.github.hello09x.shop;

import io.github.hello09x.shop.command.ShopCommand;
import io.github.hello09x.shop.config.ConfigMerger;
import io.github.hello09x.shop.config.ShopConfig;
import io.github.hello09x.shop.currency.CurrencyManager;
import io.github.hello09x.shop.gui.ChatInputHandler;
import io.github.hello09x.shop.gui.GuiListener;
import io.github.hello09x.shop.gui.ShopGuiManager;
import io.github.hello09x.shop.gui.ShopView;
import io.github.hello09x.shop.limit.LimitStore;
import io.github.hello09x.shop.log.DailyReport;
import io.github.hello09x.shop.log.ReportDeliveryStore;
import io.github.hello09x.shop.log.TransactionLog;
import io.github.hello09x.shop.shop.ShopRegistry;
import io.github.hello09x.shop.storage.FavoriteStore;
import io.github.hello09x.shop.storage.ShopSync;
import io.github.hello09x.shop.storage.StockStore;import io.github.hello09x.shop.util.Messages;
import io.github.hello09x.shop.util.Schedulers;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bstats.bukkit.Metrics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * XingXingShop 主类。
 */
public final class ShopPlugin extends JavaPlugin {

    private static ShopPlugin instance;

    private ShopConfig config;
    private Messages messages;
    private CurrencyManager currencies;
    private ShopRegistry shops;
    private StockStore stocks;
    private LimitStore limits;
    private FavoriteStore favorites;
    private ShopSync shopSync;
    private ShopGuiManager gui;
    private ChatInputHandler chatInput;
    private TransactionLog transactionLog;
    private ReportDeliveryStore reportDelivery;
    private DailyReport dailyReport;
    private io.github.hello09x.shop.hook.TrMenuTradeMonitor externalMonitor;

    /** bStats（ID 35029） */
    private Metrics metrics;

    /** 跨服交易数据库任务；关服时等待这些任务完成 */
    private final ExecutorService tradeExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "XingXingShop-TradeDB");
        t.setDaemon(true);
        return t;
    });
    private final ConcurrentLinkedQueue<Runnable> shutdownTasks = new ConcurrentLinkedQueue<>();
    private final AtomicInteger pendingTrades = new AtomicInteger();
    private volatile boolean disabling;

    public static ShopPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        long start = System.currentTimeMillis();

        // 0) 配置自动补全：把 jar 模板里新增的键补到用户文件（不改已有值）
        runConfigMerge();

        // 配置 + 语言
        this.config = new ShopConfig(this);
        this.config.load();
        this.messages = new Messages(this);

        // bStats 统计（可在 config.yml 的 metrics.enabled 关闭）
        if (this.config.metrics()) {
            try {
                this.metrics = new Metrics(this, 35029);
            } catch (Throwable t) {
                getLogger().warning("bStats 初始化失败：" + t.getMessage());
            }
        }

        // 数据库先初始化：商店配置跨服同步需要先连上库
        this.transactionLog = new TransactionLog(this);
        this.transactionLog.initDatabase();
        this.reportDelivery = new ReportDeliveryStore(this);
        this.reportDelivery.load();
        this.dailyReport = new DailyReport(this);

        // 跨服商店配置：先拉取，再加载本地商店
        this.shopSync = new ShopSync(this);
        this.shopSync.pull();
        this.shops = new ShopRegistry(this);
        this.shops.loadAll();
        if (this.shopSync.enabled() && !this.shopSync.hasPulled()) {
            this.shopSync.push();
        }

        // 库存 / 限购（优先从数据库读，数据库不可用则读文件）
        this.stocks = new StockStore(this);
        this.stocks.load();
        this.limits = new LimitStore(this);
        this.limits.load();
        this.favorites = new FavoriteStore(this);
        this.favorites.load();

        // GUI
        this.gui = new ShopGuiManager(this);
        this.chatInput = new ChatInputHandler(this);
        Bukkit.getPluginManager().registerEvents(new GuiListener(this), this);
        Bukkit.getPluginManager().registerEvents(this.chatInput, this);
        // 玩家上线补发错过的个人日报
        Bukkit.getPluginManager().registerEvents(
                new io.github.hello09x.shop.listener.PlayerJoinListener(this), this);

        // 命令
        PluginCommand cmd = getCommand("shop");
        if (cmd != null) {
            ShopCommand executor = new ShopCommand(this);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        // 货币探测必须延后一 tick：
        // Vault 会在自己 onEnable 里注册 Economy 服务，而本插件是 softdepend，
        // 启用顺序不保证，立即探测会得到「装了 Vault 但没有经济实现」的假象。
        this.currencies = new CurrencyManager(this);
        Schedulers.globalLater(this, () -> {
            currencies.detect();
            externalMonitor = new io.github.hello09x.shop.hook.TrMenuTradeMonitor(this);
            externalMonitor.start();
            if (!currencies.hasAnyAvailable()) {
                getLogger().warning("没有检测到任何可用货币！请安装 Vault + 经济插件"
                        + "（EssentialsX / CMI / XConomy 等）");
            }
        }, 1L);

        // 定时维护：清理过期限购 + 库存落盘
        long period = 20L * 60 * 30;
        Schedulers.asyncTimer(this, () -> {
            try {
                limits.cleanup();
            } catch (Throwable t) {
                getLogger().warning("清理限购失败：" + t);
            }
            try {
                stocks.save();
            } catch (Throwable t) {
                getLogger().warning("保存库存失败：" + t);
            }
        }, period, period);

        // 跨服：定时从数据库刷新内存缓存，保证别的服的买卖能及时显示
        if (transactionLog.database().shared()) {
            Schedulers.asyncTimer(this, () -> {
                try {
                    stocks.refresh();
                } catch (Throwable t) {
                    getLogger().warning("刷新跨服库存失败：" + t.getMessage());
                }
            }, 100L, 100L);
            Schedulers.asyncTimer(this, () -> {
                try {
                    limits.refresh();
                } catch (Throwable t) {
                    getLogger().warning("刷新跨服限购失败：" + t.getMessage());
                }
            }, 200L, 1200L);
        }

        // 界面每 2 秒刷新一次库存 / 余额 / 限购，跨服也能看到别的服的库存变化
        Schedulers.globalTimer(this, () -> {
            for (var player : Bukkit.getOnlinePlayers()) {
                Schedulers.entity(this, player, () -> {
                    var holder = player.getOpenInventory().getTopInventory().getHolder();
                    if (holder instanceof ShopView view && view.viewer().equals(player)) {
                        view.refreshView();
                    }
                });
            }
        }, 40L, 40L);

        // MySQL 启动时没连上：每分钟重试一次，连上后自动接管库存 / 限购
        Schedulers.asyncTimer(this, () -> {
            var db = transactionLog.database();
            if (db.type() != io.github.hello09x.shop.log.TransactionDatabase.Type.MYSQL || db.available()) {
                return;
            }
            db.init();
            if (db.available()) {
                Schedulers.global(this, () -> {
                    try {
                        stocks.load();
                        limits.load();
                    } catch (Throwable t) {
                        getLogger().warning("数据库恢复后载入数据失败：" + t.getMessage());
                    }
                });
            }
        }, 1200L, 1200L);

        // 跨服商店配置轮询
        Schedulers.asyncTimer(this, () -> {
            if (shopSync == null || !shopSync.enabled()) {
                return;
            }
            try {
                if (shopSync.poll()) {
                    Schedulers.global(this, () -> {
                        shops.loadAll();
                        stocks.load();
                        limits.load();
                    });
                }
            } catch (Throwable t) {
                getLogger().warning("跨服商店配置同步失败：" + t.getMessage());
            }
        }, 200L, 200L);

        // PlaceholderAPI 变量
        registerPlaceholders();

        // 每日报表
        dailyReport.startScheduler();

        // GitHub 新版本检查（异步，不阻塞启动）
        Schedulers.async(this,
                () -> io.github.hello09x.shop.util.UpdateChecker.checkAsync(this));

        getLogger().info("启用完成，用时 " + (System.currentTimeMillis() - start) + " ms，"
                + "商店 " + shops.all().size() + " 个，商品 " + shops.totalItems() + " 件");
    }

    /**
     * 注册 PlaceholderAPI 变量扩展。
     *
     * <p>没装 PAPI 时直接跳过，不会抛 {@code NoClassDefFoundError}。</p>
     */
    private void registerPlaceholders() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        try {
            var expansion = new io.github.hello09x.shop.hook.ShopPlaceholderExpansion(this);
            if (expansion.register()) {
                getLogger().info("已注册 PlaceholderAPI 变量（%xingsxingshop_xxx%）");
            }
        } catch (Throwable t) {
            getLogger().warning("注册 PlaceholderAPI 变量失败：" + t.getMessage());
        }
    }

    /**
     * 配置自动补全 + 首次释放。
     *
     * <p>顺序很关键：</p>
     * <ol>
     *     <li>先确保数据目录存在</li>
     *     <li>把 jar 里的所有模板文件<b>显式</b>释放出来（已存在的不覆盖）</li>
     *     <li>再对每个文件做「缺失键补全」</li>
     * </ol>
     */
    private void runConfigMerge() {
        // 1) 确保数据目录存在
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().severe("无法创建插件数据目录：" + getDataFolder().getAbsolutePath());
            return;
        }

        ConfigMerger merger = new ConfigMerger(this);

        // 2) 显式释放 jar 里的模板（用 jar 里实际存在的文件清单，不靠硬编码）
        int extracted = 0;
        int updated = 0;
        for (String path : listBundledResources()) {
            File target = new File(getDataFolder(), path);
            if (!target.exists()) {
                try {
                    saveResource(path, false);
                    extracted++;
                    getLogger().info("已释放默认配置 " + path);
                } catch (Throwable t) {
                    getLogger().warning("释放 " + path + " 失败：" + t.getMessage());
                }
                continue;
            }
            // 商店模板：jar 里的版本更新时自动替换本地旧版（先备份 .bak）
            if (path.startsWith("shops/")) {
                int bundled = templateVersion(readResourceText(path));
                int local = templateVersion(readFileText(target));
                if (bundled > local) {
                    backup(target);
                    try {
                        saveResource(path, true);
                        updated++;
                        getLogger().info("已更新商店模板 " + path
                                + "（v" + local + " → v" + bundled
                                + "，旧文件备份为 " + target.getName() + ".bak）");
                    } catch (Throwable t) {
                        getLogger().warning("更新 " + path + " 失败：" + t.getMessage());
                    }
                }
            }
        }
        if (extracted > 0) {
            getLogger().info("首次启动：共释放 " + extracted + " 个默认配置文件");
        }
        if (updated > 0) {
            getLogger().info("共更新 " + updated + " 个商店模板（旧文件已备份为 .bak）");
        }

        // 3) 缺失键补全
        reportMerge("config.yml", merger.merge("config.yml"));
        reportMerge("lang.yml", merger.merge("lang.yml"));

        File shopDir = new File(getDataFolder(), "shops");
        File[] files = shopDir.listFiles((d, n) -> n.toLowerCase(java.util.Locale.ROOT).endsWith(".yml"));
        if (files != null) {
            for (File f : files) {
                reportMerge("shops/" + f.getName(), merger.merge("shops/" + f.getName()));
            }
        }

        // 4) 自检：把结果打到日志，方便排查
        logSelfCheck();
    }

    /**
     * 列出 jar 内打包的配置资源（config.yml / lang.yml / shops/*.yml）
     */
    private @NotNull java.util.List<String> listBundledResources() {
        java.util.List<String> out = new java.util.ArrayList<>();
        java.util.jar.JarFile jar = null;
        try {
            var file = getFile();
            if (!file.isFile()) {
                // 开发环境（classes 目录）直接返回固定列表
                return java.util.List.of(
                        "config.yml", "lang.yml",
                        "shops/购买-基础物资.yml", "shops/购买-建筑商店.yml",
                        "shops/购买-装备工具.yml", "shops/收购-材料回收.yml");
            }
            jar = new java.util.jar.JarFile(file);
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.equals("config.yml") || name.equals("lang.yml")
                        || (name.startsWith("shops/") && name.endsWith(".yml"))) {
                    out.add(name);
                }
            }
        } catch (Throwable t) {
            getLogger().warning("读取 jar 资源清单失败：" + t.getMessage());
        } finally {
            if (jar != null) {
                try {
                    jar.close();
                } catch (Exception ignored) {
                    // 忽略
                }
            }
        }
        out.remove("shops/");
        java.util.Collections.sort(out);
        return out;
    }

    /**
     * 读取商店模板里的版本号（{@code # @shop-template-version: N}）。
     * 没有标记视为 0（旧版），会被新版覆盖。
     */
    private int templateVersion(@Nullable String text) {
        if (text == null) {
            return 0;
        }
        var m = java.util.regex.Pattern
                .compile("@shop-template-version:\\s*(\\d+)")
                .matcher(text);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    /** 读 jar 内资源为文本 */
    private @Nullable String readResourceText(@NotNull String path) {
        try (var in = getResource(path)) {
            if (in == null) {
                return null;
            }
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 读本地文件为文本 */
    private @Nullable String readFileText(@NotNull File file) {
        try {
            return java.nio.file.Files.readString(file.toPath(),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 备份文件为 {@code 原名.bak} */
    private void backup(@NotNull File file) {
        try {
            java.nio.file.Files.copy(file.toPath(),
                    new File(file.getParentFile(), file.getName() + ".bak").toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Throwable t) {
            getLogger().warning("备份 " + file.getName() + " 失败：" + t.getMessage());
        }
    }

    /**
     * 启动自检：检查关键文件是否都在，缺失就打警告
     */
    private void logSelfCheck() {
        String[] required = {"config.yml", "lang.yml"};
        for (String r : required) {
            if (!new File(getDataFolder(), r).isFile()) {
                getLogger().severe("缺少配置文件 " + r + "！请检查 jar 是否完整");
            }
        }
        File shopDir = new File(getDataFolder(), "shops");
        File[] shops = shopDir.listFiles((d, n) -> n.toLowerCase(java.util.Locale.ROOT).endsWith(".yml"));
        if (shops == null || shops.length == 0) {
            getLogger().warning("shops/ 目录里没有商店文件，"
                    + "输入 /shop add <分类名> <价格> 可以现场创建一个");
        }
    }

    private void reportMerge(@NotNull String path, @NotNull ConfigMerger.Result result) {
        if (result.error() != null) {
            getLogger().warning("补全 " + path + " 失败：" + result.error());
        } else if (result.changed()) {
            getLogger().info("已补全 " + path
                    + (result.added() > 0 ? "（新增 " + result.added() + " 个配置项）" : "")
                    + "，原文件备份为 " + path + ".bak");
        }
    }

    @Override
    public void onDisable() {
        disabling = true;
        drainTrades();
        tradeExecutor.shutdown();

        if (externalMonitor != null) externalMonitor.close();
        if (stocks != null) {
            stocks.save();
        }
        if (limits != null) {
            limits.save();
        }
        if (favorites != null) {
            favorites.save();
        }
        if (transactionLog != null) {
            transactionLog.close();
        }
    }

    public boolean isDisabling() {
        return disabling;
    }

    public @NotNull ExecutorService tradeExecutor() {
        return tradeExecutor;
    }

    public void tradeStarted() {
        pendingTrades.incrementAndGet();
    }

    public void tradeFinished() {
        pendingTrades.decrementAndGet();
    }

    public void enqueueTradeCompletion(@NotNull Runnable task) {
        shutdownTasks.add(task);
    }

    /**
     * 关服时等待异步交易完成，并把结果在主线程执行，避免钱扣了但没发货/没退款。
     */
    private void drainTrades() {
        long deadline = System.currentTimeMillis() + 10_000L;
        while (System.currentTimeMillis() < deadline
                && (pendingTrades.get() > 0 || !shutdownTasks.isEmpty())) {
            Runnable task;
            while ((task = shutdownTasks.poll()) != null) {
                try {
                    task.run();
                } catch (Throwable t) {
                    getLogger().warning("关服处理交易结果失败：" + t.getMessage());
                }
            }
            try {
                Thread.sleep(20L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        Runnable task;
        while ((task = shutdownTasks.poll()) != null) {
            try {
                task.run();
            } catch (Throwable t) {
                getLogger().warning("关服处理交易结果失败：" + t.getMessage());
            }
        }
    }

    /**
     * 全量重载
     */
    public void reloadEverything() {
        if (externalMonitor != null) externalMonitor.close();
        config.load();
        messages.reload();
        currencies.detect();
        shops.loadAll();
        if (shopSync != null) {
            shopSync.push();
        }
        stocks.load();
        limits.load();
        if (favorites != null) {
            favorites.load();
        }
        externalMonitor = new io.github.hello09x.shop.hook.TrMenuTradeMonitor(this);
        externalMonitor.start();
    }

    // ------------------------------------------------------------ 访问器

    public ShopConfig config() {
        return config;
    }

    public Messages messages() {
        return messages;
    }

    public CurrencyManager currencies() {
        return currencies;
    }

    public ShopRegistry shops() {
        return shops;
    }

    public StockStore stocks() {
        return stocks;
    }

    public LimitStore limits() {
        return limits;
    }

    public FavoriteStore favorites() {
        return favorites;
    }

    public ShopSync shopSync() {
        return shopSync;
    }

    public ShopGuiManager gui() {
        return gui;
    }

    public ChatInputHandler chatInput() {
        return chatInput;
    }

    public TransactionLog transactionLog() {
        return transactionLog;
    }

    public DailyReport dailyReport() {
        return dailyReport;
    }

    public ReportDeliveryStore reportDelivery() {
        return reportDelivery;
    }

}
