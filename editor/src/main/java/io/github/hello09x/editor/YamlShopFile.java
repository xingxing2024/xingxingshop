package io.github.hello09x.editor;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 一个商店 YAML 文件。
 *
 * <p>用 SnakeYAML 的 Node 树编辑，注释会随节点保留；保存时只重写被修改的节点。</p>
 */
public final class YamlShopFile {

    private final Path path;
    private final Yaml yaml;
    private MappingNode root;

    public YamlShopFile(Path path) throws IOException {
        this.path = path;
        this.yaml = createYaml();
        load();
    }

    public static Yaml createYaml() {
        LoaderOptions loader = new LoaderOptions();
        loader.setProcessComments(true);
        loader.setAllowDuplicateKeys(false);

        DumperOptions dumper = new DumperOptions();
        dumper.setProcessComments(true);
        dumper.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dumper.setIndent(2);
        dumper.setIndicatorIndent(0);
        dumper.setWidth(4096);
        dumper.setAllowUnicode(true);

        return new Yaml(new SafeConstructor(loader), new Representer(dumper), dumper, loader);
    }

    public Path path() {
        return path;
    }

    public void load() throws IOException {
        String text = Files.readString(path, StandardCharsets.UTF_8);
        Node node = yaml.compose(new StringReader(text));
        if (!(node instanceof MappingNode map)) {
            throw new IOException("不是有效的商店 YAML：" + path.getFileName());
        }
        this.root = map;
    }

    public ShopData readShop() {
        ShopData shop = new ShopData();
        shop.file = path;
        shop.document = this;
        String fallback = path.getFileName().toString().replaceFirst("(?i)\\.ya?ml$", "");
        shop.id = value("id", fallback);
        shop.name = value("name", shop.id);
        shop.icon = value("icon", "CHEST");
        shop.type = value("type", "BUY").toUpperCase(Locale.ROOT);
        shop.permission = value("permission", "");
        shop.openFrom = value("open-from", "");
        shop.openTo = value("open-to", "");
        return shop;
    }

    public List<ItemData> readItems(boolean selling) {
        List<ItemData> out = new ArrayList<>();
        MappingNode items = itemsMap(false);
        if (items == null) {
            return out;
        }
        for (NodeTuple tuple : items.getValue()) {
            if (!(tuple.getKeyNode() instanceof ScalarNode key)) {
                continue;
            }
            out.add(readItem(key.getValue(), tuple.getValueNode(), selling));
        }
        return out;
    }

    private ItemData readItem(String id, Node node, boolean selling) {
        ItemData item = new ItemData();
        item.id = id;
        item.material = id.toUpperCase(Locale.ROOT);
        if (node instanceof ScalarNode scalar) {
            if (selling) {
                item.sell = scalar.getValue();
            } else {
                item.buy = scalar.getValue();
            }
            return item;
        }
        if (!(node instanceof MappingNode map)) {
            return item;
        }
        item.material = mapValue(map, "material", item.material);
        item.customItem = mapValue(map, "custom-item", "");
        item.displayName = mapValue(map, "name", "");
        item.buy = mapValue(map, "buy", "");
        item.sell = mapValue(map, "sell", "");
        item.stock = mapValue(map, "stock", "");
        item.limit = mapValue(map, "limit", "");
        item.sellLimit = mapValue(map, "sell-limit", "");
        item.restock = mapValue(map, "restock", "");
        item.restockInterval = mapValue(map, "restock-interval", "");
        item.discount = mapValue(map, "discount", "");
        item.discountStart = mapValue(map, "discount-start", "");
        item.discountUntil = mapValue(map, "discount-until", "");
        item.vipPrice = mapValue(map, "vip-price", "");
        item.vipPermission = mapValue(map, "vip-permission", "");
        item.currency = mapValue(map, "currency", "");
        item.permission = mapValue(map, "permission", "");
        item.slot = mapValue(map, "slot", "");
        item.customModelData = mapValue(map, "custom-model-data", "");
        item.dynamic = Boolean.parseBoolean(mapValue(map, "dynamic", "false"));
        item.glow = Boolean.parseBoolean(mapValue(map, "glow", "false"));
        Node lore = findNode(map, "lore");
        if (lore instanceof SequenceNode seq) {
            List<String> lines = new ArrayList<>();
            for (Node line : seq.getValue()) {
                if (line instanceof ScalarNode s) {
                    lines.add(s.getValue());
                }
            }
            item.lore = String.join("\n", lines);
        }
        return item;
    }

