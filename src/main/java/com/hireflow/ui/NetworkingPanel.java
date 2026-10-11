package com.hireflow.ui;

import com.hireflow.context.ApplicationContext;
import com.hireflow.network.protocol.ProtocolConstants;
import com.hireflow.network.server.HireFlowTcpServer;
import com.hireflow.network.server.NetworkActivity;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Operational dashboard and management console for the HireFlow Java TCP Networking Subsystem.
 * <p>
 * Key capabilities:
 * 1. Server lifecycle control: Non-blocking Start and Stop operations via SwingWorker.
 * 2. Real-time telemetry: Live counters for connected clients, subscribed clients, dispatched events, and uptime.
 * 3. Configuration & guards: Configurable localhost port, invalid/duplicate start prevention.
 * 4. Sanitized activity log: Real-time event log displaying public announcements and socket lifecycles.
 *    Strictly compliant with candidate privacy standards: zero student PII or confidential interview data.
 * 5. Swing EDT safety: All UI updates executed on the Event Dispatch Thread with bounded timer refresh.
 */
public class NetworkingPanel extends JPanel {

    public enum ServerState {
        STOPPED("STOPPED", UIUtils.TEXT_MUTED, new Color(241, 245, 249)),
        STARTING("STARTING...", UIUtils.WARNING, new Color(254, 243, 199)),
        RUNNING("RUNNING", UIUtils.SUCCESS, new Color(209, 250, 229)),
        STOPPING("STOPPING...", UIUtils.WARNING, new Color(254, 243, 199)),
        ERROR("ERROR", UIUtils.DANGER, new Color(254, 226, 226));

        private final String label;
        private final Color fg;
        private final Color bg;

        ServerState(String label, Color fg, Color bg) {
            this.label = label;
            this.fg = fg;
            this.bg = bg;
        }

        public String getLabel() {
            return label;
        }

        public Color getFg() {
            return fg;
        }

        public Color getBg() {
            return bg;
        }
    }

    private final ApplicationContext applicationContext;
    private HireFlowTcpServer activeServer;

    // UI State & Controls
    private ServerState currentState = ServerState.STOPPED;
    private JLabel lblStateBadge;
    private JTextField txtPort;
    private JLabel lblBoundAddress;
    private JButton btnStartServer;
    private JButton btnStopServer;
    private JButton btnRefreshMetrics;
    private JButton btnClearLog;

    // Stat metric value labels
    private JLabel lblConnectedClientsVal;
    private JLabel lblSubscribedClientsVal;
    private JLabel lblEventsDispatchedVal;
    private JLabel lblUptimeVal;
    private JLabel lblDisconnectMetrics;

    // Activity Stream Table
    private DefaultTableModel activityTableModel;
    private JTable activityTable;

    // Background metric refresh timer (1 second interval)
    private Timer metricRefreshTimer;

    // Testability & Telemetry error tracking
    private volatile String lastErrorMessage;
    private volatile String lastWarningMessage;

    public NetworkingPanel(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        if (applicationContext != null) {
            this.activeServer = applicationContext.getTcpServer().orElse(null);
        }

        setLayout(new BorderLayout(0, 16));
        setBackground(UIUtils.BG_MAIN);
        setBorder(new EmptyBorder(24, 28, 24, 28));

        initComponents();
        setupMetricTimer();
        refreshMetrics();
    }

    private void initComponents() {
        // 1. Top Header Area
        add(createHeaderPanel(), BorderLayout.NORTH);

        // 2. Central Scrollable Dashboard Content
        JPanel mainContent = new JPanel();
        mainContent.setLayout(new BoxLayout(mainContent, BoxLayout.Y_AXIS));
        mainContent.setOpaque(false);

        // A. Stat Cards Row
        mainContent.add(createStatCardsRow());
        mainContent.add(Box.createVerticalStrut(18));

        // B. Configuration & Standards Split Row
        JPanel splitRow = new JPanel(new GridLayout(1, 2, 18, 0));
        splitRow.setOpaque(false);
        splitRow.add(createServerControlCard());
        splitRow.add(createProtocolStandardsCard());
        mainContent.add(splitRow);
        mainContent.add(Box.createVerticalStrut(18));

        // C. Live Activity Stream Table Card
        mainContent.add(createActivityLogCard());

        JScrollPane scrollPane = new JScrollPane(mainContent);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        add(scrollPane, BorderLayout.CENTER);
    }

