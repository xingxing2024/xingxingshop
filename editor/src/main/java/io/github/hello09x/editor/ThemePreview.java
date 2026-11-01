package io.github.hello09x.editor;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

/** 无窗口渲染一张界面预览图，用于检查主题效果。 */
public final class ThemePreview {

    public static void render(Path out) throws Exception {
        JPanel root = McTheme.panel(new BorderLayout(12, 12));
        root.setBorder(new javax.swing.border.EmptyBorder(14, 16, 14, 16));

        JPanel header = McTheme.panel(new BorderLayout());
        JLabel title = McTheme.title("★ XINGXINGSHOP 商店编辑器");
        JLabel subtitle = McTheme.label("可视化编辑插件商店 · 保存保留注释", McTheme.MUTED);
        JPanel titleRow = McTheme.panel(new BorderLayout());
        titleRow.add(title, BorderLayout.WEST);
        titleRow.add(subtitle, BorderLayout.EAST);
        header.add(titleRow, BorderLayout.NORTH);

        JPanel toolbar = McTheme.panel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        toolbar.add(McTheme.button("打开文件夹"));
        toolbar.add(McTheme.button("保存当前"));
        toolbar.add(McTheme.button("保存全部"));
        toolbar.add(McTheme.label("保存后上传服务器并执行 /shop reload", McTheme.MUTED));
        header.add(toolbar, BorderLayout.SOUTH);
        root.add(header, BorderLayout.NORTH);

        DefaultListModel<String> shops = new DefaultListModel<>();
        shops.addElement("基础物资");
        shops.addElement("建筑商店");
        shops.addElement("装备工具");
        shops.addElement("材料回收");
        JList<String> list = new JList<>(shops);
        list.setSelectedIndex(1);
        list.setFixedCellHeight(44);
        list.setOpaque(false);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index,
                                                          boolean selected, boolean focus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(l, value, index, selected, false);
                label.setBorder(new javax.swing.border.EmptyBorder(6, 10, 6, 10));
                label.setFont(McTheme.FONT_BOLD);
                label.setBackground(selected ? new Color(0x5A8A3A) : new Color(0xC6C6C6));
                label.setForeground(selected ? Color.WHITE : McTheme.TEXT);
                return label;
            }
        });
        JScrollPane listScroll = new JScrollPane(list);
        listScroll.setBorder(McTheme.slotBorder());
        listScroll.getViewport().setOpaque(false);

        JPanel left = McTheme.card();
        left.setPreferredSize(new Dimension(290, 0));
        left.add(McTheme.label("商店列表", McTheme.GOLD), BorderLayout.NORTH);
        left.add(listScroll, BorderLayout.CENTER);
        JPanel leftButtons = McTheme.panel(new GridLayout(0, 1, 6, 6));
        leftButtons.add(McTheme.button("新建商店"));
        leftButtons.add(McTheme.button("删除商店"));
        left.add(leftButtons, BorderLayout.SOUTH);

        DefaultTableModel model = new DefaultTableModel(
                new Object[][]{
                        {"diamond", "DIAMOND", "260", "", "300", "16", "", "", ""},
                        {"iron_ingot", "IRON_INGOT", "18", "6", "500", "", "", "0.8", "10 / 3600s"},
                        {"gold_ingot", "GOLD_INGOT", "42", "12", "260", "", "", "", ""},
                        {"cobblestone", "COBBLESTONE", "0.65", "0.02", "", "", "1000", "", ""},
                },
                new Object[]{"商品 ID", "材质", "买价", "卖价", "库存", "限购", "限卖", "折扣", "补货"});
        JTable table = new JTable(model);
        McTheme.table(table);
        table.setFillsViewportHeight(true);
        int[] widths = {150, 170, 60, 60, 70, 60, 60, 60, 100};
        for (int i = 0; i < widths.length && i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(McTheme.slotBorder());
        tableScroll.getViewport().setBackground(McTheme.PANEL);
        tableScroll.getViewport().setOpaque(true);
        tableScroll.setOpaque(false);

        JPanel itemsCard = McTheme.card();
        itemsCard.add(McTheme.label("商品列表", McTheme.GOLD), BorderLayout.NORTH);
        itemsCard.add(tableScroll, BorderLayout.CENTER);
        JPanel itemButtons = McTheme.panel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        itemButtons.add(McTheme.button("添加商品"));
        itemButtons.add(McTheme.button("编辑商品"));
        itemButtons.add(McTheme.button("删除商品"));
        itemsCard.add(itemButtons, BorderLayout.SOUTH);

        JPanel right = McTheme.panel(new BorderLayout(0, 8));
        JPanel nav = McTheme.panel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JButton s = McTheme.button("商店设置");
        s.setForeground(McTheme.GOLD);
        nav.add(s);
        nav.add(McTheme.button("商品列表"));
        right.add(nav, BorderLayout.NORTH);
        right.add(itemsCard, BorderLayout.CENTER);

        JPanel body = McTheme.panel(new BorderLayout(12, 0));
        body.add(left, BorderLayout.WEST);
        body.add(right, BorderLayout.CENTER);
        root.add(body, BorderLayout.CENTER);
        root.add(McTheme.label("● 已加载 15 个商店 · 862 件商品", McTheme.GREEN), BorderLayout.SOUTH);

        root.setSize(1180, 780);
        layoutTree(root);
        BufferedImage image = new BufferedImage(1180, 780, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        root.printAll(g);
        g.dispose();
        ImageIO.write(image, "png", out.toFile());
        System.out.println("PREVIEW " + out);
    }

    private static void layoutTree(Container container) {
        container.doLayout();
        for (Component component : container.getComponents()) {
            if (component instanceof Container child) {
                layoutTree(child);
            }
        }
    }
}