    public void applyShop(ShopData shop) {
        setTop("id", shop.id, false);
        setTop("name", shop.name, true);
        setTop("icon", shop.icon, false);
        setTop("type", shop.type.toUpperCase(Locale.ROOT), false);
        setOrRemove("permission", shop.permission, true);
        setOrRemove("open-from", shop.openFrom, true);
        setOrRemove("open-to", shop.openTo, true);
    }

    /**
     * 更新已有商品。原来是一行简写的商品，如果只改价格会保持简写。
     */
    public void updateItem(ItemData data, boolean selling) {
        MappingNode items = ensureItemsMap();
        NodeTuple tuple = findTuple(items, data.id);
        if (tuple == null) {
            addItem(data, selling);
            return;
        }

        Node old = tuple.getValueNode();
        String priceKey = selling ? "sell" : "buy";
        String priceValue = selling ? data.sell : data.buy;

        if (old instanceof ScalarNode scalar && onlyPrice(data, selling)) {
            ScalarNode value = scalarNode(priceValue, false);
            copyComments(scalar, value);
            replaceValue(items, tuple, value);
            return;
        }

        if (old instanceof ScalarNode scalar) {
            MappingNode map = new MappingNode(Tag.MAP, new ArrayList<>(), DumperOptions.FlowStyle.BLOCK);
            if (!priceValue.isBlank()) {
                ScalarNode value = scalarNode(priceValue, false);
                copyComments(scalar, value);
                map.getValue().add(new NodeTuple(keyNode(priceKey), value));
            }
            writeItemFields(map, data, selling, true);
            replaceValue(items, tuple, map);
            return;
        }

        if (old instanceof MappingNode map) {
            writeItemFields(map, data, selling, false);
        }
    }

    public void addItem(ItemData data, boolean selling) {
        MappingNode items = ensureItemsMap();
        MappingNode map = new MappingNode(Tag.MAP, new ArrayList<>(), DumperOptions.FlowStyle.BLOCK);
        writeItemFields(map, data, selling, true);
        items.getValue().add(new NodeTuple(keyNode(data.id), map));
    }

    public void removeItem(String id) {
        MappingNode items = itemsMap(false);
        if (items == null) {
            return;
        }
        items.getValue().removeIf(t -> t.getKeyNode() instanceof ScalarNode s && s.getValue().equals(id));
    }

