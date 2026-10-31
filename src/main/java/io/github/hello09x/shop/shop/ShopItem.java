package io.github.hello09x.shop.shop;

import io.github.hello09x.shop.ShopPlugin;
import io.github.hello09x.shop.util.ChineseNames;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 一个商品。
 *
 * <p><b>所有价格都是「单个」的价格</b>，配置里写的 {@code buy: 0.5} 就是
 * 「1 个卖 0.5 元」，不再有「一份多少个」的概念。</p>
 */
public final class ShopItem {

    private final String key;
    private final String shopId;
    private final String itemId;

    /** GUI 图标（带中文展示名） */
    private final ItemStack display;
    private final Material material;
    /** 自定义物品原始模板（ItemsAdder / Nexo / MMOItems），null 表示原版物品 */
    private final @Nullable ItemStack customTemplate;

    /** 单个买入价 */
    private final double buyPrice;
    /** 单个卖出价；{@code null} 表示按全局 sell-ratio 计算 */
    private final Double sellPrice;
    private final String currencyId;

    private final int slot;
    private final String permission;

    /** 库存上限：<= 0 表示不限量 */
    private final int maxStock;
    private final boolean dynamic;

    /** 每日限购（买）：<= 0 表示不限 */
    private final int limit;
    /** 每日限卖（卖）：-1 表示按价格自动分档，>0 用手写值，0 表示不限 */
    private final int sellLimit;

    private volatile int stock;
    private volatile int sold;

    public ShopItem(
            @NotNull String shopId,
            @NotNull String itemId,
            @NotNull ItemStack display,
            @NotNull Material material,
            @Nullable ItemStack customTemplate,
            double buyPrice,
            @Nullable Double sellPrice,
            @Nullable String currencyId,
            int slot,
            @Nullable String permission,
            int maxStock,
            boolean dynamic,
            int limit,
            int sellLimit
    ) {
        this.shopId = shopId;
        this.itemId = itemId;
        this.key = shopId + ":" + itemId;
        this.display = display;
        this.material = material;
        this.customTemplate = customTemplate == null ? null : customTemplate.clone();
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.currencyId = currencyId;
        this.slot = slot;
        this.permission = permission;
        this.maxStock = maxStock;
        this.stock = maxStock;
        this.dynamic = dynamic;
        this.limit = limit;
        this.sellLimit = sellLimit;
    }

    // ================================================================
    //  价格（全部是单价）
    // ================================================================

    /** 当前买入单价 */
    public double buyPrice() {
        if (!isDynamicActive()) {
            return round(buyPrice);
        }
        int base = Math.max(1, maxStock);
        double ratio = (double) (base - stock) / base;
        double multiplier = 1 + ratio * ShopPlugin.getInstance().config().dynamicCoefficient();
        return round(buyPrice * clamp(multiplier));
    }

    /** 基础买入单价（配置里写的） */
    public double baseBuyPrice() {
        return buyPrice;
    }

    /** 当前卖出单价（含动态收购衰减） */
    public double sellPrice() {
        double base = sellPrice != null
                ? sellPrice
                : buyPrice * ShopPlugin.getInstance().config().sellRatio();

        if (isSellDynamic()) {
            var cfg = ShopPlugin.getInstance().config();
            double drop = Math.min(cfg.sellDynamicMax(),
                    (double) sold / cfg.sellDynamicLimit() * cfg.sellDynamicMax());
            base = base * (1 - drop);
        }

        if (!isDynamicActive()) {
            return round(base);
        }
        int max = Math.max(1, maxStock);
        double ratio = (double) (max - stock) / max;
        double multiplier = 1 + ratio * ShopPlugin.getInstance().config().dynamicCoefficient();
        return round(base * clamp(multiplier));
    }

    public boolean isDynamicActive() {
        return dynamic && maxStock > 0 && ShopPlugin.getInstance().config().dynamicPricing();
    }

    /** 收购价是否因为卖太多而下跌 */
    public boolean isStockDown() {
        return isSellDynamic() && sold > 0;
    }

    public boolean isSellDynamic() {
        return ShopPlugin.getInstance().config().sellDynamic();
    }

    private double clamp(double multiplier) {
        var cfg = ShopPlugin.getInstance().config();
        return Math.max(cfg.dynamicMin(), Math.min(multiplier, cfg.dynamicMax()));
    }

    private static double round(double v) {
        return Math.round(v * 10000D) / 10000D;
    }

    // ================================================================
    //  库存
    // ================================================================

    public boolean isLimitedStock() {
        return maxStock > 0;
    }

