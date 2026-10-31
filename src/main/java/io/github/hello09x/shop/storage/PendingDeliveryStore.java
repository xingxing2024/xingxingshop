package io.github.hello09x.shop.storage;

import io.github.hello09x.shop.ShopPlugin;
import io.github.hello09x.shop.util.Text;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家离线时没发出去的商品。
 * 交易结果回来时玩家已经下线，就先进这里，上线后补发。
 */
public final class PendingDeliveryStore {

    private final ShopPlugin plugin;
    private final File file;
    private final Map<UUID, List<ItemStack>> data = new LinkedHashMap<>();

    public PendingDeliveryStore(@NotNull ShopPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "pending-delivery.yml");
    }

    public synchronized void load() {
        data.clear();
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String raw : yaml.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(raw);
                List<ItemStack> stacks = new ArrayList<>();
                for (String encoded : yaml.getStringList(raw)) {
                    ItemStack stack = decode(encoded);
                    if (stack != null) {
                        stacks.add(stack);
                    }
                }
                if (!stacks.isEmpty()) {
                    data.put(uuid, stacks);
                }
            } catch (IllegalArgumentException ignored) {
                // 坏数据跳过
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (var entry : data.entrySet()) {
            List<String> encoded = new ArrayList<>();
            for (ItemStack stack : entry.getValue()) {
                String value = encode(stack);
                if (value != null) {
                    encoded.add(value);
                }
            }
            if (!encoded.isEmpty()) {
                yaml.set(entry.getKey().toString(), encoded);
            }
        }
        try {
            java.nio.file.Path target = file.toPath();
            java.nio.file.Path temp = target.resolveSibling(file.getName() + ".tmp");
            yaml.save(temp.toFile());
            java.nio.file.Files.move(temp, target,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            plugin.getLogger().warning("保存 pending-delivery.yml 失败：" + e.getMessage());
        }
    }

    public synchronized void add(@NotNull UUID uuid, @NotNull ItemStack stack) {
        if (stack.getType().isAir() || stack.getAmount() <= 0) {
            return;
        }
        data.computeIfAbsent(uuid, k -> new ArrayList<>()).add(stack.clone());
        save();
    }

    /** 玩家上线时补发，返回补发的件数 */
    public synchronized int deliver(@NotNull Player player) {
        List<ItemStack> stacks = data.remove(player.getUniqueId());
        if (stacks == null || stacks.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (ItemStack stack : stacks) {
            var leftover = player.getInventory().addItem(stack);
            for (ItemStack rest : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rest);
            }
            count += stack.getAmount();
        }
        save();
        player.sendMessage(Text.color("&a[商店] &7补发了离线期间未领取的 &f" + count + " &7个物品"));
        return count;
    }

    public synchronized boolean has(@NotNull UUID uuid) {
        List<ItemStack> stacks = data.get(uuid);
        return stacks != null && !stacks.isEmpty();
    }

    private static String encode(ItemStack stack) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (var out = new org.bukkit.util.io.BukkitObjectOutputStream(bytes)) {
                out.writeObject(stack);
            }
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (Exception e) {
            return null;
        }
    }

    private static ItemStack decode(String encoded) {
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            try (var in = new org.bukkit.util.io.BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {
                Object object = in.readObject();
                return object instanceof ItemStack stack ? stack : null;
            }
        } catch (Exception e) {
            return null;
        }
    }
}
