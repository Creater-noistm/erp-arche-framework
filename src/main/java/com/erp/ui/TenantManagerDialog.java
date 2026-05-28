package com.erp.ui;

import com.erp.db.DatabaseManager;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 租户管理界面 — 现代表格风格，展示所有公司并支持管理操作。
 */
public class TenantManagerDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private JTable table;
    private TenantTableModel tableModel;
    private JLabel statusBar;

    private static final Color HEADER_BG = new Color(25, 118, 210);
    private static final Color HEADER_FG = Color.WHITE;
    private static final Color ROW_ALT = new Color(245, 248, 255);
    public TenantManagerDialog(JFrame owner) {
        super(owner, "公司管理", true);
        setSize(800, 520);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(0, 0));

        // ── 顶部标题栏 ──
        JPanel titleBar = new JPanel() {
            private static final long serialVersionUID = 1L;
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new GradientPaint(0, 0, HEADER_BG, 0, getHeight(), new Color(13, 71, 161)));
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        titleBar.setPreferredSize(new Dimension(800, 50));
        titleBar.setLayout(new BorderLayout());
        JLabel titleLabel = new JLabel("  🏢 公司管理");
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 18));
        titleLabel.setForeground(Color.WHITE);
        titleBar.add(titleLabel, BorderLayout.WEST);
        add(titleBar, BorderLayout.NORTH);

        // ── 工具栏 ──
        JPanel toolBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        toolBar.setBackground(Color.WHITE);
        toolBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(220, 220, 220)));

        JButton addBtn = createToolButton("➕  新增公司", new Color(46, 125, 50));
        addBtn.addActionListener(e -> showEditDialog(null));
        JButton editBtn = createToolButton("✏️  编辑", new Color(25, 118, 210));
        editBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0) showEditDialog(tableModel.getRow(row));
            else JOptionPane.showMessageDialog(this, "请先选择一行");
        });
        JButton delBtn = createToolButton("🗑  删除", new Color(211, 47, 47));
        delBtn.addActionListener(e -> deleteSelected());
        JButton refreshBtn = createToolButton("🔄  刷新", new Color(100, 100, 100));
        refreshBtn.addActionListener(e -> refresh());

        toolBar.add(addBtn);
        toolBar.add(editBtn);
        toolBar.add(delBtn);
        toolBar.add(Box.createHorizontalStrut(20));
        toolBar.add(refreshBtn);
        add(toolBar, BorderLayout.AFTER_LAST_LINE);

        // ── 数据表格 ──
        tableModel = new TenantTableModel();
        table = new JTable(tableModel);
        styleTable(table);
        add(new JScrollPane(table), BorderLayout.CENTER);

        // ── 底部状态栏 ──
        statusBar = new JLabel(" ");
        statusBar.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        statusBar.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        statusBar.setBackground(new Color(245, 245, 245));
        statusBar.setOpaque(true);
        add(statusBar, BorderLayout.SOUTH);

        refresh();
    }

    private JButton createToolButton(String text, Color accent) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        btn.setForeground(accent);
        btn.setBackground(Color.WHITE);
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(accent, 1),
            BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setFocusPainted(false);
        return btn;
    }

    private void styleTable(JTable table) {
        table.setRowHeight(32);
        table.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setSelectionBackground(new Color(187, 222, 251));
        table.setSelectionForeground(Color.BLACK);

        JTableHeader header = table.getTableHeader();
        header.setFont(new Font("微软雅黑", Font.BOLD, 13));
        header.setBackground(HEADER_BG);
        header.setForeground(HEADER_FG);
        header.setPreferredSize(new Dimension(header.getWidth(), 36));
        header.setBorder(BorderFactory.createEmptyBorder());

        // 交替行颜色
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
                Component comp = super.getTableCellRendererComponent(t, v, s, f, r, c);
                if (!s) setBackground(r % 2 == 0 ? Color.WHITE : ROW_ALT);
                return comp;
            }
        };
        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }
    }

    private void refresh() {
        List<Object[]> rows = DatabaseManager.getInstance().executeQuery(
            "SELECT t.id, t.name, t.code, t.status, t.contact_name, " +
            " (SELECT COUNT(*) FROM erp_users u WHERE u.tenant_id = t.id) as user_count, " +
            " t.created_at FROM erp_tenants t ORDER BY t.code",
            rs -> {
                List<Object[]> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(new Object[]{
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("code"),
                        rs.getString("status"),
                        rs.getString("contact_name"),
                        rs.getInt("user_count"),
                        rs.getTimestamp("created_at")
                    });
                }
                return list;
            }
        );
        tableModel.setRows(rows);
        statusBar.setText("共 " + rows.size() +  " 家公司");
    }

    private void showEditDialog(Object[] row) {
        boolean isNew = row == null;
        JTextField nameField = new JTextField(isNew ? "" : (String) row[1]);
        nameField.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        JTextField codeField = new JTextField(isNew ? "" : (String) row[2]);
        codeField.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        JTextField contactField = new JTextField(isNew ? "" : val(row[4]));
        contactField.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        JComboBox<String> statusCombo = new JComboBox<>(new String[]{"ACTIVE", "SUSPENDED", "TRIAL"});
        statusCombo.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        if (!isNew) statusCombo.setSelectedItem((String) row[3]);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(5, 10, 5, 10);
        g.gridx = 0; g.weightx = 0.3;
        form.add(new JLabel("公司名称:"), g);
        g.gridx = 1; g.weightx = 0.7;
        form.add(nameField, g);
        g.gridx = 0;
        form.add(new JLabel("公司编码:"), g);
        g.gridx = 1;
        form.add(codeField, g);
        g.gridx = 0;
        form.add(new JLabel("联系人:"), g);
        g.gridx = 1;
        form.add(contactField, g);
        g.gridx = 0;
        form.add(new JLabel("状态:"), g);
        g.gridx = 1;
        form.add(statusCombo, g);

        int result = JOptionPane.showConfirmDialog(this, form,
            isNew ? "新增公司" : "编辑公司", JOptionPane.OK_CANCEL_OPTION);
        if (result != JOptionPane.OK_OPTION) return;

        String name = nameField.getText().trim();
        String code = codeField.getText().trim();
        if (name.isEmpty() || code.isEmpty()) {
            JOptionPane.showMessageDialog(this, "公司名称和编码不能为空");
            return;
        }

        if (isNew) {
            String id = "t-" + code.toLowerCase();
            DatabaseManager.getInstance().executeUpdate(
                "INSERT INTO erp_tenants (id, name, code, status, contact_name) VALUES (?, ?, ?, ?, ?)",
                id, name, code, statusCombo.getSelectedItem(), contactField.getText().trim());
            DatabaseManager.getInstance().executeUpdate(
                "INSERT INTO erp_users (id, tenant_id, username, display_name, password_hash, role_id) VALUES (?, ?, ?, ?, ?, 'tenant_admin')",
                "u-" + id + "-admin", id, "admin", name + "管理员", "admin123");
        } else {
            DatabaseManager.getInstance().executeUpdate(
                "UPDATE erp_tenants SET name=?, code=?, status=?, contact_name=? WHERE id=?",
                name, code, statusCombo.getSelectedItem(), contactField.getText().trim(), row[0]);
        }
        refresh();
    }

    private void deleteSelected() {
        int row = table.getSelectedRow();
        if (row < 0) { JOptionPane.showMessageDialog(this, "请选择一行"); return; }
        Object[] data = tableModel.getRow(row);
        int userCount = (int) data[5];
        int confirm = JOptionPane.showConfirmDialog(this,
            "<html><h3>确认删除公司？</h3>"
            + "<p>公司: <b>" + data[1] + "</b></p>"
            + "<p>关联用户: " + userCount + " 人</p>"
            + "<p style='color:red;'>该操作不可恢复！</p></html>",
            "确认删除", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            String id = (String) data[0];
            DatabaseManager.getInstance().executeUpdate("DELETE FROM erp_entities WHERE tenant_id=?", id);
            DatabaseManager.getInstance().executeUpdate("DELETE FROM erp_users WHERE tenant_id=?", id);
            DatabaseManager.getInstance().executeUpdate("DELETE FROM erp_login_log WHERE tenant_id=?", id);
            DatabaseManager.getInstance().executeUpdate("DELETE FROM erp_org_units WHERE tenant_id=?", id);
            DatabaseManager.getInstance().executeUpdate("DELETE FROM erp_tenants WHERE id=?", id);
            refresh();
        }
    }

    private static String val(Object o) { return o == null ? "" : o.toString(); }

    // ── 表格模型 ──
    private static class TenantTableModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private final String[] cols = {"公司名称", "编码", "状态", "联系人", "用户数", "创建时间"};
        private List<Object[]> rows = new ArrayList<>();
        void setRows(List<Object[]> r) { rows = r; fireTableDataChanged(); }
        Object[] getRow(int i) { return rows.get(i); }
        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public Object getValueAt(int r, int c) {
            Object[] row = rows.get(r);
            if (c == 3) { // 状态列美化
                String s = (String) row[3];
                return "ACTIVE".equals(s) ? "● 启用" : "● 停用";
            }
            return c < row.length ? row[c] : "";
        }
        @Override public String getColumnName(int c) { return cols[c]; }
    }
}