    public int stock() {
        return stock;
    }

    public void stock(int value) {
        this.stock = value;
    }

    public boolean takeStock(int quantity) {
        if (!isLimitedStock()) {
            return true;
        }
        if (stock < quantity) {
            return false;
        }
        stock -= quantity;
        return true;
    }

    public void giveStock(int quantity) {
        if (isLimitedStock()) {
            stock = Math.min(maxStock, stock + quantity);
        }
    }

    public int maxStock() {
        return maxStock;
    }

    public int sold() {
        return sold;
    }

    public void sold(int value) {
        this.sold = value;
    }

    public void addSold(int quantity) {
        this.sold += quantity;
    }

    // ================================================================
    //  每日限购 / 限卖
    // ================================================================

    /** 是否限购（买） */
    public boolean isLimited() {
        return limit > 0 && ShopPlugin.getInstance().config().limits();
    }

    public int limit() {
        return limit;
    }

    /**
     * 本商品「每天最多能卖多少个」。
     *
     * <p>按卖出单价分档：越贵卖得越少。</p>
     * <pre>
     *   单价 &lt; 0.5      →  512 个/天    （泥土、农作物，随便卖）
     *   单价 &lt; 2        →  256 个/天    （煤炭、铁锭）
     *   单价 &lt; 10       →   64 个/天    （金锭、烈焰棒）
     *   单价 &lt; 100      →   16 个/天    （钻石、绿宝石）
     *   单价 &gt;= 100     →    4 个/天    （下界合金、下界之星）
     * </pre>
     * <p>yml 里手写 {@code sell-limit} 则以手写值为准；写 0 表示不限。</p>
     */
    public int sellLimit() {
        if (sellLimit >= 0) {
            return sellLimit;
        }
        var cfg = ShopPlugin.getInstance().config();
        if (!cfg.sellLimits()) {
            return 0;
        }
        double unit = sellPrice != null ? sellPrice : buyPrice * cfg.sellRatio();
        if (unit < 0.5) return 512;
        if (unit < 2) return 256;
        if (unit < 10) return 64;
        if (unit < 100) return 16;
        return 4;
    }

    /** 是否限卖 */
    public boolean isSellLimited() {
        return sellLimit() > 0;
    }

    /** 手动配置的限卖值（-1 = 自动分档） */
    public int rawSellLimit() {
        return sellLimit;
    }

    // ================================================================
    //  getter
    // ================================================================

    public @NotNull String key() {
        return key;
    }

    public @NotNull String shopId() {
        return shopId;
    }

    public @NotNull String itemId() {
        return itemId;
    }

    /** GUI 图标（带中文展示名） */
    public @NotNull ItemStack display() {
        return display.clone();
    }

    public @NotNull Material material() {
        return material;
    }

    /** 是否是 ItemsAdder / Nexo / MMOItems 等自定义物品 */
    public boolean isCustom() {
        return customTemplate != null;
    }

    /** 这个物品最大能叠多少个 */
    public int maxStackSize() {
        return customTemplate != null
                ? Math.max(1, customTemplate.getMaxStackSize())
                : material.getMaxStackSize();
    }

    /**
     * 判断背包里的物品是不是本商品。
     *
     * <p>原版按材质匹配；自定义物品按完整 ItemStack 匹配（忽略数量）。</p>
     */
    public boolean matches(@Nullable ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return false;
        }
        if (customTemplate != null) {
            return stack.isSimilar(customTemplate);
        }
        return stack.getType() == material;
    }

    /** 展示名：优先配置 name，其次自定义物品自带名字，最后中文材质名 */
    public @NotNull String displayName() {
        ItemMeta meta = display.getItemMeta();
        String configured = meta != null && meta.hasDisplayName() ? meta.getDisplayName() : null;
        return ChineseNames.ofItem(material, configured);
    }

    public @Nullable String currencyId() {
        return currencyId;
    }

    public int slot() {
        return slot;
    }

    public @Nullable String permission() {
        return permission;
    }

    /**
     * 真正发放给玩家的物品。
     *
     * <p>原版物品不带名字和 lore；自定义物品保留插件生成的完整数据。</p>
     *
     * @param amount 给多少个
     */
    public @NotNull ItemStack reward(int amount) {
        if (amount <= 0) {
            return new ItemStack(Material.AIR);
        }
        int capped = Math.min(maxStackSize(), amount);
        if (customTemplate != null) {
            ItemStack stack = customTemplate.clone();
            stack.setAmount(capped);
            return stack;
        }
        return new ItemStack(material, capped);
    }

}
