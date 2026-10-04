package ru.cw.launcher.ui;

import ru.cw.launcher.accounts.Account;
import ru.cw.launcher.accounts.ElyAuthManager;
import ru.cw.launcher.core.LauncherApp;
import ru.cw.launcher.skin.PlayerSkin;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Экран профиля: аккаунты Ely.by и оффлайн. У каждого аккаунта свой скин.
 * Аккаунты сами не создаются.
 */
public final class ProfileWindow {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    private ProfileWindow() {
    }

    public static void show(Window owner, LauncherApp app) {
        JDialog d = new JDialog(owner, "CWLauncher", Dialog.ModalityType.APPLICATION_MODAL);
        d.setUndecorated(true);
        if (owner != null && owner.isShowing()) d.setBounds(owner.getBounds());
        else {
            d.setSize(1100, 720);
            d.setLocationRelativeTo(owner);
        }
        d.getContentPane().setBackground(new Color(0x07101C));
        d.setLayout(new BorderLayout());
        ((JComponent) d.getContentPane()).setBorder(BorderFactory.createEmptyBorder(16, 18, 14, 18));

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setOpaque(false);
        d.add(root, BorderLayout.CENTER);
        Runnable[] rebuild = new Runnable[1];
        rebuild[0] = () -> {
            root.removeAll();
            root.add(header(d), BorderLayout.NORTH);
            root.add(body(d, app, rebuild[0]), BorderLayout.CENTER);
            root.add(footer(d, app, rebuild[0]), BorderLayout.SOUTH);
            root.revalidate();
            root.repaint();
        };
        rebuild[0].run();
        WindowBackground.dim(d);
        d.setVisible(true);
        PlayerSkin.refresh(app.account());
        app.refreshState();
    }

    private static JComponent header(JDialog d) {
        JLabel title = new JLabel("CWLauncher");
        title.setFont(Theme.H2);
        title.setForeground(Color.WHITE);
        JLabel sub = new JLabel(Lang.t("prof_title"));
        sub.setFont(Theme.SMALL);
        sub.setForeground(Theme.MUTED);
        JPanel titles = new JPanel();
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.setOpaque(false);
        titles.add(title);
        titles.add(sub);
        JButton close = SettingsChrome.pill("×", false);
        close.addActionListener(e -> d.dispose());
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(titles, BorderLayout.WEST);
        header.add(close, BorderLayout.EAST);
        return header;
    }

    private static JComponent body(JDialog d, LauncherApp app, Runnable rebuild) {
        Account current = app.account();
        JPanel left = column(
                currentCard(d, app, current, rebuild),
                infoCard(current),
                settingsCard(d, app, current, rebuild));
        JPanel right = column(
                manageCard(d, app, current, rebuild),
                elyCard(d, app, rebuild),
                listCard(app, rebuild));
        JPanel split = new JPanel(new GridLayout(1, 2, 14, 0));
        split.setOpaque(false);
        split.add(left);
        split.add(right);
        return split;
    }

    private static JPanel column(JComponent... cards) {
        JPanel p = new JPanel(new GridLayout(cards.length, 1, 0, 12));
        p.setOpaque(false);
        for (JComponent c : cards) p.add(c);
        return p;
    }

    private static JComponent currentCard(JDialog d, LauncherApp app, Account a, Runnable rebuild) {
        JPanel body = new JPanel(new BorderLayout(14, 0));
        body.setOpaque(false);
        body.add(avatar(a, 72), BorderLayout.WEST);
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        if (a == null) {
            text.add(nameLabel(Lang.t("prof_none")));
            text.add(Box.createVerticalStrut(4));
            text.add(muted(Lang.t("prof_none_h")));
        } else {
            text.add(nameLabel(a.username));
            text.add(Box.createVerticalStrut(2));
            text.add(muted(a.type == Account.Type.ELY ? Lang.t("prof_ely") : Lang.t("prof_off")));
            text.add(Box.createVerticalStrut(8));
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            row.setOpaque(false);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            JButton rename = SettingsChrome.pill(Lang.t("prof_rename"), false);
            rename.addActionListener(e -> rename(d, app, a, rebuild));
            row.add(rename);
            JLabel active = new JLabel("●  " + Lang.t("prof_active"));
            active.setFont(Theme.SMALL);
            active.setForeground(Theme.GREEN);
            row.add(active);
            text.add(row);
        }
        body.add(text, BorderLayout.CENTER);
        return card(Icons.Kind.USER, Lang.t("prof_current"), null, body);
    }

