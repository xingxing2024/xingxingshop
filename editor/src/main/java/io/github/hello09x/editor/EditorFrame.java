package io.github.hello09x.editor;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.prefs.Preferences;

/** 商店编辑器主界面。 */
public final class EditorFrame extends JFrame {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private final Preferences prefs = Preferences.userNodeForPackage(EditorFrame.class);

    private final DefaultListModel<ShopData> shopModel = new DefaultListModel<>();
    private final JList<ShopData> shopList = new JList<>(shopModel);
    private final ItemTableModel itemModel = new ItemTableModel();
    private final JTable itemTable = new JTable(itemModel);

    private final JTextField idField = new JTextField();
    private final JTextField nameField = new JTextField();
    private final JTextField iconField = new JTextField();
    private final JComboBox<String> typeBox = new JComboBox<>(new String[]{"BUY", "SELL"});
    private final JTextField permissionField = new JTextField();
    private final JTextField openFromField = new JTextField();
    private final JTextField openToField = new JTextField();

    private final JLabel status = new JLabel("请选择 shops 文件夹");
    private Path folder;
    private ShopData current;

    public EditorFrame() {
        super("XingXingShop 商店编辑器");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1180, 760);
        setMinimumSize(new Dimension(980, 640));
        setLocationRelativeTo(null);

        setJMenuBar(buildMenu());
        add(buildToolbar(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);

        String last = prefs.get("folder", "");
        if (!last.isBlank()) {
            Path path = Path.of(last);
            if (Files.isDirectory(path)) {
                openFolder(path);
            }
        }
    }

