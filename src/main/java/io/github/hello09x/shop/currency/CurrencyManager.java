package io.github.hello09x.shop.currency;

import io.github.hello09x.shop.ShopPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 货币注册表。
 *
 * <p>内置两种，外加 config.yml 里自定义的：</p>
 * <ul>
 *     <li>{@code vault} —— Vault 经济</li>
 *     <li>{@code points} —— PlayerPoints 点券</li>
 * </ul>
 */
public final class CurrencyManager {

    private final ShopPlugin plugin;
    private final Map<String, Currency> currencies = new LinkedHashMap<>();

    public CurrencyManager(@NotNull ShopPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 重新探测所有货币
     */
    public void detect() {
        currencies.clear();

        var vault = new VaultCurrency("vault", "金币");
        if (vault.detect()) {
            currencies.put("vault", vault);
            plugin.getLogger().info("已接入 Vault 经济");
        }

        var points = new PlayerPointsCurrency("points", "点券");
        if (points.detect()) {
            currencies.put("points", points);
            plugin.getLogger().info("已接入 PlayerPoints 点券");
        }

        // 自定义（PAPI 变量，只读）
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("custom-currency");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection c = section.getConfigurationSection(id);
                if (c == null) {
                    continue;
                }
                var currency = new PlaceholderCurrency(
                        id.toLowerCase(Locale.ROOT),
                        c.getString("display-name", id),
                        c.getString("placeholder", ""),
                        false,
                        1D
                );
                if (currency.detect()) {
                    currencies.put(currency.id(), currency);
                    plugin.getLogger().info("已接入自定义货币 " + id);
                }
            }
        }
    }

    public @Nullable Currency get(@Nullable String id) {
        return id == null ? null : currencies.get(id.toLowerCase(Locale.ROOT));
    }

    /**
     * 严格解析商品指定的货币：写了 id 但货币不可用就返回 null，不再静默换成别的货币。
     */
    public @Nullable Currency resolve(@Nullable String id) {
        if (id == null) {
            return getOrDefault(null);
        }
        Currency c = get(id);
        return c != null && c.available() ? c : null;
    }

    /**
     * 取一个可用货币；找不到就依次回退到默认货币、任意可用货币。
     *
     * @return {@code null} 表示当前服务器没有任何可用货币
     */
    public @Nullable Currency getOrDefault(@Nullable String id) {
        Currency c = get(id);
        if (c != null && c.available()) {
            return c;
        }
        c = get(plugin.config().currencyDefault());
        if (c != null && c.available()) {
            return c;
        }
        for (Currency value : currencies.values()) {
            if (value.available()) {
                return value;
            }
        }
        return null;
    }

    public boolean hasAnyAvailable() {
        return currencies.values().stream().anyMatch(Currency::available);
    }

    public @NotNull Map<String, Currency> all() {
        return currencies;
    }

    /**
     * 是否装了 Vault 插件（不论有没有经济实现）
     */
    public boolean hasVaultPlugin() {
        return Bukkit.getPluginManager().getPlugin("Vault") != null;
    }

}
