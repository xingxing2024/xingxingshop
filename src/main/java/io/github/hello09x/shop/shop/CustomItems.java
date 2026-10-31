package io.github.hello09x.shop.shop;

import io.github.hello09x.shop.ShopPlugin;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自定义物品解析。
 *
 * <p>支持三种来源，配置里写：</p>
 * <pre>
 *   custom-item: itemsadder:命名空间:物品ID
 *   custom-item: nexo:物品ID
 *   custom-item: mmoitems:类型:物品ID
 * </pre>
 *
 * <p>通过反射调用对应插件的 API，没装对应插件时只打印一次警告。</p>
 */
public final class CustomItems {

    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private CustomItems() {
    }

    public static @Nullable ItemStack resolve(@NotNull String raw) {
        String value = raw.trim();
        int split = value.indexOf(':');
        if (split <= 0) {
            warn(value, "格式应为 itemsadder:xxx / nexo:xxx / mmoitems:类型:ID");
            return null;
        }
        String provider = value.substring(0, split).toLowerCase(Locale.ROOT);
        String id = value.substring(split + 1).trim();
        if (id.isEmpty()) {
            warn(value, "物品 ID 为空");
            return null;
        }
        try {
            ItemStack result = switch (provider) {
                case "itemsadder", "ia" -> itemsAdder(id);
                case "nexo" -> nexo(id);
                case "mmoitems", "mmo" -> mmoItems(id);
                default -> {
                    warn(value, "不支持的自定义物品来源：" + provider);
                    yield null;
                }
            };
            if (result == null || result.getType() == Material.AIR) {
                return null;
            }
            return result;
        } catch (ClassNotFoundException e) {
            warn(provider, "没有安装对应的自定义物品插件");
            return null;
        } catch (Throwable t) {
            warn(value, "解析失败：" + t.getMessage());
            return null;
        }
    }

    private static @Nullable ItemStack itemsAdder(@NotNull String id) throws Exception {
        Class<?> stackClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
        Method getInstance = stackClass.getMethod("getInstance", String.class);
        Object custom = getInstance.invoke(null, id);
        if (custom == null) {
            return null;
        }
        Object item = stackClass.getMethod("getItemStack").invoke(custom);
        return item instanceof ItemStack stack ? stack.clone() : null;
    }

    private static @Nullable ItemStack nexo(@NotNull String id) throws Exception {
        Class<?> api = Class.forName("com.nexomc.nexo.api.NexoItems");
        Object custom = api.getMethod("itemFromId", String.class).invoke(null, id);
        if (custom == null) {
            return null;
        }
        Object item;
        try {
            Class<?> itemClass = Class.forName("com.nexomc.nexo.items.NexoItem");
            item = itemClass.getMethod("build").invoke(custom);
        } catch (Throwable ignored) {
            item = custom.getClass().getMethod("build").invoke(custom);
        }
        return item instanceof ItemStack stack ? stack.clone() : null;
    }

    private static @Nullable ItemStack mmoItems(@NotNull String id) throws Exception {
        String[] parts = id.split(":", 2);
        if (parts.length < 2) {
            warn("mmoitems:" + id, "格式应为 mmoitems:类型:物品ID");
            return null;
        }
        String typeName = parts[0].trim().toUpperCase(Locale.ROOT);
        String itemId = parts[1].trim();

        Class<?> typeClass = Class.forName("net.Indyuce.mmoitems.api.ItemType");
        Class<?> mmoClass = Class.forName("net.Indyuce.mmoitems.MMOItems");

        Object mmo = null;
        Object type = null;
        try {
            mmo = mmoClass.getField("plugin").get(null);
            Object types = mmoClass.getMethod("getTypes").invoke(mmo);
            if (types instanceof java.util.Map<?, ?> map) {
                type = map.get(typeName);
            }
        } catch (Throwable ignored) {
            // 旧版没有 plugin / getTypes，下面走静态方法
        }
        if (type == null) {
            try {
                type = typeClass.getMethod("get", String.class).invoke(null, typeName);
            } catch (Throwable ignored) {
                // 再试枚举
            }
        }
        if (type == null && typeClass.isEnum()) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object value = Enum.valueOf((Class<? extends Enum>) typeClass, typeName);
            type = value;
        }
        if (type == null) {
            warn("mmoitems:" + id, "未知物品类型：" + typeName);
            return null;
        }

        Object item = null;
        if (mmo != null) {
            try {
                item = mmoClass.getMethod("getItem", typeClass, String.class)
                        .invoke(mmo, type, itemId);
            } catch (Throwable ignored) {
                // 某些版本是静态方法
            }
        }
        if (item == null) {
            item = mmoClass.getMethod("getItem", typeClass, String.class)
                    .invoke(null, type, itemId);
        }
        return item instanceof ItemStack stack ? stack.clone() : null;
    }

    private static void warn(@NotNull String key, @NotNull String message) {
        if (!WARNED.add(key)) {
            return;
        }
        ShopPlugin plugin = ShopPlugin.getInstance();
        if (plugin != null) {
            plugin.getLogger().warning("[自定义物品] " + message + "（" + key + "）");
        }
    }

}
