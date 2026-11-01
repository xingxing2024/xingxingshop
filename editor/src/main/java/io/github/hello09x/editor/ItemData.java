package io.github.hello09x.editor;

/** 编辑器内存里的商品数据。空字符串表示不写这个字段。 */
public final class ItemData {

    public String id = "";
    public String material = "";
    public String customItem = "";
    public String displayName = "";
    public String buy = "";
    public String sell = "";
    public String stock = "";
    public String limit = "";
    public String sellLimit = "";
    public String restock = "";
    public String restockInterval = "";
    public String discount = "";
    public String discountStart = "";
    public String discountUntil = "";
    public String vipPrice = "";
    public String vipPermission = "";
    public String currency = "";
    public String permission = "";
    public String slot = "";
    public String customModelData = "";
    public boolean dynamic;
    public boolean glow;
    public String lore = "";

    public ItemData copy() {
        ItemData c = new ItemData();
        c.id = id;
        c.material = material;
        c.customItem = customItem;
        c.displayName = displayName;
        c.buy = buy;
        c.sell = sell;
        c.stock = stock;
        c.limit = limit;
        c.sellLimit = sellLimit;
        c.restock = restock;
        c.restockInterval = restockInterval;
        c.discount = discount;
        c.discountStart = discountStart;
        c.discountUntil = discountUntil;
        c.vipPrice = vipPrice;
        c.vipPermission = vipPermission;
        c.currency = currency;
        c.permission = permission;
        c.slot = slot;
        c.customModelData = customModelData;
        c.dynamic = dynamic;
        c.glow = glow;
        c.lore = lore;
        return c;
    }
}
