package io.github.hello09x.editor;

import javax.swing.*;
import java.awt.*;

/** 新建商店对话框。 */
public final class NewShopDialog extends JDialog {

    private final JTextField idField = new JTextField(18);
    private final JTextField nameField = new JTextField(18);
    private final JTextField iconField = new JTextField("CHEST", 18);
    private final JComboBox<String> typeBox = new JComboBox<>(new String[]{"BUY", "SELL"});
    private ShopData result;

    private NewShopDialog(Frame owner) {
        super(owner, "新建商店", true);
        McTheme.field(idField);
        McTheme.field(nameField);
        McTheme.field(iconField);
        typeBox.setBackground(McTheme.SLOT);
        typeBox.setForeground(McTheme.TEXT);
        typeBox.setFont(McTheme.FONT);

        JPanel form = McTheme.panel(new GridBagLayout());
        form.setBorder(new javax.swing.border.EmptyBorder(8, 12, 8, 12));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        add(form, c, 0, "商店 ID", idField);
        add(form, c, 1, "显示名称", nameField);
        add(form, c, 2, "图标材质", iconField);
        add(form, c, 3, "类型", typeBox);

        JButton ok = McTheme.button("创建");
        JButton cancel = McTheme.button("取消");
        ok.addActionListener(e -> create());
        cancel.addActionListener(e -> dispose());
        JPanel buttons = McTheme.panel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        buttons.add(cancel);
        buttons.add(ok);

        JPanel root = McTheme.panel(new BorderLayout(8, 8));
        root.setBorder(new javax.swing.border.EmptyBorder(10, 12, 10, 12));
        root.add(McTheme.label("◆ 新建商店", McTheme.GOLD), BorderLayout.NORTH);
        root.add(form, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);
        getRootPane().setDefaultButton(ok);
        pack();
        setLocationRelativeTo(owner);
    }

    private void add(JPanel form, GridBagConstraints c, int y, String label, JComponent field) {
        c.gridx = 0;
        c.gridy = y;
        c.weightx = 0;
        form.add(McTheme.label(label, McTheme.MUTED), c);
        c.gridx = 1;
        c.weightx = 1;
        form.add(field, c);
    }

    private void create() {
        String id = idField.getText().trim();
        if (!id.matches("[A-Za-z0-9_-]{1,64}")) {
            JOptionPane.showMessageDialog(this, "商店 ID 只能包含字母、数字、下划线、短横线", "格式错误", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            name = id;
        }
        ShopData shop = new ShopData();
        shop.id = id;
        shop.name = name;
        shop.icon = iconField.getText().trim().isEmpty() ? "CHEST" : iconField.getText().trim().toUpperCase();
        shop.type = (String) typeBox.getSelectedItem();
        result = shop;
        dispose();
    }

    public static ShopData show(Frame owner) {
        NewShopDialog dialog = new NewShopDialog(owner);
        dialog.setVisible(true);
        return dialog.result;
    }
}
