package io.github.hello09x.editor;

import java.nio.file.Path;

/** 编辑器内存里的商店数据。 */
public final class ShopData {

    public String id = "";
    public String name = "";
    public String icon = "CHEST";
    public String type = "BUY";
    public String permission = "";
    public String openFrom = "";
    public String openTo = "";
    public Path file;
    public YamlShopFile document;

    public boolean selling() {
        return "SELL".equalsIgnoreCase(type);
    }

    @Override
    public String toString() {
        return name + "  (" + id + ")";
    }
}