    private static JComponent infoCard(Account a) {
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        String id = a == null ? "—" : shortId(a);
        String platform = a == null ? "—" : (a.type == Account.Type.ELY ? "Ely.by" : Lang.t("prof_off"));
        String created = a == null ? "—" : WHEN.format(Instant.ofEpochMilli(a.createdAt));
        String status = a == null ? "—" : Lang.t("prof_active");
        body.add(infoLine(Icons.Kind.USER, Lang.t("prof_id"), id, Theme.INFO));
        body.add(Box.createVerticalStrut(8));
        body.add(infoLine(Icons.Kind.SERVER, Lang.t("prof_platform"), platform, Color.WHITE));
        body.add(Box.createVerticalStrut(8));
        body.add(infoLine(Icons.Kind.CLOCK, Lang.t("prof_created"), created, Color.WHITE));
        body.add(Box.createVerticalStrut(8));
        body.add(infoLine(Icons.Kind.CHECK, Lang.t("prof_status"), status, a == null ? Theme.MUTED : Theme.GREEN));
        return card(Icons.Kind.INFO, Lang.t("prof_info"), null, body);
    }

    private static JComponent settingsCard(JDialog d, LauncherApp app, Account a, Runnable rebuild) {
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.add(actionRow(Icons.Kind.SKIN, Lang.t("prof_avatar"), Lang.t("prof_avatar_h"), () -> {
            if (a == null) {
                Dialogs.info(d, Lang.t("prof_need"));
                return;
            }
            pickSkin(d, app, a, rebuild);
        }));
        return card(Icons.Kind.SETTINGS, Lang.t("prof_settings"), null, body);
    }

    private static JComponent manageCard(JDialog d, LauncherApp app, Account a, Runnable rebuild) {
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.add(actionRow(Icons.Kind.PLUS, Lang.t("prof_create"), Lang.t("prof_create_h"),
                () -> createAccount(d, app, rebuild)));
        body.add(Box.createVerticalStrut(8));
        body.add(actionRow(Icons.Kind.TRASH, Lang.t("prof_delete"), Lang.t("prof_delete_h"), () -> {
            if (a == null) return;
            if (!Dialogs.ask(d, Lang.t("prof_del_ask") + a.username + "?", Lang.t("prof_delete"), Lang.t("cancel"))) {
                return;
            }
            app.accounts().remove(a.id);
            PlayerSkin.refresh(app.account());
            rebuild.run();
        }));
        return card(Icons.Kind.USER, Lang.t("prof_manage"), null, body);
    }

    private static JComponent elyCard(JDialog d, LauncherApp app, Runnable rebuild) {
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        JLabel brand = new JLabel("Ely.by");
        brand.setFont(Theme.H2);
        brand.setForeground(Color.WHITE);
        brand.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(brand);
        body.add(Box.createVerticalStrut(4));
        JLabel hint = muted(Lang.t("prof_ely_h"));
        body.add(hint);
        body.add(Box.createVerticalStrut(10));
        JButton enter = SettingsChrome.pill(Lang.t("prof_ely_btn"), true);
        enter.setAlignmentX(Component.LEFT_ALIGNMENT);
        enter.addActionListener(e -> loginEly(d, app, rebuild));
        body.add(enter);
        body.add(Box.createVerticalStrut(6));
        JButton cfg = SettingsChrome.pill(Lang.t("prof_ely_cfg"), false);
        cfg.setAlignmentX(Component.LEFT_ALIGNMENT);
        cfg.addActionListener(e -> configureEly(d, app));
        body.add(cfg);
        return card(Icons.Kind.NET, Lang.t("prof_ely_in"), null, body);
    }

    private static JComponent listCard(LauncherApp app, Runnable rebuild) {
        JPanel rows = new JPanel();
        rows.setOpaque(false);
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        if (app.accounts().count() == 0) {
            rows.add(muted(Lang.t("prof_empty_list")));
        } else {
            Account active = app.account();
            for (Account a : app.accounts().all()) {
                boolean on = active != null && a.id.equals(active.id);
                rows.add(accountRow(a, on, () -> {
                    app.accounts().setActive(a.id);
                    PlayerSkin.refresh(a);
                    rebuild.run();
                }));
                rows.add(Box.createVerticalStrut(6));
            }
        }
        JScrollPane scroll = new JScrollPane(rows);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        Theme.style(scroll);
        return card(Icons.Kind.SHIELD, Lang.t("prof_list"), null, scroll);
    }

