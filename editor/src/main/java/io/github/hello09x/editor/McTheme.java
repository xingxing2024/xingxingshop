package io.github.hello09x.editor;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Random;

/** Minecraft 风格的界面主题。 */
public final class McTheme {

    public static final Color BG = new Color(0xC6C6C6);
    public static final Color PANEL = new Color(0xC6C6C6);
    public static final Color PANEL_LIGHT = new Color(0xD8D8D8);
    public static final Color SLOT = new Color(0x8B8B8B);
    public static final Color TEXT = new Color(0x303030);
    public static final Color MUTED = new Color(0x5A5A5A);
    public static final Color GOLD = new Color(0x7A4A00);
    public static final Color GREEN = new Color(0x2E7D32);
    public static final Color RED = new Color(0xA02020);
    public static final Color AQUA = new Color(0x1F6F8B);
    public static final Color BORDER_LIGHT = new Color(0xFFFFFF);
    public static final Color BORDER_DARK = new Color(0x373737);

    public static final Font FONT = new Font(pickFont("Microsoft YaHei UI", "Microsoft YaHei", "SimHei",
            "Noto Sans CJK SC", "Dialog"), Font.PLAIN, 13);
    public static final Font FONT_BOLD = new Font(FONT.getFamily(), Font.BOLD, 13);
    public static final Font TITLE = new Font(FONT.getFamily(), Font.BOLD, 20);
    public static final Font MONO = new Font(pickFont("Consolas", "Monospaced"), Font.PLAIN, 13);

    private static String pickFont(String... candidates) {
        try {
            var available = java.util.Set.of(GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getAvailableFontFamilyNames());
            for (String candidate : candidates) {
                if (available.contains(candidate)) {
                    return candidate;
                }
            }
        } catch (Throwable ignored) {
        }
        return "Dialog";
    }

    private static TexturePaint texture;

    private McTheme() {
    }

    public static void install() {
        UIManager.put("Panel.background", BG);
        UIManager.put("Viewport.background", BG);
        UIManager.put("ScrollPane.background", BG);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("Label.font", FONT);
        UIManager.put("Button.font", FONT_BOLD);
        UIManager.put("Button.foreground", Color.WHITE);
        UIManager.put("TextField.font", MONO);
        UIManager.put("TextField.background", SLOT);
        UIManager.put("TextField.foreground", TEXT);
        UIManager.put("TextField.caretForeground", GREEN);
        UIManager.put("TextField.selectionBackground", new Color(0x2F5A2F));
        UIManager.put("TextArea.font", MONO);
        UIManager.put("TextArea.background", SLOT);
        UIManager.put("TextArea.foreground", TEXT);
        UIManager.put("TextArea.caretForeground", GREEN);
        UIManager.put("TextArea.selectionBackground", new Color(0x2F5A2F));
        UIManager.put("ComboBox.background", SLOT);
        UIManager.put("ComboBox.foreground", TEXT);
        UIManager.put("ComboBox.font", FONT);
        UIManager.put("CheckBox.background", BG);
        UIManager.put("CheckBox.foreground", TEXT);
        UIManager.put("CheckBox.font", FONT);
        UIManager.put("Table.font", FONT);
        UIManager.put("Table.background", PANEL);
        UIManager.put("Table.foreground", TEXT);
        UIManager.put("Table.gridColor", new Color(0x9A9A9A));
        UIManager.put("Table.selectionBackground", new Color(0x5A8A3A));
        UIManager.put("Table.selectionForeground", Color.WHITE);
        UIManager.put("TableHeader.background", PANEL_LIGHT);
        UIManager.put("TableHeader.foreground", GOLD);
        UIManager.put("TableHeader.font", FONT_BOLD);
        UIManager.put("List.background", BG);
        UIManager.put("List.foreground", TEXT);
        UIManager.put("List.font", FONT);
        UIManager.put("List.selectionBackground", new Color(0x5A8A3A));
        UIManager.put("List.selectionForeground", Color.WHITE);
        UIManager.put("ScrollBar.background", PANEL);
        UIManager.put("ScrollBar.thumb", PANEL_LIGHT);
        UIManager.put("ScrollBar.track", BG);
        UIManager.put("OptionPane.background", BG);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("OptionPane.messageFont", FONT);
        UIManager.put("OptionPane.buttonFont", FONT_BOLD);
        UIManager.put("ToolTip.background", new Color(0x100010));
        UIManager.put("ToolTip.foreground", TEXT);
        UIManager.put("ToolTip.font", FONT);
    }

    public static JPanel panel(LayoutManager layout) {
        JPanel panel = new McPanel();
        panel.setLayout(layout);
        panel.setOpaque(false);
        return panel;
    }

    public static JPanel card() {
        JPanel panel = panel(new BorderLayout());
        panel.setBorder(bevel(8, 10));
        return panel;
    }

