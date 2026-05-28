package com.erp.ui;

import com.erp.db.DatabaseManager;
import com.erp.tenant.LoginSession;
import com.erp.tenant.TenantContext;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 角色与权限管理面板 — 系统管理员专用。
 *
 * 左侧：角色列表
 * 中间：角色权限勾选框
 * 底部：用户角色分配表
 */
public class RoleManagerPanel extends JPanel {

    private static final String[] ALL_PERMISSIONS = {
        "公司管理", "员工管理", "考勤", "统计",
        "产品管理", "入库管理", "出库管理",
        "总账", "应收", "应付", "报表"
    };

    private JList<String> roleList;
    private DefaultListModel<String> roleListModel;
    private final Map<String, JCheckBox> permBoxes = new LinkedHashMap<>();
    private JTable userTable;
    private DefaultTableModel userTableModel;
    private JButton savePermBtn;

    // 缓存
    private final java.util.List<String[]> roleData = new java.util.ArrayList<>();
    private final java.util.Map<String, Set<String>> rolePerms = new HashMap<>();
    private final java.util.List<String[]> userData = new java.util.ArrayList<>();

    public RoleManagerPanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        setBackground(Color.WHITE);

        JLabel title = new JLabel("🔐 角色与权限管理");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18));
        add(title, BorderLayout.NORTH);

        // ── 左：角色列表 ──
        JPanel leftPanel = new JPanel(new BorderLayout(4, 4));
        leftPanel.setBackground(Color.WHITE);
        leftPanel.setPreferredSize(new Dimension(180, 0));
        leftPanel.setBorder(BorderFactory.createTitledBorder("角色"));
        roleListModel = new DefaultListModel<>();
        roleList = new JList<>(roleListModel);
        roleList.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        roleList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) onRoleSelected();
        });
        leftPanel.add(new JScrollPane(roleList), BorderLayout.CENTER);

        // ── 中：权限勾选 ──
        JPanel centerPanel = new JPanel(new BorderLayout(4, 4));
        centerPanel.setBackground(Color.WHITE);
        centerPanel.setBorder(BorderFactory.createTitledBorder("功能权限"));
        JPanel permPanel = new JPanel(new GridBagLayout());
        permPanel.setBackground(Color.WHITE);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridx = 0; gbc.insets = new Insets(2, 8, 2, 8);
        for (int i = 0; i < ALL_PERMISSIONS.length; i++) {
            gbc.gridy = i;
            JCheckBox box = new JCheckBox(ALL_PERMISSIONS[i]);
            box.setFont(new Font("微软雅黑", Font.PLAIN, 12));
            box.setBackground(Color.WHITE);
            permBoxes.put(ALL_PERMISSIONS[i], box);
            permPanel.add(box, gbc);
        }
        centerPanel.add(new JScrollPane(permPanel), BorderLayout.CENTER);

        savePermBtn = new JButton("💾 保存权限");
        savePermBtn.addActionListener(e -> savePermissions());
        savePermBtn.setEnabled(false);
        centerPanel.add(savePermBtn, BorderLayout.SOUTH);

        // ── 布局 ──
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, centerPanel);
        split.setDividerSize(3);
        split.setResizeWeight(0.25);
        add(split, BorderLayout.CENTER);

        // ── 下：用户角色分配 ──
        JPanel bottomPanel = new JPanel(new BorderLayout(4, 4));
        bottomPanel.setBackground(Color.WHITE);
        bottomPanel.setBorder(BorderFactory.createTitledBorder("用户角色分配"));
        userTableModel = new DefaultTableModel(new String[]{"用户名", "姓名", "公司", "角色"}, 0) {
            public boolean isCellEditable(int r, int c) { return c == 3; }
        };
        userTable = new JTable(userTableModel);
        userTable.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        userTable.getTableHeader().setFont(new Font("微软雅黑", Font.BOLD, 12));
        userTable.setRowHeight(24);
        // 角色列用下拉编辑器
        JComboBox<String> roleEditor = new JComboBox<>();
        userTable.getColumnModel().getColumn(3).setCellEditor(new DefaultCellEditor(roleEditor));
        // 监听修改
        userTableModel.addTableModelListener(e -> {
            if (e.getType() == javax.swing.event.TableModelEvent.UPDATE && e.getColumn() == 3) {
                saveUserRole(e.getFirstRow());
            }
        });
        bottomPanel.add(new JScrollPane(userTable), BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        // 加载数据
        loadData();
    }

    private void loadData() {
        loadRoles();
        loadUsers();
    }

    private void loadRoles() {
        roleData.clear();
        rolePerms.clear();
        roleListModel.clear();
        try {
            // 角色列表
            DatabaseManager.getInstance().executeQuery(
                "SELECT id, name, description FROM erp_roles ORDER BY is_system DESC, id",
                rs -> {
                    while (rs.next()) {
                        String id = rs.getString("id");
                        String name = rs.getString("name");
                        roleData.add(new String[]{id, name, rs.getString("description")});
                        roleListModel.addElement(name);
                    }
                    return null;
                });
            // 角色权限
            DatabaseManager.getInstance().executeQuery(
                "SELECT role_id, permission_key FROM erp_role_permissions",
                rs -> {
                    while (rs.next()) {
                        rolePerms.computeIfAbsent(rs.getString("role_id"), k -> new HashSet<>())
                            .add(rs.getString("permission_key"));
                    }
                    return null;
                });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadUsers() {
        userData.clear();
        while (userTableModel.getRowCount() > 0) userTableModel.removeRow(0);
        try {
            DatabaseManager.getInstance().executeQuery(
                "SELECT u.username, u.display_name, COALESCE(t.name,'') as tenant_name, u.role_id " +
                "FROM erp_users u LEFT JOIN erp_tenants t ON t.id=u.tenant_id " +
                "ORDER BY t.name, u.username",
                rs -> {
                    while (rs.next()) {
                        String uname = rs.getString("username");
                        String dname = rs.getString("display_name");
                        String tname = rs.getString("tenant_name");
                        String role = rs.getString("role_id");
                        userData.add(new String[]{uname, dname, tname, role});
                        userTableModel.addRow(new Object[]{uname, dname, tname, role});
                    }
                    return null;
                });
            // 更新角色下拉
            javax.swing.table.TableCellEditor cellEditor = userTable.getColumnModel().getColumn(3).getCellEditor();
            if (cellEditor instanceof DefaultCellEditor dce) {
                @SuppressWarnings("unchecked")
                JComboBox<String> cb = (JComboBox<String>) dce.getComponent();
                cb.removeAllItems();
                for (String[] r : roleData) cb.addItem(r[0]);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void onRoleSelected() {
        int idx = roleList.getSelectedIndex();
        if (idx < 0 || idx >= roleData.size()) {
            savePermBtn.setEnabled(false);
            return;
        }
        savePermBtn.setEnabled(true);
        String roleId = roleData.get(idx)[0];
        Set<String> perms = rolePerms.getOrDefault(roleId, Collections.emptySet());
        boolean isAll = perms.contains("*");
        for (Map.Entry<String, JCheckBox> entry : permBoxes.entrySet()) {
            entry.getValue().setSelected(isAll || perms.contains(entry.getKey()));
        }
    }

    private void savePermissions() {
        int idx = roleList.getSelectedIndex();
        if (idx < 0) return;
        String roleId = roleData.get(idx)[0];
        Set<String> selected = new HashSet<>();
        for (Map.Entry<String, JCheckBox> entry : permBoxes.entrySet()) {
            if (entry.getValue().isSelected()) selected.add(entry.getKey());
        }
        try {
            DatabaseManager.getInstance().executeUpdate(
                "DELETE FROM erp_role_permissions WHERE role_id=?", roleId);
            if (selected.size() == ALL_PERMISSIONS.length) {
                // 全选 = *
                DatabaseManager.getInstance().executeUpdate(
                    "INSERT INTO erp_role_permissions (role_id, permission_key) VALUES (?,?)",
                    roleId, "*");
            } else {
                for (String perm : selected) {
                    DatabaseManager.getInstance().executeUpdate(
                        "INSERT INTO erp_role_permissions (role_id, permission_key) VALUES (?,?)",
                        roleId, perm);
                }
            }
            JOptionPane.showMessageDialog(this, "✅ 权限保存成功！");
            loadRoles();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "保存失败: " + e.getMessage());
        }
    }

    private void saveUserRole(int row) {
        if (row < 0 || row >= userTableModel.getRowCount()) return;
        String username = (String) userTableModel.getValueAt(row, 0);
        String newRole = (String) userTableModel.getValueAt(row, 3);
        try {
            DatabaseManager.getInstance().executeUpdate(
                "UPDATE erp_users SET role_id=? WHERE username=?", newRole, username);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "修改失败: " + e.getMessage());
            loadUsers();
        }
    }
}
