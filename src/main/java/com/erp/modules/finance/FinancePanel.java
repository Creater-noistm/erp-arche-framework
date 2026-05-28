package com.erp.modules.finance;

import com.erp.db.DatabaseManager;
import com.erp.tenant.TenantContext;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.labels.StandardPieSectionLabelGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PiePlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.DecimalFormat;

/**
 * 财务概览面板 — 收支柱状图 + 费用饼图 + 交易表格。
 */
public class FinancePanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final String tenant;

    private static final Font FONT_TITLE  = new Font("微软雅黑", Font.BOLD, 14);
    private static final Font FONT_LABEL  = new Font("微软雅黑", Font.PLAIN, 11);
    private static final Font FONT_TICK   = new Font("微软雅黑", Font.PLAIN, 10);
    private static final Font FONT_CARD   = new Font("微软雅黑", Font.PLAIN, 11);
    private static final Font FONT_CARD_V = new Font("微软雅黑", Font.BOLD, 16);

    private static final Color C_REVENUE = new Color(52, 168, 83);
    private static final Color C_EXPENSE = new Color(234, 67, 53);
    private static final Color C_PROFIT  = new Color(66, 133, 244);
    private static final Color C_AR      = new Color(251, 188, 4);

    private static final Color[] PIE_COLORS = {
        new Color(234, 67, 53),   new Color(66, 133, 244),  new Color(251, 188, 4),
        new Color(52, 168, 83),   new Color(142, 68, 173),  new Color(234, 134, 46),
        new Color(22, 160, 133),  new Color(230, 126, 34)
    };

    public FinancePanel() {
        String t = TenantContext.getCurrentTenantId();
        tenant = (t != null) ? t : "t-arche";

        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(16, 16, 16, 16));

        JLabel title = new JLabel("财务概览 Dashboard");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18));
        add(title, BorderLayout.NORTH);

        JPanel body = new JPanel(new GridBagLayout());
        body.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.BOTH;
        g.insets = new Insets(4, 0, 4, 0);

        g.gridx = 0; g.gridy = 0; g.weightx = 1; g.weighty = 0;
        body.add(buildMetricCards(), g);

        g.gridy = 1; g.weighty = 1;
        body.add(buildChartRow(), g);

        g.gridy = 2; g.weighty = 0.5;
        body.add(buildTransactionTable(), g);

        add(body, BorderLayout.CENTER);
    }

    private JPanel buildMetricCards() {
        double revenue  = queryDouble("SELECT COALESCE(SUM(i.credit_amount),0) FROM fin_voucher_items i JOIN fin_account_subjects s ON s.id=i.subject_id WHERE s.tenant_id=? AND s.subject_type='PROFIT'");
        double expense  = queryDouble("SELECT COALESCE(SUM(i.debit_amount),0) FROM fin_voucher_items i JOIN fin_account_subjects s ON s.id=i.subject_id WHERE s.tenant_id=? AND s.subject_type='COST'");
        double profit   = revenue - expense;
        double ar       = Math.abs(queryDouble("SELECT ROUND(COALESCE(SUM(i.debit_amount - i.credit_amount),0),2) FROM fin_voucher_items i JOIN fin_account_subjects s ON s.id=i.subject_id WHERE s.tenant_id=? AND s.subject_code='1122'"));

        JPanel row = new JPanel(new GridLayout(1, 4, 12, 0));
        row.setOpaque(false);
        row.add(metricCard("本月收入", "¥ " + fmt(revenue), C_REVENUE));
        row.add(metricCard("本月支出", "¥ " + fmt(expense), C_EXPENSE));
        row.add(metricCard("净利润", "¥ " + fmt(profit), profit >= 0 ? C_PROFIT : C_EXPENSE));
        row.add(metricCard("应收账款", "¥ " + fmt(ar), C_AR));
        return row;
    }

    private JPanel metricCard(String label, String value, Color accent) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(228, 228, 228), 1),
            new EmptyBorder(12, 16, 12, 16)));
        card.setBackground(Color.WHITE);
        JLabel tl = new JLabel(label); tl.setFont(FONT_CARD); tl.setForeground(new Color(140, 140, 140));
        JLabel vl = new JLabel(value); vl.setFont(FONT_CARD_V); vl.setForeground(accent);
        card.add(tl, BorderLayout.NORTH);
        card.add(vl, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildChartRow() {
        JPanel row = new JPanel(new GridLayout(1, 2, 12, 0));
        row.setOpaque(false);
        row.add(buildRevenueBarChart());
        row.add(buildExpensePieChart());
        return row;
    }

    /** 月度收支对比柱状图 */
    private ChartPanel buildRevenueBarChart() {
        DefaultCategoryDataset ds = new DefaultCategoryDataset();
        boolean hasData = false;
        try {
            hasData = DatabaseManager.getInstance().executeQuery(
                "SELECT DATE_FORMAT(v.voucher_date,'%Y-%m') AS m, " +
                "ROUND(SUM(CASE WHEN s.subject_type='PROFIT' THEN i.credit_amount ELSE 0 END),2) AS rev, " +
                "ROUND(SUM(CASE WHEN s.subject_type='COST' THEN i.debit_amount ELSE 0 END),2) AS exp " +
                "FROM fin_vouchers v JOIN fin_voucher_items i ON i.voucher_id=v.id " +
                "JOIN fin_account_subjects s ON s.id=i.subject_id " +
                "WHERE v.tenant_id=? AND v.voucher_date >= DATE_SUB(CURDATE(),INTERVAL 6 MONTH) " +
                "GROUP BY m ORDER BY m",
                rs -> {
                    boolean any = false;
                    while (rs.next()) {
                        String m = rs.getString("m");
                        ds.addValue(rs.getDouble("rev"), "收入", m);
                        ds.addValue(rs.getDouble("exp"), "支出", m);
                        any = true;
                    }
                    return any;
                }, tenant);
        } catch (Exception ignored) {}
        if (!hasData) { ds.addValue(0, "收入", "无数据"); ds.addValue(0, "支出", "无数据"); }

        JFreeChart chart = ChartFactory.createBarChart(
            "月度收支对比", null, "金额 (¥)", ds,
            PlotOrientation.VERTICAL, true, true, false);
        applyStyle(chart);

        CategoryPlot plot = chart.getCategoryPlot();
        BarRenderer r = (BarRenderer) plot.getRenderer();
        r.setSeriesPaint(0, C_REVENUE);
        r.setSeriesPaint(1, C_EXPENSE);
        r.setShadowVisible(false);

        return wrap(chart);
    }

    /** 费用构成饼图 */
    private ChartPanel buildExpensePieChart() {
        DefaultPieDataset<String> ds = new DefaultPieDataset<>();
        boolean hasData = false;
        try {
            hasData = DatabaseManager.getInstance().executeQuery(
                "SELECT s.name, ROUND(SUM(i.debit_amount),2) AS amt " +
                "FROM fin_voucher_items i JOIN fin_account_subjects s ON s.id=i.subject_id " +
                "JOIN fin_vouchers v ON v.id=i.voucher_id " +
                "WHERE s.tenant_id=? AND s.subject_type='COST' " +
                "AND v.voucher_date >= DATE_SUB(CURDATE(),INTERVAL 1 MONTH) " +
                "GROUP BY s.name ORDER BY amt DESC",
                rs -> {
                    boolean any = false;
                    while (rs.next()) { ds.setValue(rs.getString("name"), rs.getDouble("amt")); any = true; }
                    return any;
                }, tenant);
        } catch (Exception ignored) {}
        if (!hasData) ds.setValue("暂无费用数据", 1);

        JFreeChart chart = ChartFactory.createPieChart("本月费用构成", ds, true, true, false);
        applyStyle(chart);

        PiePlot<?> plot = (PiePlot<?>) chart.getPlot();
        plot.setLabelGenerator(new StandardPieSectionLabelGenerator(
            "{0}: ¥{1} ({2})", new DecimalFormat("#,##0"), new DecimalFormat("0.0%")));
        plot.setSimpleLabels(true);
        int ci = 0;
        for (Object key : ds.getKeys()) {
            if (ci < PIE_COLORS.length) plot.setSectionPaint((Comparable<?>) key, PIE_COLORS[ci++]);
        }

        return wrap(chart);
    }

    /** 最近业务 — 销售订单 + 会计凭证 统一展示 */
    private JPanel buildTransactionTable() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder("最近业务"));

        DefaultTableModel model = new DefaultTableModel(
            new String[]{"日期", "类型", "单号", "客户/摘要", "金额", "状态"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(model);
        table.setRowHeight(24);
        table.setFont(new Font("微软雅黑", Font.PLAIN, 11));

        try {
            // 最近销售订单
            DatabaseManager.getInstance().executeQuery(
                "SELECT order_date, order_no, customer_name, total_amount, status, created_by " +
                "FROM biz_sales_orders WHERE tenant_id=? ORDER BY order_date DESC LIMIT 5",
                rs -> {
                    while (rs.next()) {
                        String status = switch (rs.getString("status")) {
                            case "DRAFT" -> "草稿"; case "CONFIRMED" -> "已确认";
                            case "SHIPPED" -> "已发货"; case "PAID" -> "已收款";
                            default -> rs.getString("status"); };
                        model.addRow(new Object[]{
                            rs.getDate("order_date"), "📋 销售订单",
                            rs.getString("order_no"), rs.getString("customer_name"),
                            "¥ " + fmt(rs.getDouble("total_amount")), status
                        });
                    }
                    return null;
                }, tenant);

            // 最近凭证
            DatabaseManager.getInstance().executeQuery(
                "SELECT v.voucher_date, v.voucher_no, v.description, " +
                "ROUND(SUM(i.debit_amount+COALESCE(i.credit_amount,0)),2) AS amt, v.status, v.created_by " +
                "FROM fin_vouchers v JOIN fin_voucher_items i ON i.voucher_id=v.id " +
                "WHERE v.tenant_id=? GROUP BY v.id ORDER BY v.voucher_date DESC LIMIT 5",
                rs -> {
                    while (rs.next()) {
                        model.addRow(new Object[]{
                            rs.getDate("voucher_date"), "📒 会计凭证",
                            rs.getString("voucher_no"), rs.getString("description"),
                            "¥ " + fmt(rs.getDouble("amt")),
                            "POSTED".equals(rs.getString("status")) ? "已过账" : "待过账"
                        });
                    }
                    return null;
                }, tenant);
        } catch (Exception e) {
            model.addRow(new Object[]{"--", "--", "数据加载失败", "--", "--", "--"});
        }
        p.add(new JScrollPane(table), BorderLayout.CENTER);
        return p;
    }

    // ═══════════════════════════════════════════
    //  辅助
    // ═══════════════════════════════════════════

    private double queryDouble(String sql) {
        try { return DatabaseManager.getInstance().executeQuery(sql, rs -> rs.next() ? rs.getDouble(1) : 0, tenant); }
        catch (Exception e) { return 0; }
    }

    private String fmt(double v) { return String.format("%,.2f", v); }

    private ChartPanel wrap(JFreeChart chart) {
        ChartPanel cp = new ChartPanel(chart);
        cp.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(228, 228, 228), 1),
            new EmptyBorder(8, 8, 8, 8)));
        cp.setBackground(Color.WHITE);
        return cp;
    }

    private void applyStyle(JFreeChart chart) {
        chart.setBackgroundPaint(Color.WHITE);
        chart.getTitle().setFont(FONT_TITLE);
        chart.getTitle().setPaint(new Color(50, 50, 50));
        chart.removeLegend();

        if (chart.getPlot() instanceof PiePlot pp) {
            pp.setBackgroundPaint(Color.WHITE);
            pp.setOutlineVisible(false);
            pp.setLabelFont(FONT_LABEL);
            pp.setShadowPaint(null);
            pp.setSectionOutlinesVisible(false);
            return;
        }
        if (chart.getPlot() instanceof CategoryPlot cp) {
            cp.setBackgroundPaint(Color.WHITE);
            cp.setRangeGridlinePaint(new Color(235, 235, 235));
            cp.setDomainGridlinesVisible(false);
            cp.setOutlineVisible(false);
            cp.getDomainAxis().setLabelFont(FONT_LABEL);
            cp.getDomainAxis().setTickLabelFont(FONT_TICK);
            cp.getRangeAxis().setLabelFont(FONT_LABEL);
            cp.getRangeAxis().setTickLabelFont(FONT_TICK);
        }
    }
}
