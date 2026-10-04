package ru.cw.launcher.ui;

import ru.cw.launcher.core.LauncherApp;
import ru.cw.launcher.updates.UpdateInfoManager;
import ru.cw.launcher.updates.UpdateParser;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * История обновлений Common World: список TXT из UpdateInfo.zip и полный текст выбранного файла.
 */
public final class UpdatesWindow {

    private UpdatesWindow() {
    }

    public static void show(Window owner, LauncherApp app) {
        JDialog d = new JDialog(owner, "Обновления Common World", Dialog.ModalityType.APPLICATION_MODAL);
        d.setUndecorated(true);
        if (owner != null && owner.isShowing()) d.setBounds(owner.getBounds());
        else {
            d.setSize(980, 640);
            d.setLocationRelativeTo(owner);
        }
        d.getContentPane().setBackground(new Color(0x0B1220));
        d.setLayout(new BorderLayout(12, 8));
        ((JComponent) d.getContentPane()).setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));

        JLabel title = new JLabel("Обновления Common World");
        title.setFont(Theme.H2);
        title.setForeground(Color.WHITE);
        JLabel status = new JLabel(" ");
        status.setFont(Theme.SMALL);
        status.setForeground(new Color(0x9BB0D0));
        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        head.add(title);
        head.add(Box.createVerticalStrut(4));
        head.add(status);
        d.add(head, BorderLayout.NORTH);

        DefaultListModel<UpdateParser.Note> model = new DefaultListModel<>();
        JList<UpdateParser.Note> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setBackground(Theme.FIELD);
        list.setForeground(Theme.TEXT);
        list.setFont(Theme.BODY);
        list.setFixedCellHeight(36);
        list.setCellRenderer((jl, value, index, selected, focus) -> {
            JLabel l = new JLabel(value == null ? "" : value.title());
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

        AtomicReference<List<UpdateParser.Note>> current = new AtomicReference<>(List.of());
        Runnable showSelected = () -> {
            UpdateParser.Note note = list.getSelectedValue();
            if (note == null) {
                area.setText(model.isEmpty() ? status.getText() : "");
            } else {
                area.setText(note.title() + "\n\n" + note.text());
            }
            area.setCaretPosition(0);
        };
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showSelected.run();
        });

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

        Runnable fill = () -> {
            model.clear();
            for (UpdateParser.Note note : current.get()) model.addElement(note);
            if (!model.isEmpty()) list.setSelectedIndex(0);
            else showSelected.run();
        };

        Runnable refresh = new Runnable() {
            @Override
            public void run() {
                status.setText("Загрузка информации об обновлениях…");
                app.io().submit(() -> {
                    UpdateInfoManager.Outcome outcome = app.updateInfo().sync(true);
                    SwingUtilities.invokeLater(() -> apply(d, status, current, fill, outcome, this));
                });
            }
        };

        List<UpdateParser.Note> cached = app.updateInfo().cached();
        current.set(cached);
        fill.run();
        if (!cached.isEmpty()) status.setText(" ");
        else status.setText("Информация об обновлениях пока недоступна.");
        if (!app.updateInfo().isBusy()) refresh.run();

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        CutButton again = new CutButton("Повторить", CutButton.Accent.PRIMARY, false);
        CutButton close = new CutButton("Закрыть", CutButton.Accent.GHOST, false);
        again.addActionListener(e -> {
            if (!app.updateInfo().isBusy()) refresh.run();
        });
        close.addActionListener(e -> d.dispose());
        buttons.add(again);
        buttons.add(close);
        d.add(buttons, BorderLayout.SOUTH);
        WindowBackground.dim(d);
        d.setVisible(true);
    }

    private static void apply(Component parent, JLabel status, AtomicReference<List<UpdateParser.Note>> current,
                              Runnable fill, UpdateInfoManager.Outcome outcome, Runnable retry) {
        if (outcome.notes() != null && !outcome.notes().isEmpty()) {
            current.set(outcome.notes());
            fill.run();
        }
        switch (outcome.kind()) {
            case OK -> status.setText(" ");
            case CACHED -> {
                status.setText(outcome.message() == null ? " " : outcome.message());
                if (outcome.message() != null) Dialogs.toast(parent, outcome.message(), new Color(0x4C8DFF));
            }
            case BUSY -> status.setText(outcome.message());
            case EMPTY -> {
                status.setText(outcome.message());
                if (current.get().isEmpty()) {
                    Dialogs.error(parent, outcome.message(), null, retry);
                }
            }
            case CORRUPT -> Dialogs.error(parent,
                    "Архив обновлений повреждён или имеет неправильный формат.", null, retry);
            case NETWORK -> {
                if (current.get().isEmpty()) {
                    status.setText("Информация об обновлениях пока недоступна.");
                    Dialogs.error(parent, "Не удалось загрузить информацию об обновлениях.", null, retry);
                } else {
                    status.setText("Используется сохранённая информация об обновлениях.");
                }
            }
        }
    }
}
