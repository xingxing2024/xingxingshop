package io.github.hello09x.editor;

import javax.swing.*;
import java.awt.*;

/** 商品编辑对话框。 */
public final class ItemDialog extends JDialog {

    private final JTextField idField = new JTextField(18);
    private final JTextField materialField = new JTextField(18);
    private final JTextField customItemField = new JTextField(18);
    private final JTextField nameField = new JTextField(18);
    private final JTextField buyField = new JTextField(18);
    private final JTextField sellField = new JTextField(18);
    private final JTextField stockField = new JTextField(18);
    private final JTextField limitField = new JTextField(18);
    private final JTextField sellLimitField = new JTextField(18);
    private final JTextField restockField = new JTextField(18);
    private final JTextField restockIntervalField = new JTextField(18);
    private final JTextField discountField = new JTextField(18);
    private final JTextField discountStartField = new JTextField(18);
    private final JTextField discountUntilField = new JTextField(18);
    private final JTextField vipPriceField = new JTextField(18);
    private final JTextField vipPermissionField = new JTextField(18);
    private final JTextField currencyField = new JTextField(18);
    private final JTextField permissionField = new JTextField(18);
    private final JTextField slotField = new JTextField(18);
    private final JTextField modelDataField = new JTextField(18);
    private final JCheckBox dynamicBox = new JCheckBox("开启动态价格");
    private final JCheckBox glowBox = new JCheckBox("图标发光");
    private final JTextArea loreArea = new JTextArea(4, 24);

    private final boolean isNew;
    private ItemData result;

    private ItemDialog(Frame owner, ItemData item, boolean selling, boolean isNew) {
        super(owner, isNew ? "添加商品" : "编辑商品：" + item.id, true);
        this.isNew = isNew;

        idField.setText(item.id);
        idField.setEditable(isNew);
        materialField.setText(item.material);
        customItemField.setText(item.customItem);
        customItemField.setToolTipText("例如 itemsadder:ns:id / nexo:id / mmoitems:TYPE:ID");
        nameField.setText(item.displayName);
        buyField.setText(item.buy);
        sellField.setText(item.sell);
        stockField.setText(item.stock);
        limitField.setText(item.limit);
        sellLimitField.setText(item.sellLimit);
        restockField.setText(item.restock);
        restockIntervalField.setText(item.restockInterval);
        discountField.setText(item.discount);
        discountStartField.setText(item.discountStart);
        discountUntilField.setText(item.discountUntil);
        vipPriceField.setText(item.vipPrice);
        vipPermissionField.setText(item.vipPermission);
        currencyField.setText(item.currency);
        permissionField.setText(item.permission);
        slotField.setText(item.slot);
        modelDataField.setText(item.customModelData);
        dynamicBox.setSelected(item.dynamic);
        glowBox.setSelected(item.glow);
        loreArea.setText(item.lore);
        loreArea.setLineWrap(true);

        JPanel form = McTheme.panel(new GridBagLayout());
        form.setBorder(new javax.swing.border.EmptyBorder(8, 12, 8, 12));
        for (JTextField field : java.util.List.of(idField, materialField, customItemField, nameField,
                buyField, sellField, stockField, limitField, sellLimitField, restockField,
                restockIntervalField, discountField, discountStartField, discountUntilField,
                vipPriceField, vipPermissionField, currencyField, permissionField, slotField,
                modelDataField)) {
            McTheme.field(field);
        }
        McTheme.area(loreArea);
        dynamicBox.setOpaque(false);
        dynamicBox.setForeground(McTheme.TEXT);
        glowBox.setOpaque(false);
        glowBox.setForeground(McTheme.TEXT);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 4, 3, 4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        int y = 0;

        y = section(form, c, y, "基本");
        y = row(form, c, y, "商品 ID", idField);
        y = row(form, c, y, "材质", materialField);
        y = row(form, c, y, "自定义物品", customItemField);
        y = row(form, c, y, "显示名称", nameField);
        y = row(form, c, y, "Lore", new JScrollPane(loreArea));

        y = section(form, c, y, "价格 / 库存");
        y = row(form, c, y, "买价", buyField);
        y = row(form, c, y, "卖价", sellField);
        y = row(form, c, y, "库存", stockField);
        y = row(form, c, y, "补货数量", restockField);
        y = row(form, c, y, "补货间隔（秒）", restockIntervalField);

        y = section(form, c, y, "限购 / 折扣 / VIP");
        y = row(form, c, y, "每日限购", limitField);
        y = row(form, c, y, "每日限卖", sellLimitField);
        y = row(form, c, y, "折扣（0.8=八折）", discountField);
        y = row(form, c, y, "折扣开始", discountStartField);
        y = row(form, c, y, "折扣结束", discountUntilField);
        y = row(form, c, y, "VIP 价", vipPriceField);
        y = row(form, c, y, "VIP 权限", vipPermissionField);

        y = section(form, c, y, "其他");
        y = row(form, c, y, "货币", currencyField);
        y = row(form, c, y, "权限", permissionField);
        y = row(form, c, y, "槽位", slotField);
        y = row(form, c, y, "模型数据", modelDataField);
        y = row(form, c, y, "", dynamicBox);
        y = row(form, c, y, "", glowBox);

        JButton ok = McTheme.button("保存");
        JButton cancel = McTheme.button("取消");
        ok.addActionListener(e -> save());
        cancel.addActionListener(e -> dispose());
        JPanel buttons = McTheme.panel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        buttons.add(cancel);
        buttons.add(ok);

        JScrollPane scroll = new JScrollPane(form);
        scroll.setBorder(McTheme.slotBorder());
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        JPanel root = McTheme.panel(new BorderLayout(8, 8));
        root.setBorder(new javax.swing.border.EmptyBorder(10, 12, 10, 12));
        root.add(McTheme.label(isNew ? "◆ 添加商品" : "◆ 编辑商品", McTheme.GOLD), BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);
        getRootPane().setDefaultButton(ok);
        setSize(580, 760);
        setLocationRelativeTo(owner);
    }

