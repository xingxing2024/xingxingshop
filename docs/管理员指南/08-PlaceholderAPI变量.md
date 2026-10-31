# PlaceholderAPI 变量

装了 **PlaceholderAPI** 后，XingXingShop 会自动注册一批变量，标识符是 `xingsxingshop`。

可以放进计分板、Tab、全息、聊天前缀等任何支持 PAPI 的地方。

> 没装 PlaceholderAPI 也能正常用商店，只是这些变量不生效。

---

## 玩家变量

需要玩家上下文（`%xingsxingshop_xxx%` 在计分板 / Tab 里会自动带上当前玩家）。

| 变量 | 说明 |
|---|---|
| `%xingsxingshop_balance%` | 默认货币余额 |
| `%xingsxingshop_balance_vault%` | 指定货币的余额（把 `vault` 换成货币 id） |
| `%xingsxingshop_balance_points%` | PlayerPoints 点券余额 |
| `%xingsxingshop_buy_today%` | 今天买了多少个 |
| `%xingsxingshop_buy_today_times%` | 今天买了多少笔 |
| `%xingsxingshop_buy_today_money%` | 今天花了多少钱 |
| `%xingsxingshop_sell_today%` | 今天卖了多少个 |
| `%xingsxingshop_sell_today_times%` | 今天卖了多少笔 |
| `%xingsxingshop_sell_today_money%` | 今天赚了多少钱 |
| `%xingsxingshop_total_today%` | 今天交易总笔数 |
| `%xingsxingshop_total_today_count%` | 今天交易总件数 |
| `%xingsxingshop_net_today%` | 今天净收入（卖出 − 买入） |

别名（和上面等价）：

- `buy_today_money` = `spent_today`
- `sell_today_money` = `earned_today`

## 全局变量

不需要玩家上下文。

| 变量 | 说明 |
|---|---|
| `%xingsxingshop_global_buy_today%` | 全服今天买入总件数 |
| `%xingsxingshop_global_buy_today_money%` | 全服今天玩家总支出 |
| `%xingsxingshop_global_sell_today%` | 全服今天卖出总件数 |
| `%xingsxingshop_global_sell_today_money%` | 全服今天玩家总收入 |
| `%xingsxingshop_global_total_today%` | 全服今天交易总笔数 |
| `%xingsxingshop_global_total_records%` | 数据库里的有效交易总记录数 |
| `%xingsxingshop_global_net_today%` | 全服今天净流入 |

## 商店变量

| 变量 | 说明 |
|---|---|
| `%xingsxingshop_shops%` | 商店分类数 |
| `%xingsxingshop_items%` | 商品总数 |
| `%xingsxingshop_currencies%` | 可用货币数量 |
| `%xingsxingshop_database%` | 数据库类型（`sqlite` / `mysql` / `none`） |

---

## 说明

- **管理员交易不计入统计**：拥有 `xshop.admin` 权限的玩家，其交易不会算进上面任何「今日 / 全局」变量。
- 变量取的是**当天 0 点到现在**的数据。
- 余额类变量取自对应货币系统，只读展示。

## 示例

计分板：

```yaml
lines:
  - '&6金币：&f%xingsxingshop_balance%'
  - '&a今日买入：&f%xingsxingshop_buy_today% 个'
  - '&c今日支出：&f%xingsxingshop_buy_today_money%'
```

Tab：

```
&7在线商店：&f%xingsxingshop_shops% &7类 &f%xingsxingshop_items% &7件商品
```

---

[返回文档目录](../README.md)