    private JPanel createHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JPanel leftBox = new JPanel(new GridLayout(2, 1, 0, 4));
        leftBox.setOpaque(false);

        JPanel titleBadgeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        titleBadgeRow.setOpaque(false);

        JLabel title = new JLabel("TCP Networking & Broadcasting");
        title.setFont(UIUtils.FONT_HEADER);
        title.setForeground(UIUtils.TEXT_PRIMARY);
        titleBadgeRow.add(title);

        lblStateBadge = new JLabel(currentState.getLabel());
        lblStateBadge.setFont(UIUtils.FONT_SMALL);
        lblStateBadge.setForeground(currentState.getFg());
        lblStateBadge.setBackground(currentState.getBg());
        lblStateBadge.setOpaque(true);
        lblStateBadge.setBorder(new CompoundBorder(
                new LineBorder(currentState.getFg(), 1, true),
                new EmptyBorder(3, 8, 3, 8)
        ));
        titleBadgeRow.add(lblStateBadge);

        JLabel subtitle = new JLabel("Multi-Client Socket Management, Bounded Backpressure & Transactional Public Announcements");
        subtitle.setFont(UIUtils.FONT_REGULAR);
        subtitle.setForeground(UIUtils.TEXT_SECONDARY);

        leftBox.add(titleBadgeRow);
        leftBox.add(subtitle);

        JPanel rightBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightBox.setOpaque(false);

        btnRefreshMetrics = UIUtils.createSecondaryButton("Refresh Metrics");
        btnRefreshMetrics.addActionListener(e -> refreshMetrics());
        rightBox.add(btnRefreshMetrics);

        header.add(leftBox, BorderLayout.WEST);
        header.add(rightBox, BorderLayout.EAST);

