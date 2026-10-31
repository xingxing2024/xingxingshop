# XingXingShop

> 面向 **Paper / Purpur 1.20.6 ~ 26.x** 的 GUI 商店插件：购买 / 收购分离、MySQL 跨服、库存限购、动态价格、自定义物品、折扣 VIP、交易日报。

[![Build](https://github.com/xingxing2024/xingxingshop/actions/workflows/build.yml/badge.svg)](https://github.com/xingxing2024/xingxingshop/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/xingxing2024/xingxingshop)](https://github.com/xingxing2024/xingxingshop/releases)
[![License](https://img.shields.io/github/license/xingxing2024/xingxingshop)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange)](https://adoptium.net/)
[![Paper](https://img.shields.io/badge/Paper-1.20.6%20~%2026.x-blueviolet)](https://papermc.io/)
[![bStats](https://img.shields.io/badge/bStats-35029-2f9e44)](https://bstats.org/plugin/bukkit/xingxingshop/35029)

**[完整文档（Wiki）](https://github.com/xingxing2024/xingxingshop/wiki)** · **[下载最新版](https://github.com/xingxing2024/xingxingshop/releases)** · **[问题反馈](https://github.com/xingxing2024/xingxingshop/issues)**

---

## 为什么用它

| 需求 | XingXingShop |
|---|---|
| 购买和收购分开 | 两套独立菜单，`type: BUY` / `type: SELL` |
| 不想让玩家刷钱 | 防低买高卖、价格校验、交易二次确认、每日限购限卖 |
| 多个服共享经济 | MySQL 跨服，事务原子扣库存 / 限购，多服同时买不会超卖 |
| 配置不想太复杂 | 商品一行一个：`bread: 1.5`，数据库只填主机和账号密码 |
| 用自定义物品 | 支持 ItemsAdder / Nexo / MMOItems，按插件 ID 匹配 |
| 需要运营功能 | 限时折扣、VIP 价格、搜索、收藏、最近购买 |
| 需要排查问题 | 交易日志、每日日报、个人日报、可疑交易提醒 |

---

## 功能

### 商店

- 购买 / 收购分离，两级分类菜单
- 单价计价：配置写的就是「1 个多少钱」
- 左键 1 个 / 右键 1 组或全部卖出 / Shift+左键输入数量
- 交易二次确认，防误触
- 库存、动态价格、每日限购、每日限卖
- 商品搜索、收藏、最近购买
- 限时折扣、VIP 专属价格
- 商品自动补货
- 管理员交易记录面板 `/shop log gui`
- 音效全部可配置

### 数据与跨服

- SQLite（单服，零配置）
- MySQL（跨服，多服共享）
- 交易 / 库存 / 限购 / 日报投递记录全部入库
- HikariCP 连接池，交易异步处理
- SQLite / MySQL 驱动不打包，首次启动从阿里云按需下载
- 跨服库存事务原子扣减，不会超卖
- 商店配置可通过数据库同步到其他服
- 数据库表前缀，支持多插件共库

### 安全

- 防低买高卖：收购价高于最低买价时自动压价
- 负数 / NaN 价格校验
- 关服等待异步交易并补偿
- 管理员交易不计入日报统计
- 可疑交易提醒在线管理员

### 管理

- 全部数据入数据库，重启不丢
- 每日全服日报 / 个人日报
- PlaceholderAPI 变量
- bStats 匿名统计（可关闭）
- 启动检查 GitHub 新版本
- 配置自动补全，升级不丢已有值

---

## 安装

1. 服务端：**Paper / Purpur 1.20.6 ~ 26.x**
2. Java：**21+**
3. 安装 **Vault** + 任意经济插件（EssentialsX / CMI / XConomy 等）
4. 把 `XingXingShop-1.0.26.jar` 放进 `plugins/`
5. 启动服务器

首次启动会自动释放：

```text
plugins/XingXingShop/
├── config.yml
├── lang.yml
├── shops/          # 15 个商店模板，862 件商品
├── lib/            # 首次启动从阿里云下载的 JDBC 驱动
├── logs/           # 交易文本日志
└── reports/        # 日报
```

> 首次启动需要联网下载一次 SQLite / MySQL 驱动；之后复用 `lib/` 里的文件。离线服务器可以手动把驱动放进 `plugins/XingXingShop/lib/`，或在 `config.yml` 关闭 `database.driver-download`。

---

## 玩家命令

```text
/shop                 打开商店
/shop buy [分类]      购买菜单
/shop sell [分类]     收购菜单
/shop money           查看余额
/shop search <关键词> 搜索商品
/shop recent          最近 10 笔购买
/shop fav list        查看收藏
/shop myreport        我的交易日报
/shop help            帮助
```

| 操作 | 购买菜单 | 收购菜单 |
|---|---|---|
| 左键 | 买 1 个 | 卖 1 个 |
| 右键 | 买 1 组（64） | 全部卖出 |
| Shift+左键 | 聊天框输入数量 | 聊天框输入数量 |

---

## 管理员命令

```text
/shop list                            查看所有商店
/shop add <分类> <买价> [数量]         手持物品上架
/shop remove <分类> <商品id>           下架商品
/shop price <分类> <商品id> <价格>     改价
/shop stock <分类> <商品id> <库存>     改库存
/shop info <分类> <商品id>             商品详情
/shop log [玩家名] [页码]              交易记录
/shop log gui [页码]                   交易记录面板
/shop report [today|yesterday|week]   全服日报
/shop reload                          重载配置
/shop reload force                    强制覆盖跨服商店配置
```

---

## 商品配置

最简单的写法：

```yaml
items:
  bread: 1.5
  cooked_beef: 3
  cobblestone: 0.5
```

需要库存 / 限购 / 动态价格时：

```yaml
items:
  diamond: 260 stock=300 limit=16 dynamic=true
  golden_apple: 78 stock=200 limit=3
```

需要折扣 / VIP 价格时：

```yaml
diamond:
  buy: 200
  discount: 0.8
  discount-until: '2026-12-31 23:59'
  vip-price: 150
  vip-permission: xshop.vip
```

自定义物品：

```yaml
ruby:
  custom-item: itemsadder:myitems:ruby
  buy: 100

magic_sword:
  custom-item: nexo:magic_sword
  buy: 500

legendary_blade:
  custom-item: mmoitems:SWORD:LEGENDARY_BLADE
  buy: 2000
```

详细写法见 [商店配置文档](https://github.com/xingxing2024/xingxingshop/wiki/%E7%AE%A1%E7%90%86%E5%91%98%E6%8C%87%E5%8D%97-03-%E5%95%86%E5%BA%97%E9%85%8D%E7%BD%AE)。

---

## 跨服配置

所有服填同一个 MySQL：

```yaml
database:
  type: mysql
  shop-sync: true
  table-prefix: ''
  mysql:
    host: 127.0.0.1
    port: 3306
    database: minecraft
    username: root
    password: ''
    pool-size: 4
    connection-timeout: 5000
```

效果：

- 库存 / 限购 / 交易记录跨服共享
- 多服同时购买不会超卖
- 一服 `/shop reload` 后其他服自动同步商店配置（10 秒内）
- 关闭同步：`shop-sync: false`

---

## 环境要求

| 项目 | 要求 |
|---|---|
| 服务端 | Paper / Purpur 1.20.6 ~ 26.x（26.3 实测通过） |
| Java | **21+** |
| 必需 | Vault + 任意经济插件 |
| 可选 | PlayerPoints、PlaceholderAPI、ItemsAdder、Nexo、MMOItems |

### 版本支持

| 版本 | 状态 |
|---|---|
| Paper / Purpur 1.20.6 ~ 26.x | ✅ 编译基线 |
| Paper / Purpur 26.3 | ✅ 实测 |
| Folia 1.20.6+ | ⚠️ 已做调度兼容，未实机验证 |
| 1.20.5 及更老 | ❌ 不支持 |
| Spigot / CraftBukkit | ❌ 不支持 |

---

## 权限

| 权限 | 默认 | 说明 |
|---|---|---|
| `xshop.use` | true | 打开商店、购买 |
| `xshop.sell` | true | 出售物品 |
| `xshop.admin` | op | 管理命令 |
| `xshop.vip` | false | 商品 VIP 价格示例权限 |

---

## 文档

| 玩家指南 | 管理员指南 |
|---|---|
| [快速上手](docs/玩家指南/01-快速上手.md) | [安装部署](docs/管理员指南/01-安装部署.md) |
| [操作说明](docs/玩家指南/02-操作说明.md) | [配置说明](docs/管理员指南/02-配置说明.md) |
| [价格说明](docs/玩家指南/03-价格说明.md) | [商店配置](docs/管理员指南/03-商店配置.md) |
| [常见问题](docs/玩家指南/04-常见问题.md) | [游戏内管理](docs/管理员指南/04-游戏内管理.md) |
| [个人日报](docs/玩家指南/05-个人日报.md) | [定价指南](docs/管理员指南/05-定价指南.md) |
| | [常见问题](docs/管理员指南/06-常见问题.md) |
| | [交易记录与日报](docs/管理员指南/07-交易记录与日报.md) |
| | [PlaceholderAPI 变量](docs/管理员指南/08-PlaceholderAPI变量.md) |

其它：[指令总览](docs/指令总览.md) · [更新日志](docs/更新日志.md) · [完整 Wiki](https://github.com/xingxing2024/xingxingshop/wiki)

---

## 统计与隐私

bStats 页面：https://bstats.org/plugin/bukkit/xingxingshop/35029

只上报匿名运行数据，不收集玩家名、交易内容或数据库信息。关闭方式：

```yaml
metrics:
  enabled: false
```

---

## 构建

需要 JDK 21：

```bash
mvn clean package
```

产物：

```text
target/XingXingShop-1.0.26.jar
```

---

## 许可

[Apache-2.0](LICENSE)

如果这个插件对你有帮助，欢迎 Star ⭐









