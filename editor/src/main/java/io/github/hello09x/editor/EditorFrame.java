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
    private final ItemGridPanel itemGrid = new ItemGridPanel();

    private final JTextField idField = new JTextField();
    private final JTextField nameField = new JTextField();
    private final JTextField iconField = new JTextField();
    private final JComboBox<String> typeBox = new JComboBox<>(new String[]{"BUY", "SELL"});
    private final JTextField permissionField = new JTextField();
    private final JTextField openFromField = new JTextField();
    private final JTextField openToField = new JTextField();

    private final JLabel status = new JLabel("请选择 shops 文件夹");
    private final JLabel pageLabel = McTheme.label("第 1 / 1 页", McTheme.MUTED);
    private Path folder;
    private ShopData current;

    public EditorFrame() {
        super("XingXingShop 商店编辑器");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1180, 780);
        setMinimumSize(new Dimension(1000, 660));
        setLocationRelativeTo(null);
        setContentPane(McTheme.panel(new BorderLayout()));
        getContentPane().setBackground(McTheme.BG);

        setJMenuBar(buildMenu());
        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
        add(buildStatus(), BorderLayout.SOUTH);

        String last = prefs.get("folder", "");
        if (!last.isBlank()) {
            Path path = Path.of(last);
            if (Files.isDirectory(path)) {
                openFolder(path);
            }
        }
        javax.swing.SwingUtilities.invokeLater(this::loadTexturesAuto);
    }

    private void loadTexturesAuto() {
        String saved = prefs.get("client-jar", "");
        if (!saved.isBlank()) {
            Path path = Path.of(saved);
            if (Files.isRegularFile(path)) {
                loadTextures(path);
                return;
            }
        }
        Path found = TextureBank.findClientJar();
        if (found != null) {
            loadTextures(found);
        } else {
            status.setText("未找到 Minecraft 客户端，物品图标先用方块代替；点击「选择材质」指定");
            try {
                if (!prefs.getBoolean("asked-textures", false)) {
                    prefs.putBoolean("asked-textures", true);
                    int answer = JOptionPane.showConfirmDialog(this,
                            "没有自动找到 Minecraft 客户端 jar。\n"
                                    + "选择客户端 jar 或材质包后，商品格子会显示真实物品图标。\n"
                                    + "现在选择吗？",
                            "选择 Minecraft 材质", JOptionPane.YES_NO_OPTION);
                    if (answer == JOptionPane.YES_OPTION) {
                        chooseClientJar();
                    }
                }
            } catch (Throwable ignored) {
                // 无桌面环境时忽略
            }
        }
    }

    private void chooseClientJar() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择 Minecraft 客户端 jar 或材质包 zip");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Minecraft jar / 材质包 zip", "jar", "zip"));
        String saved = prefs.get("client-jar", "");
        if (!saved.isBlank()) {
            chooser.setSelectedFile(Path.of(saved).toFile());
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            loadTextures(chooser.getSelectedFile().toPath());
        }
    }

    private void loadTextures(Path jar) {
        try {
            TextureBank.get().load(jar);
            prefs.put("client-jar", jar.toString());
            itemGrid.repaint();
            shopList.repaint();
            status.setText("已加载 Minecraft 材质：" + jar.getFileName());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "加载材质失败：" + e.getMessage(),
                    "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private JPanel buildHeader() {
        JPanel header = McTheme.panel(new BorderLayout());
        header.setBorder(new javax.swing.border.EmptyBorder(10, 12, 6, 12));

        JPanel titleRow = McTheme.panel(new BorderLayout());
        JLabel title = McTheme.title("★ XINGXINGSHOP 商店编辑器");
        JLabel subtitle = McTheme.label("可视化编辑插件商店 · 保存保留注释", McTheme.MUTED);
        titleRow.add(title, BorderLayout.WEST);
        titleRow.add(subtitle, BorderLayout.EAST);
        header.add(titleRow, BorderLayout.NORTH);

        JPanel bar = McTheme.panel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        JButton open = McTheme.button("打开文件夹");
        open.addActionListener(e -> chooseFolder());
        JButton save = McTheme.button("保存当前");
        save.addActionListener(e -> saveSettings());
        JButton saveAll = McTheme.button("保存全部");
        saveAll.addActionListener(e -> saveAll());
        JButton textures = McTheme.button("选择材质");
        textures.setToolTipText("选择 Minecraft 客户端 jar 或材质包 zip，用真实物品图标");
        textures.addActionListener(e -> chooseClientJar());
        bar.add(open);
        bar.add(save);
        bar.add(saveAll);
        bar.add(textures);
        bar.add(McTheme.label("保存后上传服务器并执行 /shop reload", McTheme.MUTED));
        header.add(bar, BorderLayout.SOUTH);
        return header;
    }

    private JPanel buildStatus() {
        JPanel panel = McTheme.panel(new BorderLayout());
        panel.setBorder(new javax.swing.border.EmptyBorder(4, 12, 6, 12));
        status.setFont(McTheme.FONT_BOLD);
        status.setForeground(McTheme.GREEN);
        panel.add(status, BorderLayout.WEST);
        return panel;
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
        JMenuItem jar = new JMenuItem("选择 Minecraft 客户端 jar…");
        jar.addActionListener(e -> chooseClientJar());
        JMenuItem exit = new JMenuItem("退出");
        exit.addActionListener(e -> dispose());
        file.add(open);
        file.addSeparator();
        file.add(save);
        file.add(saveAll);
        file.addSeparator();
        file.add(jar);
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

    private JSplitPane buildBody() {
        shopList.setCellRenderer(new ShopRenderer());
        shopList.setFixedCellHeight(44);
        shopList.setOpaque(false);
        shopList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                selectShop(shopList.getSelectedValue());
            }
        });

        JScrollPane listScroll = new JScrollPane(shopList);
        listScroll.setBorder(McTheme.slotBorder());
        listScroll.getViewport().setOpaque(false);
        listScroll.setOpaque(false);

        JPanel left = McTheme.card();
        left.setPreferredSize(new Dimension(300, 0));
        left.add(McTheme.label("商店列表", McTheme.GOLD), BorderLayout.NORTH);
        left.add(listScroll, BorderLayout.CENTER);

        JPanel leftButtons = McTheme.panel(new GridLayout(0, 1, 6, 6));
        JButton create = McTheme.button("新建商店");
        create.addActionListener(e -> createShop());
        JButton delete = McTheme.button("删除商店");
        delete.addActionListener(e -> deleteShop());
        JButton refresh = McTheme.button("刷新列表");
        refresh.addActionListener(e -> reloadFolder());
        leftButtons.add(create);
        leftButtons.add(delete);
        leftButtons.add(refresh);
        left.add(leftButtons, BorderLayout.SOUTH);

        CardLayout cards = new CardLayout();
        JPanel cardPanel = new JPanel(cards);
        cardPanel.setOpaque(false);
        cardPanel.add(buildSettings(), "settings");
        cardPanel.add(buildItems(), "items");

        JButton tabSettings = McTheme.button("商店设置");
        JButton tabItems = McTheme.button("商品列表");
        JPanel nav = McTheme.panel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        nav.add(tabSettings);
        nav.add(tabItems);
        tabSettings.addActionListener(e -> {
            cards.show(cardPanel, "settings");
            tabSettings.setForeground(McTheme.GOLD);
            tabItems.setForeground(Color.WHITE);
        });
        tabItems.addActionListener(e -> {
            cards.show(cardPanel, "items");
            tabItems.setForeground(McTheme.GOLD);
            tabSettings.setForeground(Color.WHITE);
        });
        tabSettings.setForeground(McTheme.GOLD);

        JPanel right = McTheme.panel(new BorderLayout());
        right.add(nav, BorderLayout.NORTH);
        right.add(cardPanel, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setDividerLocation(300);
        split.setBorder(null);
        split.setOpaque(false);
        split.setDividerSize(6);
        return split;
    }

    private JPanel buildSettings() {
        McTheme.field(idField);
        McTheme.field(nameField);
        McTheme.field(iconField);
        McTheme.field(permissionField);
        McTheme.field(openFromField);
        McTheme.field(openToField);
        typeBox.setBackground(McTheme.SLOT);
        typeBox.setForeground(McTheme.TEXT);
        typeBox.setFont(McTheme.FONT);

        JPanel form = McTheme.panel(new GridBagLayout());
        form.setBorder(new javax.swing.border.EmptyBorder(8, 12, 8, 12));
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

        JButton save = McTheme.button("保存商店设置");
        save.addActionListener(e -> saveSettings());
        c.gridx = 1;
        c.gridy = y;
        c.weightx = 0;
        form.add(save, c);

        JLabel tip = McTheme.label("时间格式：2026-12-31 23:59，留空表示不限", McTheme.MUTED);
        c.gridx = 0;
        c.gridy = y + 1;
        c.gridwidth = 2;
        form.add(tip, c);

        JPanel card = McTheme.card();
        card.add(McTheme.label("商店设置", McTheme.GOLD), BorderLayout.NORTH);
        card.add(form, BorderLayout.CENTER);

        JPanel wrapper = McTheme.panel(new BorderLayout());
        wrapper.setBorder(new javax.swing.border.EmptyBorder(6, 6, 6, 6));
        wrapper.add(card, BorderLayout.NORTH);
        return wrapper;
    }

    private int row(JPanel form, GridBagConstraints c, int y, String label, JComponent field) {
        c.gridx = 0;
        c.gridy = y;
        c.weightx = 0;
        c.gridwidth = 1;
        form.add(McTheme.label(label, McTheme.MUTED), c);
        c.gridx = 1;
        c.weightx = 1;
        form.add(field, c);
        return y + 1;
    }

    private JPanel buildItems() {
        itemGrid.setOnEdit(item -> editItem(item));
        JPanel gridWrap = McTheme.panel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        gridWrap.add(itemGrid);

        JPanel buttons = McTheme.panel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        JButton prev = McTheme.button("上一页");
        prev.addActionListener(e -> {
            if (itemGrid.prevPage()) {
                updatePageLabel();
            }
        });
        JButton next = McTheme.button("下一页");
        next.addActionListener(e -> {
            if (itemGrid.nextPage()) {
                updatePageLabel();
            }
        });
        JButton add = McTheme.button("添加商品");
        add.addActionListener(e -> addItem());
        JButton edit = McTheme.button("编辑选中");
        edit.addActionListener(e -> {
            ItemData selected = itemGrid.getSelected();
            if (selected != null) {
                editItem(selected);
            }
        });
        JButton delete = McTheme.button("删除选中");
        delete.addActionListener(e -> deleteItem());
        buttons.add(prev);
        buttons.add(pageLabel);
        buttons.add(next);
        buttons.add(add);
        buttons.add(edit);
        buttons.add(delete);

        JPanel card = McTheme.card();
        card.add(McTheme.label("商品背包（左键格子直接编辑）", McTheme.GOLD), BorderLayout.NORTH);
        card.add(gridWrap, BorderLayout.CENTER);
        card.add(buttons, BorderLayout.SOUTH);

        JPanel wrapper = McTheme.panel(new BorderLayout());
        wrapper.setBorder(new javax.swing.border.EmptyBorder(6, 6, 6, 6));
        wrapper.add(card, BorderLayout.CENTER);
        return wrapper;
    }

    private void updatePageLabel() {
        pageLabel.setText("第 " + itemGrid.page() + " / " + itemGrid.pages() + " 页");
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
        itemGrid.setItems(List.of());
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
        itemGrid.setSelling(shop.selling());
        List<ItemData> items = shop.document.readItems(shop.selling());
        itemGrid.setItems(items);
        updatePageLabel();
        status.setText(shop.file.toString() + "  ·  " + items.size() + " 件商品");
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
        if (current.document.readItems(current.selling()).stream().anyMatch(i -> i.id.equals(item.id))) {
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

    private void editItem(ItemData old) {
        if (current == null || old == null) {
            return;
        }
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
        ItemData item = itemGrid.getSelected();
        if (item == null) {
            JOptionPane.showMessageDialog(this, "请先点击选择一个商品", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
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
                itemGrid.setItems(List.of());
            }
            status.setText("已移动到 deleted/" + name);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "删除失败：" + e.getMessage(),
                    "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** 商店列表的 Minecraft 槽位样式。 */
    private static final class ShopRenderer extends JPanel implements ListCellRenderer<ShopData> {
        private final JLabel nameLabel = new JLabel();
        private final JLabel metaLabel = new JLabel();
        private final JLabel iconLabel = new JLabel();

        ShopRenderer() {
            super(new BorderLayout());
            setOpaque(true);
            nameLabel.setFont(McTheme.FONT_BOLD);
            metaLabel.setFont(McTheme.FONT.deriveFont(11f));
            iconLabel.setPreferredSize(new Dimension(30, 30));
            add(iconLabel, BorderLayout.WEST);
            JPanel text = new JPanel(new BorderLayout());
            text.setOpaque(false);
            text.add(nameLabel, BorderLayout.CENTER);
            text.add(metaLabel, BorderLayout.SOUTH);
            add(text, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends ShopData> list, ShopData shop,
                                                      int index, boolean selected, boolean focus) {
            setBackground(selected ? new Color(0x5A8A3A) : new Color(0xC6C6C6));
            setBorder(javax.swing.BorderFactory.createCompoundBorder(
                    javax.swing.BorderFactory.createLineBorder(
                            selected ? new Color(0x2E7D32) : new Color(0x8B8B8B), 1),
                    new javax.swing.border.EmptyBorder(4, 8, 4, 8)));
            nameLabel.setText(shop.name);
            nameLabel.setForeground(selected ? Color.WHITE : McTheme.TEXT);
            metaLabel.setText((shop.selling() ? "收购" : "购买") + " · " + shop.id);
            metaLabel.setForeground(shop.selling() ? McTheme.GOLD : McTheme.AQUA);
            iconLabel.setIcon(TextureBank.get().icon(shop.icon, 24));
            setToolTipText(shop.file.toString());
            return this;
        }
    }
}
