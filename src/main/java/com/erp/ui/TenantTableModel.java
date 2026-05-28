package com.erp.ui;

import javax.swing.table.AbstractTableModel;
import java.util.List;

/**
 * 租户管理表格模型 — 接受外部 List<Object[]> 数据源，状态列与到期时间列可编辑。
 */
class TenantTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;

    private final String[] cols;
    private final List<Object[]> data;

    TenantTableModel(String[] cols, List<Object[]> data) {
        this.cols = cols;
        this.data = data;
    }

    @Override
    public int getRowCount() {
        return data.size();
    }

    @Override
    public int getColumnCount() {
        return cols.length;
    }

    @Override
    public String getColumnName(int c) {
        return cols[c];
    }

    @Override
    public Object getValueAt(int r, int c) {
        return data.get(r)[c];
    }

    @Override
    public void setValueAt(Object v, int r, int c) {
        data.get(r)[c] = v;
        fireTableCellUpdated(r, c);
    }

    @Override
    public boolean isCellEditable(int r, int c) {
        return c == 2 || c == 3;
    }
}
