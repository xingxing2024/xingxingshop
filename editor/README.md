# XingXingShop 商店编辑器

独立的 Windows 桌面商店编辑器，用 Java Swing 编写，可打包成带运行环境的 EXE。

## 功能

- 打开 `plugins/XingXingShop/shops` 文件夹，列出所有商店
- 编辑商店名称、图标、类型、权限、活动时间
- 商品表格：添加 / 编辑 / 删除 / 双击编辑
- 支持字段：材质、自定义物品、显示名、Lore、买价、卖价、库存、补货、限购、限卖、折扣、VIP 价、货币、权限、槽位、模型数据、动态价格、发光
- 保存时**保留 YAML 注释**，并生成 `.bak` 备份
- 删除商店移动到 `shops/deleted/`，不会直接删文件
- 支持 `--selftest` 和 `--validate <文件夹>` 命令行自检

## 构建

需要 JDK 21+ 与 Maven。

```bash
cd editor
mvn package
java -jar target/XingXingShopEditor.jar
```

自检：

```bash
java -jar target/XingXingShopEditor.jar --selftest
java -jar target/XingXingShopEditor.jar --validate "路径/plugins/XingXingShop/shops"
```

## 打包 EXE

```powershell
# 生成带 Java 运行时的 app-image
jpackage --type app-image --name XingXingShopEditor --app-version 1.0.0 `
  --input target/app --main-jar XingXingShopEditor.jar `
  --main-class io.github.hello09x.editor.EditorMain `
  --dest target/dist --java-options "-Dfile.encoding=UTF-8"

# 压缩分发
Compress-Archive -Path target/dist/XingXingShopEditor/* `
  -DestinationPath target/XingXingShopEditor-1.0.0-windows-x64.zip
```

`target/dist/XingXingShopEditor/XingXingShopEditor.exe` 可直接运行，无需安装 Java。

## 使用

1. 打开编辑器，选择 `plugins/XingXingShop/shops`
2. 编辑商店和商品
3. 保存
4. 把文件上传回服务器
5. 执行 `/shop reload`

## 注意

- 商品 ID 创建后不能在编辑器里修改
- 保存会重排该商品块内的字段顺序，但注释会保留
- YAML 里非常规写法（锚点、复杂嵌套）可能不被完整保留，建议先备份
