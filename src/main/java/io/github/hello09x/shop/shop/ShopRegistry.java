package io.github.hello09x.shop.shop;

import io.github.hello09x.shop.ShopPlugin;
import io.github.hello09x.shop.util.ItemSerializer;
import io.github.hello09x.shop.util.Text;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 商店加载器：读取 {@code shops/*.yml}。
 *
 * <p>如果旧版本残留了模板文件（如 {@code basic.yml} / {@code gear.yml}），
 * 会自动跳过，避免和新的中文模板重复。</p>
 */
public final class ShopRegistry {

    /** 首次启动时释放的模板 */
    private static final String[] TEMPLATES = {
            "购买-基础物资.yml",
            "购买-建筑商店.yml",
            "购买-装备工具.yml",
            "购买-红石科技.yml",
            "购买-酿造加工.yml",
            "购买-农业园艺.yml",
            "购买-染料色彩.yml",
            "购买-装饰美化.yml",
            "购买-海洋水族.yml",
            "购买-下界末地.yml",
            "购买-杂物工具.yml",
            "收购-材料回收.yml",
            "收购-农产品.yml",
            "收购-矿物宝石.yml",
            "收购-怪物掉落.yml"
    };

    /** 旧版本模板名，发现就跳过，避免重复商店 */
    private static final List<String> LEGACY = List.of(
            "basic.yml", "gear.yml",
            "购买-建造方块.yml"
    );

    private final ShopPlugin plugin;
    private final Map<String, Shop> shops = new LinkedHashMap<>();

    public ShopRegistry(@NotNull ShopPlugin plugin) {
        this.plugin = plugin;
    }

    public @NotNull File folder() {
        File dir = new File(plugin.getDataFolder(), "shops");
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("无法创建商店目录：" + dir.getAbsolutePath());
        }
        File[] existing = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (existing == null || existing.length == 0) {
            for (String t : TEMPLATES) {
                try {
                    plugin.saveResource("shops/" + t, false);
                } catch (Throwable ignored) {
                    // 模板不存在就跳过
                }
            }
        }
        return dir;
    }

    public void loadAll() {
        shops.clear();
        File dir = folder();
        File[] files = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null || files.length == 0) {
            plugin.getLogger().warning("商店目录里没有任何 yml：" + dir.getAbsolutePath());
            return;
        }
        for (File file : files) {
            String fileName = file.getName();
            if (LEGACY.contains(fileName)) {
                plugin.getLogger().info("跳过旧版模板 " + fileName + "（已被新模板取代，可手动删除）");
                continue;
            }
            try {
                Shop shop = load(file);
                if (shop != null) {
                    shops.put(shop.id().toLowerCase(Locale.ROOT), shop);
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("加载 " + fileName + " 失败：" + t);
            }
        }
        if (plugin.config().antiArbitrage()) {
            enforceAntiArbitrage();
        }
    }

    /**
     * 防止低买高卖：收购菜单的价格不能高于购买菜单同款的最低买价。
     */
    private void enforceAntiArbitrage() {
        for (Shop sellShop : byType(Shop.Type.SELL)) {
            for (ShopItem sellItem : sellShop.items()) {
                double minBuy = Double.MAX_VALUE;
                for (Shop buyShop : byType(Shop.Type.BUY)) {
                    for (ShopItem buyItem : buyShop.items()) {
                        if (sameCurrency(sellItem, buyItem) && sameProduct(sellItem, buyItem)) {
                            minBuy = Math.min(minBuy, buyItem.minBuyPrice());
                        }
                    }
                }
                if (minBuy == Double.MAX_VALUE) {
                    continue;
                }
                double allowed = minBuy / Math.max(1, sellItem.dynamicMultiplierMax());
                if (sellItem.baseSellPrice() > allowed) {
                    plugin.getLogger().warning("防刷钱：收购商店 " + sellShop.id() + " 的商品 "
                            + sellItem.itemId() + " 收购价 " + sellItem.baseSellPrice()
                            + " 高于安全上限 " + allowed + "，已自动压价");
                    sellItem.capSellPrice(allowed);
                }
            }
        }
    }

    private static boolean sameProduct(@NotNull ShopItem a, @NotNull ShopItem b) {
        if (a.isCustom() || b.isCustom()) {
            return a.isCustom() && b.isCustom()
                    && a.matches(b.reward(1));
        }
        return a.material() == b.material();
    }

    private boolean sameCurrency(@NotNull ShopItem a, @NotNull ShopItem b) {
        String ca = a.currencyId() != null ? a.currencyId() : plugin.config().currencyDefault();
        String cb = b.currencyId() != null ? b.currencyId() : plugin.config().currencyDefault();
        return ca.equalsIgnoreCase(cb);
    }

    private double sanitizeDiscount(double value) {
        if (!Double.isFinite(value) || value <= 0 || value > 1) {
            return 1;
        }
        return value;
    }

    private long parseDiscountUntil(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            String text = raw.trim();
            java.time.LocalDateTime time;
            if (text.length() == 10) {
                time = java.time.LocalDate.parse(text).atTime(23, 59, 59);
            } else if (text.contains("T")) {
                time = java.time.LocalDateTime.parse(text);
            } else {
                time = java.time.LocalDateTime.parse(text,
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            }
            return time.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Throwable t) {
            plugin.getLogger().warning("折扣截止时间格式错误：" + raw + "（可用 2026-12-31 或 2026-12-31 23:59）");
            return 0;
        }
    }

    private double sanitizePrice(double value, @NotNull String shopId,
                                 @NotNull String itemId, @NotNull String key) {
        if (!Double.isFinite(value) || value < 0) {
            plugin.getLogger().warning(shopId + " 的商品 " + itemId
                    + " 价格 " + key + " 无效（" + value + "），已按 0 处理");
            return 0;
        }
        return value;
    }

    private @Nullable Shop load(@NotNull File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String fallbackId = file.getName().substring(0, file.getName().length() - 4);
        String id = yaml.getString("id", fallbackId);

        String typeRaw = yaml.getString("type", "BUY").toUpperCase(Locale.ROOT);
        Shop.Type type = typeRaw.startsWith("SELL") ? Shop.Type.SELL : Shop.Type.BUY;

        Material icon = matchMaterial(yaml.getString("icon"));
        if (icon == null) {
            icon = type == Shop.Type.SELL ? Material.CHEST : Material.EMERALD;
        }

        Shop shop = new Shop(id, yaml.getString("name", id), icon,
                yaml.getStringList("desc"), yaml.getString("permission"), type);

        // ---------- 简写：items 是列表 ----------
        List<Map<?, ?>> shortForm = yaml.getMapList("items");
        int index = 0;
        for (Map<?, ?> raw : shortForm) {
            String line = raw.values().stream().findFirst().map(String::valueOf).orElse("");
            if (line.isBlank()) {
                continue;
            }
            ShopItem item = parseShort(id, index, line, type, index);
            if (item != null) {
                shop.addItem(item);
                index++;
            }
        }

        // ---------- 详写：items 是 section ----------
        ConfigurationSection longForm = yaml.getConfigurationSection("items");
        if (longForm != null) {
            int autoSlot = index;
            for (String itemId : longForm.getKeys(false)) {
                ConfigurationSection s = longForm.getConfigurationSection(itemId);
                if (s == null) {
                    Object v = longForm.get(itemId);
                    // 简写：bread: 0.488  或  diamond: 260 stock=300 limit=16 dynamic=true
                    ShopItem item = parseShort(id, autoSlot, itemId + " " + v, type, autoSlot, itemId);
                    if (item != null) {
                        shop.addItem(item);
                        autoSlot++;
                    }
                    continue;
                }
                ShopItem item = parseSection(id, itemId, s, type, autoSlot);
                if (item != null) {
                    shop.addItem(item);
                    if (item.slot() < 0) {
                        autoSlot++;
                    }
                }
            }
        }

        return shop;
    }

    /**
     * 商品网格槽位合法范围（第 2~5 行）。
     *
     * <p>建议在 yml 里写 0~35（相对商品区），也允许直接写 9~44（绝对槽位），
     * 超出范围会自动按顺序排列。</p>
     */
    public static final int GRID_MIN = 0;
    public static final int GRID_MAX = 35;

    private @Nullable ShopItem parseSection(
            @NotNull String shopId,
            @NotNull String itemId,
            @NotNull ConfigurationSection s,
            @NotNull Shop.Type type,
            int autoSlot
    ) {
        String customId = s.getString("custom-item");
        ItemStack customTemplate = null;
        Material material;
        if (customId != null && !customId.isBlank()) {
            customTemplate = CustomItems.resolve(customId);
            if (customTemplate == null) {
                plugin.getLogger().warning(shopId + " 的商品 " + itemId
                        + " 自定义物品无法解析：" + customId);
                return null;
            }
            material = customTemplate.getType();
        } else {
            material = matchMaterial(s.getString("material"));
            if (material == null) {
                material = matchMaterial(itemId);
            }
            if (material == null) {
                plugin.getLogger().warning(shopId + " 的商品 " + itemId + " 材质无效");
                return null;
            }
        }

        boolean sell = type == Shop.Type.SELL;

        // 主价格：收购菜单读 sell，购买菜单读 buy
        String primaryKey = sell ? "sell" : "buy";
        String otherKey = sell ? "buy" : "sell";
        double primaryRaw = s.contains(primaryKey)
                ? s.getDouble(primaryKey)
                : s.getDouble("price", 0);
        // 另一个价格（购买菜单里的 sell = 回收价，反之亦然）
        Double other = s.contains(otherKey) ? s.getDouble(otherKey) : null;
        primaryRaw = sanitizePrice(primaryRaw, shopId, itemId, primaryKey);
        if (other != null) {
            other = sanitizePrice(other, shopId, itemId, otherKey);
        }

        double buyPrice;
        Double sellPrice;
        if (sell) {
            // 收购菜单：primary 是卖价，other 是（可选的）家伙
            sellPrice = primaryRaw;
            buyPrice = other != null ? other : 0;
        } else {
            buyPrice = primaryRaw;
            sellPrice = other;
        }

        List<String> lore = s.isList("lore") ? s.getStringList("lore")
                : s.getString("lore") == null ? List.of() : List.of(s.getString("lore"));

        var display = customTemplate != null
                ? ItemSerializer.decorate(customTemplate, s.getString("name"), lore,
                        s.getInt("custom-model-data", 0), s.getBoolean("glow", false))
                : ItemSerializer.buildDisplay(
                        material, 1, s.getString("name"), lore,
                        s.getInt("custom-model-data", 0), s.getBoolean("glow", false));

        double discount = sanitizeDiscount(s.getDouble("discount", 1));
        long discountUntil = parseDiscountUntil(s.getString("discount-until"));
        Double vipPrice = s.contains("vip-price")
                ? sanitizePrice(s.getDouble("vip-price"), shopId, itemId, "vip-price")
                : null;
        String vipPermission = s.getString("vip-permission");

        return new ShopItem(
                shopId, itemId, display, material, customTemplate, customId,
                buyPrice,
                sellPrice,
                s.getString("currency"),
                normalizeSlot(s.getInt("slot", -1)),
                s.getString("permission"),
                s.getInt("stock", 0),
                // 单个商品开启动态价格：dynamic: true
                s.getBoolean("dynamic", false),
                s.getInt("limit", 0),
                // 单卖的每日上限：-1 = 按价格自动分档，0 = 不限，>0 = 手写值
                s.getInt("sell-limit", sell ? -1 : 0),
                discount, discountUntil, vipPrice, vipPermission
        );
    }

    /**
     * 把 config 里的 slot 规范到 0~35。
     *
     * <p>允许写绝对槽位（9~44），也允许写相对槽位（0~35）；越界则返回 -1 交给自动排列。</p>
     */
    private static int normalizeSlot(int raw) {
        if (raw < 0) {
            return -1;
        }
        // 绝对槽位 9~44 → 相对 0~35
        if (raw >= 9 && raw <= 44) {
            return raw - 9;
        }
        if (raw >= GRID_MIN && raw <= GRID_MAX) {
            return raw;
        }
        return -1;
    }

    private @Nullable ShopItem parseShort(
            @NotNull String shopId,
            int index,
            @NotNull String line,
            @NotNull Shop.Type type,
            int autoSlot
    ) {
        return parseShort(shopId, index, line, type, autoSlot, null);
    }

    /**
     * 一行商品。
     *
     * <pre>
     *   bread 0.488
     *   diamond 260 stock=300 limit=16 dynamic=true
     *   cobblestone 0.02 sell-limit=1000
     * </pre>
     *
     * @param fixedItemId 为 {@code null} 时按「材质_序号」生成商品 ID
     */
    private @Nullable ShopItem parseShort(
            @NotNull String shopId,
            int index,
            @NotNull String line,
            @NotNull Shop.Type type,
            int autoSlot,
            @Nullable String fixedItemId
    ) {
        String[] parts = line.trim().split("\\s+");
        if (parts.length == 0) {
            return null;
        }
        double price = parts.length > 1 ? parse(parts[1], 0) : 0;
        price = sanitizePrice(price, shopId, fixedItemId != null ? fixedItemId : parts[0], "price");
        boolean sell = type == Shop.Type.SELL;

        // 单价后面的 key=value 选项
        Map<String, String> opts = new HashMap<>();
        for (int i = 2; i < parts.length; i++) {
            int eq = parts[i].indexOf('=');
            if (eq > 0) {
                opts.put(parts[i].substring(0, eq).toLowerCase(Locale.ROOT),
                        parts[i].substring(eq + 1));
            }
        }

        boolean glow = boolOpt(opts, "glow", false);
        int modelData = intOpt(opts, "custom-model-data",
                intOpt(opts, "model", 0));

        String customId = opts.get("custom-item");
        ItemStack customTemplate = null;
        Material material;
        if (customId != null && !customId.isBlank()) {
            customTemplate = CustomItems.resolve(customId);
            if (customTemplate == null) {
                plugin.getLogger().warning(shopId + " 简写商品自定义物品无法解析：" + customId);
                return null;
            }
            material = customTemplate.getType();
        } else {
            material = matchMaterial(parts[0]);
            if (material == null) {
                plugin.getLogger().warning(shopId + " 简写商品材质无效：" + parts[0]);
                return null;
            }
        }

        int stock = intOpt(opts, "stock", 0);
        int limit = intOpt(opts, "limit", 0);
        int sellLimit = opts.containsKey("sell-limit")
                ? intOpt(opts, "sell-limit", -1)
                : (sell ? -1 : 0);
        boolean dynamic = boolOpt(opts, "dynamic", false);
        int slot = normalizeSlot(intOpt(opts, "slot", -1));
        double discount = sanitizeDiscount(doubleOpt(opts, "discount", 1));
        long discountUntil = parseDiscountUntil(opts.get("discount-until"));
        Double vipPrice = opts.containsKey("vip-price")
                ? sanitizePrice(parse(opts.get("vip-price"), 0), shopId, parts[0], "vip-price")
                : null;

        double buyPrice;
        Double sellPrice;
        if (sell) {
            sellPrice = price;
            buyPrice = opts.containsKey("buy")
                    ? sanitizePrice(parse(opts.get("buy"), 0), shopId, parts[0], "buy")
                    : 0;
        } else {
            buyPrice = price;
            sellPrice = opts.containsKey("sell")
                    ? sanitizePrice(parse(opts.get("sell"), 0), shopId, parts[0], "sell")
                    : null;
        }

        var display = customTemplate != null
                ? ItemSerializer.decorate(customTemplate, opts.get("name"), null, modelData, glow)
                : ItemSerializer.buildDisplay(material, 1, null, null, modelData, glow);
        String itemId = fixedItemId != null
                ? fixedItemId
                : material.name().toLowerCase(Locale.ROOT) + "_" + index;

        return new ShopItem(
                shopId, itemId, display, material, customTemplate, customId,
                buyPrice,
                sellPrice,
                opts.get("currency"),
                slot != -1 ? slot : autoSlot,
                opts.get("permission"),
                stock, dynamic, limit, sellLimit,
                discount, discountUntil, vipPrice, opts.get("vip-permission")
        );
    }

    private static int intOpt(@NotNull Map<String, String> opts, @NotNull String key, int fallback) {
        String raw = opts.get(key);
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean boolOpt(@NotNull Map<String, String> opts, @NotNull String key, boolean fallback) {
        String raw = opts.get(key);
        return raw == null ? fallback : Boolean.parseBoolean(raw.trim());
    }

    private static double doubleOpt(@NotNull Map<String, String> opts, @NotNull String key, double fallback) {
        String raw = opts.get(key);
        if (raw == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static @Nullable Material matchMaterial(@Nullable String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
        } catch (Throwable t) {
            return null;
        }
    }

    private static double parse(@NotNull String raw, double fallback) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // ------------------------------------------------------------ 查询

    public @Nullable Shop get(@Nullable String id) {
        return id == null ? null : shops.get(id.toLowerCase(Locale.ROOT));
    }

    public @NotNull List<Shop> byType(@NotNull Shop.Type type) {
        return shops.values().stream().filter(s -> s.type() == type).toList();
    }

    public @NotNull Map<String, Shop> all() {
        return shops;
    }

    public @NotNull List<String> ids() {
        return new ArrayList<>(shops.values().stream().map(Shop::id).toList());
    }

    public int totalItems() {
        return shops.values().stream().mapToInt(s -> s.items().size()).sum();
    }

    public @NotNull File fileOf(@NotNull String shopId) {
        return new File(folder(), shopId.toLowerCase(Locale.ROOT) + ".yml");
    }

    public static @NotNull String color(@Nullable String raw) {
        return Text.color(raw);
    }

}
