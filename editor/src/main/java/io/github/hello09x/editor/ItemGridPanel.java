package io.github.hello09x.editor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 类似玩家背包的物品格子。
 * 左键点击直接打开编辑，格子里显示物品图标、名称和价格。
 */
public final class ItemGridPanel extends JPanel implements Scrollable {

    private static final int COLUMNS = 9;
    private static final int SLOT_W = 64;
    private static final int SLOT_H = 78;

    private final List<ItemData> items = new ArrayList<>();
    private int selected = -1;
    private Consumer<ItemData> onEdit = item -> {
    };
    private boolean selling;

    public ItemGridPanel() {
        setLayout(new GridLayout(0, COLUMNS, 4, 4));
        setOpaque(false);
        setBorder(new javax.swing.border.EmptyBorder(8, 8, 8, 8));
    }

    public void setSelling(boolean selling) {
        this.selling = selling;
        rebuild();
    }

    public void setItems(List<ItemData> list) {
        items.clear();
        items.addAll(list);
        selected = -1;
        rebuild();
    }

    public void setOnEdit(Consumer<ItemData> onEdit) {
        this.onEdit = onEdit;
    }

    public ItemData getSelected() {
        return selected >= 0 && selected < items.size() ? items.get(selected) : null;
    }

    private void rebuild() {
        removeAll();
        for (int i = 0; i < items.size(); i++) {
            add(new Slot(i));
        }
        int remainder = items.size() % COLUMNS;
        if (remainder != 0) {
            for (int i = 0; i < COLUMNS - remainder; i++) {
                add(new EmptySlot());
            }
        }
        revalidate();
        repaint();
        int rows = Math.max(1, (items.size() + COLUMNS - 1) / COLUMNS);
        setPreferredSize(new Dimension(16 + COLUMNS * (SLOT_W + 4), 16 + rows * (SLOT_H + 4)));
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 24;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return Math.max(24, visibleRect.height - 48);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return false;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }

    private final class Slot extends JComponent {
        private final int index;

        Slot(int index) {
            this.index = index;
            setPreferredSize(new Dimension(SLOT_W, SLOT_H));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            ItemData item = items.get(index);
            setToolTipText("<html><b>" + html(item.displayName.isBlank() ? item.id : item.displayName) + "</b><br>"
                    + "ID: " + html(item.id) + "<br>"
                    + "材质: " + html(item.customItem.isBlank() ? item.material : item.customItem) + "<br>"
                    + (selling ? "收购价: " : "买价: ") + html(selling ? item.sell : item.buy) + "<br>"
                    + "库存: " + html(item.stock.isBlank() ? "不限" : item.stock) + "<br>"
                    + "<i>左键点击编辑</i></html>");
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    selected = index;
                    repaint();
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        onEdit.accept(item);
                    }
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();

            g2.setColor(McTheme.BORDER_DARK);
            g2.fillRect(0, 0, w, h);
            g2.setColor(selected == index ? new Color(0xD8D8D8) : new Color(0x8B8B8B));
            g2.fillRect(2, 2, w - 4, h - 4);
            g2.setColor(new Color(0x373737));
            g2.drawLine(2, 2, w - 3, 2);
            g2.drawLine(2, 2, 2, h - 3);
            g2.setColor(Color.WHITE);
            g2.drawLine(2, h - 3, w - 3, h - 3);
            g2.drawLine(w - 3, 2, w - 3, h - 3);
            if (selected == index) {
                g2.setColor(McTheme.GREEN);
                g2.drawRect(1, 1, w - 2, h - 2);
            }

            ItemData item = items.get(index);
            drawCube(g2, w / 2 - 13, 8, item);
            String name = item.displayName.isBlank() ? item.id : item.displayName;
            if (name.length() > 8) {
                name = name.substring(0, 7) + "…";
            }
            g2.setFont(McTheme.FONT.deriveFont(Font.BOLD, 10f));
            g2.setColor(new Color(0x202020));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(name, (w - fm.stringWidth(name)) / 2, 50);
            String price = selling ? item.sell : item.buy;
            String priceText = price == null || price.isBlank() ? "—" : price;
            g2.setColor(new Color(0x5A3A00));
            g2.setFont(McTheme.FONT.deriveFont(Font.PLAIN, 9f));
            fm = g2.getFontMetrics();
            g2.drawString(priceText, (w - fm.stringWidth(priceText)) / 2, 64);
            g2.dispose();
        }
    }

    private static final class EmptySlot extends JComponent {
        EmptySlot() {
            setPreferredSize(new Dimension(SLOT_W, SLOT_H));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(McTheme.BORDER_DARK);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setColor(new Color(0x8B8B8B));
            g2.fillRect(2, 2, getWidth() - 4, getHeight() - 4);
            g2.setColor(new Color(0x373737));
            g2.drawLine(2, 2, getWidth() - 3, 2);
            g2.drawLine(2, 2, 2, getHeight() - 3);
            g2.setColor(Color.WHITE);
            g2.drawLine(2, getHeight() - 3, getWidth() - 3, getHeight() - 3);
            g2.drawLine(getWidth() - 3, 2, getWidth() - 3, getHeight() - 3);
            g2.dispose();
        }
    }

    private static void drawCube(Graphics2D g2, int x, int y, ItemData item) {
        int hash = (item.material + item.customItem + item.id).hashCode();
        int r = 90 + Math.abs(hash % 120);
        int g = 90 + Math.abs((hash / 7) % 120);
        int b = 90 + Math.abs((hash / 13) % 120);
        Color base = new Color(r, g, b);
        Color top = base.brighter();
        Color right = base.darker();
        int s = 13;
        Polygon topFace = new Polygon(
                new int[]{x, x + s, x + s * 2, x + s},
                new int[]{y + s / 2, y, y + s / 2, y + s}, 4);
        g2.setColor(top);
        g2.fillPolygon(topFace);
        g2.setColor(base);
        g2.fillPolygon(new Polygon(
                new int[]{x, x + s, x + s, x},
                new int[]{y + s / 2, y + s, y + s * 2, y + s * 3 / 2}, 4));
        g2.setColor(right);
        g2.fillPolygon(new Polygon(
                new int[]{x + s * 2, x + s, x + s, x + s * 2},
                new int[]{y + s / 2, y + s, y + s * 2, y + s * 3 / 2}, 4));
        g2.setColor(new Color(0, 0, 0, 80));
        g2.drawPolygon(topFace);
    }

    private static String html(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
