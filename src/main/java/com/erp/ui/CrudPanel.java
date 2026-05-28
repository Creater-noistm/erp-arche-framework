package com.erp.ui;

import com.erp.db.DatabaseManager;
import com.erp.kernel.MicroKernel;
import com.erp.tenant.TenantContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用 CRUD 面板 — 配置 SQL 即可增删改真实数据库。
 *
 * 用法：
 *   new CrudPanel("产品管理",
 *       "SELECT id, product_name, ... FROM inv_products WHERE tenant_id='xxx'",
 *       "inv_products",
 *       CrudColumn.text("名称", "product_name", true),
 *       CrudColumn.text("编码", "product_code", true));
 */
public class CrudPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(CrudPanel.class);

    private JTable table;
    private CrudTableModel tableModel;
    private final List<CrudColumn> columns;
    private final String tableName;
    private final String selectSql;
    private Object[] queryParams = new Object[0];
    private String tenantId;
    private JLabel statusLabel;
    private java.util.List<Object[]> allRows = new java.util.ArrayList<>();
    private JTextField searchField;

    public CrudPanel(String title, String selectSql, String tableName, boolean readOnly, CrudColumn... cols) {
        this.columns = List.of(cols);
        this.tableName = tableName;
        this.selectSql = selectSql;

        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        // 工具栏
        JPanel toolBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));

        JButton refreshBtn = new JButton("刷新");
        refreshBtn.addActionListener(e -> refresh());
        toolBar.add(refreshBtn);

        if (!readOnly) {
            JButton addBtn = new JButton("新增");
            addBtn.addActionListener(e -> showForm(null));
            toolBar.add(addBtn);

            JButton editBtn = new JButton("编辑");
            editBtn.addActionListener(e -> {
                int row = table.getSelectedRow();
                if (row >= 0) showForm(tableModel.getRow(row));
                else JOptionPane.showMessageDialog(this, "请先选择一行");
            });
            toolBar.add(editBtn);

            JButton delBtn = new JButton("删除");
            delBtn.addActionListener(e -> deleteSelected());
            toolBar.add(delBtn);
        }

        // 搜索栏
        JPanel searchBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        searchField = new JTextField(15);
        searchField.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        searchField.putClientProperty("JTextField.placeholderText", "搜索...");
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            void doFilter() { filterRows(searchField.getText().trim()); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { doFilter(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { doFilter(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { doFilter(); }
        });
        // 导出按钮（所有人可用）
        JButton exportBtn = new JButton("导出");
        exportBtn.addActionListener(e -> exportToCSV());
        toolBar.add(exportBtn);

        // 导入按钮（仅管理员）
        boolean isAdmin = isCurrentUserAdmin();
        if (isAdmin && !readOnly) {
            JButton importBtn = new JButton("导入");
            importBtn.addActionListener(e -> importFromCSV());
            toolBar.add(importBtn);
        }

        toolBar.add(searchField);
        add(toolBar, BorderLayout.NORTH);

        // 表格
        tableModel = new CrudTableModel(cols);
        table = new JTable(tableModel);
        table.setRowHeight(28);
        table.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        table.getTableHeader().setFont(new Font("微软雅黑", Font.BOLD, 13));
        table.setSelectionBackground(new Color(187, 222, 251));
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        add(new JScrollPane(table), BorderLayout.CENTER);

        // 状态栏
        statusLabel = new JLabel(" " + title);
        statusLabel.setFont(statusLabel.getFont().deriveFont(11f));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        add(statusLabel, BorderLayout.SOUTH);
    }

    public CrudPanel withParams(Object... params) {
        this.queryParams = params;
        // tenant_id 从 TenantContext 读取，不依赖 params[0]
        this.tenantId = TenantContext.getCurrentTenantId();
        refresh();
        return this;
    }

    /** 在工具栏追加自定义按钮 */
    public CrudPanel addToolbarButton(String label, Runnable action) {
        // 找到工具栏并添加按钮
        java.awt.Component[] children = getComponents();
        for (java.awt.Component c : children) {
            if (c instanceof JPanel && ((JPanel)c).getLayout() instanceof FlowLayout) {
                JButton btn = new JButton(label);
                btn.addActionListener(e -> action.run());
                ((JPanel)c).add(btn);
                break;
            }
        }
        return this;
    }

    /** 获取当前选中行的第一个单元格值（ID） */
    public String getSelectedId() {
        int row = table.getSelectedRow();
        if (row < 0) return null;
        Object v = tableModel.getValueAt(row, 0);
        return v != null ? v.toString() : null;
    }

    /** 获取当前选中行的指定列值 */
    public String getSelectedValue(int colIndex) {
        int row = table.getSelectedRow();
        if (row < 0) return null;
        Object v = tableModel.getValueAt(row, colIndex);
        return v != null ? v.toString() : null;
    }

    /** 直接弹出编辑对话框（不需要点第二次"编辑"） */
    public void openEditDialog(String id, String name) {
        int row = table.getSelectedRow();
        if (row < 0) { JOptionPane.showMessageDialog(this, "请先选择一行"); return; }
        Object[] rowData = tableModel.getRow(row);
        showForm(rowData);
    }

    public void refresh() {
        List<Object[]> rows = DatabaseManager.getInstance().executeQuery(selectSql, rs -> {
            List<Object[]> list = new ArrayList<>();
            while (rs.next()) {
                Object[] row = new Object[columns.size()];
                for (int i = 0; i < columns.size(); i++) {
                    row[i] = rs.getObject(columns.get(i).resultIndex);
                }
                list.add(row);
            }
            return list;
        }, queryParams);
        allRows = rows;
        filterRows(searchField != null ? searchField.getText().trim() : "");
    }

    private void filterRows(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            tableModel.setRows(allRows);
            statusLabel.setText("共 " + allRows.size() + " 条记录");
        } else {
            String kw = keyword.toLowerCase();
            List<Object[]> filtered = new ArrayList<>();
            for (Object[] row : allRows) {
                for (Object col : row) {
                    if (col != null && col.toString().toLowerCase().contains(kw)) {
                        filtered.add(row);
                        break;
                    }
                }
            }
            tableModel.setRows(filtered);
            statusLabel.setText("共 " + filtered.size() + " / " + allRows.size() + " 条记录");
        }
    }

    private void showForm(Object[] existingRow) {
        boolean isNew = existingRow == null;
        Map<CrudColumn, JComponent> fields = new LinkedHashMap<>();

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(4, 8, 4, 8);
        g.gridx = 0; g.weightx = 0.3;

        int idx = 0;
        for (CrudColumn col : columns) {
            if (!col.editable) { idx++; continue; }
            g.gridy = idx; g.gridx = 0;
            form.add(new JLabel(col.label + ":"), g);
            g.gridx = 1;

            String curVal = existingRow != null && col.resultIndex <= existingRow.length
                ? (existingRow[col.resultIndex - 1] == null ? "" : existingRow[col.resultIndex - 1].toString())
                : "";

            JComponent input;
            if (col.hasChoices()) {
                JComboBox<String> cb = new JComboBox<>(col.choices());
                cb.setFont(new Font("微软雅黑", Font.PLAIN, 14));
                cb.setSelectedItem(curVal);
                input = cb;
            } else {
                JTextField tf = new JTextField(curVal, 20);
                tf.setFont(new Font("微软雅黑", Font.PLAIN, 14));
                input = tf;
            }
            fields.put(col, input);
            form.add(input, g);
            idx++;
        }

        int result = JOptionPane.showConfirmDialog(this, form,
            isNew ? "新增" : "编辑", JOptionPane.OK_CANCEL_OPTION);
        if (result != JOptionPane.OK_OPTION) return;

        // 收集值 + 日期/必填验证
        Map<String, String> values = new LinkedHashMap<>();
        boolean hasEmpty = false;
        for (var e : fields.entrySet()) {
            JComponent comp = e.getValue();
            String val;
            if (comp instanceof JComboBox<?> cb) {
                val = cb.getSelectedItem() != null ? cb.getSelectedItem().toString() : "";
            } else {
                val = ((JTextField) comp).getText().trim();
            }
            // 日期字段格式校验（字段名含 date 或 time）
            String colName = e.getKey().dbColumn.toLowerCase();
            if (!val.isEmpty() && (colName.contains("date") || colName.contains("time"))) {
                if (!val.matches("\\d{4}-\\d{2}-\\d{2}( \\d{2}:\\d{2}(:\\d{2})?)?")) {
                    JOptionPane.showMessageDialog(this,
                        "日期格式不正确: " + colName + "\n请使用 YYYY-MM-DD 格式\n例如: 2026-05-25",
                        "输入错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }
            if (val.isEmpty() && !e.getKey().dbColumn.equals("remark") && !e.getKey().dbColumn.equals("description")) {
                hasEmpty = true;
            }
            values.put(e.getKey().dbColumn, val);
        }
        if (hasEmpty && isNew) {
            JOptionPane.showMessageDialog(this,
                "存在必填字段为空，请检查后重试。", "校验失败", JOptionPane.WARNING_MESSAGE);
            showForm(existingRow); // 重新打开表单
            return;
        }

        if (isNew) {
            // 附上操作人 + 租户ID
            values.put("created_by", getCurrentUser());
            if (tenantId != null && !tenantId.isEmpty()) {
                values.put("tenant_id", tenantId);
            }
            // 如果缺少 id 字段 + 表主键不是自增 → 自动生成 UUID 主键
            if (!values.containsKey("id") && !hasAutoIncrementId()) {
                values.put("id", java.util.UUID.randomUUID().toString().substring(0, 16));
            }

            String cols = String.join(", ", values.keySet());
            String placeholders = String.join(", ", values.keySet().stream().map(k -> "?").toList());
            try {
                executeWrite(
                    "INSERT INTO " + tableName + " (" + cols + ") VALUES (" + placeholders + ")",
                    values.values().toArray());
            } catch (RuntimeException ex) {
                log.error("新增记录失败", ex);
                JOptionPane.showMessageDialog(this,
                    "新增失败，请检查必填字段是否已填写完整。",
                    "操作失败", JOptionPane.ERROR_MESSAGE);
                return;
            }
        } else {
            String idCol = columns.get(0).dbColumn; // 第一列是 ID
            String idVal = existingRow[0].toString();
            String setClause = String.join(", ", values.keySet().stream().map(k -> k + "=?").toList());
            List<Object> params = new ArrayList<>(values.values());
            params.add(idVal);
            String where = " WHERE " + idCol + "=?";
            if (tenantId != null && !tenantId.isEmpty()) {
                where += " AND tenant_id=?";
                params.add(tenantId);
            }
            executeWrite(
                "UPDATE " + tableName + " SET " + setClause + where,
                params.toArray());
        }
        refresh();
    }

    private void deleteSelected() {
        int row = table.getSelectedRow();
        if (row < 0) { JOptionPane.showMessageDialog(this, "请先选择一行"); return; }
        Object[] data = tableModel.getRow(row);
        String idCol = columns.get(0).dbColumn;
        String idVal = data[0].toString();
        String display = columns.size() > 1 ? data[1].toString() : idVal;

        int confirm = JOptionPane.showConfirmDialog(this,
            "确认删除「" + display + "」？", "确认删除", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            if (tenantId != null && !tenantId.isEmpty()) {
                executeWrite(
                    "DELETE FROM " + tableName + " WHERE " + idCol + "=? AND tenant_id=?", idVal, tenantId);
            } else {
                executeWrite(
                    "DELETE FROM " + tableName + " WHERE " + idCol + "=?", idVal);
            }
            // 审计日志
            try {
                String operator = getCurrentUser();
                String tenantId = TenantContext.getCurrentTenantId();
                executeWrite(
                    "INSERT INTO erp_audit_log (tenant_id, user_id, action, entity_type, entity_id, details_json) VALUES (?,?,?,'DELETE',?,?)",
                    tenantId, operator, tableName, idVal,
                    "{\"deleted_by\":\"" + operator + "\",\"display\":\"" + display + "\"}");
            } catch (Exception e) {
                log.warn("审计日志写入失败(删除操作 audit trail 丢失): {}", e.getMessage());
            }
            refresh();
        }
    }

    // ── 辅助 ──

    /** 写入操作经过 DataRouter（走拦截器链：租户隔离+审计） */
    private int executeWrite(String sql, Object... params) {
        try {
            MicroKernel mk = MicroKernel.getInstance();
            if (mk != null && mk.getDataRouter() != null) {
                return mk.getDataRouter().executeUpdate(sql, params);
            }
        } catch (Exception ignored) {}
        return DatabaseManager.getInstance().executeUpdate(sql, params);
    }

    /** 查询操作经过 DataRouter */
    private <T> T executeRead(String sql, com.erp.db.DatabaseManager.ResultSetHandler<T> handler, Object... params) {
        try {
            MicroKernel mk = MicroKernel.getInstance();
            if (mk != null && mk.getDataRouter() != null) {
                return mk.getDataRouter().executeQuery(sql, handler, params);
            }
        } catch (Exception ignored) {}
        return DatabaseManager.getInstance().executeQuery(sql, rs -> { try { return handler.handle(rs); } catch (java.sql.SQLException e) { throw new RuntimeException(e); } }, params);
    }

    /** 判断当前表是否使用自增 ID（查数据库元数据，不硬编码） */
    private boolean hasAutoIncrementId() {
        if (autoIncTables.contains(tableName)) return true;
        try {
            Boolean has = DatabaseManager.getInstance().executeQuery(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME='id' " +
                "AND EXTRA LIKE '%auto_increment%'",
                rs -> rs.next() && rs.getInt(1) > 0, tableName);
            if (has) autoIncTables.add(tableName);
            return has;
        } catch (Exception e) { return false; }
    }
    private static final java.util.Set<String> autoIncTables = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    private String getCurrentUser() {
        var session = TenantContext.getLoginSession();
        return session != null ? session.username() : "unknown";
    }

    private boolean isCurrentUserAdmin() {
        var s = TenantContext.getLoginSession();
        if (s == null) return false;
        return s.isSystemAdmin() || "tenant_admin".equals(s.roleId()) || "admin".equals(s.roleId());
    }

    // ── 导入导出 ──

    private void exportToCSV() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("导出 CSV");
        chooser.setSelectedFile(new java.io.File(tableName + ".csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        try (java.io.PrintWriter pw = new java.io.PrintWriter(
                new java.io.OutputStreamWriter(
                    new java.io.FileOutputStream(chooser.getSelectedFile()), "UTF-8"))) {
            // BOM
            pw.print('\uFEFF');
            // 表头
            pw.println(columns.stream().map(c -> c.label).reduce((a,b) -> a + "," + b).orElse(""));
            // 数据
            for (int r = 0; r < tableModel.getRowCount(); r++) {
                for (int c = 0; c < columns.size(); c++) {
                    if (c > 0) pw.print(",");
                    Object v = tableModel.getValueAt(r, c);
                    String s = v != null ? v.toString().replace("\"", "\"\"") : "";
                    pw.print("\"" + s + "\"");
                }
                pw.println();
            }
            JOptionPane.showMessageDialog(this, "✅ 导出成功！");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "导出失败: " + e.getMessage());
        }
    }

    private void importFromCSV() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("导入 CSV");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(
                    new java.io.FileInputStream(chooser.getSelectedFile()), "UTF-8"))) {
            // 跳过 BOM
            reader.mark(1);
            if (reader.read() != '\uFEFF') reader.reset();

            String header = reader.readLine();
            if (header == null) { JOptionPane.showMessageDialog(this, "文件为空"); return; }

            String[] colNames = header.replace("\"", "").split(",");
            // 映射列名到 dbColumn
            java.util.Map<String, CrudColumn> colMap = new java.util.LinkedHashMap<>();
            for (CrudColumn c : columns) {
                for (String cn : colNames) {
                    if (c.label.equals(cn.trim()) || c.dbColumn.equals(cn.trim())) {
                        colMap.put(c.dbColumn, c);
                    }
                }
            }

            int count = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                String[] vals = parseCSVLine(line);
                java.util.Map<String, String> rowVals = new java.util.LinkedHashMap<>();
                for (int i = 0; i < Math.min(vals.length, colNames.length); i++) {
                    String cn = colNames[i].trim();
                    for (CrudColumn c : columns) {
                        if (c.label.equals(cn) || c.dbColumn.equals(cn)) {
                            rowVals.put(c.dbColumn, vals[i]);
                        }
                    }
                }
                if (rowVals.isEmpty()) continue;

                rowVals.put("created_by", getCurrentUser());
                if (tenantId != null && !tenantId.isEmpty()) {
                    rowVals.put("tenant_id", tenantId);
                }

                String cols = String.join(", ", rowVals.keySet());
                String phs  = String.join(", ", rowVals.keySet().stream().map(k -> "?").toList());
                DatabaseManager.getInstance().executeUpdate(
                    "INSERT INTO " + tableName + " (" + cols + ") VALUES (" + phs + ")",
                    rowVals.values().toArray());
                count++;
            }
            JOptionPane.showMessageDialog(this, "✅ 成功导入 " + count + " 条记录！");
            refresh();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "导入失败: " + e.getMessage());
        }
    }

    private String[] parseCSVLine(String line) {
        java.util.List<String> result = new java.util.ArrayList<>();
        boolean inQuotes = false;
        StringBuilder sb = new StringBuilder();
        for (char c : line.toCharArray()) {
            if (c == '"') { inQuotes = !inQuotes; continue; }
            if (c == ',' && !inQuotes) { result.add(sb.toString()); sb.setLength(0); continue; }
            sb.append(c);
        }
        result.add(sb.toString());
        return result.toArray(new String[0]);
    }

    // ── 列定义 ──

    public record CrudColumn(String label, String dbColumn, int resultIndex, boolean editable,
                              String[] choices) {
        public CrudColumn(String label, String dbColumn, int resultIndex, boolean editable) {
            this(label, dbColumn, resultIndex, editable, null);
        }
        public static CrudColumn id(String label, String dbColumn) {
            return new CrudColumn(label, dbColumn, 1, false, null);
        }
        public static CrudColumn text(String label, String dbColumn, int index) {
            return new CrudColumn(label, dbColumn, index, true, null);
        }
        public static CrudColumn readOnly(String label, String dbColumn, int index) {
            return new CrudColumn(label, dbColumn, index, false, null);
        }
        /** 下拉选择列（编辑时显示下拉框） */
        public static CrudColumn choices(String label, String dbColumn, int index, String... opts) {
            return new CrudColumn(label, dbColumn, index, true, opts);
        }
        public boolean hasChoices() { return choices != null && choices.length > 0; }
    }

    // ── 表格模型 ──

    private static class CrudTableModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private final CrudColumn[] cols;
        private List<Object[]> rows = new ArrayList<>();
        CrudTableModel(CrudColumn... cols) { this.cols = cols; }
        void setRows(List<Object[]> r) { rows = r; fireTableDataChanged(); }
        Object[] getRow(int i) { return rows.get(i); }
        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c].label; }
        @Override public Object getValueAt(int r, int c) {
            Object[] row = rows.get(r);
            int idx = cols[c].resultIndex;
            return idx > 0 && idx <= row.length ? (row[idx-1] == null ? "" : row[idx-1]) : "";
        }
    }
}
