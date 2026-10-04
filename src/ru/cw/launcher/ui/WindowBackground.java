package ru.cw.launcher.ui;

import ru.cw.launcher.background.BackgroundManager;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;

/** Фон выбранной картинки на всех окнах, кроме главного меню, с затемнением из настроек. */
public final class WindowBackground {

    private WindowBackground() {
    }

    public static void dim(RootPaneContainer window) {
        Container content = window.getContentPane();
        if (!(content instanceof JComponent pane) || pane.getClientProperty("cw.dimmed") != null) return;
        pane.putClientProperty("cw.dimmed", Boolean.TRUE);
        JPanel layer = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                BackgroundManager.paintShared(g, getWidth(), getHeight(), true);
            }
        };
        layer.setOpaque(true);
        window.setContentPane(layer);
        pane.setOpaque(false);
        layer.add(pane, BorderLayout.CENTER);
        clear(pane);
    }

    private static void clear(Component c) {
        if (c instanceof JTextComponent || c instanceof JList || c instanceof JScrollBar) return;
        if (c instanceof JPanel || c instanceof JScrollPane || c instanceof JViewport || c instanceof JTabbedPane) {
            ((JComponent) c).setOpaque(false);
        }
        if (c instanceof JScrollPane sp) {
            sp.getViewport().setOpaque(false);
        }
        if (c instanceof Container box) {
            for (Component child : box.getComponents()) clear(child);
        }
    }
}
