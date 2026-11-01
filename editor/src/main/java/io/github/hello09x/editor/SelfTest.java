package io.github.hello09x.editor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** 无界面自检：验证 YAML 注释保留、读取、修改、增删商品。 */
public final class SelfTest {

    public static int run() {
        try {
            Path dir = Files.createTempDirectory("xshop-editor-test");
            Path file = dir.resolve("basic.yml");
            Files.writeString(file, """
                    # 顶部注释
                    id: basic
                    name: '基础物资'   # 名称注释
                    icon: GRASS_BLOCK
                    type: BUY

                    items:
                      # 面包注释
                      bread: 1.5   # 面包行内注释
                      diamond:
                        # 钻石价格注释
                        buy: 260
                        stock: 300
                        limit: 16
                    """, StandardCharsets.UTF_8);

            YamlShopFile doc = new YamlShopFile(file);
            ShopData shop = doc.readShop();
            check("读取商店 id", shop.id.equals("basic"));
            check("读取商店名称", shop.name.equals("基础物资"));
            check("读取商品数量", doc.readItems(false).size() == 2);

            ItemData bread = doc.readItems(false).stream().filter(i -> i.id.equals("bread")).findFirst().orElseThrow();
            check("读取简写价格", bread.buy.equals("1.5"));
            bread.buy = "2.0";
            doc.updateItem(bread, false);

            ItemData diamond = doc.readItems(false).stream().filter(i -> i.id.equals("diamond")).findFirst().orElseThrow();
            check("读取块状价格", diamond.buy.equals("260"));
            diamond.stock = "250";
            doc.updateItem(diamond, false);

            shop.name = "新的名字";
            doc.applyShop(shop);

            ItemData apple = new ItemData();
            apple.id = "apple";
            apple.material = "APPLE";
            apple.buy = "3.5";
            apple.stock = "100";
            doc.addItem(apple, false);

            doc.removeItem("bread");
            doc.save();

            String text = Files.readString(file, StandardCharsets.UTF_8);
            check("保留顶部注释", text.contains("# 顶部注释"));
            check("保留名称注释", text.contains("# 名称注释"));
            check("保留商品注释", text.contains("# 钻石价格注释"));
            check("名称已修改", text.contains("新的名字"));
            check("简写商品已删除", !text.contains("bread"));
            check("新商品已写入", text.contains("apple"));
            check("价格已修改", text.contains("260") && text.contains("250"));

            YamlShopFile reloaded = new YamlShopFile(file);
            check("重新加载商品数", reloaded.readItems(false).size() == 2);
            check("重新加载名称", reloaded.readShop().name.equals("新的名字"));

            // 新建商店 + 添加商品，检查 items: {} 会变成块状
            Path created = dir.resolve("created.yml");
            ShopData blank = new ShopData();
            blank.id = "created";
            blank.name = "新建商店";
            blank.type = "BUY";
            blank.icon = "CHEST";
            YamlShopFile createdDoc = YamlShopFile.create(created, blank);
            ItemData fresh = new ItemData();
            fresh.id = "stone";
            fresh.material = "STONE";
            fresh.buy = "1";
            createdDoc.addItem(fresh, false);
            createdDoc.save();
            String createdText = Files.readString(created, StandardCharsets.UTF_8);
            check("新商店改为块状 items", createdText.contains("items:\n  stone:"));
            check("新商店商品可读", new YamlShopFile(created).readItems(false).size() == 1);

            delete(dir);
            System.out.println("SELFTEST PASS");
            return 0;
        } catch (Throwable t) {
            System.out.println("SELFTEST FAIL: " + t);
            t.printStackTrace();
            return 1;
        }
    }

    private static void check(String name, boolean ok) {
        System.out.println((ok ? "PASS " : "FAIL ") + name);
        if (!ok) {
            throw new AssertionError(name);
        }
    }

    private static void delete(Path dir) {
        try (var stream = Files.walk(dir)) {
            stream.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }
}
