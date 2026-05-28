package com.erp.modules.tech;

import com.erp.db.DatabaseManager;
import com.erp.tenant.TenantContext;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.data.category.DefaultCategoryDataset;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * 技术概览面板 — 产品 + BOM 统计。
 */
public class TechPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final String tenant;

    private static final Font FONT_TITLE  = new Font("微软雅黑", Font.BOLD, 14);
    private static final Font FONT_LABEL  = new Font("微软雅黑", Font.PLAIN, 11);
    private static final Font FONT_TICK   = new Font("微软雅黑", Font.PLAIN, 10);
    private static final Font FONT_CARD   = new Font("微软雅黑", Font.PLAIN, 11);
    private static final Font FONT_CARD_V = new Font("微软雅黑", Font.BOLD, 16);

    private static final Color C_PRODUCT  = new Color(66, 133, 244);
    private static final Color C_BOM      = new Color(52, 168, 83);
    private static final Color C_CAT      = new Color(251, 188, 4);
    private static final Color C_COMP     = new Color(142, 68, 173);
    private static final Color C_BAR      = new Color(66, 133, 244);

    public TechPanel() {
        String t = TenantContext.getCurrentTenantId();
        tenant = (t != null) ? t : "t-arche";

        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(16, 16, 16, 16));

        JLabel title = new JLabel("技术概览 Dashboard");
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
        body.add(buildCategoryChart(), g);

        add(body, BorderLayout.CENTER);
    }

    private JPanel buildMetricCards() {
        int productCnt = queryInt("SELECT COUNT(*) FROM inv_products WHERE tenant_id=?");
        int bomCnt     = queryInt("SELECT COUNT(*) FROM tech_bom WHERE tenant_id=?");
        int catCnt     = queryInt("SELECT COUNT(DISTINCT category) FROM inv_products WHERE tenant_id=?");
        int compCnt    = queryInt("SELECT COUNT(DISTINCT component_name) FROM tech_bom WHERE tenant_id=? AND product_id IS NOT NULL");

        JPanel row = new JPanel(new GridLayout(1, 4, 12, 0));
        row.setOpaque(false);
        row.add(metricCard("📦 产品总数", productCnt + " 种", C_PRODUCT));
        row.add(metricCard("🧩 BOM条目", bomCnt + " 条", C_BOM));
        row.add(metricCard("🏷 产品类别", catCnt + " 类", C_CAT));
        row.add(metricCard("⚙ 部件种类", compCnt + " 种", C_COMP));
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

    /** 产品类别 + 各产品BOM条目数柱状图 */
    private ChartPanel buildCategoryChart() {
        DefaultCategoryDataset ds = new DefaultCategoryDataset();
        boolean hasData = false;
        try {
            hasData = DatabaseManager.getInstance().executeQuery(
                "SELECT p.category, COUNT(DISTINCT b.id) AS bom_cnt " +
                "FROM inv_products p LEFT JOIN tech_bom b ON b.product_id=p.id AND b.tenant_id=p.tenant_id " +
                "WHERE p.tenant_id=? GROUP BY p.category ORDER BY bom_cnt DESC",
                rs -> {
                    boolean any = false;
                    while (rs.next()) { ds.addValue(rs.getInt("bom_cnt"), "BOM条目", rs.getString("category")); any = true; }
                    return any;
                }, tenant);
        } catch (Exception ignored) {}
        if (!hasData) ds.addValue(0, "BOM条目", "暂无数据");

        JFreeChart chart = ChartFactory.createBarChart(
            "各产品类别 BOM 条目数", null, "BOM条目", ds,
            PlotOrientation.VERTICAL, false, true, false);
        applyStyle(chart);

        CategoryPlot plot = chart.getCategoryPlot();
        BarRenderer r = (BarRenderer) plot.getRenderer();
        r.setSeriesPaint(0, C_BAR);
        r.setShadowVisible(false);
        r.setMaximumBarWidth(0.10);

        return wrap(chart);
    }

    private int queryInt(String sql) {
        try { return DatabaseManager.getInstance().executeQuery(sql, rs -> rs.next() ? rs.getInt(1) : 0, tenant); }
        catch (Exception e) { return 0; }
    }

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
