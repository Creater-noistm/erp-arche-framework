package com.erp.ui;

import com.erp.db.DatabaseManager;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 通用数据列表面板 — 支持自定义 SQL 查询，数据由数据库计算而来。
 *
 * 两种模式：
 *   1. 指定 SQL（计算数据）：new EntityListPanel("标题", sql, "列名"...)
 *   2. 旧版 entity 模式：new EntityListPanel(entityType, "列名"...)
 */
public class EntityListPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JTable table;
    private final EntityTableModel tableModel;
    private final SqlQueryProvider queryProvider;
    // readOnly 由构造参数直接决定按钮添加逻辑，不持久化为字段

    /** SQL 查询提供接口 */
    @FunctionalInterface
    public interface SqlQueryProvider {
        List<Object[]> query(DatabaseManager db);
    }

    // ── 模式 1：自定义 SQL ──

    public EntityListPanel(String title, String sql, String... displayColumns) {
        this(title, false, db -> db.executeQuery(sql, rs -> {
            List<Object[]> list = new ArrayList<>();
            int cols = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                Object[] row = new Object[cols];
                for (int i = 0; i < cols; i++) row[i] = rs.getObject(i + 1);
                list.add(row);
            }
            return list;
        }), displayColumns);
    }

    // ── 模式 2：带参数的 SQL ──

    public EntityListPanel(String title, String sql, Object[] params, String... displayColumns) {
        this(title, false, db -> db.executeQuery(sql, rs -> {
            List<Object[]> list = new ArrayList<>();
            int cols = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                Object[] row = new Object[cols];
                for (int i = 0; i < cols; i++) row[i] = rs.getObject(i + 1);
                list.add(row);
            }
            return list;
        }, params), displayColumns);
    }

    // ── 模式 3：自定义 Provider ──

    public EntityListPanel(String title, boolean readOnly, SqlQueryProvider queryProvider, String... displayColumns) {
        this.queryProvider = queryProvider;

        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        // 工具栏
        JPanel toolBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        JButton refreshBtn = new JButton("🔄 刷新");
        refreshBtn.addActionListener(e -> refresh());
        toolBar.add(refreshBtn);

        if (!readOnly) {
            JButton addBtn = new JButton("➕ 新增");
            addBtn.addActionListener(e -> showAddDialog());
            toolBar.add(addBtn);
            JButton deleteBtn = new JButton("🗑 删除");
            deleteBtn.addActionListener(e -> deleteSelected());
            toolBar.add(deleteBtn);
        }

        add(toolBar, BorderLayout.NORTH);

        // 表格
        tableModel = new EntityTableModel(displayColumns);
        table = new JTable(tableModel);
        table.setRowHeight(28);
        table.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        table.getTableHeader().setFont(new Font("微软雅黑", Font.BOLD, 13));
        table.setSelectionBackground(new Color(187, 222, 251));
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        add(new JScrollPane(table), BorderLayout.CENTER);

        // 状态栏
        JLabel statusBar = new JLabel(" " + title);
        statusBar.setFont(statusBar.getFont().deriveFont(11f));
        statusBar.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        add(statusBar, BorderLayout.SOUTH);

        refresh();
    }

    // ── 模式 0：旧版 erp_entities 查询（保留兼容）──

    public EntityListPanel(String entityType, String... displayColumns) {
        this(entityType + "列表", false, db -> db.executeQuery(
            "SELECT id, code, name, status, created_at FROM erp_entities WHERE entity_type = ? AND tenant_id = ? ORDER BY created_at DESC",
            rs -> {
                List<Object[]> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(new Object[]{
                        rs.getString("id"), rs.getString("code"), rs.getString("name"),
                        rs.getString("status"), rs.getTimestamp("created_at")
                    });
                }
                return list;
            }, entityType, com.erp.tenant.TenantContext.getCurrentTenantId()
        ), displayColumns);
    }

    public void refresh() {
        List<Object[]> rows = queryProvider.query(DatabaseManager.getInstance());
        tableModel.setRows(rows);
    }

    private void showAddDialog() {
        JOptionPane.showMessageDialog(this, "新增功能由各业务模块提供专用表单");
    }

    private void deleteSelected() {
        int row = table.getSelectedRow();
        if (row < 0) { JOptionPane.showMessageDialog(this, "请先选择一行"); return; }
        JOptionPane.showMessageDialog(this, "删除功能由各业务模块提供");
    }

    // ── 表格模型 ──

    private static class EntityTableModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private final String[] cols;
        private List<Object[]> rows = new ArrayList<>();
        EntityTableModel(String[] cols) { this.cols = cols; }
        void setRows(List<Object[]> r) { rows = r; fireTableDataChanged(); }
        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }
        @Override public Object getValueAt(int r, int c) {
            Object[] row = rows.get(r);
            return c < row.length ? (row[c] == null ? "" : row[c]) : "";
        }
    }
}
