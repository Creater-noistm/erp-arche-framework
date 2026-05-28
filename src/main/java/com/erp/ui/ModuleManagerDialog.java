package com.erp.ui;

import com.erp.kernel.MicroKernel;
import com.erp.module.ErpModule;
import com.erp.module.ModuleManager;
import com.erp.module.ModuleState;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.*;
import java.awt.*;
import java.util.List;

/**
 * 模块管理对话框 — 查看模块状态、启停模块。
 */
public class ModuleManagerDialog extends JDialog {
    private static final long serialVersionUID = 1L;

    private final MicroKernel kernel;
    private final ModuleManager moduleManager;
    private JTable moduleTable;
    private ModuleTableModel tableModel;

    public ModuleManagerDialog(Frame owner, MicroKernel kernel) {
        super(owner, "模块管理", true);
        this.kernel = kernel;
        this.moduleManager = kernel.getModuleManager();
        initialize();
        refreshData();
    }

    private void initialize() {
        setSize(800, 500);
        setLocationRelativeTo(getOwner());
        setLayout(new BorderLayout(8, 8));

        // 工具栏
        JPanel toolBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        JButton refreshBtn = new JButton("刷新");
        refreshBtn.addActionListener(e -> refreshData());
        toolBar.add(refreshBtn);

        JButton startBtn = new JButton("启动模块");
        startBtn.addActionListener(e -> startSelectedModule());
        toolBar.add(startBtn);

        JButton stopBtn = new JButton("停止模块");
        stopBtn.addActionListener(e -> stopSelectedModule());
        toolBar.add(stopBtn);

        add(toolBar, BorderLayout.NORTH);

        // 表格
        tableModel = new ModuleTableModel();
        moduleTable = new JTable(tableModel);
        moduleTable.setRowHeight(28);
        moduleTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        moduleTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // 列宽
        TableColumnModel colModel = moduleTable.getColumnModel();
        colModel.getColumn(0).setPreferredWidth(180);  // ID
        colModel.getColumn(1).setPreferredWidth(140);  // Name
        colModel.getColumn(2).setPreferredWidth(80);   // Version
        colModel.getColumn(3).setPreferredWidth(60);   // Vendor
        colModel.getColumn(4).setPreferredWidth(80);   // State
        colModel.getColumn(5).setPreferredWidth(100);  // Description

        add(new JScrollPane(moduleTable), BorderLayout.CENTER);

        // 底部
        JPanel bottom = new JPanel(new BorderLayout());
        JLabel info = new JLabel("  状态说明: ● 绿色=运行  ● 灰色=停止  ● 红色=错误");
        info.setFont(info.getFont().deriveFont(11f));
        info.setBorder(new EmptyBorder(4, 8, 4, 8));
        bottom.add(info, BorderLayout.CENTER);

        JButton closeBtn = new JButton("关闭");
        closeBtn.addActionListener(e -> dispose());
        JPanel btnPanel = new JPanel();
        btnPanel.add(closeBtn);
        bottom.add(btnPanel, BorderLayout.EAST);

        add(bottom, BorderLayout.SOUTH);
    }

    private void refreshData() {
        tableModel.setModules(moduleManager.getAllModules());
        tableModel.fireTableDataChanged();
    }

    private void startSelectedModule() {
        int row = moduleTable.getSelectedRow();
        if (row >= 0) {
            String moduleId = (String) tableModel.getValueAt(row, 0);
            moduleManager.restartModule(moduleId);
            refreshData();
        }
    }

    private void stopSelectedModule() {
        int row = moduleTable.getSelectedRow();
        if (row >= 0) {
            String moduleId = (String) tableModel.getValueAt(row, 0);
            int confirm = JOptionPane.showConfirmDialog(this,
                "确认停止模块 " + moduleId + "？", "确认",
                JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                kernel.undeployModule(moduleId);
                refreshData();
            }
        }
    }

    // ── 表格模型 ──

    private static class ModuleTableModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private final String[] columns = {"模块ID", "名称", "版本", "供应商", "状态", "描述"};
        private List<ErpModule> modules = List.of();

        void setModules(List<ErpModule> modules) { this.modules = modules; }

        @Override
        public int getRowCount() { return modules.size(); }

        @Override
        public int getColumnCount() { return columns.length; }

        @Override
        public String getColumnName(int col) { return columns[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            ErpModule m = modules.get(row);
            return switch (col) {
                case 0 -> m.getId();
                case 1 -> m.getName();
                case 2 -> m.getVersion();
                case 3 -> m.getVendor();
                case 4 -> m.getState();
                case 5 -> m.getDescription();
                default -> "";
            };
        }

        @Override
        public Class<?> getColumnClass(int col) {
            return col == 4 ? ModuleState.class : String.class;
        }
    }
}
