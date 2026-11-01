package io.github.hello09x.editor;

import javax.swing.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class EditorMain {

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("--selftest")) {
            System.exit(SelfTest.run());
        }
        if (args.length > 1 && args[0].equalsIgnoreCase("--validate")) {
            System.exit(validate(Path.of(args[1])));
        }
        if (args.length > 1 && args[0].equalsIgnoreCase("--preview")) {
            try {
                McTheme.install();
                ThemePreview.render(Path.of(args[1]));
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        McTheme.install();
        SwingUtilities.invokeLater(() -> new EditorFrame().setVisible(true));
    }

    private static int validate(Path folder) {
        if (!Files.isDirectory(folder)) {
            System.out.println("Not a folder: " + folder);
            return 1;
        }
        int shops = 0;
        int items = 0;
        int errors = 0;
        try (var stream = Files.list(folder)) {
            for (Path file : stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".yml"))
                    .sorted().toList()) {
                try {
                    YamlShopFile doc = new YamlShopFile(file);
                    ShopData shop = doc.readShop();
                    int count = doc.readItems(shop.selling()).size();
                    shops++;
                    items += count;
                    System.out.println("OK   " + file.getFileName() + "  " + shop.id + "  items=" + count);
                } catch (Throwable t) {
                    errors++;
                    System.out.println("FAIL " + file.getFileName() + "  " + t.getMessage());
                }
            }
        } catch (Exception e) {
            System.out.println("Read folder failed: " + e.getMessage());
            return 1;
        }
        System.out.println("VALIDATE shops=" + shops + " items=" + items + " errors=" + errors);
        return errors == 0 ? 0 : 1;
    }
}