    private int section(JPanel form, GridBagConstraints c, int y, String title) {
        c.gridx = 0;
        c.gridy = y++;
        c.gridwidth = 2;
        JLabel label = new JLabel("— " + title + " —");
        label.setForeground(McTheme.GOLD);
        label.setFont(McTheme.FONT_BOLD);
        form.add(label, c);
        c.gridwidth = 1;
        return y;
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

    private void save() {
        String id = idField.getText().trim();
        if (id.isEmpty()) {
            error("商品 ID 不能为空");
            return;
        }
        String material = materialField.getText().trim().toUpperCase();
        String custom = customItemField.getText().trim();
        if (material.isEmpty() && custom.isEmpty()) {
            error("材质和自定义物品至少填一个");
            return;
        }
        if (!validNumbers()) {
            return;
        }
        ItemData item = new ItemData();
        item.id = id;
        item.material = material;
        item.customItem = custom;
        item.displayName = nameField.getText().trim();
        item.buy = buyField.getText().trim();
        item.sell = sellField.getText().trim();
        item.stock = stockField.getText().trim();
        item.limit = limitField.getText().trim();
        item.sellLimit = sellLimitField.getText().trim();
        item.restock = restockField.getText().trim();
        item.restockInterval = restockIntervalField.getText().trim();
        item.discount = discountField.getText().trim();
        item.discountStart = discountStartField.getText().trim();
        item.discountUntil = discountUntilField.getText().trim();
        item.vipPrice = vipPriceField.getText().trim();
        item.vipPermission = vipPermissionField.getText().trim();
        item.currency = currencyField.getText().trim();
        item.permission = permissionField.getText().trim();
        item.slot = slotField.getText().trim();
        item.customModelData = modelDataField.getText().trim();
        item.dynamic = dynamicBox.isSelected();
        item.glow = glowBox.isSelected();
        item.lore = loreArea.getText().trim();
        result = item;
        dispose();
    }

    private boolean validNumbers() {
        if (!optionalDouble(buyField.getText()) || !optionalDouble(sellField.getText())
                || !optionalDouble(vipPriceField.getText())) {
            error("价格必须是数字");
            return false;
        }
        if (!optionalInt(stockField.getText()) || !optionalInt(limitField.getText())
                || !optionalInt(sellLimitField.getText()) || !optionalInt(restockField.getText())
                || !optionalInt(restockIntervalField.getText()) || !optionalInt(slotField.getText())
                || !optionalInt(modelDataField.getText())) {
            error("库存、限购、补货、槽位等必须是整数");
            return false;
        }
        String discount = discountField.getText().trim();
        if (!discount.isEmpty()) {
            try {
                double d = Double.parseDouble(discount);
                if (d <= 0 || d > 1) {
                    error("折扣要填 0~1，例如 0.8 表示八折");
                    return false;
                }
            } catch (NumberFormatException e) {
                error("折扣必须是数字");
                return false;
            }
        }
        return true;
    }

    private static boolean optionalDouble(String raw) {
        if (raw == null || raw.isBlank()) {
            return true;
        }
        try {
            return Double.parseDouble(raw.trim()) >= 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean optionalInt(String raw) {
        if (raw == null || raw.isBlank()) {
            return true;
        }
        try {
            return Integer.parseInt(raw.trim()) >= 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void error(String message) {
        JOptionPane.showMessageDialog(this, message, "无法保存", JOptionPane.WARNING_MESSAGE);
    }

    public static ItemData show(Frame owner, ItemData item, boolean selling, boolean isNew) {
        ItemDialog dialog = new ItemDialog(owner, item, selling, isNew);
        dialog.setVisible(true);
        return dialog.result;
    }
}
