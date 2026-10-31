package io.github.hello09x.shop.log;

import io.github.hello09x.shop.ShopPlugin;
import org.jetbrains.annotations.NotNull;

import javax.sql.DataSource;
import java.io.File;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * JDBC 驱动加载器：驱动不打进插件，首次启动从阿里云 Maven 镜像下载。
 */
final class DriverLoader implements AutoCloseable {

    private static final String SQLITE_PATH =
            "org/xerial/sqlite-jdbc/3.53.0.0/sqlite-jdbc-3.53.0.0.jar";
    private static final String MYSQL_PATH =
            "com/mysql/mysql-connector-j/9.6.0/mysql-connector-j-9.6.0.jar";
    private static final String PROTOBUF_PATH =
            "com/google/protobuf/protobuf-java/4.31.1/protobuf-java-4.31.1.jar";

    private final URLClassLoader loader;
    private final Driver driver;

    private DriverLoader(URLClassLoader loader, Driver driver) {
        this.loader = loader;
        this.driver = driver;
    }

    static @NotNull DriverLoader load(@NotNull ShopPlugin plugin, boolean mysql) throws Exception {
        var cfg = plugin.config();
        List<File> jars = new ArrayList<>();
        if (mysql) {
            jars.add(download(plugin, "mysql-connector-j-9.6.0.jar", MYSQL_PATH));
            jars.add(download(plugin, "protobuf-java-4.31.1.jar", PROTOBUF_PATH));
        } else {
            jars.add(download(plugin, "sqlite-jdbc-3.53.0.0.jar", SQLITE_PATH));
        }
        URL[] urls = new URL[jars.size()];
        for (int i = 0; i < jars.size(); i++) {
            urls[i] = jars.get(i).toURI().toURL();
        }
        URLClassLoader loader = new URLClassLoader(urls, DriverLoader.class.getClassLoader());
        try {
            Class<?> clazz = Class.forName(mysql ? "com.mysql.cj.jdbc.Driver" : "org.sqlite.JDBC", true, loader);
            Driver driver = (Driver) clazz.getDeclaredConstructor().newInstance();
            return new DriverLoader(loader, driver);
        } catch (Throwable t) {
            loader.close();
            throw new IllegalStateException("JDBC 驱动加载失败：" + t.getMessage(), t);
        }
    }

    Driver driver() {
        return driver;
    }

    private static File download(ShopPlugin plugin, String fileName, String repoPath) throws Exception {
        File dir = new File(plugin.getDataFolder(), "lib");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("无法创建驱动目录：" + dir.getAbsolutePath());
        }
        File target = new File(dir, fileName);
        if (target.isFile() && target.length() > 1024) {
            return target;
        }
        if (!plugin.config().dbDriverDownload()) {
            throw new IllegalStateException("缺少 JDBC 驱动 " + fileName + "，且已关闭自动下载");
        }
        String repo = plugin.config().dbDriverRepo();
        String base = repo.endsWith("/") ? repo.substring(0, repo.length() - 1) : repo;
        URI uri = URI.create(base + "/" + repoPath);
        plugin.getLogger().info("正在从阿里云下载数据库驱动：" + fileName);
        Path temp = new File(dir, fileName + ".tmp").toPath();
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", "XingXingShop-DriverLoader")
                .GET()
                .build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("下载 " + fileName + " 失败，HTTP " + response.statusCode());
        }
        try (InputStream in = response.body()) {
            Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
        }
        if (Files.size(temp) < 1024) {
            Files.deleteIfExists(temp);
            throw new IllegalStateException("下载的驱动文件无效：" + fileName);
        }
        Files.move(temp, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    @Override
    public void close() {
        try {
            loader.close();
        } catch (Exception ignored) {
            // 忽略
        }
    }

    /**
     * Hikari 用的 DataSource，直接调用下载驱动的 connect，不依赖 DriverManager。
     */
    record DriverDataSource(Driver driver, String url, Properties properties) implements DataSource {

        @Override
        public Connection getConnection() throws SQLException {
            Connection connection = driver.connect(url, properties);
            if (connection == null) {
                throw new SQLException("JDBC 驱动拒绝连接：" + url);
            }
            return connection;
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            Properties copy = new Properties();
            copy.putAll(properties);
            if (username != null) copy.setProperty("user", username);
            if (password != null) copy.setProperty("password", password);
            Connection connection = driver.connect(url, copy);
            if (connection == null) {
                throw new SQLException("JDBC 驱动拒绝连接：" + url);
            }
            return connection;
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) return iface.cast(this);
            throw new SQLException("不支持 unwrap");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return iface.isInstance(this);
        }
    }
}