    public void save() throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (Files.isRegularFile(path)) {
            Files.copy(path, path.resolveSibling(path.getFileName() + ".bak"),
                    StandardCopyOption.REPLACE_EXISTING);
        }
        StringWriter writer = new StringWriter();
        yaml.serialize(root, writer);
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temp, writer.toString(), StandardCharsets.UTF_8);
        Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
    }

    public static YamlShopFile create(Path file, ShopData shop) throws IOException {
        String text = "# 由 XingXingShop 商店编辑器创建\n"
                + "id: " + shop.id + "\n"
                + "name: '" + shop.name.replace("'", "''") + "'\n"
                + "icon: " + (shop.icon.isBlank() ? "CHEST" : shop.icon) + "\n"
                + "type: " + shop.type.toUpperCase(Locale.ROOT) + "\n"
                + "\n"
                + "items: {}\n";
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return new YamlShopFile(file);
    }

    // ------------------------------------------------------------ 内部

    private void writeItemFields(MappingNode map, ItemData data, boolean selling, boolean fresh) {
        setField(map, "material", data.material, false);
        setField(map, "custom-item", data.customItem, true);
        setField(map, "name", data.displayName, true);
        setField(map, "buy", data.buy, false);
        setField(map, "sell", data.sell, false);
        setField(map, "stock", data.stock, false);
        setField(map, "limit", data.limit, false);
        setField(map, "sell-limit", data.sellLimit, false);
        setField(map, "restock", data.restock, false);
        setField(map, "restock-interval", data.restockInterval, false);
        setField(map, "discount", data.discount, false);
        setField(map, "discount-start", data.discountStart, true);
        setField(map, "discount-until", data.discountUntil, true);
        setField(map, "vip-price", data.vipPrice, false);
        setField(map, "vip-permission", data.vipPermission, true);
        setField(map, "currency", data.currency, false);
        setField(map, "permission", data.permission, true);
        setField(map, "slot", data.slot, false);
        setField(map, "custom-model-data", data.customModelData, false);
        setBoolean(map, "dynamic", data.dynamic);
        setBoolean(map, "glow", data.glow);
        setLore(map, data.lore);

        // 价格字段放到靠前位置（新商品）
        if (fresh) {
            String key = selling ? "sell" : "buy";
            String value = selling ? data.sell : data.buy;
            if (!value.isBlank()) {
                moveFieldFirst(map, key);
            }
        }
    }

    private boolean onlyPrice(ItemData data, boolean selling) {
        boolean defaultMaterial = data.material == null || data.material.isBlank()
                || data.material.equalsIgnoreCase(data.id);
        if (!defaultMaterial) {
            return false;
        }
        if (selling) {
            return !data.sell.isBlank() && data.buy.isBlank()
                    && allBlank(data.customItem, data.displayName, data.stock, data.limit,
                    data.sellLimit, data.restock, data.restockInterval, data.discount, data.discountStart,
                    data.discountUntil, data.vipPrice, data.vipPermission, data.currency, data.permission,
                    data.slot, data.customModelData, data.lore)
                    && !data.dynamic && !data.glow;
        }
        return !data.buy.isBlank() && data.sell.isBlank()
                && allBlank(data.customItem, data.displayName, data.stock, data.limit,
                data.sellLimit, data.restock, data.restockInterval, data.discount, data.discountStart,
                data.discountUntil, data.vipPrice, data.vipPermission, data.currency, data.permission,
                data.slot, data.customModelData, data.lore)
                && !data.dynamic && !data.glow;
    }

    private static boolean allBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private void setField(MappingNode map, String key, String value, boolean quote) {
        if (value == null || value.isBlank()) {
            removeField(map, key);
            return;
        }
        setMapScalar(map, key, value, quote);
    }

    private void setBoolean(MappingNode map, String key, boolean value) {
        if (!value) {
            removeField(map, key);
            return;
        }
        setMapScalar(map, key, "true", false);
    }

    private void setLore(MappingNode map, String lore) {
        if (lore == null || lore.isBlank()) {
            removeField(map, "lore");
            return;
        }
        List<Node> lines = new ArrayList<>();
        for (String line : lore.split("\\R", -1)) {
            lines.add(new ScalarNode(Tag.STR, line, null, null,
                    line.isEmpty() ? DumperOptions.ScalarStyle.SINGLE_QUOTED : DumperOptions.ScalarStyle.PLAIN));
        }
        SequenceNode seq = new SequenceNode(Tag.SEQ, lines, DumperOptions.FlowStyle.BLOCK);
        setMapNode(map, "lore", seq);
    }

    private void setTop(String key, String value, boolean quote) {
        setMapScalar(root, key, value, quote);
    }

    private void setOrRemove(String key, String value, boolean quote) {
        if (value == null || value.isBlank()) {
            removeField(root, key);
        } else {
            setMapScalar(root, key, value, quote);
        }
    }

    private String value(String key, String fallback) {
        Node node = findNode(root, key);
        return node instanceof ScalarNode s ? s.getValue() : fallback;
    }

    private MappingNode itemsMap(boolean create) {
        Node node = findNode(root, "items");
        if (node instanceof MappingNode map) {
            if (create && map.getFlowStyle() != DumperOptions.FlowStyle.BLOCK) {
                map.setFlowStyle(DumperOptions.FlowStyle.BLOCK);
            }
            return map;
        }
        if (!create) {
            return null;
        }
        MappingNode map = new MappingNode(Tag.MAP, new ArrayList<>(), DumperOptions.FlowStyle.BLOCK);
        setMapNode(root, "items", map);
        return map;
    }

    private MappingNode ensureItemsMap() {
        return itemsMap(true);
    }

    private void setMapScalar(MappingNode map, String key, String value, boolean quote) {
        NodeTuple tuple = findTuple(map, key);
        ScalarNode node = scalarNode(value, quote);
        if (tuple != null) {
            Node old = tuple.getValueNode();
            copyComments(old, node);
            replaceValue(map, tuple, node);
        } else {
            map.getValue().add(new NodeTuple(keyNode(key), node));
        }
    }

    private void setMapNode(MappingNode map, String key, Node value) {
        NodeTuple tuple = findTuple(map, key);
        if (tuple != null) {
            copyComments(tuple.getValueNode(), value);
            replaceValue(map, tuple, value);
        } else {
            map.getValue().add(new NodeTuple(keyNode(key), value));
        }
    }

    private void removeField(MappingNode map, String key) {
        map.getValue().removeIf(t -> t.getKeyNode() instanceof ScalarNode s && s.getValue().equals(key));
    }

    private void moveFieldFirst(MappingNode map, String key) {
        NodeTuple tuple = findTuple(map, key);
        if (tuple == null) {
            return;
        }
        map.getValue().remove(tuple);
        map.getValue().add(0, tuple);
    }

    private static String mapValue(MappingNode map, String key, String fallback) {
        Node node = findNode(map, key);
        return node instanceof ScalarNode s ? s.getValue() : fallback;
    }

    private static Node findNode(MappingNode map, String key) {
        for (NodeTuple tuple : map.getValue()) {
            if (tuple.getKeyNode() instanceof ScalarNode s && s.getValue().equals(key)) {
                return tuple.getValueNode();
            }
        }
        return null;
    }

    private static NodeTuple findTuple(MappingNode map, String key) {
        for (NodeTuple tuple : map.getValue()) {
            if (tuple.getKeyNode() instanceof ScalarNode s && s.getValue().equals(key)) {
                return tuple;
            }
        }
        return null;
    }

    private static void replaceValue(MappingNode map, NodeTuple tuple, Node value) {
        int index = map.getValue().indexOf(tuple);
        if (index >= 0) {
            map.getValue().set(index, new NodeTuple(tuple.getKeyNode(), value));
        }
    }

    private static ScalarNode keyNode(String key) {
        return new ScalarNode(Tag.STR, key, null, null, DumperOptions.ScalarStyle.PLAIN);
    }

    private static ScalarNode scalarNode(String value, boolean quote) {
        if (!quote) {
            if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
                return new ScalarNode(Tag.BOOL, value.toLowerCase(Locale.ROOT), null, null, DumperOptions.ScalarStyle.PLAIN);
            }
            if (value.matches("-?\\d+")) {
                return new ScalarNode(Tag.INT, value, null, null, DumperOptions.ScalarStyle.PLAIN);
            }
            if (value.matches("-?\\d+\\.\\d+")) {
                return new ScalarNode(Tag.FLOAT, value, null, null, DumperOptions.ScalarStyle.PLAIN);
            }
        }
        boolean needsQuote = quote || value.isBlank()
                || value.contains(": ") || value.startsWith("#") || value.startsWith("-")
                || value.contains(" #") || value.contains("\n");
        return new ScalarNode(Tag.STR, value, null, null,
                needsQuote ? DumperOptions.ScalarStyle.SINGLE_QUOTED : DumperOptions.ScalarStyle.PLAIN);
    }

    private static void copyComments(Node from, Node to) {
        to.setInLineComments(from.getInLineComments());
        to.setBlockComments(from.getBlockComments());
        to.setEndComments(from.getEndComments());
    }
}
