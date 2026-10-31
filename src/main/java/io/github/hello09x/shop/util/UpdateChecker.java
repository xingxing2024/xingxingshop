package io.github.hello09x.shop.util;

import io.github.hello09x.shop.ShopPlugin;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 检查 GitHub Releases 是否有新版本。失败时静默，不影响开服。
 */
public final class UpdateChecker {

    private static final String API =
            "https://api.github.com/repos/xingxing2024/xingxingshop/releases/latest";
    private static final Pattern TAG = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");

    private UpdateChecker() {
    }

    public static void checkAsync(@NotNull ShopPlugin plugin) {
        if (!plugin.config().updateCheck()) {
            return;
        }
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(API))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "XingXingShop-UpdateChecker")
                .GET()
                .build();
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() != 200) {
                        return;
                    }
                    Matcher matcher = TAG.matcher(response.body());
                    if (!matcher.find()) {
                        return;
                    }
                    String latest = matcher.group(1).replaceFirst("^[vV]", "");
                    String current = plugin.getPluginMeta().getVersion();
                    if (newer(latest, current)) {
                        plugin.getLogger().warning("发现新版本：" + latest + "（当前 " + current
                                + "） https://github.com/xingxing2024/xingxingshop/releases");
                    }
                })
                .exceptionally(t -> null);
    }

    private static boolean newer(String latest, String current) {
        String[] a = latest.split("[^0-9]+");
        String[] b = current.split("[^0-9]+");
        int n = Math.max(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int x = i < a.length ? parseInt(a[i]) : 0;
            int y = i < b.length ? parseInt(b[i]) : 0;
            if (x != y) {
                return x > y;
            }
        }
        return false;
    }

    private static int parseInt(String raw) {
        try {
            return raw.isEmpty() ? 0 : Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

}
