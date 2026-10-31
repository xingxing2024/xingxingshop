package io.github.hello09x.shop.util;

import org.bukkit.ChatColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * 文本工具：{@code &} 颜色码 + {@code {key}} 占位符。
 */
public final class Text {

    private Text() {
    }

    public static @NotNull String color(@Nullable String raw) {
        return raw == null ? "" : ChatColor.translateAlternateColorCodes('&', raw);
    }

    /**
     * 去掉所有颜色码，得到纯文本（用于消息里显示物品名）
     */
    public static @NotNull String strip(@Nullable String raw) {
        if (raw == null) {
            return "";
        }
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', raw));
    }

    public static @NotNull String replace(@NotNull String raw, @Nullable Object... args) {
        String result = raw;
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                result = result.replace("{" + i + "}", args[i] == null ? "" : String.valueOf(args[i]));
            }
        }
        return color(result);
    }

    public static @NotNull String replaceNamed(@NotNull String raw, @NotNull Map<String, ?> values) {
        String result = raw;
        for (var e : values.entrySet()) {
            result = result.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : String.valueOf(e.getValue()));
        }
        return color(result);
    }

}
