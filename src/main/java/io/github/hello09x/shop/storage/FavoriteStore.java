package io.github.hello09x.shop.storage;

import io.github.hello09x.shop.ShopPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家收藏夹。按服务器本地文件保存，结构简单，重启不丢。
 */
public final class FavoriteStore {

    private final ShopPlugin plugin;
    private final File file;
    private final Map<UUID, Set<String>> data = new ConcurrentHashMap<>();

    public FavoriteStore(@NotNull ShopPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "favorites.yml");
    }

    public void load() {
        data.clear();
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String raw : yaml.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(raw);
                Set<String> set = ConcurrentHashMap.newKeySet();
                set.addAll(yaml.getStringList(raw));
                data.put(uuid, set);
            } catch (IllegalArgumentException ignored) {
                // 忽略损坏的 UUID
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (var entry : data.entrySet()) {
            yaml.set(entry.getKey().toString(), new ArrayList<>(entry.getValue()));
        }
        try {
            yaml.save(file);
        } catch (Exception e) {
            plugin.getLogger().warning("保存 favorites.yml 失败：" + e.getMessage());
        }
    }

    public boolean add(@NotNull UUID player, @NotNull String itemKey) {
        boolean added = data.computeIfAbsent(player, k -> ConcurrentHashMap.newKeySet()).add(itemKey);
        if (added) {
            save();
        }
        return added;
    }

    public boolean remove(@NotNull UUID player, @NotNull String itemKey) {
        Set<String> set = data.get(player);
        boolean removed = set != null && set.remove(itemKey);
        if (removed) {
            save();
        }
        return removed;
    }

    public boolean contains(@NotNull UUID player, @NotNull String itemKey) {
        Set<String> set = data.get(player);
        return set != null && set.contains(itemKey);
    }

    public @NotNull List<String> list(@NotNull UUID player) {
        Set<String> set = data.get(player);
        return set == null ? List.of() : new ArrayList<>(set);
    }

}