    private static JComponent footer(JDialog d, LauncherApp app, Runnable rebuild) {
        JLabel note = new JLabel(Lang.t("prof_foot"), Icons.of(Icons.Kind.INFO, 14).getIconObject(), SwingConstants.LEFT);
        note.setFont(Theme.SMALL);
        note.setForeground(Theme.MUTED);
        JButton refresh = SettingsChrome.pill("↻", false);
        refresh.addActionListener(e -> {
            PlayerSkin.refresh(app.account());
            rebuild.run();
        });
        JButton back = SettingsChrome.pill(Lang.t("prof_back"), false);
        back.addActionListener(e -> d.dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(refresh);
        buttons.add(back);
        JPanel south = new JPanel(new BorderLayout(12, 0));
        south.setOpaque(false);
        south.add(note, BorderLayout.CENTER);
        south.add(buttons, BorderLayout.EAST);
        return south;
    }

    private static void createAccount(JDialog owner, LauncherApp app, Runnable rebuild) {
        JDialog box = child(owner, Lang.t("prof_new"));
        JTextField nick = Theme.field("", 16);
        JButton offline = SettingsChrome.pill(Lang.t("prof_offline_btn"), true);
        JButton ely = SettingsChrome.pill(Lang.t("prof_ely_btn"), false);
        offline.addActionListener(e -> {
            String name = nick.getText().trim();
            if (!Account.validNick(name)) {
                Dialogs.info(box, Lang.t("prof_nick_bad"));
                return;
            }
            if (app.accounts().addOffline(name) == null) {
                Dialogs.info(box, Lang.t("prof_nick_bad"));
                return;
            }
            box.dispose();
            PlayerSkin.refresh(app.account());
            rebuild.run();
        });
        ely.addActionListener(e -> {
            box.dispose();
            loginEly(owner, app, rebuild);
        });
        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JLabel label = Theme.label(Lang.t("prof_nick"));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        nick.setAlignmentX(Component.LEFT_ALIGNMENT);
        nick.setMaximumSize(new Dimension(360, 32));
        form.add(label);
        form.add(Box.createVerticalStrut(6));
        form.add(nick);
        form.add(Box.createVerticalStrut(8));
        form.add(muted(Lang.t("prof_create_h")));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(ely);
        buttons.add(offline);
        box.add(form, BorderLayout.CENTER);
        box.add(buttons, BorderLayout.SOUTH);
        box.pack();
        box.setSize(Math.max(box.getWidth(), 420), box.getHeight() + 12);
        box.setLocationRelativeTo(owner);
        box.setVisible(true);
    }

    private static void rename(JDialog owner, LauncherApp app, Account a, Runnable rebuild) {
        if (a.type == Account.Type.ELY) {
            Dialogs.info(owner, Lang.t("prof_ely_name"));
            return;
        }
        JDialog box = child(owner, Lang.t("prof_rename"));
        JTextField nick = Theme.field(a.username, 16);
        JButton ok = SettingsChrome.pill(Lang.t("save"), true);
        JButton cancel = SettingsChrome.pill(Lang.t("cancel"), false);
        ok.addActionListener(e -> {
            String name = nick.getText().trim();
            if (!Account.validNick(name)) {
                Dialogs.info(box, Lang.t("prof_nick_bad"));
                return;
            }
            if (!app.accounts().renameOffline(a.id, name)) {
                Dialogs.info(box, Lang.t("prof_nick_taken"));
                return;
            }
            box.dispose();
            rebuild.run();
        });
        cancel.addActionListener(e -> box.dispose());
        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        nick.setAlignmentX(Component.LEFT_ALIGNMENT);
        nick.setMaximumSize(new Dimension(320, 32));
        form.add(nick);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(cancel);
        buttons.add(ok);
        box.add(form, BorderLayout.CENTER);
        box.add(buttons, BorderLayout.SOUTH);
        box.pack();
        box.setSize(Math.max(box.getWidth(), 360), box.getHeight() + 8);
        box.setLocationRelativeTo(owner);
        box.setVisible(true);
    }

    private static void pickSkin(JDialog owner, LauncherApp app, Account a, Runnable rebuild) {
        Frame frame = owner.getOwner() instanceof Frame f ? f : null;
        FileDialog dialog = new FileDialog(frame, Lang.t("prof_avatar"), FileDialog.LOAD);
        dialog.setFile("*.png;*.jpg;*.jpeg");
        dialog.setVisible(true);
        if (dialog.getFile() == null) return;
        Path src = Path.of(dialog.getDirectory(), dialog.getFile());
        try {
            PlayerSkin.assign(a, src);
        } catch (Exception ex) {
            Dialogs.error(owner, "Скин не сохранён.", ex.getMessage(), null);
            return;
        }
        PlayerSkin.refresh(a);
        rebuild.run();
    }

    private static void loginEly(Component parent, LauncherApp app, Runnable rebuild) {
        if (ElyAuthManager.configError(app.cfg) != null && !configureEly(parent, app)) return;
        String problem = ElyAuthManager.configError(app.cfg);
        if (problem != null) {
            ru.cw.launcher.util.Utils.openUrl("https://account.ely.by/dev/applications");
            Dialogs.info(parent, problem);
            return;
        }
        app.io().submit(() -> {
            ElyAuthManager.Result r = ElyAuthManager.startLogin(app.cfg, app.accounts(), (phase, text) -> {
            });
            SwingUtilities.invokeLater(() -> {
                if (!r.ok()) {
                    Dialogs.error(parent, "Не удалось войти через Ely.by.", r.error(), null);
                } else {
                    Dialogs.info(parent, "Вход через Ely.by выполнен: " + r.username());
                }
                PlayerSkin.refresh(app.account());
                rebuild.run();
            });
        });
    }

    /** Поля client id остаются: без них вход Ely.by не стартует. */
    private static boolean configureEly(Component parent, LauncherApp app) {
        JDialog box = child(parent instanceof Window w ? w : null, Lang.t("prof_ely_cfg"));
        JTextField id = Theme.field(app.cfg.elyClientId == null ? "" : app.cfg.elyClientId, 28);
        JTextField redirect = Theme.field(app.cfg.elyRedirectUri == null ? "" : app.cfg.elyRedirectUri, 28);
        JTextField backend = Theme.field(app.cfg.elyBackendUrl == null ? "" : app.cfg.elyBackendUrl, 28);
        boolean[] ok = {false};
        JButton save = SettingsChrome.pill(Lang.t("save"), true);
        JButton cancel = SettingsChrome.pill(Lang.t("cancel"), false);
        save.addActionListener(e -> {
            app.cfg.elyClientId = id.getText().trim();
            app.cfg.elyRedirectUri = redirect.getText().trim();
            app.cfg.elyBackendUrl = backend.getText().trim();
            app.saveSettings();
            ok[0] = true;
            box.dispose();
        });
        cancel.addActionListener(e -> box.dispose());
        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        form.add(fieldBlock(Lang.t("ely_id"), id));
        form.add(Box.createVerticalStrut(8));
        form.add(fieldBlock(Lang.t("ely_redirect"), redirect));
        form.add(Box.createVerticalStrut(8));
        form.add(fieldBlock(Lang.t("ely_backend"), backend));
        form.add(Box.createVerticalStrut(8));
        form.add(muted(Lang.t("prof_ely_h")));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(cancel);
        buttons.add(save);
        box.add(form, BorderLayout.CENTER);
        box.add(buttons, BorderLayout.SOUTH);
        box.pack();
        box.setSize(Math.max(box.getWidth(), 460), box.getHeight() + 8);
        box.setLocationRelativeTo(parent);
        box.setVisible(true);
        return ok[0];
    }

    private static JDialog child(Window owner, String title) {
        JDialog box = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        box.setUndecorated(true);
        box.getContentPane().setBackground(new Color(0x0B1220));
        box.setLayout(new BorderLayout(0, 8));
        ((JComponent) box.getContentPane()).setBorder(BorderFactory.createEmptyBorder(14, 14, 12, 14));
        return box;
    }

    private static JComponent fieldBlock(String caption, JTextField field) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        JLabel label = Theme.label(caption);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setMaximumSize(new Dimension(420, 32));
        p.add(label);
        p.add(Box.createVerticalStrut(4));
        p.add(field);
        return p;
    }

