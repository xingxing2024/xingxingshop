package io.github.hello09x.shop.util;

import io.github.hello09x.shop.ShopPlugin;
import io.github.hello09x.shop.currency.Currency;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 语言文件（{@code lang.yml}）。
 *
 * <p>键缺失时自动回退到 jar 内置默认值。</p>
 */
public final class Messages {

    private final ShopPlugin plugin;
    private FileConfiguration lang;
    private FileConfiguration defaults;

    public Messages(@NotNull ShopPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "lang.yml");
        if (!file.exists()) {
            plugin.saveResource("lang.yml", false);
        }
        this.lang = YamlConfiguration.loadConfiguration(file);
        try (var in = plugin.getResource("lang.yml")) {
            this.defaults = in == null ? null
                    : YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            this.defaults = null;
        }
    }

    public @NotNull String raw(@NotNull String key) {
        String v = lang.getString(key);
        if (v == null && defaults != null) {
            v = defaults.getString(key);
        }
        return v == null ? key : v;
    }

    public @NotNull String prefix() {
        return Text.color(raw("prefix"));
    }

    /** 带占位符的消息 */
    public void send(@NotNull CommandSender to, @NotNull String key, @Nullable Object... args) {
        String body = Text.replace(raw(key), args);
        if (body.isEmpty()) {
            return;
        }
        to.sendMessage(prefix() + body);
    }

    /** 金额显示 */
    public static @NotNull String money(@Nullable Currency currency, double amount) {
        return currency == null ? Currency.formatNumber(amount) : currency.format(amount);
    }

    /** 命名占位符消息 */
    public void sendNamed(@NotNull CommandSender to, @NotNull String key, @NotNull Map<String, ?> values) {
        to.sendMessage(prefix() + Text.replaceNamed(raw(key), values));
    }

}
