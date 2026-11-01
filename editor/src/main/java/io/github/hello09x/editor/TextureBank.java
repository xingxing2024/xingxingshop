package io.github.hello09x.editor;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 从本机 Minecraft 客户端 jar 读取原版材质。
 * 不随编辑器分发材质文件，避免版权问题。
 */
public final class TextureBank {

    private static final TextureBank INSTANCE = new TextureBank();
    private static final String[] ROOTS = {
            "assets/minecraft/textures/item/",
            "assets/minecraft/textures/block/"
    };

    private final Map<String, Image> cache = new HashMap<>();
    private final Map<String, ImageIcon> iconCache = new HashMap<>();
    private final Set<String> missing = new HashSet<>();
    private ZipFile zip;
    private Path jarPath;

    private TextureBank() {
    }

    public static TextureBank get() {
        return INSTANCE;
    }

    public Path jarPath() {
        return jarPath;
    }

    public boolean loaded() {
        return zip != null;
    }

    public static Path findClientJar() {
        java.util.List<Path> roots = new java.util.ArrayList<>();
        String appData = System.getenv("APPDATA");
        String home = System.getProperty("user.home");
        if (appData != null) {
            roots.add(Path.of(appData, ".minecraft"));
            roots.add(Path.of(appData, "PrismLauncher", "instances"));
            roots.add(Path.of(appData, "ModrinthApp", "profiles"));
        }
        if (home != null) {
            roots.add(Path.of(home, ".minecraft"));
            roots.add(Path.of(home, "curseforge", "minecraft", "Instances"));
            roots.add(Path.of(home, "curseforge", "minecraft", "Install"));
        }
        java.util.List<Path> candidates = new java.util.ArrayList<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            Path versions = root.resolve("versions");
            if (Files.isDirectory(versions)) {
                collectJars(versions, candidates);
            }
            // 启动器的实例目录：Instances/<实例>/versions/<版本>/<版本>.jar
            try (var walk = Files.walk(root, 5)) {
                walk.filter(Files::isDirectory)
                        .filter(p -> p.getFileName().toString().equals("versions"))
                        .forEach(p -> collectJars(p, candidates));
            } catch (IOException ignored) {
            }
        }
        return candidates.stream()
                .distinct()
                .filter(TextureBank::hasTextures)
                .max(java.util.Comparator.comparingLong(p -> {
                    try {
                        return Files.getLastModifiedTime(p).toMillis();
                    } catch (IOException e) {
                        return 0;
                    }
                }))
                .orElse(null);
    }

    private static void collectJars(Path versions, java.util.List<Path> out) {
        try (var dirs = Files.list(versions)) {
            dirs.filter(Files::isDirectory)
                    .map(dir -> dir.resolve(dir.getFileName().toString() + ".jar"))
                    .filter(Files::isRegularFile)
                    .forEach(out::add);
        } catch (IOException ignored) {
        }
    }

    private static boolean hasTextures(Path jar) {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            return zip.getEntry("assets/minecraft/textures/item/stone.png") != null
                    || zip.getEntry("assets/minecraft/textures/block/stone.png") != null;
        } catch (IOException e) {
            return false;
        }
    }

    public boolean load(Path jar) throws IOException {
        close();
        ZipFile opened = new ZipFile(jar.toFile());
        if (opened.getEntry("assets/minecraft/textures/item/stone.png") == null
                && opened.getEntry("assets/minecraft/textures/block/stone.png") == null) {
            opened.close();
            throw new IOException("这个 jar 里没有 Minecraft 材质：" + jar.getFileName());
        }
        this.zip = opened;
        this.jarPath = jar;
        cache.clear();
        iconCache.clear();
        missing.clear();
        return true;
    }

    public void close() {
        if (zip != null) {
            try {
                zip.close();
            } catch (IOException ignored) {
            }
        }
        zip = null;
    }

    public Image texture(String material) {
        if (zip == null || material == null || material.isBlank()) {
            return null;
        }
        String key = material.toLowerCase(java.util.Locale.ROOT)
                .replace("minecraft:", "")
                .replace(' ', '_');
        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        if (missing.contains(key)) {
            return null;
        }
        for (String root : ROOTS) {
            ZipEntry entry = zip.getEntry(root + key + ".png");
            if (entry != null) {
                try (var in = zip.getInputStream(entry)) {
                    BufferedImage image = javax.imageio.ImageIO.read(in);
                    if (image != null) {
                        cache.put(key, image);
                        return image;
                    }
                } catch (IOException ignored) {
                }
            }
        }
        missing.add(key);
        return null;
    }

    public ImageIcon icon(String material, int size) {
        Image texture = texture(material);
        if (texture == null) {
            return null;
        }
        String key = material.toLowerCase(java.util.Locale.ROOT) + "@" + size;
        return iconCache.computeIfAbsent(key, k -> {
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(texture, 0, 0, size, size, null);
            g.dispose();
            return new ImageIcon(image);
        });
    }
}
