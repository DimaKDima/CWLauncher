package ru.cw.launcher.ui;

import ru.cw.launcher.news.NewsManager;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/** Новость целиком: тот же большой прокручиваемый экран, что и у списка обновлений. */
public final class NewsWindow {

    private NewsWindow() {
    }

    public static void show(Window owner, List<NewsManager.Item> items) {
        JDialog d = new JDialog(owner, "Новости", Dialog.ModalityType.APPLICATION_MODAL);
        d.setUndecorated(true);
        if (owner != null && owner.isShowing()) d.setBounds(owner.getBounds());
        else {
            d.setSize(980, 640);
            d.setLocationRelativeTo(owner);
        }
        d.getContentPane().setBackground(new Color(0x0B1220));
        d.setLayout(new BorderLayout(12, 8));
        ((JComponent) d.getContentPane()).setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));

        JLabel title = new JLabel("Новости");
        title.setFont(Theme.H2);
        title.setForeground(Color.WHITE);
        d.add(title, BorderLayout.NORTH);

        DefaultListModel<NewsManager.Item> model = new DefaultListModel<>();
        for (NewsManager.Item item : items) model.addElement(item);
        JList<NewsManager.Item> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setBackground(Theme.FIELD);
        list.setForeground(Theme.TEXT);
        list.setFont(Theme.BODY);
        list.setFixedCellHeight(44);
        list.setCellRenderer((jl, value, index, selected, focus) -> {
            String caption = value == null ? "" : value.title();
            if (caption.length() > 48) caption = caption.substring(0, 47) + "…";
            JLabel l = new JLabel(caption);
            l.setOpaque(true);
            l.setFont(Theme.BODY);
            l.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
            l.setBackground(selected ? new Color(0x1A3F73) : Theme.FIELD);
            l.setForeground(selected ? Color.WHITE : Theme.TEXT);
            return l;
        });

        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(Theme.BODY);
        area.setBackground(Theme.FIELD);
        area.setForeground(Theme.TEXT);
        area.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        Runnable showSelected = () -> {
            NewsManager.Item item = list.getSelectedValue();
            if (item == null) {
                area.setText("");
            } else {
                String date = item.date() == null || item.date().isBlank() ? "" : item.date() + "\n\n";
                area.setText(item.title() + "\n\n" + date + (item.text() == null ? "" : item.text()));
            }
            area.setCaretPosition(0);
        };
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showSelected.run();
        });
        if (!model.isEmpty()) list.setSelectedIndex(0);
        else showSelected.run();

        JScrollPane listScroll = new JScrollPane(list);
        JScrollPane textScroll = new JScrollPane(area);
        Theme.style(listScroll);
        Theme.style(textScroll);
        listScroll.setBorder(BorderFactory.createLineBorder(new Color(0x2458A0)));
        textScroll.setBorder(BorderFactory.createLineBorder(new Color(0x2458A0)));
        listScroll.getViewport().setBackground(Theme.FIELD);
        textScroll.getViewport().setBackground(Theme.FIELD);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroll, textScroll);
        split.setDividerLocation(280);
        split.setBackground(new Color(0x0B1220));
        split.setBorder(null);
        d.add(split, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        CutButton close = new CutButton("Закрыть", CutButton.Accent.GHOST, false);
        close.addActionListener(e -> d.dispose());
        buttons.add(close);
        d.add(buttons, BorderLayout.SOUTH);
        WindowBackground.dim(d);
        d.setVisible(true);
    }
}
