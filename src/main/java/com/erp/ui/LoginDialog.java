package com.erp.ui;

import com.erp.db.DatabaseManager;
import com.erp.tenant.LoginSession;
import com.erp.tenant.TenantContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;

/**
 * 登录对话框 — 用户名全局唯一，自动识别所属公司。
 *
 * 输入用户名 + 密码 → 自动查所属公司，管理员权限由账号标识
 */
public class LoginDialog extends JDialog {

    private static final Logger log = LoggerFactory.getLogger(LoginDialog.class);
    private static final long serialVersionUID = 1L;

    private JTextField usernameField;
    private JPasswordField passwordField;

    private JLabel statusLabel;
    private JButton loginBtn;
    private boolean loginSuccess = false;
    private LoginSession resultSession;

    public LoginDialog() {
        super((Frame) null, "ERP v" + readVersion(), true);
        setSize(440, 420);
        setLocationRelativeTo(null);
        setResizable(false);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Color.WHITE);
        root.setBorder(BorderFactory.createLineBorder(new Color(210, 210, 210)));
        setContentPane(root);

        // ── 顶部品牌区域 ──
        JPanel header = new JPanel() {
            private static final long serialVersionUID = 1L;
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new GradientPaint(0, 0, new Color(42, 82, 152), getWidth(), getHeight(), new Color(30, 60, 120)));
                g2.fillRect(0, 0, getWidth(), getHeight());
                // 装饰圆
                g2.setColor(new Color(255, 255, 255, 15));
                g2.fillOval(-60, -30, 200, 200);
                g2.fillOval(getWidth()-80, getHeight()-40, 120, 120);
            }
        };
        header.setPreferredSize(new Dimension(440, 110));
        header.setLayout(new GridBagLayout());
        JLabel brand = new JLabel("ERP 企业管理系统");
        brand.setFont(new Font("微软雅黑", Font.BOLD, 28));
        brand.setForeground(Color.WHITE);
        JLabel subBrand = new JLabel("v" + readVersion() + " · 微内核多租户架构");
        subBrand.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        subBrand.setForeground(new Color(180, 200, 240));
        JPanel brandPanel = new JPanel(new GridLayout(2, 1, 0, 4));
        brandPanel.setOpaque(false);
        brandPanel.add(brand);
        brandPanel.add(subBrand);
        header.add(brandPanel);
        root.add(header, BorderLayout.NORTH);

        // ── 表单区域 ──
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBackground(Color.WHITE);
        formPanel.setBorder(BorderFactory.createEmptyBorder(32, 45, 24, 45));
        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(5, 0, 5, 0);

        // 用户名输入框
        g.gridy = 0; g.insets = new Insets(2, 0, 2, 0);
        JLabel userIcon = new JLabel("用户名");
        userIcon.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        userIcon.setForeground(new Color(100, 100, 100));
        formPanel.add(userIcon, g);
        g.gridy = 1; g.insets = new Insets(0, 0, 12, 0);
        usernameField = new JTextField();
        usernameField.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        usernameField.setPreferredSize(new Dimension(300, 38));
        usernameField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 200)),
            BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        formPanel.add(usernameField, g);

        // 密码输入框
        g.gridy = 2; g.insets = new Insets(2, 0, 2, 0);
        JLabel pwdIcon = new JLabel("密码");
        pwdIcon.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        pwdIcon.setForeground(new Color(100, 100, 100));
        formPanel.add(pwdIcon, g);
        g.gridy = 3; g.insets = new Insets(0, 0, 16, 0);
        passwordField = new JPasswordField();
        passwordField.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        passwordField.setPreferredSize(new Dimension(300, 38));
        passwordField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 200)),
            BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        formPanel.add(passwordField, g);

        // 状态提示
        statusLabel = new JLabel(" ");
        statusLabel.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        statusLabel.setForeground(new Color(220, 53, 69));
        g.gridy = 4; g.insets = new Insets(0, 0, 0, 0);
        formPanel.add(statusLabel, g);

        root.add(formPanel, BorderLayout.CENTER);

        // ── 按钮区域 ──
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 10));
        btnPanel.setBackground(new Color(248, 248, 248));
        btnPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 16, 0));

        loginBtn = new JButton("登  录") {
            private static final long serialVersionUID = 1L;
            @Override public Dimension getPreferredSize() { return new Dimension(220, 42); }
        };
        loginBtn.setFont(new Font("微软雅黑", Font.BOLD, 15));
        loginBtn.setBackground(new Color(42, 82, 152));
        loginBtn.setForeground(Color.WHITE);
        loginBtn.setFocusPainted(false);
        loginBtn.setBorder(BorderFactory.createEmptyBorder(10, 40, 10, 40));
        loginBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        loginBtn.addActionListener(e -> doLogin());
        btnPanel.add(loginBtn);

        root.add(btnPanel, BorderLayout.SOUTH);

        // 回车登录
        getRootPane().setDefaultButton(loginBtn);
        passwordField.addActionListener(e -> doLogin());
    }

    private JLabel createFieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("微软雅黑", Font.BOLD, 13));
        label.setForeground(new Color(60, 60, 60));
        return label;
    }

    public boolean isLoginSuccess() { return loginSuccess; }

    public LoginSession getLoginSession() { return resultSession; }

    private void doLogin() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (username.isEmpty() || password.isEmpty()) {
            showError("请输入用户名和密码");
            return;
        }

        loginBtn.setEnabled(false);
        statusLabel.setForeground(new Color(100, 100, 100));
        statusLabel.setText("正在验证...");

        try {
            DatabaseManager db = DatabaseManager.getInstance();

            // 查用户（限制跨租户重复用户名）
            String[] userInfo = db.executeQuery(
                "SELECT id, tenant_id, password_hash, display_name, is_system_admin, COALESCE(role_id,'') as role_id " +
                "FROM erp_users WHERE username = ? AND status = 'ACTIVE' " +
                "ORDER BY is_system_admin DESC, created_at DESC LIMIT 1",
                rs -> {
                    if (rs.next()) {
                        return new String[]{
                            rs.getString("id"), rs.getString("tenant_id"),
                            rs.getString("password_hash"), rs.getString("display_name"),
                            String.valueOf(rs.getBoolean("is_system_admin")), rs.getString("role_id")
                        };
                    }
                    return null;
                }, username);

            if (userInfo == null) {
                logFailed("用户名不存在");
                showError("用户名或密码错误");
                return;
            }

            String userId = userInfo[0];
            String tenantId = userInfo[1];
            String passwordHash = userInfo[2];
            String displayName = userInfo[3];
            boolean isSysAdmin = Boolean.parseBoolean(userInfo[4]);
            String roleId = userInfo[5];

            // 验证密码（BCrypt 或明文兼容）
            if (!checkPassword(password, passwordHash, username)) {
                logFailed("密码错误");
                showError("用户名或密码错误");
                return;
            }

            // 系统管理员不归属具体公司，跳过公司查询
            String tenantName;
            if (isSysAdmin) {
                tenantName = "系统管理";
            } else {
                String[] tenantInfo = db.executeQuery(
                    "SELECT name, status FROM erp_tenants WHERE id = ?",
                    rs -> rs.next() ? new String[]{rs.getString("name"), rs.getString("status")} : null,
                    tenantId);

                if (tenantInfo == null) {
                    showError("所属公司不存在，请联系管理员");
                    return;
                }
                tenantName = tenantInfo[0];
                String tenantStatus = tenantInfo[1];

                // 检查公司状态
                if (!"ACTIVE".equals(tenantStatus)) {
                    db.executeUpdate(
                        "UPDATE erp_users SET status='DISABLED' WHERE tenant_id=? AND status='ACTIVE'", tenantId);
                    String reason = "TRIAL".equals(tenantStatus) ? "该公司处于试用期，暂未开通" : "该公司已被停用，无法登录";
                    showError(reason);
                    return;
                }
            }

            // 创建会话
            resultSession = new LoginSession(
                tenantId, tenantName, userId, username, displayName, roleId, isSysAdmin);
            TenantContext.setLoginSession(resultSession);
            db.executeUpdate(
                "INSERT INTO erp_login_log (user_id, tenant_id, username, login_result) VALUES (?,?,?,'SUCCESS')",
                userId, tenantId, username);
            loginSuccess = true;
            log.info("✅ {} 登录 {} @ {} (角色: {})", username, tenantName, tenantId, roleId);
            dispose();
        } catch (Exception e) {
            log.error("登录失败", e);
            showError("系统错误: " + e.getMessage());
        } finally {
            loginBtn.setEnabled(true);
        }
    }

    private void logFailed(String reason) {
        try {
            DatabaseManager.getInstance().executeUpdate(
                "INSERT INTO erp_login_log (user_id, tenant_id, username, login_result, fail_reason) " +
                "VALUES ('-','-',?, 'FAILURE', ?)",
                usernameField.getText().trim(), reason);
        } catch (Exception e) {
            log.warn("登录失败审计记录写入失败: {}", e.getMessage());
        }
    }

    private void showError(String msg) {
        statusLabel.setText("❌ " + msg);
        statusLabel.setForeground(new Color(211, 47, 47));
    }

    private boolean checkPassword(String input, String stored, String username) {
        // 1. BCrypt hash
        if (stored.startsWith("$2a$")) {
            return org.mindrot.jbcrypt.BCrypt.checkpw(input, stored);
        }
        // 2. 明文匹配 + 自动升级
        if (input.equals(stored)) {
            // 后台升级为 BCrypt
            String hashed = org.mindrot.jbcrypt.BCrypt.hashpw(input, org.mindrot.jbcrypt.BCrypt.gensalt());
            try {
                DatabaseManager.getInstance().executeUpdate(
                    "UPDATE erp_users SET password_hash=? WHERE username=?", hashed, username);
            } catch (Exception e) {
                log.warn("密码哈希升级失败(user={}): {}", username, e.getMessage());
            }
            return true;
        }
        return false;
    }

    private static String readVersion() {
        try {
            String v = java.nio.file.Files.readString(
                java.nio.file.Paths.get("version.txt")).trim();
            return v.isEmpty() ? "1.0.0" : v;
        } catch (Exception e) { return "1.0.0"; }
    }
}
