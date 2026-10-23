package io.bettermindustry.desktop;

import io.bettermindustry.protocol.InputCommandType;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DesktopMultiViewMain {
    private static final Color BACKGROUND = new Color(8, 14, 21);
    private static final Color PANEL = new Color(16, 28, 40);
    private static final Color BORDER = new Color(42, 70, 94);
    private static final Color TEXT = new Color(220, 232, 242);
    private static final Color MUTED = new Color(132, 153, 171);
    private static final Color ACCENT = new Color(105, 184, 255);
    private static final Color GOOD = new Color(62, 210, 126);
    private static final Color WARN = new Color(242, 184, 82);

    private final JFrame frame = new JFrame("BetterMindustry · Desktop MultiView");
    private final JTextField serverField = new JTextField("http://127.0.0.1:19090");
    private final JTextField usernameField = new JTextField(12);
    private final JPasswordField passwordField = new JPasswordField(12);
    private final JLabel identityLabel = new JLabel("OFFLINE");
    private final JLabel statusLabel = new JLabel("正在连接 Gateway…");
    private final JPanel sectorsPanel = new JPanel(new GridLayout(1, 0, 14, 0));
    private final Map<String, SectorCard> cards = new LinkedHashMap<>();
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(3, runnable -> {
        Thread thread = new Thread(runnable, "bettermindustry-desktop-client");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private final Path screenshotPath;

    private GatewayClient client;
    private GatewayClient.SessionInfo session;

    public static void main(String[] args) {
        Path screenshotPath = null;
        for (int i = 0; i < args.length - 1; i++) {
            if ("--screenshot".equals(args[i])) screenshotPath = Path.of(args[i + 1]);
        }
        Path finalScreenshotPath = screenshotPath;
        SwingUtilities.invokeLater(() -> {
            configureTheme();
            new DesktopMultiViewMain(finalScreenshotPath).show();
        });
    }

    private DesktopMultiViewMain(Path screenshotPath) {
        this.screenshotPath = screenshotPath;
    }

    private void show() {
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(1100, 720));
        frame.setSize(1480, 900);
        frame.getContentPane().setBackground(BACKGROUND);
        frame.setLayout(new BorderLayout(12, 12));

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.X_AXIS));
        top.setBackground(PANEL);
        top.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER),
            BorderFactory.createEmptyBorder(10, 12, 10, 12)
        ));
        top.add(label("Gateway", MUTED, Font.PLAIN, 12));
        top.add(Box.createHorizontalStrut(6));
        styleField(serverField, 190);
        top.add(serverField);
        top.add(Box.createHorizontalStrut(12));
        top.add(label("账户", MUTED, Font.PLAIN, 12));
        top.add(Box.createHorizontalStrut(6));
        styleField(usernameField, 130);
        styleField(passwordField, 150);
        top.add(usernameField);
        top.add(Box.createHorizontalStrut(6));
        top.add(passwordField);
        top.add(Box.createHorizontalStrut(8));
        JButton login = button("登录", () -> login(false));
        JButton register = button("注册", () -> login(true));
        JButton guest = button("重新连接访客", this::connectGuest);
        top.add(login);
        top.add(Box.createHorizontalStrut(6));
        top.add(register);
        top.add(Box.createHorizontalStrut(6));
        top.add(guest);
        top.add(Box.createHorizontalStrut(14));
        identityLabel.setForeground(MUTED);
        top.add(identityLabel);
        top.add(Box.createHorizontalGlue());

        sectorsPanel.setBackground(BACKGROUND);
        sectorsPanel.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBackground(BACKGROUND);
        root.add(top, BorderLayout.NORTH);
        root.add(sectorsPanel, BorderLayout.CENTER);
        statusLabel.setForeground(MUTED);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 14, 8, 14));
        root.add(statusLabel, BorderLayout.SOUTH);

        frame.setContentPane(root);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        connectGuest();
        if (screenshotPath != null) scheduleScreenshot();

        executor.scheduleWithFixedDelay(this::refreshSnapshot, 500, 1_000, TimeUnit.MILLISECONDS);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                executor.shutdownNow();
            }
        });
    }

    private void scheduleScreenshot() {
        Timer timer = new Timer(8_000, event -> {
            try {
                Path target = screenshotPath.toAbsolutePath().normalize();
                Path parent = target.getParent();
                if (parent != null) Files.createDirectories(parent);
                BufferedImage image = new BufferedImage(
                    Math.max(1, frame.getWidth()),
                    Math.max(1, frame.getHeight()),
                    BufferedImage.TYPE_INT_ARGB
                );
                Graphics2D graphics = image.createGraphics();
                try {
                    frame.getRootPane().printAll(graphics);
                } finally {
                    graphics.dispose();
                }
                ImageIO.write(image, "png", target.toFile());
                System.out.println("Screenshot written: " + target);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                executor.shutdownNow();
                frame.dispose();
            }
        });
        timer.setRepeats(false);
        timer.start();
    }

    private void connectGuest() {
        runAsync("建立访客 Session…", () -> {
            client = new GatewayClient(URI.create(serverField.getText().trim()));
            session = client.connect();
            applyIdentity();
            refreshSnapshot();
            return null;
        });
    }

    private void login(boolean register) {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        runAsync(register ? "注册并登录…" : "登录…", () -> {
            if (client == null) client = new GatewayClient(URI.create(serverField.getText().trim()));
            if (register) client.register(username, password);
            session = client.login(username, password);
            applyIdentity();
            refreshSnapshot();
            return null;
        });
    }

    private void applyIdentity() {
        SwingUtilities.invokeLater(() -> {
            if (session == null) {
                identityLabel.setText("OFFLINE");
                return;
            }
            identityLabel.setText(session.username() == null
                ? "GUEST · " + shortId(session.clientId())
                : session.username() + " · " + session.role());
            identityLabel.setForeground(session.username() == null ? MUTED : GOOD);
        });
    }

    private Void refreshSnapshot() {
        if (client == null || !refreshing.compareAndSet(false, true)) return null;
        try {
            GatewayClient.Snapshot snapshot = client.snapshot();
            Map<String, byte[]> images = new LinkedHashMap<>();
            for (GatewayClient.Sector sector : snapshot.sectors()) {
                SectorCard existing = cards.get(sector.id());
                if (!sector.worldDataAvailable()) continue;
                if (existing != null && existing.imageVersion == sector.worldSnapshotVersion()) continue;
                try {
                    images.put(sector.id(), client.worldImage(sector.id()));
                } catch (RuntimeException ignored) {
                }
            }
            SwingUtilities.invokeLater(() -> render(snapshot, images));
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> {
                statusLabel.setForeground(WARN);
                statusLabel.setText("Gateway 刷新失败：" + e.getMessage());
            });
        } finally {
            refreshing.set(false);
        }
        return null;
    }

    private void render(GatewayClient.Snapshot snapshot, Map<String, byte[]> images) {
        List<String> currentIds = new ArrayList<>();
        for (GatewayClient.Sector sector : snapshot.sectors()) {
            currentIds.add(sector.id());
            SectorCard card = cards.get(sector.id());
            if (card == null) {
                card = new SectorCard(sector.id());
                cards.put(sector.id(), card);
                sectorsPanel.add(card.panel);
            }
            card.update(sector, snapshot, images.get(sector.id()));
        }
        cards.entrySet().removeIf(entry -> {
            if (currentIds.contains(entry.getKey())) return false;
            sectorsPanel.remove(entry.getValue().panel);
            return true;
        });
        sectorsPanel.revalidate();
        sectorsPanel.repaint();
        statusLabel.setForeground(MUTED);
        statusLabel.setText("Gateway " + serverField.getText().trim()
            + " · Focus " + snapshot.focusSectorId()
            + " · 控制者 " + (snapshot.controlOwnerId() == null ? "空闲" : shortId(snapshot.controlOwnerId())));
    }

    private final class SectorCard {
        private final JPanel panel = new JPanel(new BorderLayout(0, 8));
        private final JLabel title = label("", TEXT, Font.BOLD, 18);
        private final JLabel subtitle = label("", MUTED, Font.PLAIN, 12);
        private final JLabel image = new JLabel("等待地图快照…", JLabel.CENTER);
        private final JTextArea metrics = new JTextArea();
        private final JButton focus = button("设为主控", this::focus);
        private final JButton acquire = button("获取控制", this::acquire);
        private final JButton release = button("释放控制", this::release);
        private final JButton pause = button("暂停", () -> action(InputCommandType.PAUSE));
        private final JButton resume = button("继续", () -> action(InputCommandType.RESUME));
        private GatewayClient.Sector sector;
        private GatewayClient.Snapshot snapshot;
        private BufferedImage rawImage;
        private long imageVersion = Long.MIN_VALUE;

        SectorCard(String id) {
            panel.setBackground(PANEL);
            panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)
            ));
            JPanel header = new JPanel();
            header.setOpaque(false);
            header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
            header.add(title);
            header.add(subtitle);

            image.setOpaque(true);
            image.setBackground(BACKGROUND);
            image.setForeground(MUTED);
            image.setPreferredSize(new Dimension(640, 430));
            image.setBorder(BorderFactory.createLineBorder(BORDER));

            metrics.setEditable(false);
            metrics.setOpaque(false);
            metrics.setForeground(TEXT);
            metrics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            metrics.setRows(7);
            metrics.setColumns(20);

            JPanel controls = new JPanel(new GridLayout(3, 2, 7, 7));
            controls.setOpaque(false);
            controls.add(focus);
            controls.add(acquire);
            controls.add(release);
            controls.add(pause);
            controls.add(resume);

            JSplitPane lower = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(metrics), controls);
            lower.setResizeWeight(0.62);
            lower.setDividerSize(7);
            lower.setBorder(null);
            lower.setOpaque(false);

            panel.add(header, BorderLayout.NORTH);
            panel.add(image, BorderLayout.CENTER);
            panel.add(lower, BorderLayout.SOUTH);
            title.setText(id);
        }

        void update(GatewayClient.Sector next, GatewayClient.Snapshot state, byte[] bytes) {
            sector = next;
            snapshot = state;
            boolean focused = next.id().equals(state.focusSectorId());
            boolean controlling = state.clientId().equals(state.controlOwnerId());
            boolean canControl = session != null
                && ("PLAYER".equals(session.role()) || "ADMIN".equals(session.role()));

            title.setText(next.id() + (focused ? "  ·  FOCUS" : "  ·  OBSERVE"));
            title.setForeground(focused ? ACCENT : TEXT);
            subtitle.setText(next.map() + " · " + next.state()
                + " · :" + next.port() + " " + (next.portOpen() ? "online" : "offline"));
            metrics.setText(
                "Game Tick   " + Math.floor(next.gameTick()) + "\n"
                    + "游戏时间    " + formatTime(next.gameTimeSeconds()) + "\n"
                    + "暂停        " + (next.paused() ? "yes" : "no") + "\n"
                    + "玩家 / 单位 " + next.players() + " / " + next.units() + "\n"
                    + "敌人        " + next.enemies() + "\n"
                    + "建筑        " + next.buildings() + "\n"
                    + "视口        " + next.viewMode()
            );
            focus.setEnabled(!focused);
            acquire.setEnabled(focused && canControl && !controlling);
            release.setEnabled(focused && controlling && canControl);
            pause.setEnabled(focused && controlling && canControl && !next.paused());
            resume.setEnabled(focused && controlling && canControl && next.paused());
            panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(focused ? ACCENT : BORDER, focused ? 2 : 1),
                BorderFactory.createEmptyBorder(11, 11, 11, 11)
            ));

            if (bytes != null) {
                try {
                    rawImage = ImageIO.read(new ByteArrayInputStream(bytes));
                    imageVersion = next.worldSnapshotVersion();
                } catch (Exception ignored) {
                }
            }
            scaleImage();
        }

        private void scaleImage() {
            if (rawImage == null) return;
            int width = Math.max(320, image.getWidth() > 0 ? image.getWidth() : 640);
            int height = Math.max(220, image.getHeight() > 0 ? image.getHeight() : 430);
            double scale = Math.min((double)width / rawImage.getWidth(), (double)height / rawImage.getHeight());
            int scaledWidth = Math.max(1, (int)(rawImage.getWidth() * scale));
            int scaledHeight = Math.max(1, (int)(rawImage.getHeight() * scale));
            image.setIcon(new ImageIcon(rawImage.getScaledInstance(
                scaledWidth,
                scaledHeight,
                java.awt.Image.SCALE_SMOOTH
            )));
            image.setText("");
        }

        private void focus() {
            if (sector == null) return;
            runAsync("切换 Focus 到 " + sector.id() + "…", () -> {
                client.setFocus(sector.id());
                refreshSnapshot();
                return null;
            });
        }

        private void acquire() {
            if (sector == null) return;
            runAsync("获取 " + sector.id() + " 控制权…", () -> {
                client.acquireControl(sector.id());
                refreshSnapshot();
                return null;
            });
        }

        private void release() {
            if (sector == null) return;
            runAsync("释放 " + sector.id() + " 控制权…", () -> {
                client.releaseControl(sector.id());
                refreshSnapshot();
                return null;
            });
        }

        private void action(InputCommandType type) {
            if (sector == null) return;
            long tick = (long)Math.floor(sector.gameTick());
            runAsync(type + " " + sector.id() + "…", () -> {
                GatewayClient.InputResult result = client.action(sector.id(), tick, type, "");
                SwingUtilities.invokeLater(() -> statusLabel.setText(
                    result.status() + " · ackTick=" + Math.floor(result.acknowledgementTick())
                        + " · paused=" + result.acknowledgementPaused()
                ));
                refreshSnapshot();
                return null;
            });
        }
    }

    private void runAsync(String status, ThrowingSupplier<Void> action) {
        statusLabel.setForeground(MUTED);
        statusLabel.setText(status);
        executor.submit(() -> {
            try {
                action.get();
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setForeground(Color.decode("#ff7b7b"));
                    statusLabel.setText(e.getMessage());
                });
            }
        });
    }

    private static void configureTheme() {
        UIManager.put("Panel.background", PANEL);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("Button.background", new Color(27, 48, 67));
        UIManager.put("Button.foreground", TEXT);
        UIManager.put("TextField.background", BACKGROUND);
        UIManager.put("TextField.foreground", TEXT);
        UIManager.put("TextField.caretForeground", TEXT);
        UIManager.put("PasswordField.background", BACKGROUND);
        UIManager.put("PasswordField.foreground", TEXT);
        UIManager.put("TextArea.background", PANEL);
        UIManager.put("TextArea.foreground", TEXT);
        UIManager.put("TextArea.caretForeground", TEXT);
        UIManager.put("TextArea.selectionBackground", new Color(35, 80, 112));
        UIManager.put("TextArea.selectionForeground", Color.WHITE);
        UIManager.put("ScrollPane.background", BACKGROUND);
        UIManager.put("Viewport.background", PANEL);
        UIManager.put("SplitPane.background", BACKGROUND);
        UIManager.put("SplitPane.dividerSize", 6);
    }

    private static void styleField(JTextField field, int columns) {
        field.setColumns(columns);
        field.setMaximumSize(new Dimension(columns * 10, 30));
        field.setBackground(BACKGROUND);
        field.setForeground(TEXT);
        field.setCaretColor(TEXT);
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER),
            BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));
    }

    private static JLabel label(String text, Color color, int style, int size) {
        JLabel label = new JLabel(text);
        label.setForeground(color);
        label.setFont(new Font(Font.SANS_SERIF, style, size));
        return label;
    }

    private static JButton button(String text, Runnable action) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.addActionListener(event -> action.run());
        return button;
    }

    private static String shortId(String value) {
        return value == null || value.length() <= 12 ? value : value.substring(0, 12) + "…";
    }

    private static String formatTime(double seconds) {
        long total = (long)seconds;
        long hours = total / 3600;
        long minutes = (total % 3600) / 60;
        long remaining = total % 60;
        return (hours > 0 ? hours + ":" : "")
            + String.format("%02d:%02d", minutes, remaining);
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
