package io.github.hello09x.shop.storage;

import io.github.hello09x.shop.ShopPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 跨服商店配置同步。
 *
 * <p>所有服务器共用一个 MySQL：任意一服执行 {@code /shop reload} 会把本地
 * {@code shops/*.yml} 上传到数据库，其他服每 10 秒检查一次并自动拉取。</p>
 */
public final class ShopSync {

    private final ShopPlugin plugin;
    private volatile long lastVersion;
    private volatile boolean pulled;

    public ShopSync(@NotNull ShopPlugin plugin) {
        this.plugin = plugin;
        this.lastVersion = plugin.transactionLog().database().loadShopConfigVersion();
    }

    public boolean enabled() {
        return plugin.config().shopSync() && plugin.transactionLog().database().shared();
    }

    /** 启动时拉取；返回是否拉到远程配置 */
    public boolean pull() {
        if (!enabled()) {
            return false;
        }
        Map<String, String> configs = plugin.transactionLog().database().loadShopConfigs();
        if (configs.isEmpty()) {
            lastVersion = 0;
            return false;
        }
        write(configs);
        lastVersion = plugin.transactionLog().database().loadShopConfigVersion();
        pulled = true;
        plugin.getLogger().info("已从数据库同步 " + configs.size() + " 个商店配置");
        return true;
    }

    /** 管理员 /shop reload 后上传本地配置 */
    public void push() {
        push(false);
    }

    /**
     * 上传本地配置。
     *
     * @param force true = 覆盖其他服刚上传的版本
     */
    public boolean push(boolean force) {
        if (!enabled()) {
            return false;
        }
        long remote = plugin.transactionLog().database().loadShopConfigVersion();
        if (!force && remote > lastVersion) {
            plugin.getLogger().warning("商店配置冲突：其他服已经更新过配置。"
                    + "本次上传已取消，确认要覆盖请使用 /shop reload force");
            return false;
        }
        Map<String, String> configs = read();
        if (configs.isEmpty()) {
            return false;
        }
        lastVersion = Math.max(System.currentTimeMillis(), remote + 1);
        plugin.transactionLog().database().replaceShopConfigs(configs, lastVersion);
        pulled = true;
        return true;
    }

    /** 轮询远程版本；有新版本就写回本地文件 */
    public boolean poll() {
        if (!enabled()) {
            return false;
        }
        long remote = plugin.transactionLog().database().loadShopConfigVersion();
        if (remote <= lastVersion) {
            return false;
        }
        Map<String, String> configs = plugin.transactionLog().database().loadShopConfigs();
        if (configs.isEmpty()) {
            return false;
        }
        write(configs);
        lastVersion = remote;
        plugin.getLogger().info("检测到其他服的商店配置更新，已自动同步");
        return true;
    }

    public boolean hasPulled() {
        return pulled;
    }

    private @NotNull Map<String, String> read() {
        Map<String, String> out = new HashMap<>();
        File[] files = folder().listFiles((d, n) -> n.toLowerCase(java.util.Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            return out;
        }
        for (File file : files) {
            try {
                out.put(file.getName(), Files.readString(file.toPath(), StandardCharsets.UTF_8));
            } catch (Exception e) {
                plugin.getLogger().warning("读取商店配置 " + file.getName() + " 失败：" + e.getMessage());
            }
        }
        return out;
    }

    private File folder() {
        return new File(plugin.getDataFolder(), "shops");
    }

    private void write(@NotNull Map<String, String> configs) {
        File folder = folder();
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("无法创建商店目录：" + folder.getAbsolutePath());
            return;
        }
        try {
            for (var entry : configs.entrySet()) {
                Path target = new File(folder, entry.getKey()).toPath();
                String old = Files.isRegularFile(target)
                        ? Files.readString(target, StandardCharsets.UTF_8) : null;
                if (!entry.getValue().equals(old)) {
                    Files.writeString(target, entry.getValue(), StandardCharsets.UTF_8);
                }
            }
            File[] local = folder.listFiles((d, n) -> n.toLowerCase(java.util.Locale.ROOT).endsWith(".yml"));
            if (local != null) {
                for (File file : local) {
                    if (!configs.containsKey(file.getName())) {
                        Files.deleteIfExists(file.toPath());
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("写入商店配置失败：" + e.getMessage());
        }
    }

}
