package com.erp.ui;

import com.erp.kernel.MicroKernel;
import com.erp.module.ErpModule;
import com.erp.module.ModuleManager;
import com.erp.tenant.LoginSession;
import com.erp.tenant.TenantContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.tree.*;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.SwingUtilities;
import javax.swing.JDialog;
import javax.swing.JTextField;
import javax.swing.event.DocumentListener;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;
import com.erp.db.DatabaseManager;

/**
 * ═══════════════════════════════════════════════════════════════
 * FrameworkWindow — 始祖ERP主窗口
 *
 * 设计特色：
 * - 完全由模块贡献驱动界面生成
 * - 左侧模块导航树（自动从已部署模块构建）
 * - 右侧为模块面板容器（Tab页）
 * - 可停靠工具栏
 * - 底部状态栏显示系统状态
 * - 支持主题切换
 *
 * 不硬编码任何业务功能 —— 所有菜单、面板、工具栏
 * 均由已部署模块贡献，真正的"空壳"框架。
 * ═══════════════════════════════════════════════════════════════
 */
public class FrameworkWindow extends JFrame {
    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(FrameworkWindow.class);

    /** 静态实例，方便模块代码访问 */
    public static FrameworkWindow INSTANCE;

    private final MicroKernel kernel;
    private final ModuleManager moduleManager;

    /* 主布局组件 */
    private JTree moduleTree;
    private DefaultTreeModel treeModel;
    private ClosableTabbedPane contentPane;
    private JPanel toolbarPanel;
    private JLabel statusLabel;

    /* 已打开的面板索引 */
    private final Map<String, JPanel> openPanels = new LinkedHashMap<>();

    private final LoginSession loginSession;

    public FrameworkWindow(MicroKernel kernel, LoginSession loginSession) {
        this.kernel = kernel;
        this.moduleManager = kernel.getModuleManager();
        this.loginSession = loginSession;
        INSTANCE = this;
        log.info("FrameworkWindow: user={}, isSystemAdmin={}",
            loginSession != null ? loginSession.username() : "null",
            loginSession != null ? loginSession.isSystemAdmin() : false);
        initializeWindow();
        buildUI();
        registerGlobalListeners();
        updateStatusBar();
        startAutoRefresh();
        startNotificationPoller();
        startVersionChecker();
    }

