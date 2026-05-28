package com.erp.ui;

import javax.swing.*;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
import java.awt.event.*;

/**
 * 可关闭的标签页组件 — 每个标签右侧带 ❌ 关闭按钮。
 */
public class ClosableTabbedPane extends JTabbedPane {

    public ClosableTabbedPane() {
        super();
    }

    @Override
    public void addTab(String title, Component component) {
        addTab(title, null, component, true);
    }

    public void addTab(String title, Icon icon, Component component, boolean closable) {
        super.addTab(title, icon, component);
        int idx = getTabCount() - 1;
        setTabComponentAt(idx, new TabHeader(title, closable, idx));
        setSelectedIndex(idx);
    }

    public void addTab(String title, Component component, boolean closable) {
        addTab(title, null, component, closable);
    }

    private class TabHeader extends JPanel {
        public TabHeader(String title, boolean closable, int index) {
            setLayout(new BorderLayout(4, 0));
            setOpaque(false);
            JLabel label = new JLabel(title);
            label.setFont(getFont().deriveFont(12f));
            add(label, BorderLayout.CENTER);

            if (closable) {
                JButton close = new CloseButton(index);
                add(close, BorderLayout.EAST);
            }
        }
    }

    private class CloseButton extends JButton {
        public CloseButton(int index) {
            setText("✕");
            setFont(getFont().deriveFont(Font.PLAIN, 12));
            setPreferredSize(new Dimension(18, 18));
            setBorder(BorderFactory.createEmptyBorder());
            setContentAreaFilled(false);
            setFocusable(false);
            setToolTipText("关闭");
            addActionListener(e -> {
                int i = getTabIndex(this);
                if (i >= 0 && i < getTabCount()) {
                    removeTabAt(i);
                }
            });
        }
    }

    private int getTabIndex(JButton btn) {
        for (int i = 0; i < getTabCount(); i++) {
            Component c = getTabComponentAt(i);
            if (c instanceof TabHeader) {
                for (Component child : ((TabHeader) c).getComponents()) {
                    if (child == btn) return i;
                }
            }
        }
        return -1;
    }
}