    private JMenuBar buildMenu() {
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("文件");
        JMenuItem open = new JMenuItem("打开 shops 文件夹…");
        open.addActionListener(e -> chooseFolder());
        JMenuItem save = new JMenuItem("保存当前商店");
        save.addActionListener(e -> saveSettings());
        JMenuItem saveAll = new JMenuItem("保存全部");
        saveAll.addActionListener(e -> saveAll());
        JMenuItem exit = new JMenuItem("退出");
        exit.addActionListener(e -> dispose());
        file.add(open);
        file.addSeparator();
        file.add(save);
        file.add(saveAll);
        file.addSeparator();
        file.add(exit);
        bar.add(file);

        JMenu help = new JMenu("帮助");
        JMenuItem about = new JMenuItem("关于");
        about.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "XingXingShop 商店编辑器 1.0.0\n"
                        + "编辑 plugins/XingXingShop/shops 下的商店文件。\n"
                        + "保存会保留原有注释，并生成 .bak 备份。\n"
                        + "保存后把文件上传回服务器并执行 /shop reload。",
                "关于", JOptionPane.INFORMATION_MESSAGE));
        help.add(about);
        bar.add(help);
        return bar;
    }

    private JPanel buildToolbar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        JButton open = new JButton("打开文件夹");
        open.addActionListener(e -> chooseFolder());
        JButton save = new JButton("保存当前");
        save.addActionListener(e -> saveSettings());
        JButton saveAll = new JButton("保存全部");
        saveAll.addActionListener(e -> saveAll());
        bar.add(open);
        bar.add(save);
        bar.add(saveAll);
        bar.add(new JLabel("  （保存后会生成 .bak 备份，上传服务器后执行 /shop reload）"));
        return bar;
    }

    private JSplitPane buildBody() {
        shopList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean selected, boolean focus) {
                ShopData shop = (ShopData) value;
                JLabel label = (JLabel) super.getListCellRendererComponent(list,
                        shop.name + "  [" + shop.type + "]", index, selected, focus);
                label.setToolTipText(shop.id + " · " + shop.file);
                return label;
            }
        });
        shopList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                selectShop(shopList.getSelectedValue());
            }
        });

        JPanel left = new JPanel(new BorderLayout(4, 4));
        left.setPreferredSize(new Dimension(280, 0));
        left.add(new JScrollPane(shopList), BorderLayout.CENTER);

        JPanel leftButtons = new JPanel(new GridLayout(0, 1, 4, 4));
        JButton create = new JButton("新建商店");
        create.addActionListener(e -> createShop());
        JButton delete = new JButton("删除商店");
        delete.addActionListener(e -> deleteShop());
        JButton refresh = new JButton("刷新列表");
        refresh.addActionListener(e -> reloadFolder());
        leftButtons.add(create);
        leftButtons.add(delete);
        leftButtons.add(refresh);
        left.add(leftButtons, BorderLayout.SOUTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("商店设置", buildSettings());
        tabs.addTab("商品", buildItems());

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, tabs);
        split.setDividerLocation(280);
        return split;
    }

    private JPanel buildSettings() {
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 6, 5, 6);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        int y = 0;
        y = row(form, c, y, "商店 ID", idField);
        y = row(form, c, y, "显示名称", nameField);
        y = row(form, c, y, "图标材质", iconField);
        y = row(form, c, y, "类型", typeBox);
        y = row(form, c, y, "权限", permissionField);
        y = row(form, c, y, "活动开始", openFromField);
        y = row(form, c, y, "活动结束", openToField);

        JButton save = new JButton("保存商店设置");
        save.addActionListener(e -> saveSettings());
        c.gridx = 1;
        c.gridy = y;
        c.weightx = 0;
        form.add(save, c);

        JLabel tip = new JLabel("时间格式：2026-12-31 23:59，留空表示不限");
        tip.setForeground(Color.GRAY);
        c.gridx = 0;
        c.gridy = y + 1;
        c.gridwidth = 2;
        form.add(tip, c);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(form, BorderLayout.NORTH);
        return panel;
    }

    private int row(JPanel form, GridBagConstraints c, int y, String label, JComponent field) {
        c.gridx = 0;
        c.gridy = y;
        c.weightx = 0;
        c.gridwidth = 1;
        form.add(new JLabel(label), c);
        c.gridx = 1;
        c.weightx = 1;
        form.add(field, c);
        return y + 1;
    }

    private JPanel buildItems() {
        itemTable.setRowHeight(26);
        itemTable.setAutoCreateRowSorter(true);
        itemTable.getColumnModel().getColumn(0).setPreferredWidth(140);
        itemTable.getColumnModel().getColumn(1).setPreferredWidth(150);
        DefaultTableCellRenderer right = new DefaultTableCellRenderer();
        right.setHorizontalAlignment(SwingConstants.RIGHT);
        for (int i = 2; i < itemModel.getColumnCount(); i++) {
            itemTable.getColumnModel().getColumn(i).setCellRenderer(right);
        }
        itemTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() >= 2) {
                    editItem();
                }
            }
        });

        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.add(new JScrollPane(itemTable), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        JButton add = new JButton("添加商品");
        add.addActionListener(e -> addItem());
        JButton edit = new JButton("编辑商品");
        edit.addActionListener(e -> editItem());
        JButton delete = new JButton("删除商品");
        delete.addActionListener(e -> deleteItem());
        buttons.add(add);
        buttons.add(edit);
        buttons.add(delete);
        buttons.add(new JLabel("  双击商品可编辑"));
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private void chooseFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择 plugins/XingXingShop/shops 文件夹");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            openFolder(chooser.getSelectedFile().toPath());
        }
    }

    private void openFolder(Path path) {
        this.folder = path;
        prefs.put("folder", path.toString());
        reloadFolder();
    }

    private void reloadFolder() {
        shopModel.clear();
        itemModel.setItems(List.of());
        current = null;
        if (folder == null || !Files.isDirectory(folder)) {
            status.setText("请选择 shops 文件夹");
            return;
        }
        try (var stream = Files.list(folder)) {
            List<Path> files = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".yml"))
                    .sorted()
                    .toList();
            for (Path file : files) {
                try {
                    YamlShopFile document = new YamlShopFile(file);
                    ShopData shop = document.readShop();
                    shopModel.addElement(shop);
                } catch (Exception e) {
                    status.setText("加载失败：" + file.getFileName() + " - " + e.getMessage());
                }
            }
            status.setText("已加载 " + shopModel.size() + " 个商店：" + folder);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "读取文件夹失败：" + e.getMessage(),
                    "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void selectShop(ShopData shop) {
        current = shop;
        if (shop == null) {
            return;
        }
        idField.setText(shop.id);
        nameField.setText(shop.name);
        iconField.setText(shop.icon);
        typeBox.setSelectedItem(shop.selling() ? "SELL" : "BUY");
        permissionField.setText(shop.permission);
        openFromField.setText(shop.openFrom);
        openToField.setText(shop.openTo);
        itemModel.setItems(shop.document.readItems(shop.selling()));
        status.setText(shop.file.toString() + "  ·  " + itemModel.getRowCount() + " 件商品");
    }

    private void saveSettings() {
        if (current == null) {
            return;
        }
        String id = idField.getText().trim();
        if (!id.matches("[A-Za-z0-9_-]{1,64}")) {
            JOptionPane.showMessageDialog(this, "商店 ID 只能包含字母、数字、下划线、短横线",
                    "格式错误", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String oldId = current.id;
        current.id = id;
        current.name = nameField.getText().trim().isEmpty() ? id : nameField.getText().trim();
        current.icon = iconField.getText().trim().isEmpty() ? "CHEST" : iconField.getText().trim().toUpperCase();
        current.type = (String) typeBox.getSelectedItem();
        current.permission = permissionField.getText().trim();
        current.openFrom = openFromField.getText().trim();
        current.openTo = openToField.getText().trim();
        try {
            current.document.applyShop(current);
            current.document.save();
            shopList.repaint();
            status.setText("已保存：" + current.file.getFileName()
                    + (oldId.equals(id) ? "" : "（文件名未改，商店 ID 已变为 " + id + "）"));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "保存失败：" + e.getMessage(),
                    "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveAll() {
        int saved = 0;
        for (int i = 0; i < shopModel.size(); i++) {
            try {
                shopModel.get(i).document.save();
                saved++;
            } catch (Exception e) {
                status.setText("保存失败：" + shopModel.get(i).file.getFileName());
            }
        }
        status.setText("已保存 " + saved + " 个商店文件");
    }

    private void addItem() {
        if (current == null) {
            return;
        }
        ItemData blank = new ItemData();
        ItemData item = ItemDialog.show(this, blank, current.selling(), true);
        if (item == null) {
            return;
        }
        if (itemModel.contains(item.id)) {
            JOptionPane.showMessageDialog(this, "商品 ID 已存在：" + item.id,
                    "无法添加", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            current.document.addItem(item, current.selling());
            current.document.save();
            selectShop(current);
            status.setText("已添加商品：" + item.id);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "保存失败：" + e.getMessage(),
                    "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void editItem() {
        if (current == null) {
            return;
        }
        int row = itemTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "请先选择一个商品", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        ItemData old = itemModel.get(itemTable.convertRowIndexToModel(row));
        ItemData edited = ItemDialog.show(this, old.copy(), current.selling(), false);
        if (edited == null) {
            return;
        }
        try {
            current.document.updateItem(edited, current.selling());
            current.document.save();
            selectShop(current);
            status.setText("已保存商品：" + edited.id);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "保存失败：" + e.getMessage(),
                    "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteItem() {
        if (current == null) {
            return;
        }
        int row = itemTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "请先选择一个商品", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        ItemData item = itemModel.get(itemTable.convertRowIndexToModel(row));
        int answer = JOptionPane.showConfirmDialog(this, "确定删除商品 " + item.id + "？",
                "删除商品", JOptionPane.YES_NO_OPTION);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            current.document.removeItem(item.id);
            current.document.save();
            selectShop(current);
            status.setText("已删除商品：" + item.id);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "保存失败：" + e.getMessage(),
                    "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void createShop() {
        if (folder == null) {
            JOptionPane.showMessageDialog(this, "请先打开 shops 文件夹", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        ShopData shop = NewShopDialog.show(this);
        if (shop == null) {
            return;
        }
        Path file = folder.resolve(shop.id + ".yml");
        if (Files.exists(file)) {
            JOptionPane.showMessageDialog(this, "文件已存在：" + file.getFileName(),
                    "无法创建", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            YamlShopFile document = YamlShopFile.create(file, shop);
            ShopData data = document.readShop();
            shopModel.addElement(data);
            shopList.setSelectedValue(data, true);
            status.setText("已创建商店：" + file.getFileName());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "创建失败：" + e.getMessage(),
                    "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteShop() {
        ShopData shop = shopList.getSelectedValue();
        if (shop == null || folder == null) {
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "删除商店 " + shop.name + "？\n文件会移动到 shops/deleted/ 保留，不会直接删除。",
                "删除商店", JOptionPane.YES_NO_OPTION);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            Path deleted = folder.resolve("deleted");
            Files.createDirectories(deleted);
            String name = shop.file.getFileName().toString().replaceFirst("(?i)\\.ya?ml$", "")
                    + "-" + LocalDateTime.now().format(STAMP) + ".yml";
            Files.move(shop.file, deleted.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            shopModel.removeElement(shop);
            if (current == shop) {
                current = null;
                itemModel.setItems(List.of());
            }
            status.setText("已移动到 deleted/" + name);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "删除失败：" + e.getMessage(),
                    "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** 表格模型。 */
    private static final class ItemTableModel extends AbstractTableModel {

        private static final String[] COLUMNS = {
                "商品 ID", "材质", "买价", "卖价", "库存", "限购", "限卖", "折扣", "补货", "动态"
        };
        private List<ItemData> items = new ArrayList<>();

        void setItems(List<ItemData> items) {
            this.items = new ArrayList<>(items);
            fireTableDataChanged();
        }

        boolean contains(String id) {
            return items.stream().anyMatch(i -> i.id.equals(id));
        }

        ItemData get(int row) {
            return items.get(row);
        }

        @Override
        public int getRowCount() {
            return items.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int row, int column) {
            ItemData item = items.get(row);
            return switch (column) {
                case 0 -> item.id;
                case 1 -> item.customItem.isBlank() ? item.material : item.customItem;
                case 2 -> item.buy;
                case 3 -> item.sell;
                case 4 -> item.stock;
                case 5 -> item.limit;
                case 6 -> item.sellLimit;
                case 7 -> item.discount;
                case 8 -> item.restock.isBlank() ? "" : item.restock + " / " + item.restockInterval + "s";
                case 9 -> item.dynamic ? "是" : "";
                default -> "";
            };
        }
    }
}