    private void initializeWindow() {
        setTitle("ERP v" + readLocalVersion() + " · " + loginSession.tenantName() + "  —  " + loginSession.displayName());
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { shutdown(); }
        });
        setSize(1400, 900);
        setMinimumSize(new Dimension(1024, 600));
        setLocationRelativeTo(null);

        // 应用主题
        applyTheme(kernel.getConfig().getUiTheme());

        log.info("FrameworkWindow initialized");
    }

    private void buildUI() {
        // 主布局：BorderLayout
        JPanel mainPanel = new JPanel(new BorderLayout(0, 0));
        setContentPane(mainPanel);

        // ── 顶部：菜单栏 ──
        setJMenuBar(buildMenuBar());

        // ── 顶部：工具栏 ──
        toolbarPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
        toolbarPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY),
            new EmptyBorder(4, 8, 4, 8)
        ));
        toolbarPanel.setBackground(new Color(245, 245, 245));
        mainPanel.add(toolbarPanel, BorderLayout.NORTH);

        // ── 中央分割：左侧导航树 + 右侧内容区 ──
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerSize(4);
        splitPane.setResizeWeight(0.18); // 左侧占 18%

        // 左侧：模块导航树
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setPreferredSize(new Dimension(240, 0));

        JLabel navTitle = new JLabel("  功能导航", SwingConstants.LEFT);
        navTitle.setFont(navTitle.getFont().deriveFont(Font.BOLD, 13));
        navTitle.setBorder(new EmptyBorder(8, 12, 8, 8));
        navTitle.setOpaque(true);
        navTitle.setBackground(new Color(230, 230, 230));
        leftPanel.add(navTitle, BorderLayout.NORTH);

        buildModuleTree();
        JScrollPane treeScroll = new JScrollPane(moduleTree);
        treeScroll.setBorder(null);
        leftPanel.add(treeScroll, BorderLayout.CENTER);

        splitPane.setLeftComponent(leftPanel);

        // 右侧：Tab 面板
        contentPane = new ClosableTabbedPane();
        contentPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        contentPane.setBorder(new EmptyBorder(4, 4, 4, 4));

        // 默认欢迎面板
        contentPane.addTab("🏠 首页", createWelcomePanel(), false);

        // 监听标签关闭事件 → 清理缓存
        contentPane.addContainerListener(new java.awt.event.ContainerAdapter() {
            @Override public void componentRemoved(java.awt.event.ContainerEvent e) {
                openPanels.entrySet().removeIf(entry -> entry.getValue() == e.getChild());
            }
        });

        splitPane.setRightComponent(contentPane);
        mainPanel.add(splitPane, BorderLayout.CENTER);

        // ── 底部：状态栏 ──
        statusLabel = new JLabel(" ");
        statusLabel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY),
            new EmptyBorder(3, 12, 3, 12)
        ));
        statusLabel.setFont(statusLabel.getFont().deriveFont(11f));
        statusLabel.setBackground(new Color(240, 240, 240));
        statusLabel.setOpaque(true);
        mainPanel.add(statusLabel, BorderLayout.SOUTH);
    }

    // ── 模块导航树 ──

    private void buildModuleTree() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("ERP 系统");

        // 系统管理员 → 只显示系统管理导航
        if (loginSession != null && loginSession.isSystemAdmin()) {
            DefaultMutableTreeNode sysAdminNode = new DefaultMutableTreeNode("🚀 系统管理");
            // 公司管理 → 回到首页 Dashboard
            addMenuNode(sysAdminNode, new String[]{"🏢 公司管理"}, 0, () -> {
                closeAllPanels();
                JPanel dashboard = createAdminDashboard();
                contentPane.addTab("🏠 首页", dashboard, false);
                openPanels.put("🏠 首页", dashboard);
                contentPane.setSelectedComponent(dashboard);
            }, "system");
            addMenuNode(sysAdminNode, new String[]{"⚙ 模块管理"}, 0, () -> showModuleManager(), "system");
            addMenuNode(sysAdminNode, new String[]{"🔧 系统配置"}, 0, () -> showConfigDialog(), "system");
            root.add(sysAdminNode);
        } else {
            // 普通用户 — 按模块分组构建导航树
            List<ErpModule> modules = moduleManager.getModulesInStartOrder();
            for (ErpModule module : modules) {
                DefaultMutableTreeNode moduleNode = new DefaultMutableTreeNode(
                    new ModuleTreeNode(module.getName(), module.getId(), "module"));
                root.add(moduleNode);

                Map<String, Runnable> menus = module.getMenuContributions();
                String roleId = loginSession != null ? loginSession.roleId() : "";
                for (Map.Entry<String, Runnable> entry : menus.entrySet()) {
                    String menuPath = entry.getKey();
                    if (!isMenuAllowedForRole(menuPath, roleId)) continue;
                    String[] parts = menuPath.split("/");
                    addMenuNode(moduleNode, parts, 0, entry.getValue(), module.getId());
                }
            }
        }

        treeModel = new DefaultTreeModel(root);
        moduleTree = new JTree(treeModel);
        moduleTree.setRootVisible(true);
        moduleTree.setShowsRootHandles(true);
        moduleTree.setRowHeight(26);
        moduleTree.setFont(moduleTree.getFont().deriveFont(12f));

        // 点击事件
        moduleTree.addTreeSelectionListener(e -> {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode)
                moduleTree.getLastSelectedPathComponent();
            if (node == null) return;
            Object userObj = node.getUserObject();
            if (userObj instanceof MenuActionNode actionNode) {
                actionNode.action.run();
            } else if (userObj instanceof ModuleTreeNode moduleNode) {
                openModulePanel(moduleNode.moduleId);
            }
        });

        // 双击展开/折叠
        moduleTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    TreePath path = moduleTree.getPathForLocation(e.getX(), e.getY());
                    if (path != null) {
                        DefaultMutableTreeNode node =
                            (DefaultMutableTreeNode) path.getLastPathComponent();
                        if (node.isLeaf() && node.getUserObject() instanceof MenuActionNode actionNode) {
                            actionNode.action.run();
                        }
                    }
                }
            }
        });

        // 展开第一级节点
        for (int i = 0; i < moduleTree.getRowCount(); i++) {
            moduleTree.expandRow(i);
        }
    }

    // 角色权限缓存（懒加载）
    private static Map<String, Set<String>> rolePermsCache = null;

    private static boolean isMenuAllowedForRole(String menuPath, String roleId) {
        if (roleId == null || roleId.isEmpty()) return true;
        if ("admin".equals(roleId) || "tenant_admin".equals(roleId)) return true;

        // 懒加载权限映射
        if (rolePermsCache == null) {
            rolePermsCache = new HashMap<>();
            try {
                DatabaseManager.getInstance().executeQuery(
                    "SELECT role_id, permission_key FROM erp_role_permissions",
                    rs -> {
                        while (rs.next()) {
                            rolePermsCache.computeIfAbsent(
                                rs.getString("role_id"), k -> new HashSet<>())
                                .add(rs.getString("permission_key"));
                        }
                        return null;
                    });
            } catch (Exception e) {
                log.warn("加载角色权限映射失败 — 菜单权限检查将回退为拒绝", e);
            }
        }

        Set<String> perms = rolePermsCache.get(roleId);
        if (perms == null) return false;
        if (perms.contains("*")) return true; // 通配符 = 全部允许

        String topCategory = menuPath.split("/")[0];
        return perms.contains(topCategory);
    }

    private void addMenuNode(DefaultMutableTreeNode parent, String[] parts,
                              int index, Runnable action, String moduleId) {
        if (index >= parts.length) return;

        String current = parts[index].trim();
        if (current.isEmpty()) return;

        // 检查是否已存在同名的子节点
        for (int i = 0; i < parent.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(i);
            Object userObj = child.getUserObject();
            String name;
            if (userObj instanceof MenuActionNode mn) name = mn.displayName;
            else if (userObj instanceof ModuleTreeNode mn) name = mn.displayName;
            else name = userObj.toString();

            if (name.equals(current)) {
                if (index == parts.length - 1 && userObj instanceof DefaultMutableTreeNode) {
                    // 叶子节点 - 替换为动作
                    child.setUserObject(new MenuActionNode(current, action, moduleId));
                } else {
                    addMenuNode(child, parts, index + 1, action, moduleId);
                }
                return;
            }
        }

        // 创建新节点
        DefaultMutableTreeNode newNode;
        if (index == parts.length - 1) {
            newNode = new DefaultMutableTreeNode(
                new MenuActionNode(current, action, moduleId));
        } else {
            newNode = new DefaultMutableTreeNode(current);
            addMenuNode(newNode, parts, index + 1, action, moduleId);
        }
        parent.add(newNode);
    }

    // ── 菜单栏 ──

    private JMenuBar buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // 系统菜单
        JMenu sysMenu = new JMenu("系统");
        sysMenu.add(new JMenuItem(new AbstractAction("模块管理...") {
            public void actionPerformed(ActionEvent e) { showModuleManager(); }
        }));
        // 仅系统管理员可见（防止暴露客户信息）
        if (loginSession != null && loginSession.isSystemAdmin()) {
            sysMenu.add(new JMenuItem(new AbstractAction("公司管理...") {
                public void actionPerformed(ActionEvent e) { showTenantManager(); }
            }));
        }
        sysMenu.addSeparator();
        sysMenu.add(new JMenuItem(new AbstractAction("系统配置...") {
            public void actionPerformed(ActionEvent e) { showConfigDialog(); }
        }));
        sysMenu.addSeparator();
        sysMenu.add(new JMenuItem(new AbstractAction("退出") {
            public void actionPerformed(ActionEvent e) { shutdown(); }
        }));
        menuBar.add(sysMenu);

        // 视图菜单
        JMenu viewMenu = new JMenu("视图");
        viewMenu.add(new JMenuItem(new AbstractAction("刷新模块树") {
            public void actionPerformed(ActionEvent e) { refreshModuleTree(); }
        }));
        viewMenu.add(new JMenuItem(new AbstractAction("关闭所有面板") {
            public void actionPerformed(ActionEvent e) { closeAllPanels(); }
        }));
        menuBar.add(viewMenu);

        // 工具菜单
        JMenu toolsMenu = new JMenu("工具");
        toolsMenu.add(new JMenuItem(new AbstractAction("数据模型浏览器...") {
            public void actionPerformed(ActionEvent e) { showDataModelBrowser(); }
        }));
        toolsMenu.add(new JMenuItem(new AbstractAction("事件监控器...") {
            public void actionPerformed(ActionEvent e) { showEventMonitor(); }
        }));
        menuBar.add(toolsMenu);

        // 帮助菜单
        JMenu helpMenu = new JMenu("帮助");
        helpMenu.add(new JMenuItem(new AbstractAction("关于始祖ERP") {
            public void actionPerformed(ActionEvent e) { showAboutDialog(); }
        }));
        helpMenu.add(new JMenuItem(new AbstractAction("模块开发者指南") {
            public void actionPerformed(ActionEvent e) { showDevGuide(); }
        }));
        menuBar.add(helpMenu);

        return menuBar;
    }

    // ── 面板管理 ──

    /** 打开模块的面板 */
    public void openModulePanel(String moduleId) {
        ErpModule module = moduleManager.getModule(moduleId);
        if (module == null) return;

        Map<String, JPanel> panels = module.getPanelContributions();
        for (Map.Entry<String, JPanel> entry : panels.entrySet()) {
            String title = entry.getKey();
            if (!openPanels.containsKey(title)) {
                JPanel panel = entry.getValue();
                contentPane.addTab(title, panel);
                openPanels.put(title, panel);
                contentPane.setSelectedComponent(panel);
                log.debug("Opened panel: {}", title);
            } else {
                // 切换到已打开的面板
                JPanel existing = openPanels.get(title);
                contentPane.setSelectedComponent(existing);
            }
        }
    }

    /** 在内容区打开一个功能面板（Tab页） */
    public void openContentTab(String title, JPanel panel) {
        if (openPanels.containsKey(title)) {
            JPanel existing = openPanels.get(title);
            contentPane.setSelectedComponent(existing);
            return;
        }
        contentPane.addTab(title, panel);
        openPanels.put(title, panel);
        contentPane.setSelectedComponent(panel);
        log.debug("Opened content tab: {}", title);
    }

    /** 关闭所有打开的面板 */
    public void closeAllPanels() {
        contentPane.removeAll();
        openPanels.clear();
        contentPane.addTab("🏠 首页", createWelcomePanel(), false);
    }

    // ── 定时自动刷新 ──

    /** 每10秒刷新当前面板数据 */
    private void startAutoRefresh() {
        javax.swing.Timer timer = new javax.swing.Timer(10_000, e -> {
            java.awt.Component comp = contentPane.getSelectedComponent();
            if (comp instanceof CrudPanel crud) {
                crud.refresh();
            } else if (comp instanceof SalesPanel sp) {
                sp.refresh();
            } else if (loginSession != null && loginSession.isSystemAdmin()
                    && comp instanceof JPanel) {
                // 管理员首页 — 重新加载租户数据
                if (tenantTableModel != null) {
                    loadTenantData(tenantSearchField != null ?
                        tenantSearchField.getText().trim() : null);
                }
            }
        });
        backgroundTimers.add(timer);
        timer.start();
    }

    // ── 服务器消息通知轮询 ──

    private final java.util.Set<String> seenNotifications = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    private void startNotificationPoller() {
        loadSeenNotifications();
        javax.swing.Timer timer = new javax.swing.Timer(30_000, e -> checkNotifications());
        backgroundTimers.add(timer);
        timer.start();
        javax.swing.SwingUtilities.invokeLater(this::checkNotifications);
    }

    private void loadSeenNotifications() {
        try {
            java.nio.file.Path p = resolvePath("notifications.seen");
            if (java.nio.file.Files.exists(p)) {
                java.util.List<String> lines = java.nio.file.Files.readAllLines(p);
                for (String line : lines) {
                    String id = line.trim();
                    if (!id.isEmpty()) seenNotifications.add(id);
                }
                // 超过 200 条则截断，防止文件无限增长
                if (lines.size() > 200) {
                    java.util.List<String> tail = lines.subList(lines.size() - 200, lines.size());
                    java.nio.file.Files.write(p, tail);
                }
            }
        } catch (Exception e) {
            System.err.println("[Notify] load failed: " + e.getMessage());
        }
    }

    private void saveSeenNotification(String id) {
        try {
            java.nio.file.Path p = resolvePath("notifications.seen");
            java.nio.file.Files.write(p,
                id.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception e) {
            System.err.println("[Notify] save failed: " + e.getMessage());
        }
    }

    // ── 窗口关闭时停止所有 Timer ──
    @Override public void dispose() {
        for (javax.swing.Timer t : backgroundTimers) t.stop();
        backgroundTimers.clear();
        super.dispose();
    }
    private final java.util.List<javax.swing.Timer> backgroundTimers = new java.util.ArrayList<>();

    private void checkNotifications() {
        try {
            java.util.List<String[]> fresh = new java.util.ArrayList<>();
            com.erp.db.DatabaseManager.getInstance().executeQuery(
                "SELECT id, title, message FROM erp_notifications " +
                "WHERE (target_tenant='*' OR target_tenant=?) " +
                "ORDER BY created_at DESC LIMIT 20",
                rs -> {
                    while (rs.next()) {
                        fresh.add(new String[]{
                            rs.getString("id"), rs.getString("title"), rs.getString("message")});
                    }
                    return null;
                },
                TenantContext.getCurrentTenantId());

            // 只弹未见过的新通知（跨重启持久化保存到文件）
            for (String[] row : fresh) {
                String id = row[0];
                if (seenNotifications.contains(id)) continue;
                seenNotifications.add(id);
                saveSeenNotification(id);
                String title = row[1], msg = row[2];
                javax.swing.SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(this, msg != null ? msg : "", "📢 " + title, JOptionPane.INFORMATION_MESSAGE));
            }
        } catch (Exception ex) {
            // 静默失败，不打扰用户
        }
    }

    // ── 自动版本更新 ──

    private String currentVersion = "1.0.0";

    private String readLocalVersion() {
        try {
            java.nio.file.Path p = resolvePath("version.txt");
            if (java.nio.file.Files.exists(p)) {
                String v = java.nio.file.Files.readString(p).trim();
                return v.isEmpty() ? "1.0.0" : v;
            }
        } catch (Exception e) { /* fall through */ }
        return "1.0.0";
    }

    /** 解析相对路径：JAR目录 → class目录 → 当前工作目录 → 向上搜3层 */
    private static java.nio.file.Path resolvePath(String name) {
        try {
            String cp = FrameworkWindow.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI().getPath();
            if (cp != null && cp.matches("/[A-Za-z]:/.*")) cp = cp.substring(1);
            java.nio.file.Path p = java.nio.file.Paths.get(cp);
            if (java.nio.file.Files.isRegularFile(p)) p = p.getParent();
            // 从 class 目录向上搜 3 层
            for (int i = 0; i < 3 && p != null; i++) {
                java.nio.file.Path resolved = p.resolve(name);
                if (java.nio.file.Files.exists(resolved)) return resolved;
                p = p.getParent();
            }
        } catch (Exception ignored) {}
        return java.nio.file.Paths.get(name);
    }

    private void startVersionChecker() {
        // 启动时立即检查一次
        SwingUtilities.invokeLater(this::checkVersionUpdate);
        // 每30秒检查一次
        javax.swing.Timer timer = new javax.swing.Timer(30_000, e -> checkVersionUpdate());
        backgroundTimers.add(timer);
        timer.start();
    }

    private static String ignoredVersion = "";

    private void checkVersionUpdate() {
        try {
            ReleaseRecord rel = com.erp.db.DatabaseManager.getInstance().executeQuery(
                "SELECT version, jar_url, notes, jar_data FROM erp_releases ORDER BY id DESC LIMIT 1",
                rs -> { if (!rs.next()) return null;
                    ReleaseRecord r = new ReleaseRecord(); r.version = rs.getString("version");
                    r.jarUrl = rs.getString("jar_url"); r.notes = rs.getString("notes");
                    r.jarData = rs.getBytes("jar_data"); return r; });
            if (rel == null) return;

            // 跳过已忽略的版本或已是最新版
            if (rel.version.equals(ignoredVersion)) return;
            // 每次检查时重新读取本地版本文件（兼容 _update.bat 重启后版本号变化）
            String localVer = readLocalVersion();
            if (localVer != null && compareVersion(rel.version, localVer) <= 0) return;

            // 是 JAR 运行还是源码运行？
            boolean isJar = getCurrentJarPath() != null;
            boolean hasUpdate = rel.jarData != null || (rel.jarUrl != null && !rel.jarUrl.isEmpty());

            if (isJar && hasUpdate) {
                // ── JAR 环境 + 有更新数据 → 自动升级 ──
                int choice = JOptionPane.showConfirmDialog(this,
                    "🆕 发现新版本 v" + rel.version + "\n\n" +
                    (rel.notes != null ? rel.notes : "") +
                    "\n\n是否立即更新？",
                    "自动更新", JOptionPane.YES_NO_OPTION);
                if (choice != JOptionPane.YES_OPTION) {
                    ignoredVersion = rel.version; // 记住，不再弹出
                    return;
                }
                doUpdate(rel);
            } else if (!isJar) {
                // ── 源码/IDE 环境 → 仅提示一次，不更新 ──
                JOptionPane.showMessageDialog(this,
                    "🆕 新版本 v" + rel.version + " 已发布\n\n" +
                    (rel.notes != null ? rel.notes : "") +
                    "\n\n当前为开发环境，不会自动更新。\n请重新编译源码获取最新功能。",
                    "新版本可用（源码环境）", JOptionPane.INFORMATION_MESSAGE);
                ignoredVersion = rel.version; // 只提示一次
            } else {
                // ── JAR 环境但无更新数据 → 仅通知 ──
                JOptionPane.showMessageDialog(this,
                    "🆕 发现新版本 v" + rel.version + "\n\n" +
                    (rel.notes != null ? rel.notes : "") +
                    "\n\n请联系管理员获取安装包。",
                    "新版本可用", JOptionPane.INFORMATION_MESSAGE);
                ignoredVersion = rel.version;
            }
        } catch (Exception ex) {
            log.warn("Version check failed: {}", ex.getMessage());
        }
    }

    private void doUpdate(ReleaseRecord rel) {
        try {
            java.nio.file.Path currentJar = getCurrentJarPath();
            if (currentJar == null) {
                JOptionPane.showMessageDialog(this, "无法定位当前程序，请手动更新。");
                return;
            }
            java.nio.file.Path dir = currentJar.getParent();
            // 新 JAR 写到同目录的 .new 文件
            java.nio.file.Path newJar = dir.resolve("erp-app.jar.new");
            if (rel.jarData != null) {
                java.nio.file.Files.write(newJar, rel.jarData);
            } else {
                try (java.io.InputStream in = new java.net.URL(rel.jarUrl).openStream()) {
                    java.nio.file.Files.copy(in, newJar, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
            java.nio.file.Files.writeString(dir.resolve("version.txt"), rel.version);

            // 写批处理：等旧进程退出 → 替换 JAR → 启动新 JAR
            String batPath = dir.resolve("_update.bat").toString();
            String javaExe = System.getProperty("java.home") + java.io.File.separator + "bin" + java.io.File.separator + "javaw";
            try (java.io.PrintWriter pw = new java.io.PrintWriter(batPath, "GBK")) {
                String dirPath = dir.toString();
                pw.println("@echo off");
                pw.println("cd /d \"" + dirPath + "\"");
                pw.println(":wait");
                pw.println("timeout /t 2 /nobreak >nul");
                pw.println("if exist \"" + dirPath + "\\erp-app.jar.new\" (");
                pw.println("  del /f \"" + dirPath + "\\erp-app.jar\" 2>nul");
                pw.println("  move /y \"" + dirPath + "\\erp-app.jar.new\" \"" + dirPath + "\\erp-app.jar\"");
                pw.println(")");
                pw.println("start \"\" /D \"" + dirPath + "\" \"" + javaExe + "\" -jar \"" + dirPath + "\\erp-app.jar\"");
                pw.println("del \"%~f0\"");
            }
            // 启动批处理后退出
            Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", "/min", "\"\"", batPath}, null, dir.toFile());
            JOptionPane.showMessageDialog(this, "✅ 更新完成！即将重启。");
            System.exit(0);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "更新失败: " + e.getMessage());
        }
    }

    /** 数字版号比较：1.10.0 > 1.9.0 */
    private static int compareVersion(String a, String b) {
        String[] pa = a.split("\\."), pb = b.split("\\.");
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            int va = i < pa.length ? Integer.parseInt(pa[i]) : 0;
            int vb = i < pb.length ? Integer.parseInt(pb[i]) : 0;
            if (va != vb) return va - vb;
        }
        return 0;
    }

    private java.nio.file.Path getCurrentJarPath() {
        // 方式1：从 CodeSource 定位（运行 JAR 时有效）
        try {
            java.net.URI uri = getClass().getProtectionDomain().getCodeSource().getLocation().toURI();
            // Windows 下 URI 可能是 /D:/path/ 格式，需修复
            String path = uri.getPath();
            if (path != null && path.matches("/[A-Za-z]:/.*")) {
                path = path.substring(1); // 去掉前导 /
            }
            java.nio.file.Path p = java.nio.file.Paths.get(path);
            if (java.nio.file.Files.isRegularFile(p) && p.toString().endsWith(".jar")) return p.toAbsolutePath();
        } catch (Exception ignored) {}
        // 方式2：在当前目录找 erp-app.jar
        try {
            java.nio.file.Path p = java.nio.file.Paths.get("erp-app.jar").toAbsolutePath();
            if (java.nio.file.Files.isRegularFile(p)) return p;
        } catch (Exception ignored) {}
        return null;
    }

    private void restartApp() {
        String javaExe = System.getProperty("java.home") + java.io.File.separator + "bin" + java.io.File.separator + "javaw";
        java.nio.file.Path jarPath = getCurrentJarPath();
        String jarName = jarPath != null ? jarPath.getFileName().toString() : "erp-app.jar";
        java.io.File dir = jarPath != null ? jarPath.getParent().toFile() : new java.io.File(".");
        try {
            new ProcessBuilder(javaExe, "-jar", jarName).directory(dir).start();
        } catch (Exception e) { log.error("Restart failed: {}", e.getMessage()); }
        System.exit(0);
    }

    private void publishNewVersion() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择要发布的 JAR 文件");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JAR 文件 (*.jar)", "jar"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        java.io.File jarFile = chooser.getSelectedFile();

        String ver = JOptionPane.showInputDialog(this, "请输入新版本号 (如 1.0.2)：", "发布新版本", JOptionPane.QUESTION_MESSAGE);
        if (ver == null || ver.trim().isEmpty()) return;
        ver = ver.trim();

        String notes = JOptionPane.showInputDialog(this, "请输入更新说明：", "发布新版本", JOptionPane.QUESTION_MESSAGE);
        if (notes == null || notes.trim().isEmpty()) notes = "新版本发布";

        try {
            byte[] jarData = java.nio.file.Files.readAllBytes(jarFile.toPath());
            com.erp.db.DatabaseManager.getInstance().executeUpdate(
                "INSERT INTO erp_releases (version, jar_data, notes) VALUES (?,?,?)",
                ver, jarData, notes);
            JOptionPane.showMessageDialog(this,
                "✅ 版本 v" + ver + " 已发布！\n所有客户端将在30秒内收到更新通知。", "发布成功", JOptionPane.INFORMATION_MESSAGE);
            currentVersion = null; // 下次检查时检测到新版本
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "发布失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ── 欢迎面板 ──

    private JPanel createWelcomePanel() {
        // 系统管理员 → 公司管理面板
        if (loginSession != null && loginSession.isSystemAdmin()) {
            return createAdminDashboard();
        }
        // 普通用户 → 欢迎页
        return createUserWelcome();
    }

    /** 系统管理员首页 — 公司管理 Dashboard */
    private JPanel createAdminDashboard() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(Color.WHITE);
        panel.setBorder(new EmptyBorder(16, 16, 16, 16));

        // ── 顶部：标题 + 版本管理 ──
        JPanel topPanel = new JPanel(new BorderLayout(8, 4));
        topPanel.setOpaque(false);
        JLabel title = new JLabel("欢迎使用本系统，总管理员 " + loginSession.displayName());
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20));
        topPanel.add(title, BorderLayout.WEST);

        JPanel verBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
        verBar.setOpaque(false);
        JLabel verLabel = new JLabel("📦 v" + readLocalVersion());
        verLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        JButton checkBtn = new JButton("🔍 检查更新");
        checkBtn.addActionListener(e -> {
            currentVersion = null; ignoredVersion = "";
            checkVersionUpdate();
        });
        JButton publishBtn = new JButton("📤 发布新版本");
        publishBtn.addActionListener(e -> publishNewVersion());
        JButton roleBtn = new JButton("🔐 角色管理");
        roleBtn.addActionListener(e -> openRoleManager());
        JButton notifyBtn = new JButton("📢 系统公告");
        notifyBtn.setForeground(new Color(234, 67, 53));
        notifyBtn.addActionListener(e -> sendSystemAnnouncement());
        verBar.add(verLabel);
        verBar.add(checkBtn);
        verBar.add(publishBtn);
        verBar.add(roleBtn);
        verBar.add(notifyBtn);
        topPanel.add(verBar, BorderLayout.EAST);
        panel.add(topPanel, BorderLayout.NORTH);

        // ── 统计数据 ──
        int total   = queryTenantInt("SELECT COUNT(*) FROM erp_tenants");
        int active  = queryTenantInt("SELECT COUNT(*) FROM erp_tenants WHERE status='ACTIVE'");
        int trial   = queryTenantInt("SELECT COUNT(*) FROM erp_tenants WHERE status='TRIAL'");
        int expired = queryTenantInt("SELECT COUNT(*) FROM erp_tenants WHERE expires_at IS NOT NULL AND expires_at < NOW()");

        JPanel cards = new JPanel(new GridLayout(1, 4, 12, 0));
        cards.setOpaque(false);
        cards.add(metricCard("🏢 公司总数", total + " 家", new Color(66, 133, 244)));
        cards.add(metricCard("✅ 正常使用", active + " 家", new Color(52, 168, 83)));
        cards.add(metricCard("⏳ 试用期", trial + " 家", new Color(251, 188, 4)));
        cards.add(metricCard("⚠ 已到期", expired + " 家", new Color(234, 67, 53)));

        panel.add(cards, BorderLayout.CENTER);

        // ── 公司列表（可编辑状态和到期时间，支持搜索） ──
        JPanel tablePanel = buildTenantTable();
        panel.add(tablePanel, BorderLayout.SOUTH);

        return panel;
    }

    // ═══════════════════════════════════════════
    //  公司管理表格（可编辑 + 搜索）
    // ═══════════════════════════════════════════

    private java.util.List<String> tenantIdCache = new java.util.ArrayList<>();
    private java.util.List<Object[]> tenantDataCache = new java.util.ArrayList<>();
    private JTable tenantTable;
    private TenantTableModel tenantTableModel;
    private JTextField tenantSearchField;

    private JPanel buildTenantTable() {
        loadTenantData(null);
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setOpaque(false);

        // ── 搜索栏 ──
        JPanel searchBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        searchBar.setOpaque(false);
        JLabel searchIcon = new JLabel("🔍");
        tenantSearchField = new JTextField(20);
        tenantSearchField.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        tenantSearchField.putClientProperty("JTextField.placeholderText", "搜索公司名称或编码...");
        tenantSearchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            void doFilter() { loadTenantData(tenantSearchField.getText().trim()); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { doFilter(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { doFilter(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { doFilter(); }
        });
        searchBar.add(searchIcon);
        searchBar.add(tenantSearchField);

        // ── 表格模型 ──
        String[] cols = {"公司名称", "编码", "状态", "到期时间", "联系人", "联系电话"};
        tenantTableModel = new TenantTableModel(cols, tenantDataCache);
        tenantTable = new JTable(tenantTableModel);
        tenantTable.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        tenantTable.getTableHeader().setFont(new Font("微软雅黑", Font.BOLD, 12));
        tenantTable.setRowHeight(26);
        tenantTable.setShowGrid(true);
        tenantTable.setGridColor(new Color(230, 230, 230));

        // 状态列 → 下拉编辑器
        JComboBox<String> statusEditor = new JComboBox<>(new String[]{"ACTIVE", "TRIAL", "SUSPENDED", "DISABLED"});
        statusEditor.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        tenantTable.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(statusEditor));

        // 到期时间列 → 日期选择编辑器
        tenantTable.getColumnModel().getColumn(3).setCellEditor(new DateCellEditor());

        // 状态列颜色渲染
        tenantTable.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
                Component comp = super.getTableCellRendererComponent(t, v, s, f, r, c);
                String st = v != null ? v.toString() : "";
                if ("ACTIVE".equals(st)) setForeground(new Color(52, 168, 83));
                else if ("TRIAL".equals(st)) setForeground(new Color(251, 188, 4));
                else setForeground(new Color(234, 67, 53));
                return comp;
            }
        });

        // 到期时间红色提醒
        tenantTable.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
                Component comp = super.getTableCellRendererComponent(t, v, s, f, r, c);
                String val = v != null ? v.toString() : "";
                if (!"永久".equals(val)) {
                    try {
                        java.time.LocalDate exp = java.time.LocalDate.parse(val);
                        if (exp.isBefore(java.time.LocalDate.now())) {
                            setForeground(new Color(234, 67, 53));
                        } else if (exp.isBefore(java.time.LocalDate.now().plusDays(7))) {
                            setForeground(new Color(251, 188, 4));
                        } else {
                            setForeground(Color.DARK_GRAY);
                        }
                    } catch (Exception ex) { setForeground(Color.DARK_GRAY); }
                } else { setForeground(Color.GRAY); }
                return comp;
            }
        });

        // 双击进入公司详情
        tenantTable.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = tenantTable.getSelectedRow();
                    if (row >= 0 && row < tenantIdCache.size()) {
                        String name = (String) tenantTableModel.getValueAt(row, 0);
                        openCompanyView(tenantIdCache.get(row), name);
                    }
                }
            }
        });

        // 监听编辑保存
        tenantTableModel.addTableModelListener(e -> {
            if (e.getType() == javax.swing.event.TableModelEvent.UPDATE) {
                int row = e.getFirstRow();
                int col = e.getColumn();
                if (row >= 0 && row < tenantIdCache.size()) {
                    saveTenantChange(tenantIdCache.get(row), col, row);
                }
            }
        });

        JScrollPane scroll = new JScrollPane(tenantTable);
        scroll.setBorder(BorderFactory.createTitledBorder("💡 点击状态/到期时间可直接修改  |  双击公司名称查看详情"));

        panel.add(searchBar, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    /** 从数据库加载租户列表（可按关键词过滤） */
    private void loadTenantData(String keyword) {
        tenantIdCache.clear();
        tenantDataCache.clear();
        String sql = "SELECT id, name, code, status, expires_at, contact_name, contact_phone FROM erp_tenants";
        if (keyword != null && !keyword.isEmpty()) {
            sql += " WHERE name LIKE ? OR code LIKE ?";
        }
        sql += " ORDER BY status, name";
        try {
            if (keyword != null && !keyword.isEmpty()) {
                String kw = "%" + keyword + "%";
                com.erp.db.DatabaseManager.getInstance().executeQuery(sql, rs -> {
                    while (rs.next()) {
                        tenantIdCache.add(rs.getString("id"));
                        tenantDataCache.add(new Object[]{
                            rs.getString("name"), rs.getString("code"),
                            rs.getString("status"),
                            rs.getTimestamp("expires_at") != null ?
                                rs.getTimestamp("expires_at").toString().substring(0, 10) : "永久",
                            rs.getString("contact_name") != null ? rs.getString("contact_name") : "",
                            rs.getString("contact_phone") != null ? rs.getString("contact_phone") : ""
                        });
                    }
                    return null;
                }, kw, kw);
            } else {
                com.erp.db.DatabaseManager.getInstance().executeQuery(sql, rs -> {
                    while (rs.next()) {
                        tenantIdCache.add(rs.getString("id"));
                        tenantDataCache.add(new Object[]{
                            rs.getString("name"), rs.getString("code"),
                            rs.getString("status"),
                            rs.getTimestamp("expires_at") != null ?
                                rs.getTimestamp("expires_at").toString().substring(0, 10) : "永久",
                            rs.getString("contact_name") != null ? rs.getString("contact_name") : "",
                            rs.getString("contact_phone") != null ? rs.getString("contact_phone") : ""
                        });
                    }
                    return null;
                });
            }
        } catch (Exception ex) { log.warn("Load tenant list failed: {}", ex.getMessage()); }

        if (tenantTableModel != null) {
            tenantTableModel.fireTableDataChanged();
        }
    }

    /** 编辑后保存到数据库 */
    private void saveTenantChange(String tenantId, int col, int row) {
        if (row < 0 || row >= tenantDataCache.size()) return;
        if (col == 2) { // 状态
            String newStatus = (String) tenantTableModel.getValueAt(row, 2);
            try {
                com.erp.db.DatabaseManager.getInstance().executeUpdate(
                    "UPDATE erp_tenants SET status=? WHERE id=?", newStatus, tenantId);
                log.info("Tenant {} status → {}", tenantId, newStatus);
            } catch (Exception ex) { log.error("Update status failed: {}", ex.getMessage()); }
        } else if (col == 3) { // 到期时间
            String newDate = (String) tenantTableModel.getValueAt(row, 3);
            try {
                if ("永久".equals(newDate) || newDate == null || newDate.isEmpty()) {
                    com.erp.db.DatabaseManager.getInstance().executeUpdate(
                        "UPDATE erp_tenants SET expires_at=NULL WHERE id=?", tenantId);
                } else {
                    com.erp.db.DatabaseManager.getInstance().executeUpdate(
                        "UPDATE erp_tenants SET expires_at=? WHERE id=?", java.sql.Date.valueOf(newDate), tenantId);
                }
                log.info("Tenant {} expires → {}", tenantId, newDate);
            } catch (Exception ex) { log.error("Update expires_at failed: {}", ex.getMessage()); }
        }
    }

    // ── 日期选择编辑器（已迁至 DateCellEditor.java）──



    // ── 公司详情面板 ──

    /** 打开公司详情面板 */
    private void openCompanyView(String tenantId, String tenantName) {
        if (openPanels.containsKey("📋 " + tenantName)) {
            contentPane.setSelectedComponent(openPanels.get("📋 " + tenantName));
            return;
        }
        JPanel panel = createCompanyViewPanel(tenantId, tenantName);
        contentPane.addTab("📋 " + tenantName, panel);
        openPanels.put("📋 " + tenantName, panel);
        contentPane.setSelectedComponent(panel);
    }

    /** 创建公司详情面板 — 展示该公司的统计数据 */
    private JPanel createCompanyViewPanel(String tenantId, String tenantName) {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBackground(Color.WHITE);
        panel.setBorder(new EmptyBorder(16, 16, 16, 16));

        // 标题
        JLabel title = new JLabel("📋 " + tenantName + "  ·  公司详情");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18));
        panel.add(title, BorderLayout.NORTH);

        // 统计数据
        String tenant = tenantId;
        int empCount  = queryTenantInt("SELECT COUNT(*) FROM hr_employees WHERE tenant_id=?", tenant);
        int prodCount = queryTenantInt("SELECT COUNT(*) FROM inv_products WHERE tenant_id=?", tenant);
        int userCount = queryTenantInt("SELECT COUNT(*) FROM erp_users WHERE tenant_id=?", tenant);

        JPanel cards = new JPanel(new GridLayout(1, 3, 12, 0));
        cards.setOpaque(false);
        cards.add(metricCard("👥 员工", empCount + " 人", new Color(66, 133, 244)));
        cards.add(metricCard("📦 产品", prodCount + " 种", new Color(52, 168, 83)));
        cards.add(metricCard("🔑 用户", userCount + " 个", new Color(251, 188, 4)));
        panel.add(cards, BorderLayout.CENTER);

        return panel;
    }

    private int queryTenantInt(String sql, Object... params) {
        try {
            return com.erp.db.DatabaseManager.getInstance()
                .executeQuery(sql, rs -> rs.next() ? rs.getInt(1) : 0, params);
        } catch (Exception e) { return 0; }
    }

    /** 普通用户欢迎页 */
    private JPanel createUserWelcome() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.insets = new Insets(10, 10, 10, 10);

        String companyName = loginSession != null ? loginSession.tenantName() : "";
        String userName    = loginSession != null ? loginSession.displayName() : "";

        JLabel title = new JLabel("欢迎使用本系统");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 28));
        panel.add(title, gbc);

        if (!companyName.isEmpty() || !userName.isEmpty()) {
            JLabel subtitle = new JLabel(companyName + " · " + userName);
            subtitle.setFont(subtitle.getFont().deriveFont(14f));
            subtitle.setForeground(Color.GRAY);
            gbc.gridy = 1; panel.add(subtitle, gbc);
        }

        JLabel hint = new JLabel("← 左侧导航树选择功能模块");
        hint.setFont(hint.getFont().deriveFont(11f));
        hint.setForeground(Color.LIGHT_GRAY);
        gbc.gridy = 2; gbc.insets = new Insets(30, 10, 10, 10);
        panel.add(hint, gbc);

        return panel;
    }

    private int queryTenantInt(String sql) {
        try {
            return com.erp.db.DatabaseManager.getInstance()
                .executeQuery(sql, rs -> rs.next() ? rs.getInt(1) : 0);
        } catch (Exception e) { return 0; }
    }

    private JPanel metricCard(String label, String value, Color accent) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(228, 228, 228), 1),
            new EmptyBorder(12, 16, 12, 16)));
        card.setBackground(Color.WHITE);
        JLabel tl = new JLabel(label);
        tl.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        tl.setForeground(new Color(140, 140, 140));
        JLabel vl = new JLabel(value);
        vl.setFont(new Font("微软雅黑", Font.BOLD, 16));
        vl.setForeground(accent);
        card.add(tl, BorderLayout.NORTH);
        card.add(vl, BorderLayout.CENTER);
        return card;
    }

    private void addStatusRow(JPanel panel, String label, String value) {
        panel.add(new JLabel(label + ":", SwingConstants.RIGHT));
        JLabel valLabel = new JLabel(value, SwingConstants.LEFT);
        valLabel.setFont(valLabel.getFont().deriveFont(Font.BOLD));
        panel.add(valLabel);
    }

    private void openRoleManager() {
        if (openPanels.containsKey("🔐 角色管理")) {
            contentPane.setSelectedComponent(openPanels.get("🔐 角色管理"));
            return;
        }
        RoleManagerPanel panel = new RoleManagerPanel();
        contentPane.addTab("🔐 角色管理", panel);
        openPanels.put("🔐 角色管理", panel);
        contentPane.setSelectedComponent(panel);
    }

    /** 大喇叭 — 管理员发送系统公告 */
    private void sendSystemAnnouncement() {
        JTextArea msgArea = new JTextArea(5, 30);
        msgArea.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        msgArea.setLineWrap(true);
        msgArea.setWrapStyleWord(true);
        JScrollPane sp = new JScrollPane(msgArea);
        sp.setBorder(BorderFactory.createTitledBorder("公告内容"));

        JComboBox<String> target = new JComboBox<>(new String[]{"* (所有公司)", "t-south (华南)", "t-arche (始祖)"});
        target.setFont(new Font("微软雅黑", Font.PLAIN, 12));

        JPanel dialog = new JPanel(new BorderLayout(8, 8));
        dialog.add(target, BorderLayout.NORTH);
        dialog.add(sp, BorderLayout.CENTER);

        int result = JOptionPane.showConfirmDialog(this, dialog,
            "📢 发送系统公告", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        String msg = msgArea.getText().trim();
        if (msg.isEmpty()) { JOptionPane.showMessageDialog(this, "公告内容不能为空"); return; }

        String targetVal = "*";
        String targetText = (String) target.getSelectedItem();
        if (targetText != null && targetText.startsWith("t-")) {
            targetVal = targetText.substring(0, targetText.indexOf(' '));
        }

        try {
            String id = java.util.UUID.randomUUID().toString().substring(0, 16);
            String user = getCurrentUser();
            com.erp.db.DatabaseManager.getInstance().executeUpdate(
                "INSERT INTO erp_notifications (id, title, message, target_tenant, created_by) VALUES (?,?,?,?,?)",
                id, "系统公告", msg, targetVal, user);
            JOptionPane.showMessageDialog(this, "✅ 公告已发布！\n所有目标用户将在30秒内收到。");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "发布失败: " + e.getMessage());
        }
    }

    private String getCurrentUser() {
        if (loginSession != null) return loginSession.username();
        return "sysadmin";
    }

    // ── 对话框 ──

    private void showModuleManager() {
        ModuleManagerDialog dialog = new ModuleManagerDialog(this, kernel);
        dialog.setVisible(true);
    }

    private void showTenantManager() {
        TenantManagerDialog dialog = new TenantManagerDialog(this);
        dialog.setVisible(true);
    }

    private void showConfigDialog() {
        JOptionPane.showMessageDialog(this,
            "内核配置:\n" + kernel.getConfig().toString(),
            "系统配置", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showDataModelBrowser() {
        var entityTypes = kernel.getDataModelRegistry().getRegisteredEntityTypes();
        StringBuilder sb = new StringBuilder("已注册实体类型:\n");
        for (String et : entityTypes) {
            var def = kernel.getDataModelRegistry().getEntityDefinition(et);
            if (def != null) {
                sb.append("  • ").append(et)
                    .append(" (").append(def.displayName()).append(")")
                    .append(" — 字段: ").append(def.baseFields().size())
                    .append(" + 自定义: ")
                    .append(kernel.getDataModelRegistry().getCustomFields(et).size())
                    .append("\n");
            }
        }
        JOptionPane.showMessageDialog(this, sb.toString(), "数据模型浏览器", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showEventMonitor() {
        JOptionPane.showMessageDialog(this,
            "事件总线统计:\n"
            + "  已发布事件: " + kernel.getEventBus().getTotalPublished() + "\n"
            + "  已分发事件: " + kernel.getEventBus().getTotalDispatched() + "\n"
            + "  监听器数: " + kernel.getEventBus().getListenerCount() + "\n"
            + "  死信队列: " + kernel.getEventBus().getDeadLetterCount(),
            "事件监控", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showAboutDialog() {
        JOptionPane.showMessageDialog(this,
            "始祖ERP框架 (Arche ERP Framework)\n"
            + "版本: 1.0.0-alpha\n\n"
            + "微内核 + 插件架构设计\n"
            + "核心仅提供: 权限 · 组织架构 · 数据路由\n"
            + "所有业务功能作为独立模块热插拔运行\n\n"
            + "「什么都能往里面填」的终极扩展目标",
            "关于始祖ERP", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showDevGuide() {
        JOptionPane.showMessageDialog(this,
            "模块开发者指南:\n\n"
            + "1. 实现 ErpModule 接口\n"
            + "2. 提供模块标识与生命周期方法\n"
            + "3. 通过 getMenuContributions() 贡献菜单\n"
            + "4. 通过 getPanelContributions() 贡献面板\n"
            + "5. 通过 getProvidedServices() 提供服务\n"
            + "6. 通过 getEntityDefinitions() 定义数据模型\n"
            + "7. 通过 kernel.getEventBus().subscribe() 订阅事件\n\n"
            + "模块间仅通过 EventBus / ServiceRegistry 通信",
            "开发者指南", JOptionPane.INFORMATION_MESSAGE);
    }

    // ── 刷新 ──

    /** 刷新模块导航树 */
    public void refreshModuleTree() {
        buildModuleTree();
        // 更新工具栏
        toolbarPanel.removeAll();
        rebuildToolbar();
        toolbarPanel.revalidate();
        toolbarPanel.repaint();
        updateStatusBar();
    }

    private void rebuildToolbar() {
        JLabel toolLabel = new JLabel(" 快捷操作: ");
        toolLabel.setFont(toolLabel.getFont().deriveFont(11f));
        toolbarPanel.add(toolLabel);

        for (ErpModule module : moduleManager.getModulesInStartOrder()) {
            List<Action> actions = module.getToolbarActions();
            for (Action action : actions) {
                JButton btn = new JButton(action);
                btn.setFont(btn.getFont().deriveFont(11f));
                btn.setBorderPainted(false);
                btn.setContentAreaFilled(false);
                btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                btn.addMouseListener(new MouseAdapter() {
                    public void mouseEntered(MouseEvent e) {
                        btn.setContentAreaFilled(true);
                        btn.setBackground(new Color(220, 220, 240));
                    }
                    public void mouseExited(MouseEvent e) {
                        btn.setContentAreaFilled(false);
                    }
                });
                toolbarPanel.add(btn);
            }
        }
    }

    // ── 事件监听 ──

    private void registerGlobalListeners() {
        kernel.addHotDeployListener(event -> {
            SwingUtilities.invokeLater(() -> {
                refreshModuleTree();
                setStatus("模块 " + event.module().getName() + " 已"
                    + (event.type() == MicroKernel.HotDeployEventType.DEPLOYED ? "部署" : "卸载"));
            });
        });
    }

    // ── 状态栏 ──

    public void setStatus(String message) {
        statusLabel.setText("  " + message);
    }

    private void updateStatusBar() {
        int moduleCount = moduleManager.getActiveModuleCount();
        int serviceCount = kernel.getServiceRegistry().getServiceCount();
        String userInfo = loginSession != null
            ? loginSession.displayName() + " @ " + loginSession.tenantName()
            : TenantContext.getCurrentTenantId();
        setStatus("用户: " + userInfo + "  |  模块: " + moduleCount + "  |  服务: " + serviceCount);
    }

    // ── 主题 ──

    private void applyTheme(String themeName) {
        try {
            switch (themeName.toLowerCase()) {
                case "nimbus" -> {
                    for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                        if ("Nimbus".equals(info.getName())) {
                            UIManager.setLookAndFeel(info.getClassName());
                            return;
                        }
                    }
                }
                case "metal" ->
                    UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
                case "windows" ->
                    UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
                case "motif" ->
                    UIManager.setLookAndFeel("com.sun.java.swing.plaf.motif.MotifLookAndFeel");
                default -> {}
            }
        } catch (Exception e) {
            log.warn("Failed to apply theme '{}', using default", themeName);
        }
    }

    // ── 关闭 ──

    private void shutdown() {
        int confirm = JOptionPane.showConfirmDialog(this,
            "确认退出ERP系统？", "退出确认",
            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            setStatus("正在关闭系统...");
            kernel.shutdown();
            dispose();
            System.exit(0);
        }
    }

    // ── 内部节点类型 ──

    /** 模块树节点 */
    private record ModuleTreeNode(String displayName, String moduleId, String type) {
        @Override
        public String toString() { return displayName; }
    }

    /** 菜单动作节点 */
    private record MenuActionNode(String displayName, Runnable action, String moduleId) {
        @Override
        public String toString() { return displayName; }
    }
}
