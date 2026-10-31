package io.github.hello09x.shop.command;

import io.github.hello09x.shop.currency.Currency;

import io.github.hello09x.shop.ShopPlugin;
import io.github.hello09x.shop.shop.Shop;
import io.github.hello09x.shop.shop.ShopItem;
import io.github.hello09x.shop.util.ItemSerializer;
import io.github.hello09x.shop.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /shop} 命令。
 *
 * <pre>
 * /shop                 打开主菜单
 * /shop buy [分类]      打开发购买菜单
 * /shop sell [分类]     打开发收购菜单
 * /shop money           查看余额
 * /shop list            列出所有商店（管理员）
 * /shop reload          重载（管理员）
 * /shop add &lt;分类&gt; &lt;买价&gt; [数量]   手持物品上架（管理员）
 * /shop remove &lt;分类&gt; &lt;商品id&gt;        下架（管理员）
 * /shop price &lt;分类&gt; &lt;商品id&gt; &lt;价格&gt;  改价（管理员）
 * /shop stock &lt;分类&gt; &lt;商品id&gt; &lt;库存&gt;  改库存（管理员）
 * /shop info &lt;分类&gt; &lt;商品id&gt;         详情（管理员）
 * </pre>
 */
public final class ShopCommand implements CommandExecutor, TabCompleter {

    private final ShopPlugin plugin;

    public ShopCommand(@NotNull ShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (args.length == 0) {
            return openMenu(sender, null, null);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "record" -> {
                recordExternal(sender, args);
                return true;
            }
            case "help", "?" -> {
                help(sender);
                return true;
            }
            case "buy" -> {
                return openMenu(sender, Shop.Type.BUY, args.length > 1 ? args[1] : null);
            }
            case "sell" -> {
                return openMenu(sender, Shop.Type.SELL, args.length > 1 ? args[1] : null);
            }
            case "money", "balance" -> {
                return money(sender);
            }
            case "search", "find" -> {
                return search(sender, args);
            }
            case "recent", "history" -> {
                return recent(sender);
            }
            case "fav", "favorite", "favorites" -> {
                return favorites(sender, args);
            }
            case "myreport", "mystats", "me", "stats", "daily" -> {
                return playerReport(sender, args);
            }
            case "reload" -> {
                if (!admin(sender)) {
                    return true;
                }
                plugin.reloadEverything();
                plugin.messages().send(sender, "reloaded");
                return true;
            }
            case "list" -> {
                if (!admin(sender)) {
                    return true;
                }
                list(sender);
                return true;
            }
            case "log", "logs" -> {
                if (!admin(sender)) {
                    return true;
                }
                log(sender, args);
                return true;
            }
            case "report", "reports" -> {
                if (!admin(sender)) {
                    return true;
                }
                report(sender, args);
                return true;
            }
            case "add" -> {
                return add(sender, args);
            }
            case "remove", "del" -> {
                return remove(sender, args);
            }
            case "price" -> {
                return price(sender, args);
            }
            case "stock" -> {
                return stock(sender, args);
            }
            case "info" -> {
                return info(sender, args);
            }
            default -> {
                // /shop <分类id>
                return openMenu(sender, null, args[0]);
            }
        }
    }

    // ------------------------------------------------------------ help

    /**
     * 分页 / 分组的指令帮助（直接发送）
     */
    private void help(@NotNull CommandSender sender) {
        helpLines(sender).forEach(l -> sender.sendMessage(Text.color(l)));
    }

