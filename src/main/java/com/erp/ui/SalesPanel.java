package com.erp.ui;

import com.erp.db.DatabaseManager;
import com.erp.tenant.TenantContext;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 销售面板 — 一次销售提交，联动写入 4 张表（事务保证原子性）：
 *
 *   biz_sales_orders + biz_sales_order_items  →  销售单
 *   inv_stock_out                              →  库存出库
 *   fin_invoices                               →  销售发票
 *   fin_vouchers + fin_voucher_items           →  会计凭证
 */
public class SalesPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final String tenantId;
    private final String userId;
    private final JTextField customerField;
    private final JComboBox<ProductItem> productCombo;
    private final JTextField qtyField;
    private final JTextField priceField;
    private final DefaultTableModel lineModel;
    private final JLabel totalLabel;

    public SalesPanel() {
        this.tenantId = TenantContext.getCurrentTenantId();
        this.userId = TenantContext.getLoginSession() != null
            ? TenantContext.getLoginSession().userId() : "unknown";

        setLayout(new BorderLayout(10, 0));

        // ── 左侧：表单区域 ──
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createTitledBorder("销售单信息"));
        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(4, 8, 4, 8);

        g.gridy = 0; g.gridx = 0; formPanel.add(new JLabel("客户名称:"), g);
        customerField = new JTextField(15);
        g.gridx = 1; formPanel.add(customerField, g);

        g.gridy = 1; g.gridx = 0; formPanel.add(new JLabel("产品:"), g);
        productCombo = new JComboBox<>();
        loadProducts();
        g.gridx = 1; formPanel.add(productCombo, g);

        g.gridy = 2; g.gridx = 0; formPanel.add(new JLabel("数量:"), g);
        qtyField = new JTextField("1", 10);
        g.gridx = 1; formPanel.add(qtyField, g);

        g.gridy = 3; g.gridx = 0; formPanel.add(new JLabel("单价:"), g);
        priceField = new JTextField(10);
        productCombo.addActionListener(e -> {
            ProductItem p = (ProductItem) productCombo.getSelectedItem();
            if (p != null) priceField.setText(String.valueOf(p.unitPrice));
        });
        g.gridx = 1; formPanel.add(priceField, g);

        // 添加到明细按钮
        g.gridy = 4; g.gridx = 0;
        JButton addLineBtn = new JButton("➕ 添加明细");
        addLineBtn.addActionListener(e -> addLine());
        formPanel.add(addLineBtn, g);

        // 总金额
        g.gridy = 5; g.gridx = 0; g.gridwidth = 2;
        totalLabel = new JLabel("合计: ¥ 0.00");
        totalLabel.setFont(totalLabel.getFont().deriveFont(Font.BOLD, 16));
        formPanel.add(totalLabel, g);

        // 提交按钮
        g.gridy = 6; g.insets = new Insets(16, 8, 4, 8);
        JButton submitBtn = new JButton("✅ 提交销售单（联动出库+凭证）");
        submitBtn.setFont(submitBtn.getFont().deriveFont(Font.BOLD, 14));
        submitBtn.setBackground(new Color(46, 125, 50));
        submitBtn.setForeground(Color.WHITE);
        submitBtn.addActionListener(e -> submitSale());
        formPanel.add(submitBtn, g);

        add(formPanel, BorderLayout.WEST);

        // ── 右侧：明细表格 ──
        lineModel = new DefaultTableModel(new String[]{"产品", "数量", "单价", "金额"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable lineTable = new JTable(lineModel);
        lineTable.setRowHeight(24);
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBorder(BorderFactory.createTitledBorder("销售明细"));
        rightPanel.add(new JScrollPane(lineTable), BorderLayout.CENTER);

        JButton delLineBtn = new JButton("🗑 删除选中行");
        delLineBtn.addActionListener(e -> {
            int row = lineTable.getSelectedRow();
            if (row >= 0) lineModel.removeRow(row);
            updateTotal();
        });
        rightPanel.add(delLineBtn, BorderLayout.SOUTH);

        add(rightPanel, BorderLayout.CENTER);

        // 初始化产品价格
        ProductItem first = (ProductItem) productCombo.getSelectedItem();
        if (first != null) priceField.setText(String.valueOf(first.unitPrice));
    }

    public void refresh() {
        productCombo.removeAllItems();
        loadProducts();
    }

    private void loadProducts() {
        try {
            DatabaseManager.getInstance().executeQuery(
                "SELECT id, product_name, unit_price FROM inv_products WHERE tenant_id=? AND is_active=1 ORDER BY product_code",
                rs -> {
                    while (rs.next()) {
                        productCombo.addItem(new ProductItem(
                            rs.getString("id"), rs.getString("product_name"), rs.getDouble("unit_price")));
                    }
                    return null;
                }, tenantId);
        } catch (Exception e) {
            productCombo.addItem(new ProductItem("--", "无法加载产品", 0));
        }
    }

    private void addLine() {
        ProductItem p = (ProductItem) productCombo.getSelectedItem();
        if (p == null) return;
        try {
            double qty = Double.parseDouble(qtyField.getText().trim());
            double price = Double.parseDouble(priceField.getText().trim());
            double total = qty * price;
            lineModel.addRow(new Object[]{p.name, qty, price, String.format("%.2f", total)});
            updateTotal();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "数量和单价必须是数字");
        }
    }

    private void updateTotal() {
        double total = 0;
        for (int i = 0; i < lineModel.getRowCount(); i++) {
            String amt = lineModel.getValueAt(i, 3).toString();
            total += Double.parseDouble(amt);
        }
        totalLabel.setText(String.format("合计: ¥ %,.2f", total));
    }

    /** 核心：事务提交销售单，联动 4 张表 */
    private void submitSale() {
        String customer = customerField.getText().trim();
        if (customer.isEmpty()) { JOptionPane.showMessageDialog(this, "请输入客户名称"); return; }
        if (lineModel.getRowCount() == 0) { JOptionPane.showMessageDialog(this, "请先添加销售明细"); return; }

        // 计算总额
        double totalAmount = 0;
        for (int i = 0; i < lineModel.getRowCount(); i++) {
            totalAmount += Double.parseDouble(lineModel.getValueAt(i, 3).toString());
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String today = sdf.format(new Date());
        String orderNo = "SO-" + System.currentTimeMillis() % 100000;
        String invoiceNo = "INV-" + System.currentTimeMillis() % 100000;
        String voucherNo = "PZ-" + System.currentTimeMillis() % 100000;

        final Double finalAmount = totalAmount;

        try {
            DatabaseManager.getInstance().runTransaction(conn -> {

                // 1. 销售单主表
                long orderId;
                try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO biz_sales_orders (tenant_id, order_no, customer_name, order_date, total_amount, status, created_by) VALUES (?,?,?,?,?,'CONFIRMED',?)",
                    PreparedStatement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, tenantId);
                    ps.setString(2, orderNo);
                    ps.setString(3, customer);
                    ps.setString(4, today);
                    ps.setDouble(5, finalAmount);
                    ps.setString(6, userId);
                    ps.executeUpdate();
                    ResultSet rs = ps.getGeneratedKeys();
                    if (!rs.next()) throw new SQLException("创建销售单失败");
                    orderId = rs.getLong(1);
                }

                // 2. 明细行 + 出库
                for (int i = 0; i < lineModel.getRowCount(); i++) {
                    String prodName = lineModel.getValueAt(i, 0).toString();
                    double qty = Double.parseDouble(lineModel.getValueAt(i, 1).toString());
                    double price = Double.parseDouble(lineModel.getValueAt(i, 2).toString());
                    double lineAmt = Double.parseDouble(lineModel.getValueAt(i, 3).toString());

                    // 查找产品ID
                    String prodId;
                    try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id FROM inv_products WHERE tenant_id=? AND product_name=? LIMIT 1")) {
                        ps.setString(1, tenantId);
                        ps.setString(2, prodName);
                        ResultSet rs = ps.executeQuery();
                        if (!rs.next()) throw new SQLException("产品不存在: " + prodName);
                        prodId = rs.getString("id");
                    }

                    // 插入明细
                    try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO biz_sales_order_items (order_id, product_id, quantity, unit_price, line_total) VALUES (?,?,?,?,?)")) {
                        ps.setLong(1, orderId);
                        ps.setString(2, prodId);
                        ps.setDouble(3, qty);
                        ps.setDouble(4, price);
                        ps.setDouble(5, lineAmt);
                        ps.executeUpdate();
                    }

                    // 出库
                    try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO inv_stock_out (tenant_id, product_id, quantity, unit_price, customer, out_date, operator) VALUES (?,?,?,?,?,?,?)")) {
                        ps.setString(1, tenantId);
                        ps.setString(2, prodId);
                        ps.setDouble(3, qty);
                        ps.setDouble(4, price);
                        ps.setString(5, customer);
                        ps.setString(6, today);
                        ps.setString(7, userId);
                        ps.executeUpdate();
                    }
                }

                // 3. 发票
                try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO fin_invoices (id, tenant_id, invoice_no, invoice_type, customer_name, amount, tax_amount, total_amount, invoice_date, due_date, status, created_by) " +
                    "VALUES (?,?,?,'SALES',?,?,0,?,?,DATE_ADD(?,INTERVAL 30 DAY),'PENDING',?)")) {
                    String invId = "inv-" + System.currentTimeMillis();
                    ps.setString(1, invId);
                    ps.setString(2, tenantId);
                    ps.setString(3, invoiceNo);
                    ps.setString(4, customer);
                    ps.setDouble(5, finalAmount);
                    ps.setDouble(6, finalAmount);
                    ps.setString(7, today);
                    ps.setString(8, today);
                    ps.setString(9, userId);
                    ps.executeUpdate();
                }

                // 4. 凭证
                long voucherId;
                try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO fin_vouchers (tenant_id, voucher_no, voucher_date, description, status, created_by) VALUES (?,?,?,?,'POSTED',?)",
                    PreparedStatement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, tenantId);
                    ps.setString(2, voucherNo);
                    ps.setString(3, today);
                    ps.setString(4, "销售出库 - " + customer);
                    ps.setString(5, userId);
                    ps.executeUpdate();
                    ResultSet rs = ps.getGeneratedKeys();
                    if (!rs.next()) throw new SQLException("创建凭证失败");
                    voucherId = rs.getLong(1);
                }

                // 查找应收和收入科目
                String arSubjectId, revenueSubjectId;
                try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, subject_code FROM fin_account_subjects WHERE tenant_id=? AND subject_code IN ('1122','5001')")) {
                    ps.setString(1, tenantId);
                    ResultSet rs = ps.executeQuery();
                    arSubjectId = null; revenueSubjectId = null;
                    while (rs.next()) {
                        String code = rs.getString("subject_code");
                        if ("1122".equals(code)) arSubjectId = rs.getString("id");
                        else if ("5001".equals(code)) revenueSubjectId = rs.getString("id");
                    }
                }
                if (arSubjectId == null || revenueSubjectId == null)
                    throw new SQLException("会计科目1122或5001不存在，请先在会计科目中添加");

                // 借方：应收账款
                try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO fin_voucher_items (voucher_id, subject_id, summary, debit_amount, credit_amount) VALUES (?,?,?,?,0)")) {
                    ps.setLong(1, voucherId);
                    ps.setString(2, arSubjectId);
                    ps.setString(3, "应收账款 - " + customer);
                    ps.setDouble(4, finalAmount);
                    ps.executeUpdate();
                }

                // 贷方：主营业务收入
                try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO fin_voucher_items (voucher_id, subject_id, summary, debit_amount, credit_amount) VALUES (?,?,?,0,?)")) {
                    ps.setLong(1, voucherId);
                    ps.setString(2, revenueSubjectId);
                    ps.setString(3, "销售收入 - " + customer);
                    ps.setDouble(4, finalAmount);
                    ps.executeUpdate();
                }
            });

            // 成功
            JOptionPane.showMessageDialog(this,
                "✅ 销售单创建成功！\n\n" +
                "销售单号: " + orderNo + "\n" +
                "发票号:   " + invoiceNo + "\n" +
                "凭证号:   " + voucherNo + "\n" +
                "金额:     ¥ " + String.format("%,.2f", totalAmount) + "\n\n" +
                "已联动：库存出库 + 销售发票 + 应收账款凭证",
                "操作成功", JOptionPane.INFORMATION_MESSAGE);

            // 清空
            customerField.setText("");
            lineModel.setRowCount(0);
            totalLabel.setText("合计: ¥ 0.00");

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "❌ 提交失败（事务已回滚，数据未实际写入）\n" + e.getMessage(),
                "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private record ProductItem(String id, String name, double unitPrice) {
        @Override public String toString() { return name + " (¥" + String.format("%.0f", unitPrice) + ")"; }
    }
}