    private static JComponent card(Icons.Kind icon, String title, String text, JComponent body) {
        JPanel p = new JPanel(new BorderLayout(0, 8)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(SettingsChrome.CARD_FILL);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, 16, 16));
                g2.setColor(SettingsChrome.CARD_LINE);
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 2, getHeight() - 2, 16, 16));
                g2.dispose();
            }
        };
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        JPanel head = new JPanel(new BorderLayout(8, 0));
        head.setOpaque(false);
        JLabel name = new JLabel(title, Icons.of(icon, 16).getIconObject(), SwingConstants.LEFT);
        name.setFont(Theme.H3);
        name.setForeground(Color.WHITE);
        head.add(name, BorderLayout.WEST);
        if (text != null && !text.isBlank()) {
            JLabel desc = muted(text);
            head.add(desc, BorderLayout.SOUTH);
        }
        p.add(head, BorderLayout.NORTH);
        p.add(body, BorderLayout.CENTER);
        return p;
    }

    private static JComponent actionRow(Icons.Kind icon, String title, String hint, Runnable action) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x1E5AA8), 1, true),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
        JLabel text = new JLabel("<html><b>" + title + "</b><br><span style='color:#8b949e'>" + hint + "</span></html>");
        text.setFont(Theme.SMALL);
        text.setForeground(Color.WHITE);
        text.setIcon(Icons.of(icon, 18).getIconObject());
        row.add(text, BorderLayout.CENTER);
        JLabel chevron = new JLabel("›");
        chevron.setFont(Theme.H2);
        chevron.setForeground(Theme.INFO);
        row.add(chevron, BorderLayout.EAST);
        row.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                action.run();
            }
        });
        return row;
    }

    private static JComponent accountRow(Account a, boolean active, Runnable action) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(active ? new Color(0x4C8DFF) : new Color(0x1E5AA8), 1, true),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
        row.add(avatar(a, 36), BorderLayout.WEST);
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(nameLabel(a.username));
        JLabel kind = muted(a.type == Account.Type.ELY ? "Ely.by" : Lang.t("prof_off"));
        text.add(kind);
        row.add(text, BorderLayout.CENTER);
        if (active) {
            JLabel mark = new JLabel("●");
            mark.setForeground(Theme.GREEN);
            row.add(mark, BorderLayout.EAST);
        }
        row.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                action.run();
            }
        });
        return row;
    }

    private static JComponent infoLine(Icons.Kind icon, String label, String value, Color valueColor) {
        JPanel p = new JPanel(new BorderLayout(8, 0));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        JLabel left = new JLabel(label, Icons.of(icon, 14).getIconObject(), SwingConstants.LEFT);
        left.setFont(Theme.SMALL);
        left.setForeground(Theme.MUTED);
        JLabel right = new JLabel(value);
        right.setFont(Theme.BODY);
        right.setForeground(valueColor);
        p.add(left, BorderLayout.WEST);
        p.add(right, BorderLayout.EAST);
        return p;
    }

    private static JComponent avatar(Account a, int size) {
        BufferedImage face = PlayerSkin.headOfAccount(a);
        return new JComponent() {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(size, size);
            }

            @Override
            public Dimension getMinimumSize() {
                return getPreferredSize();
            }

            @Override
            public Dimension getMaximumSize() {
                return getPreferredSize();
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0x10233F));
                g2.fill(new RoundRectangle2D.Float(0, 0, size - 1, size - 1, 10, 10));
                g2.setColor(new Color(0x4C8DFF));
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, size - 2, size - 2, 10, 10));
                if (face != null) {
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                    int pad = Math.max(4, size / 8);
                    PlayerSkin.paint(g2, face, pad, pad, size - pad * 2);
                } else {
                    Icons.of(Icons.Kind.USER, size / 2).paint(new Color(0xD6E2F5), g2,
                            size / 4, size / 4, size / 2, size / 2);
                }
                g2.dispose();
            }
        };
    }

    private static JLabel nameLabel(String text) {
        JLabel l = new JLabel(text == null || text.isBlank() ? "—" : text);
        l.setFont(Theme.H2);
        l.setForeground(Color.WHITE);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static JLabel muted(String text) {
        JLabel l = new JLabel("<html><div style='width:280px'>" + text + "</div></html>");
        l.setFont(Theme.SMALL);
        l.setForeground(Theme.MUTED);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static String shortId(Account a) {
        String hex = a.shortUuid().toUpperCase(Locale.ROOT);
        if (hex.length() > 6) hex = hex.substring(0, 6);
        return "#" + hex;
    }
}