    /**
     * 生成帮助文本（可测试）
     */
    public @NotNull List<String> helpLines(@NotNull CommandSender sender) {
        boolean admin = sender.hasPermission("xshop.admin");
        List<String> out = new ArrayList<>();

        out.add("");
        out.add("&8&m            &r &6&lXingXingShop &7v" + plugin.getPluginMeta().getVersion()
                + " &8&m            ");
        out.add("  &7高级 GUI 商店系统 &8| &7购买与收购分开的菜单");
        out.add("");

        // ---------------- 玩家指令 ----------------
        out.add("  &e&l❯ 玩家指令");
        out.add("    &f/shop &8» &7打开主菜单");
        out.add("    &f/shop buy &e[分类] &8» &7打开购买菜单");
        out.add("    &f/shop sell &e[分类] &8» &7打开收购菜单");
        out.add("    &f/shop &e<分类> &8» &7直接进入某个分类（&7例 &f/shop building&7）");
        out.add("    &f/shop money &8» &7查看自己在各货币下的余额");
        out.add("    &f/shop search &e<关键词> &8» &7搜索商品");
        out.add("    &f/shop recent &8» &7最近 10 笔购买");
        out.add("    &f/shop fav &e[add|remove|list] [分类] [商品] &8» &7收藏商品");
        out.add("    &f/shop myreport &e[today|yesterday|week|month] &8» &7我的交易日报");
        out.add("    &f/shop help &8» &7显示这份帮助");
        out.add("");

        // ---------------- 界面操作（与真实行为一致）----------------
        out.add("  &b&l❯ 界面操作");
        out.add("    &f左键 &8» &7购买 / 出售 &f1 个");
        out.add("    &f右键 &8» &7购买菜单 = 买 &f1 组(64)&7；收购菜单 = &f全部卖出");
        out.add("    &fShift+左键 &8» &7聊天框输入数量");
        out.add("    &7所有交易都会先弹 &f确认界面&7，红/绿玻璃板决定");
        out.add("    &7顶部一行切换分类，底部一行 翻页 / 返回 / 说明 / 关闭");
        out.add("");

        // ---------------- 管理指令 ----------------
        if (admin) {
            out.add("  &c&l❯ 管理指令 &7（权限 &fxshop.admin&7）");
            out.add("    &f/shop list &8» &7列出所有商店与商品数量");
            out.add("    &f/shop reload &8» &7重载配置、语言、商店文件");
            out.add("    &f/shop add &e<分类> <买价> [库存] &8» &7上架手持物品");
            out.add("    &f/shop remove &e<分类> <商品id> &8» &7下架商品");
            out.add("    &f/shop price &e<分类> <商品id> <价格> &8» &7修改价格");
            out.add("    &f/shop stock &e<分类> <商品id> <库存> &8» &7修改库存");
            out.add("    &f/shop info &e<分类> <商品id> &8» &7查看商品详情");
            out.add("    &f/shop log &e[玩家名] [页码] &8» &7查看交易记录");
            out.add("    &f/shop report &e[today|yesterday|week|month|日期|list] &8» &7查看全服日报");
            out.add("");
        }

        // ---------------- 提示 ----------------
        out.add("  &8提示");
        out.add("    &7· 商店配置在 &fplugins/XingXingShop/shops/*.yml");
        out.add("    &7· 输入 &f/shop &7后按 &fTab &7可以补全分类和商品 id");
        out.add("    &7· 交易数据在 &fplugins/XingXingShop/transactions.db");
        out.add("    &7· 日报文件在 &fplugins/XingXingShop/reports/日期.log");
        out.add("&8&m                                                       ");
        out.add("");
        return out;
    }

    // ------------------------------------------------------------ 打开界面

    private boolean openMenu(@NotNull CommandSender sender, @Nullable Shop.Type type, @Nullable String shopId) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        if (!player.hasPermission("xshop.use")) {
            plugin.messages().send(player, "no-permission");
            return true;
        }

        if (shopId != null) {
            Shop shop = plugin.shops().get(shopId);
            if (shop == null) {
                plugin.messages().send(player, "shop-not-found", shopId);
                return true;
            }
            if (shop.permission() != null && !player.hasPermission(shop.permission())) {
                plugin.messages().send(player, "no-permission");
                return true;
            }
            plugin.gui().open(player, shop);
            return true;
        }

