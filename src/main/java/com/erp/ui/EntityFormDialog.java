package com.erp.ui;

import com.erp.db.DatabaseManager;
import com.erp.tenant.TenantContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 通用数据录入表单对话框 — 自动根据字段名生成输入框。
 */
public class EntityFormDialog extends JDialog {

    private static final Logger log = LoggerFactory.getLogger(EntityFormDialog.class);
    private static final long serialVersionUID = 1L;

    private final String entityType;
    private final Map<String, JComponent> fields = new HashMap<>();
    private boolean confirmed = false;

    public EntityFormDialog(JFrame owner, String entityType, String... fieldNames) {
        super(owner, "新增 " + entityType, true);
        this.entityType = entityType;
        setSize(500, 400);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(8, 8));

        // ── 表单区域 ──
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 4, 4, 4);

        gbc.gridx = 0; gbc.gridy = 0;
        gbc.weightx = 0.3;
        for (String name : fieldNames) {
            if ("操作".equals(name) || name.contains("ID")) continue;
            gbc.gridx = 0;
            gbc.weightx = 0.3;
            formPanel.add(new JLabel(name + ":", SwingConstants.RIGHT), gbc);

            gbc.gridx = 1;
            gbc.weightx = 0.7;
            JTextField tf = new JTextField(20);
            fields.put(name, tf);
            formPanel.add(tf, gbc);

            gbc.gridy++;
        }

        // 再加一个备注字段
        gbc.gridx = 0;
        gbc.weightx = 0.3;
        formPanel.add(new JLabel("备注:", SwingConstants.RIGHT), gbc);
        gbc.gridx = 1;
        gbc.weightx = 0.7;
        JTextArea ta = new JTextArea(3, 20);
        ta.setLineWrap(true);
        JScrollPane sp = new JScrollPane(ta);
        fields.put("备注", sp);
        formPanel.add(sp, gbc);

        add(new JScrollPane(formPanel), BorderLayout.CENTER);

        // ── 按钮 ──
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton saveBtn = new JButton("保存");
        saveBtn.addActionListener(e -> save());
        JButton cancelBtn = new JButton("取消");
        cancelBtn.addActionListener(e -> dispose());
        btnPanel.add(saveBtn);
        btnPanel.add(cancelBtn);
        add(btnPanel, BorderLayout.SOUTH);
    }

    public boolean isConfirmed() { return confirmed; }

    private void save() {
        try {
            // 收集表单数据
            StringBuilder dataJson = new StringBuilder("{");
            for (Map.Entry<String, JComponent> entry : fields.entrySet()) {
                String val = "";
                if (entry.getValue() instanceof JTextField tf) {
                    val = tf.getText();
                } else if (entry.getValue() instanceof JScrollPane sp
                    && sp.getViewport().getView() instanceof JTextArea ta) {
                    val = ta.getText();
                }
                if (dataJson.length() > 1) dataJson.append(", ");
                dataJson.append("\"").append(entry.getKey()).append("\":\"").append(val.replace("\"", "\\\"")).append("\"");
            }
            dataJson.append("}");

            String id = UUID.randomUUID().toString();
            String code = "D-" + System.currentTimeMillis() % 100000;

            DatabaseManager.getInstance().executeUpdate(
                "INSERT INTO erp_entities (id, tenant_id, entity_type, code, name, status, data_json, created_at, updated_at) VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                id, TenantContext.getCurrentTenantId(), entityType, code, code, dataJson.toString()
            );

            confirmed = true;
            log.info("Created {}: id={}, code={}", entityType, id, code);
            dispose();
        } catch (Exception e) {
            log.error("Failed to save entity", e);
            JOptionPane.showMessageDialog(this, "保存失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }
}
