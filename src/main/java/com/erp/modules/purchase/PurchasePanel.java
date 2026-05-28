package com.erp.modules.purchase;

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
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.text.DecimalFormat;

/**
 * 采购概览面板 — 供应商 + 月度采购趋势 + 入库批次。
 */
public class PurchasePanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final String tenant;

    private static final Font FONT_TITLE  = new Font("微软雅黑", Font.BOLD, 14);
    private static final Font FONT_LABEL  = new Font("微软雅黑", Font.PLAIN, 11);
    private static final Font FONT_TICK   = new Font("微软雅黑", Font.PLAIN, 10);
    private static final Font FONT_CARD   = new Font("微软雅黑", Font.PLAIN, 11);
    private static final Font FONT_CARD_V = new Font("微软雅黑", Font.BOLD, 16);

    private static final Color C_SUPPLIER = new Color(66, 133, 244);
    private static final Color C_BATCHES  = new Color(52, 168, 83);
    private static final Color C_AMOUNT   = new Color(251, 188, 4);
    private static final Color C_AVG      = new Color(142, 68, 173);
    private static final Color C_BAR      = new Color(66, 133, 244);
    private static final Color C_LINE     = new Color(15, 157, 88);

    private static final Color[] PIE_COLORS = {
        new Color(66, 133, 244),  new Color(52, 168, 83),  new Color(251, 188, 4),
        new Color(234, 67, 53),   new Color(142, 68, 173), new Color(234, 134, 46),
        new Color(22, 160, 133),  new Color(230, 126, 34)
    };

    public PurchasePanel() {
        String t = TenantContext.getCurrentTenantId();
        tenant = (t != null) ? t : "t-arche";

        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(16, 16, 16, 16));

        JLabel title = new JLabel("采购概览 Dashboard");
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

        add(body, BorderLayout.CENTER);
    }

    private JPanel buildMetricCards() {
        int supplierCnt = queryInt("SELECT COUNT(DISTINCT supplier) FROM inv_stock_in WHERE tenant_id=?");
        int batchCnt    = queryInt("SELECT COUNT(*) FROM inv_stock_in WHERE tenant_id=? AND in_date >= DATE_SUB(CURDATE(), INTERVAL 1 MONTH)");
        double totalAmt = queryDouble("SELECT COALESCE(SUM(quantity * unit_price), 0) FROM inv_stock_in WHERE tenant_id=? AND in_date >= DATE_SUB(CURDATE(), INTERVAL 1 MONTH)");
        double avgAmt   = batchCnt > 0 ? totalAmt / batchCnt : 0;

        JPanel row = new JPanel(new GridLayout(1, 4, 12, 0));
        row.setOpaque(false);
        row.add(metricCard("供应商", supplierCnt + " 家", C_SUPPLIER));
        row.add(metricCard("本月入库批次", batchCnt + " 批", C_BATCHES));
        row.add(metricCard("本月采购金额", "¥ " + fmt(totalAmt), C_AMOUNT));
        row.add(metricCard("平均批次金额", "¥ " + fmt(avgAmt), C_AVG));
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
        row.add(buildTrendBarChart());
        row.add(buildSupplierPieChart());
        return row;
    }

    /** 月度采购金额趋势 */
    private ChartPanel buildTrendBarChart() {
        DefaultCategoryDataset ds = new DefaultCategoryDataset();
        boolean hasData = false;
        try {
            hasData = DatabaseManager.getInstance().executeQuery(
                "SELECT DATE_FORMAT(in_date,'%Y-%m') AS m, COALESCE(SUM(quantity*unit_price),0) AS amt " +
                "FROM inv_stock_in WHERE tenant_id=? AND in_date >= DATE_SUB(CURDATE(),INTERVAL 6 MONTH) " +
                "GROUP BY m ORDER BY m",
                rs -> {
                    boolean any = false;
                    while (rs.next()) { ds.addValue(rs.getDouble("amt"), "金额", rs.getString("m")); any = true; }
                    return any;
                }, tenant);
        } catch (Exception ignored) {}
        if (!hasData) ds.addValue(0, "金额", "暂无数据");

        JFreeChart chart = ChartFactory.createBarChart(
            "月度采购金额趋势", null, "金额 (¥)", ds,
            PlotOrientation.VERTICAL, false, true, false);
        applyStyle(chart);

        CategoryPlot plot = chart.getCategoryPlot();
        BarRenderer r = (BarRenderer) plot.getRenderer();
        r.setSeriesPaint(0, C_BAR);
        r.setShadowVisible(false);
        r.setMaximumBarWidth(0.10);

        return wrap(chart);
    }

    /** 供应商采购占比饼图 */
    private ChartPanel buildSupplierPieChart() {
        DefaultPieDataset<String> ds = new DefaultPieDataset<>();
        boolean hasData = false;
        try {
            hasData = DatabaseManager.getInstance().executeQuery(
                "SELECT supplier, ROUND(COALESCE(SUM(quantity*unit_price),0),2) AS amt " +
                "FROM inv_stock_in WHERE tenant_id=? AND in_date >= DATE_SUB(CURDATE(),INTERVAL 3 MONTH) " +
                "GROUP BY supplier ORDER BY amt DESC",
                rs -> {
                    boolean any = false;
                    while (rs.next()) { ds.setValue(rs.getString("supplier"), rs.getDouble("amt")); any = true; }
                    return any;
                }, tenant);
        } catch (Exception ignored) {}
        if (!hasData) ds.setValue("暂无数据", 1);

        JFreeChart chart = ChartFactory.createPieChart("供应商采购占比(近3月)", ds, true, true, false);
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

    private int queryInt(String sql) {
        try { return DatabaseManager.getInstance().executeQuery(sql, rs -> rs.next() ? rs.getInt(1) : 0, tenant); }
        catch (Exception e) { return 0; }
    }

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
