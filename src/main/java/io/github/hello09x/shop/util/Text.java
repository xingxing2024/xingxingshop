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

    /**
     * 生成 1.16+ 支持的十六进制渐变文字。
     *
     * @param text 纯文本，不要带颜色码
     * @param from 起始颜色，如 {@code 7DE2FF}
     * @param to   结束颜色，如 {@code 9B8CFF}
     */
    public static @NotNull String gradient(@Nullable String text, @NotNull String from, @NotNull String to) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        int[] a = parseHex(from);
        int[] b = parseHex(to);
        int steps = Math.max(1, text.length() - 1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            double t = (double) i / steps;
            int r = (int) Math.round(a[0] + (b[0] - a[0]) * t);
            int g = (int) Math.round(a[1] + (b[1] - a[1]) * t);
            int bl = (int) Math.round(a[2] + (b[2] - a[2]) * t);
            out.append(String.format("§x§%X§%X§%X§%X§%X§%X",
                    (r >> 4) & 0xF, r & 0xF, (g >> 4) & 0xF, g & 0xF, (bl >> 4) & 0xF, bl & 0xF));
            out.append(text.charAt(i));
        }
        return out.toString();
    }

    private static int[] parseHex(String hex) {
        try {
            int value = Integer.parseInt(hex.replace("#", ""), 16);
            return new int[]{(value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF};
        } catch (NumberFormatException e) {
            return new int[]{255, 255, 255};
        }
    }

}