        return header;
    }

    private JPanel createStatCardsRow() {
        JPanel row = new JPanel(new GridLayout(1, 4, 16, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 95));

        // Connected Clients
        JPanel p1 = createMetricCard("CONNECTED CLIENTS", "0", UIUtils.INFO);
        lblConnectedClientsVal = (JLabel) p1.getClientProperty("valueLabel");
        row.add(p1);

        // Subscribed Clients
        JPanel p2 = createMetricCard("ACTIVE SUBSCRIBERS", "0", UIUtils.SUCCESS);
        lblSubscribedClientsVal = (JLabel) p2.getClientProperty("valueLabel");
        row.add(p2);

        // Events Dispatched
        JPanel p3 = createMetricCard("EVENTS DISPATCHED", "0", UIUtils.PRIMARY);
        lblEventsDispatchedVal = (JLabel) p3.getClientProperty("valueLabel");
        row.add(p3);

        // Server Uptime
        JPanel p4 = createMetricCard("SERVER UPTIME", "00:00:00", UIUtils.WARNING);
        lblUptimeVal = (JLabel) p4.getClientProperty("valueLabel");
        row.add(p4);

        return row;
    }

    private JPanel createMetricCard(String labelText, String initialValue, Color accentColor) {
        JPanel card = new JPanel(new BorderLayout(4, 4));
        card.setBackground(UIUtils.CARD_BG);
        card.setBorder(new CompoundBorder(
                new CompoundBorder(
                        new LineBorder(UIUtils.BORDER_COLOR, 1, true),
                        BorderFactory.createMatteBorder(0, 5, 0, 0, accentColor)
                ),
                new EmptyBorder(12, 16, 12, 16)
        ));

        JLabel title = new JLabel(labelText);
        title.setFont(UIUtils.FONT_SMALL);
        title.setForeground(UIUtils.TEXT_MUTED);

        JLabel val = new JLabel(initialValue);
        val.setFont(UIUtils.FONT_STAT_NUM);
        val.setForeground(UIUtils.TEXT_PRIMARY);

        card.add(title, BorderLayout.NORTH);
        card.add(val, BorderLayout.CENTER);
        card.putClientProperty("valueLabel", val);

        return card;
    }

    private JPanel createServerControlCard() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 4, 6, 4);

        // Row 0: Local Port Configuration
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.3;
        JLabel lblPort = new JLabel("Listen Port:");
        lblPort.setFont(UIUtils.FONT_BOLD);
        lblPort.setForeground(UIUtils.TEXT_PRIMARY);
        panel.add(lblPort, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.7;
        txtPort = UIUtils.createTextField(8);
        txtPort.setText(String.valueOf(ProtocolConstants.DEFAULT_PORT));
        txtPort.setToolTipText("Port between 1024 and 65535, or 0 for dynamic ephemeral port");
        panel.add(txtPort, gbc);

        // Row 1: Bound Interface & Address
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.3;
        JLabel lblBoundTitle = new JLabel("Bound Socket:");
        lblBoundTitle.setFont(UIUtils.FONT_BOLD);
        lblBoundTitle.setForeground(UIUtils.TEXT_PRIMARY);
        panel.add(lblBoundTitle, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.7;
        lblBoundAddress = new JLabel("Offline (Not bound)");
        lblBoundAddress.setFont(UIUtils.FONT_REGULAR);
        lblBoundAddress.setForeground(UIUtils.TEXT_MUTED);
        panel.add(lblBoundAddress, gbc);

        // Row 2: Secondary Metrics
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0.3;
        JLabel lblConnStats = new JLabel("Total Traffic:");
        lblConnStats.setFont(UIUtils.FONT_BOLD);
        lblConnStats.setForeground(UIUtils.TEXT_PRIMARY);
        panel.add(lblConnStats, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.7;
        lblDisconnectMetrics = new JLabel("Connections: 0 | Closed: 0 | Rejected: 0");
        lblDisconnectMetrics.setFont(UIUtils.FONT_SMALL);
        lblDisconnectMetrics.setForeground(UIUtils.TEXT_SECONDARY);
        panel.add(lblDisconnectMetrics, gbc);

        // Row 3: Action Buttons
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(16, 4, 4, 4);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        btnRow.setOpaque(false);

        btnStartServer = UIUtils.createSuccessButton("▶ Start TCP Server");
        btnStartServer.addActionListener(e -> executeStartServer());

        btnStopServer = UIUtils.createDangerButton("■ Stop TCP Server");
        btnStopServer.setEnabled(false);
        btnStopServer.addActionListener(e -> executeStopServer());

        btnRow.add(btnStartServer);
        btnRow.add(btnStopServer);

        panel.add(btnRow, gbc);

        return UIUtils.createCard("Server Controls & Lifecycle", panel);
    }

    private JPanel createProtocolStandardsCard() {
        JPanel content = new JPanel(new GridLayout(4, 1, 0, 8));
        content.setOpaque(false);

        content.add(createStandardItem("Protocol Version", "v1.0 (Newline-delimited JSON, 64 KB Max Frame)"));
        content.add(createStandardItem("Supported Commands", "PING, SERVER_INFO, GET_DRIVES, SUBSCRIBE_EVENTS, UNSUBSCRIBE_EVENTS, HEARTBEAT"));
        content.add(createStandardItem("Security & Privacy", "Strict Allowlist: JOB_DRIVE_CREATED, COMPANY_ADDED only. Zero student PII."));
        content.add(createStandardItem("Backpressure Policy", "Bounded 50-frame queue • Dedicated writer daemon • Non-blocking dispatch"));

        return UIUtils.createCard("Protocol Standards & Isolation Guardrails", content);
    }

    private JPanel createStandardItem(String title, String desc) {
        JPanel p = new JPanel(new BorderLayout(4, 2));
        p.setOpaque(false);

        JLabel t = new JLabel(title);
        t.setFont(UIUtils.FONT_BOLD);
        t.setForeground(UIUtils.TEXT_PRIMARY);

        JLabel d = new JLabel(desc);
        d.setFont(UIUtils.FONT_SMALL);
        d.setForeground(UIUtils.TEXT_SECONDARY);

        p.add(t, BorderLayout.NORTH);
        p.add(d, BorderLayout.CENTER);
        return p;
    }

    private JPanel createActivityLogCard() {
        JPanel container = new JPanel(new BorderLayout(0, 10));
        container.setOpaque(false);

        // Table Model
        String[] columns = {"Time", "Action", "Status", "Telemetry Details"};
        activityTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        activityTable = new JTable(activityTableModel);
        UIUtils.styleTable(activityTable);
        activityTable.getColumnModel().getColumn(0).setPreferredWidth(90);
        activityTable.getColumnModel().getColumn(1).setPreferredWidth(160);
        activityTable.getColumnModel().getColumn(2).setPreferredWidth(120);
        activityTable.getColumnModel().getColumn(3).setPreferredWidth(450);

        JScrollPane tableScroll = new JScrollPane(activityTable);
        tableScroll.setPreferredSize(new Dimension(800, 240));
        tableScroll.setBorder(new LineBorder(UIUtils.BORDER_COLOR, 1, true));

        // Bottom action bar
        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setOpaque(false);

        JLabel infoLabel = new JLabel("Sanitized telemetry stream • Local in-memory log does not persist to database");
        infoLabel.setFont(UIUtils.FONT_SMALL);
        infoLabel.setForeground(UIUtils.TEXT_MUTED);

        btnClearLog = UIUtils.createSecondaryButton("Clear Activity Log");
        btnClearLog.addActionListener(e -> clearActivityHistory());

        bottomBar.add(infoLabel, BorderLayout.WEST);
        bottomBar.add(btnClearLog, BorderLayout.EAST);

        container.add(tableScroll, BorderLayout.CENTER);
        container.add(bottomBar, BorderLayout.SOUTH);

        return UIUtils.createCard("Recent Networking Activity & Telemetry Log", container);
    }

    private void setupMetricTimer() {
        metricRefreshTimer = new Timer(1000, e -> refreshMetrics());
        metricRefreshTimer.start();
    }

    /**
     * Non-blocking server startup execution via SwingWorker.
     */
    public void executeStartServer() {
        if (isServerRunning()) {
            lastWarningMessage = "TCP Server is already running.";
            UIUtils.showWarning(this, lastWarningMessage, "Server Active");
            return;
        }

        int port;
        try {
            port = Integer.parseInt(txtPort.getText().trim());
            if (port < 0 || port > 65535) {
                throw new NumberFormatException("Port out of range");
            }
        } catch (NumberFormatException nfe) {
            lastErrorMessage = "Please enter a valid port number between 1024 and 65535 (or 0 for dynamic).";
            UIUtils.showError(this, lastErrorMessage, "Invalid Port");
            return;
        }

        setServerState(ServerState.STARTING);
        btnStartServer.setEnabled(false);
        txtPort.setEnabled(false);

        SwingWorker<HireFlowTcpServer, Void> worker = new SwingWorker<>() {
            @Override
            protected HireFlowTcpServer doInBackground() throws Exception {
                if (applicationContext != null) {
                    return applicationContext.startTcpServer(port);
                } else {
                    HireFlowTcpServer standalone = new HireFlowTcpServer(ApplicationContext.createInMemory(), port);
                    standalone.start();
                    return standalone;
                }
            }

            @Override
            protected void done() {
                try {
                    activeServer = get();
                    setServerState(ServerState.RUNNING);
                    btnStopServer.setEnabled(true);
                    lblBoundAddress.setText("127.0.0.1:" + activeServer.getPort());
                    lblBoundAddress.setForeground(UIUtils.SUCCESS);
                    refreshMetrics();
                } catch (Exception ex) {
                    setServerState(ServerState.ERROR);
                    btnStartServer.setEnabled(true);
                    txtPort.setEnabled(true);
                    lblBoundAddress.setText("Offline (Binding failed)");
                    lblBoundAddress.setForeground(UIUtils.DANGER);
                    String sanitized = UIUtils.sanitizeDisplayMessage(ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage());
                    lastErrorMessage = "Failed to start TCP server: " + sanitized;
                    UIUtils.showError(NetworkingPanel.this, lastErrorMessage, "Server Start Error");
                }
            }
        };

        worker.execute();
    }

    /**
     * Non-blocking server shutdown execution via SwingWorker.
     */
    public void executeStopServer() {
        if (!isServerRunning()) {
            return;
        }

        setServerState(ServerState.STOPPING);
        btnStopServer.setEnabled(false);

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                if (applicationContext != null) {
                    applicationContext.stopTcpServer();
                } else if (activeServer != null) {
                    activeServer.stop();
                }
                return null;
            }

            @Override
            protected void done() {
                setServerState(ServerState.STOPPED);
                btnStartServer.setEnabled(true);
                txtPort.setEnabled(true);
                lblBoundAddress.setText("Offline (Not bound)");
                lblBoundAddress.setForeground(UIUtils.TEXT_MUTED);
                refreshMetrics();
            }
        };

        worker.execute();
    }

    /**
     * Synchronizes live metrics on the Event Dispatch Thread (EDT).
     */
    public void refreshMetrics() {
        SwingUtilities.invokeLater(() -> {
            boolean running = isServerRunning();

            if (running && activeServer != null) {
                setServerState(ServerState.RUNNING);
                btnStartServer.setEnabled(false);
                btnStopServer.setEnabled(true);
                txtPort.setEnabled(false);
                lblBoundAddress.setText("127.0.0.1:" + activeServer.getPort());
                lblBoundAddress.setForeground(UIUtils.SUCCESS);

                lblConnectedClientsVal.setText(String.valueOf(activeServer.getActiveClientCount()));
                lblSubscribedClientsVal.setText(String.valueOf(activeServer.getEventBroadcaster().getSubscriberCount()));
                lblEventsDispatchedVal.setText(String.valueOf(activeServer.getTotalEventsDispatched()));
                lblUptimeVal.setText(formatUptime(activeServer.getUptimeSeconds()));

                lblDisconnectMetrics.setText(String.format("Connections: %d | Closed: %d | Rejected: %d",
                        activeServer.getTotalConnections(),
                        activeServer.getTotalDisconnections(),
                        activeServer.getTotalRejectedClients()));

                // Sync table log
                syncActivityLog(activeServer.getRecentActivities());
            } else if (currentState != ServerState.STARTING && currentState != ServerState.STOPPING) {
                setServerState(ServerState.STOPPED);
                btnStartServer.setEnabled(true);
                btnStopServer.setEnabled(false);
                txtPort.setEnabled(true);
                lblBoundAddress.setText("Offline (Not bound)");
                lblBoundAddress.setForeground(UIUtils.TEXT_MUTED);

                lblConnectedClientsVal.setText("0");
                lblSubscribedClientsVal.setText("0");
                lblUptimeVal.setText("00:00:00");
                if (activeServer != null) {
                    lblEventsDispatchedVal.setText(String.valueOf(activeServer.getTotalEventsDispatched()));
                    syncActivityLog(activeServer.getRecentActivities());
                }
            }
        });
    }

    private void syncActivityLog(List<NetworkActivity> activities) {
        if (activities == null) return;
        activityTableModel.setRowCount(0);
        for (NetworkActivity act : activities) {
            activityTableModel.addRow(new Object[]{
                    act.formattedTime(),
                    act.action(),
                    act.status(),
                    act.details()
            });
        }
    }

    private void clearActivityHistory() {
        if (activeServer != null) {
            activeServer.clearActivityHistory();
        }
        activityTableModel.setRowCount(0);
    }

    private void setServerState(ServerState state) {
        this.currentState = state;
        lblStateBadge.setText(state.getLabel());
        lblStateBadge.setForeground(state.getFg());
        lblStateBadge.setBackground(state.getBg());
        lblStateBadge.setBorder(new CompoundBorder(
                new LineBorder(state.getFg(), 1, true),
                new EmptyBorder(3, 8, 3, 8)
        ));
    }

    public boolean isServerRunning() {
        if (applicationContext != null) {
            return applicationContext.isTcpServerRunning();
        }
        return activeServer != null && activeServer.isRunning();
    }

    public ServerState getCurrentState() {
        return currentState;
    }

    public HireFlowTcpServer getActiveServer() {
        if (applicationContext != null && applicationContext.getTcpServer().isPresent()) {
            return applicationContext.getTcpServer().get();
        }
        return activeServer;
    }

    public JTextField getTxtPort() {
        return txtPort;
    }

    public JButton getBtnStartServer() {
        return btnStartServer;
    }

    public JButton getBtnStopServer() {
        return btnStopServer;
    }

    public DefaultTableModel getActivityTableModel() {
        return activityTableModel;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public String getLastWarningMessage() {
        return lastWarningMessage;
    }

    private String formatUptime(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        if (metricRefreshTimer != null && metricRefreshTimer.isRunning()) {
            metricRefreshTimer.stop();
        }
    }
}
