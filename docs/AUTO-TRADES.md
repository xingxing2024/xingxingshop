# TrMenu 菜单收支记录

XingXingShop 启动或 `/shop reload` 时只读扫描 TrMenu 菜单配置。含 `take-money` 或 `give-money` 的按钮会被监控。玩家点击按钮后，如果 Vault 余额发生变化，就用该按钮的显示名称记录交易，并显示在交易记录和个人日报中；没有填写显示名称的农作物按钮会根据图标显示中文作物名。

从 1.0.3 起监听 TrMenu 的 `ReceptacleInteractEvent` 内部事件，兼容数据包菜单和静态背包菜单。1.0.2 仅监听 Bukkit 背包点击，数据包菜单的收支会漏记。数据包点击会先将余额读取排入服务器主线程，再由 TrMenu 执行按钮动作；重载或停用时会注销内部监听，避免重复记录。

该模式只关联玩家点击的按钮和随后 5 秒内的 Vault 余额变化，不核实商品是否发货；若这段时间另有 Vault 转账，记录金额可能受到影响。需要 TrMenu、Vault 与 Vault 经济服务正常运行。

配置 `external-trades.trmenu.enabled: false` 可关闭。不要对同一笔菜单交易再执行 `/shop record`，避免重复统计。只修改 XingXingShop 源项目，没有部署或更改服务器配置。

此修复沿用现有配置，不新增配置键。测试使用 TrMenu 自身的 WindowListener 点击入口，未手动注入 Bukkit 点击事件或直接调用按钮动作。仓库菜单测试检查“中仓库”按钮扣款 500 后写入交易记录和中文个人日报；测试服没有安装正式服的仓库插件，不验证仓库兑换券发放。
