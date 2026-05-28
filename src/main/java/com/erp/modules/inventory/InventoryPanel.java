package com.erp.modules.inventory;

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
import java.awt.*;
import java.text.DecimalFormat;

/**
 * 库存概览面板 — 类别饼图 + 库存量柱状图 + 指标卡片。
 */
public class InventoryPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final String tenant;

    private static final Font FONT_TITLE  = new Font("微软雅黑", Font.BOLD, 14);
    private static final Font FONT_LABEL  = new Font("微软雅黑", Font.PLAIN, 11);
    private static final Font FONT_TICK   = new Font("微软雅黑", Font.PLAIN, 10);
    private static final Font FONT_CARD   = new Font("微软雅黑", Font.PLAIN, 11);
    private static final Font FONT_CARD_V = new Font("微软雅黑", Font.BOLD, 16);

    private static final Color C_PRODUCT = new Color(66, 133, 244);
    private static final Color C_CAT     = new Color(52, 168, 83);
    private static final Color C_STOCK   = new Color(251, 188, 4);
    private static final Color C_SHORT   = new Color(234, 67, 53);
    private static final Color C_BAR     = new Color(66, 133, 244);

    private static final Color[] PIE_COLORS = {
        new Color(66, 133, 244),  new Color(52, 168, 83),   new Color(251, 188, 4),
        new Color(234, 67, 53),   new Color(142, 68, 173)
    };

    public InventoryPanel() {
        String t = TenantContext.getCurrentTenantId();
        tenant = (t != null) ? t : "t-arche";

        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(16, 16, 16, 16));

        JLabel title = new JLabel("库存概览 Dashboard");
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
        int productCount = queryInt("SELECT COUNT(*) FROM inv_products WHERE tenant_id=?", tenant);
        int catCount     = queryInt("SELECT COUNT(DISTINCT category) FROM inv_products WHERE tenant_id=?", tenant);

        double totalStock = queryDouble(
            "SELECT COALESCE(SUM(COALESCE(si.qty,0)-COALESCE(so.qty,0)),0) " +
            "FROM inv_products p " +
            "LEFT JOIN (SELECT product_id,SUM(quantity) qty FROM inv_stock_in WHERE tenant_id=? GROUP BY product_id) si ON si.product_id=p.id " +
            "LEFT JOIN (SELECT product_id,SUM(quantity) qty FROM inv_stock_out WHERE tenant_id=? GROUP BY product_id) so ON so.product_id=p.id " +
            "WHERE p.tenant_id=?", tenant, tenant, tenant);

        int shortage = queryInt(
            "SELECT COUNT(*) FROM inv_products p " +
            "LEFT JOIN (SELECT product_id,SUM(quantity) qty FROM inv_stock_in WHERE tenant_id=? GROUP BY product_id) si ON si.product_id=p.id " +
            "LEFT JOIN (SELECT product_id,SUM(quantity) qty FROM inv_stock_out WHERE tenant_id=? GROUP BY product_id) so ON so.product_id=p.id " +
            "WHERE p.tenant_id=? AND (COALESCE(si.qty,0)-COALESCE(so.qty,0)) < p.min_stock",
            tenant, tenant, tenant);

        JPanel row = new JPanel(new GridLayout(1, 4, 12, 0));
        row.setOpaque(false);
        row.add(metricCard("产品总数", productCount + " 种", C_PRODUCT));
        row.add(metricCard("品类", catCount + " 类", C_CAT));
        row.add(metricCard("库存总量", fmt(totalStock) + " 件", C_STOCK));
        row.add(metricCard("短缺物资", shortage + " 项", shortage > 0 ? C_SHORT : C_CAT));
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
        row.add(buildCategoryPieChart());
        row.add(buildStockBarChart());
        return row;
    }

    /** 库存类别饼图 */
    private ChartPanel buildCategoryPieChart() {
        DefaultPieDataset<String> ds = new DefaultPieDataset<>();
        boolean hasData = false;
        try {
            hasData = DatabaseManager.getInstance().executeQuery(
                "SELECT category, COUNT(*) FROM inv_products WHERE tenant_id=? GROUP BY category ORDER BY COUNT(*) DESC",
                rs -> {
                    boolean any = false;
                    while (rs.next()) { ds.setValue(rs.getString(1), rs.getInt(2)); any = true; }
                    return any;
                }, tenant);
        } catch (Exception ignored) {}
        if (!hasData) ds.setValue("暂无数据", 1);

        JFreeChart chart = ChartFactory.createPieChart("库存类别分布", ds, true, true, false);
        applyStyle(chart);

        PiePlot<?> plot = (PiePlot<?>) chart.getPlot();
        plot.setLabelGenerator(new StandardPieSectionLabelGenerator(
            "{0}: {1}种 ({2})", new DecimalFormat("0"), new DecimalFormat("0.0%")));
        plot.setSimpleLabels(true);
        int ci = 0;
        for (Object key : ds.getKeys()) {
            if (ci < PIE_COLORS.length) plot.setSectionPaint((Comparable<?>) key, PIE_COLORS[ci++]);
        }

        return wrap(chart);
    }

    /** 库存量柱状图 Top 10 */
    private ChartPanel buildStockBarChart() {
        DefaultCategoryDataset ds = new DefaultCategoryDataset();
        boolean hasData = false;
        try {
            hasData = DatabaseManager.getInstance().executeQuery(
                "SELECT p.product_name, ROUND(COALESCE(si.qty,0)-COALESCE(so.qty,0),0) AS stock " +
                "FROM inv_products p " +
                "LEFT JOIN (SELECT product_id,SUM(quantity) qty FROM inv_stock_in WHERE tenant_id=? GROUP BY product_id) si ON si.product_id=p.id " +
                "LEFT JOIN (SELECT product_id,SUM(quantity) qty FROM inv_stock_out WHERE tenant_id=? GROUP BY product_id) so ON so.product_id=p.id " +
                "WHERE p.tenant_id=? ORDER BY stock DESC LIMIT 10",
                rs -> {
                    boolean any = false;
                    while (rs.next()) { ds.addValue(rs.getDouble("stock"), "库存量", rs.getString("product_name")); any = true; }
                    return any;
                }, tenant, tenant, tenant);
        } catch (Exception ignored) {}
        if (!hasData) ds.addValue(0, "库存量", "暂无数据");

        JFreeChart chart = ChartFactory.createBarChart(
            "产品库存 Top 10", null, "库存量", ds,
            PlotOrientation.HORIZONTAL, false, true, false);
        applyStyle(chart);

        CategoryPlot plot = chart.getCategoryPlot();
        BarRenderer r = (BarRenderer) plot.getRenderer();
        r.setSeriesPaint(0, C_BAR);
        r.setShadowVisible(false);
        r.setMaximumBarWidth(0.12);

        return wrap(chart);
    }

    // ═══════════════════════════════════════════
    //  辅助
    // ═══════════════════════════════════════════

    private int queryInt(String sql, Object... params) {
        try { return DatabaseManager.getInstance().executeQuery(sql, rs -> rs.next() ? rs.getInt(1) : 0, params); }
        catch (Exception e) { return 0; }
    }

    private double queryDouble(String sql, Object... params) {
        try { return DatabaseManager.getInstance().executeQuery(sql, rs -> rs.next() ? rs.getDouble(1) : 0, params); }
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