    public static JButton button(String text) {
        JButton button = new JButton(text);
        button.setUI(new McButtonUI());
        button.setFont(FONT_BOLD);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(7, 14, 7, 14));
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    public static JLabel title(String text) {
        JLabel label = new JLabel(text);
        label.setFont(TITLE);
        label.setForeground(GOLD);
        return label;
    }

    public static JLabel label(String text, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(FONT);
        label.setForeground(color);
        return label;
    }

    public static void field(JTextField field) {
        field.setFont(MONO);
        field.setBackground(SLOT);
        field.setForeground(TEXT);
        field.setCaretColor(GREEN);
        field.setBorder(slotBorder());
    }

    public static void area(JTextArea area) {
        area.setFont(MONO);
        area.setBackground(SLOT);
        area.setForeground(TEXT);
        area.setCaretColor(GREEN);
        area.setBorder(new EmptyBorder(4, 6, 4, 6));
    }

    public static void table(JTable table) {
        table.setBackground(PANEL);
        table.setForeground(TEXT);
        table.setGridColor(new Color(0x9A9A9A));
        table.setShowGrid(true);
        table.setRowHeight(28);
        table.setSelectionBackground(new Color(0x5A8A3A));
        table.setSelectionForeground(Color.WHITE);
        table.setFont(FONT);
        table.setBorder(new EmptyBorder(0, 0, 0, 0));
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                           boolean focus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(t, value, selected, focus, row, column);
                label.setBorder(new EmptyBorder(0, 8, 0, 8));
                label.setFont(column == 0 ? FONT_BOLD : FONT);
                label.setForeground(selected ? Color.WHITE : TEXT);
                label.setBackground(selected ? new Color(0x5A8A3A)
                        : (row % 2 == 0 ? PANEL : new Color(0xBEBEBE)));
                return label;
            }
        };
        table.setDefaultRenderer(Object.class, renderer);
        JTableHeader header = table.getTableHeader();
        header.setBackground(new Color(0x8B8B8B));
        header.setForeground(Color.WHITE);
        header.setFont(FONT_BOLD);
        header.setPreferredSize(new Dimension(100, 28));
        header.setBorder(new EmptyBorder(0, 4, 0, 4));
        header.setReorderingAllowed(false);
    }

    public static Border bevel(int vertical, int horizontal) {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_DARK, 2),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED,
                                BORDER_LIGHT, BORDER_DARK),
                        new EmptyBorder(vertical, horizontal, vertical, horizontal)));
    }

    public static Border slotBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_DARK, 2),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(0x4A4A4A), 1),
                        new EmptyBorder(4, 6, 4, 6)));
    }

    public static void paintBackground(Graphics2D g2, int width, int height) {
        g2.setPaint(texture());
        g2.fillRect(0, 0, width, height);
    }

    private static TexturePaint texture() {
        if (texture == null) {
            BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
            Random random = new Random(20261102L);
            for (int y = 0; y < 32; y++) {
                for (int x = 0; x < 32; x++) {
                    int v = 188 + random.nextInt(10);
                    image.setRGB(x, y, new Color(v, v, v).getRGB());
                }
            }
            texture = new TexturePaint(image, new Rectangle(0, 0, 32, 32));
        }
        return texture;
    }

    static final class McPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            paintBackground(g2, getWidth(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }

    static final class McButtonUI extends BasicButtonUI {
        @Override
        public void paint(Graphics g, JComponent c) {
            AbstractButton button = (AbstractButton) c;
            ButtonModel model = button.getModel();
            Graphics2D g2 = (Graphics2D) g.create();
            int w = c.getWidth();
            int h = c.getHeight();
            Color fill = model.isPressed() ? new Color(0x3A3A3A)
                    : model.isRollover() ? new Color(0x5A5A5A) : new Color(0x4A4A4A);
            g2.setColor(BORDER_DARK);
            g2.fillRect(0, 0, w, h);
            g2.setColor(fill);
            g2.fillRect(2, 2, w - 4, h - 4);
            g2.setColor(BORDER_LIGHT);
            g2.drawLine(2, 2, w - 3, 2);
            g2.drawLine(2, 2, 2, h - 3);
            g2.setColor(new Color(0x1A1A1A));
            g2.drawLine(2, h - 3, w - 3, h - 3);
            g2.drawLine(w - 3, 2, w - 3, h - 3);
            g2.dispose();
            super.paint(g, c);
        }

        @Override
        protected void paintText(Graphics g, JComponent c, Rectangle textRect, String text) {
            g.setColor(new Color(0x3F3F3F));
            g.drawString(text, textRect.x + 1, textRect.y + 1);
            super.paintText(g, c, textRect, text);
        }
    }
}