        if (type != null) {
            plugin.gui().openType(player, type);
        } else {
            plugin.gui().openEntry(player);
        }
        return true;
    }

    private boolean search(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Text.color("&c用法：/shop search <商品名 / 商品ID / 材质>"));
            return true;
        }
        String keyword = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length))
                .toLowerCase(java.util.Locale.ROOT);
        Player player = sender instanceof Player p ? p : null;
        int limit = 20;
        int shown = 0;
        sender.sendMessage(Text.color("&8&m--------&r &6搜索 &f" + keyword + " &8&m--------"));
        for (Shop shop : plugin.shops().all().values()) {
            if (player != null && shop.permission() != null && !player.hasPermission(shop.permission())) {
                continue;
            }
            for (ShopItem item : shop.sortedItems()) {
                if (player != null && item.permission() != null && !player.hasPermission(item.permission())) {
                    continue;
                }
                String hay = (item.itemId() + " " + item.material().name() + " " + item.displayName())
                        .toLowerCase(java.util.Locale.ROOT);
                if (!hay.contains(keyword)) {
                    continue;
                }
                if (shown++ >= limit) {
                    sender.sendMessage(Text.color("&7结果太多，只显示前 " + limit + " 个"));
                    return true;
                }
                Currency currency = plugin.currencies().getOrDefault(item.currencyId());
                double price = shop.type() == Shop.Type.SELL
                        ? item.sellPrice(player) : item.buyPrice(player);
                String money = currency == null ? Currency.formatNumber(price) : currency.format(price);
                sender.sendMessage(Text.color("&7[" + shop.name() + "] &f" + item.displayName()
                        + " &8(" + item.itemId() + ") &7- &e" + money));
            }
        }
        if (shown == 0) {
            sender.sendMessage(Text.color("&7没有找到匹配的商品"));
        }
        return true;
    }

    private boolean recent(@NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        var records = plugin.transactionLog().recentPurchases(player.getName(), 10);
        if (records.isEmpty()) {
            sender.sendMessage(Text.color("&7你最近还没有购买记录"));
            return true;
        }
        sender.sendMessage(Text.color("&8&m--------&r &6最近购买 &8&m--------"));
        for (var rec : records) {
            var time = java.time.LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(rec.timestamp()),
                    java.time.ZoneId.systemDefault());
            sender.sendMessage(Text.color("&7" + time.format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"))
                    + " &f" + rec.itemName() + " &7× &f" + rec.quantity()
                    + " &7- &e" + Currency.formatNumber(rec.total())));
        }
        return true;
    }

    private boolean favorites(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        String sub = args.length > 1 ? args[1].toLowerCase(java.util.Locale.ROOT) : "list";
        if (sub.equals("list")) {
            var list = plugin.favorites().list(player.getUniqueId());
            if (list.isEmpty()) {
                sender.sendMessage(Text.color("&7收藏夹是空的，用 &f/shop fav add <分类> <商品> &7添加"));
                return true;
            }
            sender.sendMessage(Text.color("&8&m--------&r &6我的收藏 &8&m--------"));
            for (String key : list) {
                int split = key.indexOf(':');
                if (split <= 0) continue;
                Shop shop = plugin.shops().get(key.substring(0, split));
                ShopItem item = shop == null ? null : shop.find(key.substring(split + 1));
                if (item == null) continue;
                sender.sendMessage(Text.color("&7[" + shop.name() + "] &f" + item.displayName()
                        + " &8(" + item.itemId() + ")"));
            }
            return true;
        }
        if (!sub.equals("add") && !sub.equals("remove") && !sub.equals("del")) {
            sender.sendMessage(Text.color("&c用法：/shop fav <add|remove|list> [分类] [商品]"));
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage(Text.color("&c用法：/shop fav " + sub + " <分类> <商品>"));
            return true;
        }
        Shop shop = plugin.shops().get(args[2]);
        if (shop == null) {
            plugin.messages().send(sender, "shop-not-found", args[2]);
            return true;
        }
        ShopItem item = shop.find(args[3]);
        if (item == null) {
            sender.sendMessage(Text.color("&c找不到商品：" + args[3]));
            return true;
        }
        if (sub.equals("add")) {
            boolean added = plugin.favorites().add(player.getUniqueId(), item.key());
            sender.sendMessage(Text.color(added ? "&a已加入收藏：" + item.displayName()
                    : "&7已经在收藏夹里了"));
        } else {
            boolean removed = plugin.favorites().remove(player.getUniqueId(), item.key());
            sender.sendMessage(Text.color(removed ? "&a已取消收藏：" + item.displayName()
                    : "&7收藏夹里没有这个商品"));
        }
        return true;
    }

    private boolean money(@NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        sender.sendMessage(Text.color("&8&m--------&r &6我的余额 &8&m--------"));
        var all = plugin.currencies().all();
        if (all.isEmpty()) {
            sender.sendMessage(Text.color("&c当前没有任何可用货币"));
            return true;
        }
        for (var currency : all.values()) {
            sender.sendMessage(Text.color("&7" + currency.displayName() + " &f"
                    + (currency.available() ? currency.format(currency.balance(player)) : "&c不可用")));
        }
        return true;
    }

    private void list(@NotNull CommandSender sender) {
        sender.sendMessage(Text.color("&8&m--------&r &6商店列表 &8&m--------"));
        for (Shop shop : plugin.shops().all().values()) {
            String typeName = shop.type() == Shop.Type.SELL ? "&6收购" : "&a购买";
            sender.sendMessage(Text.color("  " + typeName + " &f" + shop.id()
                    + " &7(" + shop.name() + "&7) &8- &f" + shop.items().size() + " &7件"));
        }
    }

    // ------------------------------------------------------------ 交易日志

    /**
     * {@code /shop log [玩家名] [页码]}
     */
    private void recordExternal(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.command.ConsoleCommandSender)) {
            sender.sendMessage(Text.color("&c外部交易登记仅允许服务器控制台调用。"));
            return;
        }
        if (args.length < 9) {
            sender.sendMessage("用法：shop record <玩家> <来源> <buy|sell> <商品ID> <数量> <总金额> <货币ID> <商品名称>");
            return;
        }
        Player player = Bukkit.getPlayerExact(args[1]);
        if (player == null) { sender.sendMessage("外部交易登记失败：玩家不在线。"); return; }
        if (!args[3].equalsIgnoreCase("buy") && !args[3].equalsIgnoreCase("sell")) {
            sender.sendMessage("外部交易登记失败：方向必须为 buy 或 sell。"); return;
        }
        try {
            plugin.transactionLog().recordExternal(player, args[2], args[4],
                    String.join(" ", java.util.Arrays.copyOfRange(args, 8, args.length)), args[7],
                    args[3].equalsIgnoreCase("buy"), Integer.parseInt(args[5]), Double.parseDouble(args[6]));
        } catch (IllegalArgumentException exception) {
            sender.sendMessage("外部交易登记失败：" + exception.getMessage());
        }
    }

    private void log(@NotNull CommandSender sender, @NotNull String[] args) {
        // 解析参数：可能是页码，也可能是玩家名
        String playerName = null;
        int page = 1;

        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if (a.matches("\\d+")) {
                page = Math.max(1, Integer.parseInt(a));
            } else {
                playerName = a;
            }
        }

        var log = plugin.transactionLog();
        int pageSize = 10;
        int total = log.count(playerName);
        int pages = Math.max(1, (total + pageSize - 1) / pageSize);
        page = Math.min(page, pages);

        // 标题
        if (playerName == null) {
            sender.sendMessage(Text.color(""));
            sender.sendMessage(Text.color(plugin.messages().raw("log.header-all")));
        } else {
            sender.sendMessage(Text.color(""));
            sender.sendMessage(Text.color(plugin.messages().raw("log.header")
                    .replace("{0}", playerName)));
        }

        var records = log.query(playerName, page, pageSize);
        if (records.isEmpty()) {
            sender.sendMessage(Text.color("  " + plugin.messages().raw("log.empty")));
        } else {
            var formatter = java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");
            for (var r : records) {
                String time = java.time.LocalDateTime.ofInstant(
                                java.time.Instant.ofEpochMilli(r.timestamp()),
                                java.time.ZoneId.systemDefault())
                        .format(formatter);
                String line = plugin.messages().raw("log.line")
                        .replace("{0}", time)
                        .replace("{1}", r.playerName())
                        .replace("{2}", r.buying() ? "&a买入" : "&6卖出")
                        .replace("{3}", r.itemName())
                        .replace("{4}", String.valueOf(r.quantity()))
                        .replace("{5}", io.github.hello09x.shop.currency.Currency.formatNumber(r.total()));
                sender.sendMessage(Text.color("  " + line));
                sender.sendMessage(Text.color("    &8来源：" + r.shopId() + " | 商品ID：" + r.itemId()
                        + " | 货币：" + r.currency() + " | 单价：" + Currency.formatNumber(r.unitPrice())));
            }
        }

        sender.sendMessage(Text.color("  " + plugin.messages().raw("log.page")
                .replace("{0}", String.valueOf(page))
                .replace("{1}", String.valueOf(pages))
                .replace("{2}", String.valueOf(total))));
        sender.sendMessage(Text.color("  " + plugin.messages().raw("log.folder")
                .replace("{0}", log.folder().getAbsolutePath())));
        sender.sendMessage(Text.color(""));
    }

    // ------------------------------------------------------------ 日报

    /**
     * {@code /shop report [日期|today|yesterday|list]}
     */
    private void report(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!plugin.config().logReport()) {
            sender.sendMessage(Text.color("&c日报功能已在 config.yml 里关闭"
                    + "（log.report.enabled）"));
            return;
        }

        String arg = args.length > 1 ? args[1].toLowerCase(java.util.Locale.ROOT) : "yesterday";

        // 列出历史日报
        if (arg.equals("list")) {
            File dir = new File(plugin.getDataFolder(), "reports");
            File[] files = dir.listFiles((d, n) -> n.endsWith(".log"));
            sender.sendMessage(Text.color(""));
            sender.sendMessage(Text.color("&8&m---------&r &6历史日报 &8&m---------"));
            if (files == null || files.length == 0) {
                sender.sendMessage(Text.color("  &7还没有生成过日报"));
            } else {
                java.util.Arrays.sort(files, java.util.Comparator
                        .comparing(File::getName).reversed());
                for (File f : files) {
                    sender.sendMessage(Text.color("  &f"
                            + f.getName().replace(".log", "")
                            + "  &8| &7/shop report " + f.getName().replace(".log", "")));
                }
            }
            sender.sendMessage(Text.color(""));
            return;
        }

        // 生成并显示
        var report = plugin.dailyReport();
        java.time.LocalDate from;
        java.time.LocalDate to;
        java.time.LocalDate today = java.time.LocalDate.now();

        switch (arg) {
            case "today" -> {
                from = today;
                to = today.plusDays(1);
            }
            case "yesterday", "day" -> {
                from = today.minusDays(1);
                to = today;
            }
            case "week" -> {
                from = today.minusDays(7);
                to = today.plusDays(1);
            }
            case "month" -> {
                from = today.minusDays(30);
                to = today.plusDays(1);
            }
            default -> {
                // 指定日期 yyyy-MM-dd
                try {
                    from = java.time.LocalDate.parse(arg);
                    to = from.plusDays(1);
                } catch (Exception e) {
                    sender.sendMessage(Text.color("&c用法：/shop report [today|yesterday|week|month|"
                            + "yyyy-MM-dd|list]"));
                    return;
                }
            }
        }

        var summary = report.summarize(from, to);
        report.format(summary).forEach(l -> sender.sendMessage(Text.color(l)));
    }

    // ------------------------------------------------------------ 个人日报

    /**
     * {@code /shop myreport [today|yesterday|week|month|日期]}
     */
    private boolean playerReport(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        if (!plugin.config().logReport() || !plugin.config().logReportPlayer()) {
            sender.sendMessage(Text.color("&c个人日报已在 config.yml 里关闭"
                    + "（log.report.player.enabled）"));
            return true;
        }

        String arg = args.length > 1 ? args[1].toLowerCase(java.util.Locale.ROOT) : "yesterday";
        java.time.LocalDate from;
        java.time.LocalDate to;
        java.time.LocalDate today = java.time.LocalDate.now();

        switch (arg) {
            case "today" -> {
                from = today;
                to = today.plusDays(1);
            }
            case "yesterday", "day" -> {
                from = today.minusDays(1);
                to = today;
            }
            case "week" -> {
                from = today.minusDays(7);
                to = today.plusDays(1);
            }
            case "month" -> {
                from = today.minusDays(30);
                to = today.plusDays(1);
            }
            default -> {
                try {
                    from = java.time.LocalDate.parse(arg);
                    to = from.plusDays(1);
                } catch (Exception e) {
                    sender.sendMessage(Text.color("&c用法：/shop myreport [today|yesterday|week|month|"
                            + "yyyy-MM-dd]"));
                    return true;
                }
            }
        }

        var summary = plugin.dailyReport().playerReport(player.getName(), from, to);
        plugin.dailyReport().formatPlayer(summary).forEach(l -> player.sendMessage(Text.color(l)));
        return true;
    }

    // ------------------------------------------------------------ 管理

    private boolean add(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!admin(sender)) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Text.color("&c用法：/shop add <分类id> <买价> [数量]"));
            return true;
        }

        String shopId = args[1];
        double price = args.length > 2 ? parse(args[2], 0) : 0;
        int amount = args.length > 3 ? (int) parse(args[3], 1) : 1;
        if (!Double.isFinite(price) || price < 0) {
            sender.sendMessage(Text.color("&c买价无效"));
            return true;
        }
        if (amount < 1) {
            amount = 1;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR) {
            plugin.messages().send(player, "hand-empty");
            return true;
        }

        Shop shop = plugin.shops().get(shopId);
        if (shop == null) {
            // 新建分类文件
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("id", shopId);
            yaml.set("name", shopId);
            yaml.set("type", "BUY");
            yaml.set("icon", hand.getType().name());
            yaml.set("desc", List.of("&7由 /shop add 创建"));
            try {
                yaml.save(plugin.shops().fileOf(shopId));
            } catch (Exception e) {
                sender.sendMessage(Text.color("&c创建文件失败：" + e.getMessage()));
                return true;
            }
            plugin.shops().loadAll();
            shop = plugin.shops().get(shopId);
            if (shop == null) {
                sender.sendMessage(Text.color("&c创建商店失败"));
                return true;
            }
        }

        // 商品 id：材质名小写，重名就加序号
        String base = hand.getType().name().toLowerCase(Locale.ROOT);
        String itemId = base;
        int n = 1;
        while (shop.find(itemId) != null) {
            itemId = base + "_" + (n++);
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(plugin.shops().fileOf(shop.id()));
        String path = "items." + itemId + ".";
        yaml.set(path + "material", hand.getType().name());
        yaml.set(path + "amount", Math.max(amount, hand.getAmount()));
        yaml.set(path + (shop.type() == Shop.Type.SELL ? "sell" : "buy"), price);
        yaml.set(path + "slot", shop.items().size());
        try {
            yaml.save(plugin.shops().fileOf(shop.id()));
        } catch (Exception e) {
            sender.sendMessage(Text.color("&c写入失败：" + e.getMessage()));
            return true;
        }

        plugin.shops().loadAll();
        plugin.messages().send(sender, "admin.added", itemId, shop.id(), price);
        return true;
    }

    private boolean remove(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!admin(sender)) {
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(Text.color("&c用法：/shop remove <分类id> <商品id>"));
            return true;
        }
        Shop shop = plugin.shops().get(args[1]);
        if (shop == null) {
            plugin.messages().send(sender, "shop-not-found", args[1]);
            return true;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(plugin.shops().fileOf(shop.id()));
        if (!yaml.contains("items." + args[2])) {
            sender.sendMessage(Text.color("&c找不到商品：" + args[2]));
            return true;
        }
        yaml.set("items." + args[2], null);
        save(sender, yaml, shop.id());
        plugin.shops().loadAll();
        plugin.messages().send(sender, "admin.removed", args[2], shop.id());
        return true;
    }

    private boolean price(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!admin(sender)) {
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage(Text.color("&c用法：/shop price <分类id> <商品id> <价格>"));
            return true;
        }
        Shop shop = plugin.shops().get(args[1]);
        if (shop == null) {
            plugin.messages().send(sender, "shop-not-found", args[1]);
            return true;
        }
        double price = parse(args[3], -1);
        if (!Double.isFinite(price) || price < 0) {
            sender.sendMessage(Text.color("&c价格无效"));
            return true;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(plugin.shops().fileOf(shop.id()));
        boolean sellShop = shop.type() == Shop.Type.SELL;
        yaml.set("items." + args[2] + (sellShop ? ".sell" : ".buy"), price);
        save(sender, yaml, shop.id());
        plugin.shops().loadAll();
        plugin.messages().send(sender, "admin.priced", args[2], price);
        return true;
    }

    private boolean stock(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!admin(sender)) {
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage(Text.color("&c用法：/shop stock <分类id> <商品id> <库存>"));
            return true;
        }
        Shop shop = plugin.shops().get(args[1]);
        if (shop == null) {
            plugin.messages().send(sender, "shop-not-found", args[1]);
            return true;
        }
        ShopItem item = shop.find(args[2]);
        if (item == null) {
            sender.sendMessage(Text.color("&c找不到商品：" + args[2]));
            return true;
        }
        int value = (int) parse(args[3], -1);
        if (value < 0) {
            sender.sendMessage(Text.color("&c库存无效"));
            return true;
        }
        if (item.isLimitedStock() && value > item.maxStock()) {
            value = item.maxStock();
        }
        item.stock(value);
        plugin.stocks().saveItem(item);
        plugin.messages().send(sender, "admin.stocked", args[2], value);
        return true;
    }

    private boolean info(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!admin(sender)) {
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(Text.color("&c用法：/shop info <分类id> <商品id>"));
            return true;
        }
        Shop shop = plugin.shops().get(args[1]);
        if (shop == null) {
            plugin.messages().send(sender, "shop-not-found", args[1]);
            return true;
        }
        ShopItem item = shop.find(args[2]);
        if (item == null) {
            sender.sendMessage(Text.color("&c找不到商品：" + args[2]));
            return true;
        }
        sender.sendMessage(Text.color("&8&m--------&r &6商品详情 &8&m--------"));
        sender.sendMessage(Text.color("&7分类 &f" + shop.id() + " &7(" + shop.name() + "&7)"));
        sender.sendMessage(Text.color("&7ID &f" + item.itemId()));
        sender.sendMessage(Text.color("&7物品 &f" + item.displayName()));
        Player viewer = sender instanceof Player p ? p : null;
        sender.sendMessage(Text.color("&7买价 &f" + item.baseBuyPrice()
                + (item.isDynamicActive() ? " &d(动态，当前 " + item.buyPrice(viewer) + ")" : "")));
        sender.sendMessage(Text.color("&7卖价 &f" + item.sellPrice(viewer)));
        sender.sendMessage(Text.color("&7库存 &f"
                + (item.isLimitedStock() ? item.stock() + " / " + item.maxStock() : "不限量")));
        sender.sendMessage(Text.color("&7限购 &f"
                + (item.isLimited() ? item.limit() + " / 天" : "不限购")));
        sender.sendMessage(Text.color("&7累计卖出 &f" + item.sold()));
        return true;
    }

    private void save(@NotNull CommandSender sender, @NotNull YamlConfiguration yaml, @NotNull String shopId) {
        try {
            yaml.save(plugin.shops().fileOf(shopId));
        } catch (Exception e) {
            sender.sendMessage(Text.color("&c保存失败：" + e.getMessage()));
        }
    }

    private boolean admin(@NotNull CommandSender sender) {
        if (sender.hasPermission("xshop.admin")) {
            return true;
        }
        plugin.messages().send(sender, "no-permission");
        return false;
    }

    private static double parse(@NotNull String raw, double fallback) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // ------------------------------------------------------------ Tab

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("help", "buy", "sell", "money", "myreport", "search", "recent", "fav"));
            if (sender.hasPermission("xshop.admin")) {
                out.addAll(List.of("list", "log", "report", "reload", "add", "remove", "price", "stock", "info"));
            }
            out.addAll(plugin.shops().ids());
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (List.of("buy", "sell").contains(sub)) {
                Shop.Type type = sub.equals("sell") ? Shop.Type.SELL : Shop.Type.BUY;
                plugin.shops().byType(type).forEach(s -> out.add(s.id()));
            } else if (sub.equals("report")) {
                out.addAll(List.of("today", "yesterday", "week", "month", "list"));
            } else if (List.of("myreport", "mystats", "me", "stats", "daily").contains(sub)) {
                out.addAll(List.of("today", "yesterday", "week", "month"));
            } else if (sub.equals("log")) {
                Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
            } else if (sender.hasPermission("xshop.admin")) {
                out.addAll(plugin.shops().ids());
            }
        } else if (args.length == 3) {
            if (sender.hasPermission("xshop.admin")) {
                Shop shop = plugin.shops().get(args[1]);
                if (shop != null) {
                    shop.items().forEach(i -> out.add(i.itemId()));
                }
            }
        }
        String cur = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(cur));
        return out;
    }

}
