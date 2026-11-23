package ntnu.bidata.iir.controlpanel;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import ntnu.bidata.iir.common.ActuatorType;
import ntnu.bidata.iir.common.Protocol;
import ntnu.bidata.iir.common.Role;
import ntnu.bidata.iir.common.SensorType;



final class ControlPanelGui extends JFrame implements PanelClient.Listener {
    private static final Color ALERT = new Color(0xD64545);
    private static final Color ON_COLOR = new Color(0x2E9E5B);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final String SCOPE_NODE = "This node";
    private static final String SCOPE_ALL = "All nodes";
    private static final String ALL_DEVICES = "all devices";

    private final Role role;
    private final Map<String, NodeState> nodes = new LinkedHashMap<>();
    private final DefaultListModel<String> nodeListModel = new DefaultListModel<>();
    private final JList<String> nodeList = new JList<>(nodeListModel);
    private final SensorTableModel tableModel = new SensorTableModel();
    private final JTable sensorTable = new JTable(tableModel);
    private final LineChartPanel chart = new LineChartPanel();
    private final JLabel nodeTitle = new JLabel("Select a node on the left");
    private final JPanel actuatorPanel = new JPanel();
    private final Map<String, JLabel> actuatorStateLabels = new HashMap<>();
    private final JTextArea log = new JTextArea(7, 40);
    private final JLabel statusLabel = new JLabel("  Connecting...");
    private final JComboBox<String> scopeBox = new JComboBox<>(new String[] {SCOPE_NODE, SCOPE_ALL});
    private final JComboBox<String> deviceBox = new JComboBox<>();
    private final JComboBox<String> actionBox = new JComboBox<>();

    private final SecurityBar securityBar = new SecurityBar(code -> sendSecurity("arm", code),
            code -> sendSecurity("disarm", code));
    private DiagnosticsWindow diagnostics;

    private PanelClient client;
    private NodeState selected;
    private boolean connectedBefore;
    private boolean leaving;

    ControlPanelGui(String panelId, Role role) {
        super("SHA Control Panel - " + panelId + " (" + role + ")");
        this.role = role;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE); // the window's X button works like Quit
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                quit();
            }
        });
        buildLayout();
        setSize(1200, 700);
        setLocationRelativeTo(null);
    }

    /** Called once the login succeeded; enables sending commands. */
    void attach(PanelClient client) {
        this.client = client;
        log("Logged in as " + role);
        setStatus(true, "Connected as " + role);
        if (diagnostics != null) {
            client.requestLog();
        }
    }

    /** Sends ARM or DISARM and reports a missing connection in the event log. */
    private boolean sendSecurity(String what, String code) {
        boolean sent = what.equals("arm") ? client.arm(code) : client.disarm(code);
        if (!sent) {
            log("NOT SENT (no connection to the broker): " + what);
        }
        return sent;
    }

    // ------------------------------------------------------------------ layout

    private void buildLayout() {
        nodeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        nodeList.setCellRenderer(new NodeCellRenderer());
        nodeList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showNode(nodes.get(nodeList.getSelectedValue()));
            }
        });
        JScrollPane nodeScroll = new JScrollPane(nodeList);
        nodeScroll.setBorder(BorderFactory.createTitledBorder("Sensor nodes"));
        nodeScroll.setPreferredSize(new Dimension(190, 100));

        nodeTitle.setFont(nodeTitle.getFont().deriveFont(Font.BOLD, 16f));
        nodeTitle.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        sensorTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sensorTable.setDefaultRenderer(Object.class, new SensorRowRenderer());
        sensorTable.setRowHeight(24);
        sensorTable.getColumnModel().getColumn(0).setPreferredWidth(80);
        sensorTable.getColumnModel().getColumn(1).setPreferredWidth(60);
        sensorTable.getColumnModel().getColumn(2).setPreferredWidth(280);
        sensorTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                chart.setSeries(tableModel.seriesAt(sensorTable.getSelectedRow()));
            }
        });
        JScrollPane tableScroll = new JScrollPane(sensorTable);
        tableScroll.setBorder(BorderFactory.createTitledBorder("Sensors"));
        tableScroll.setPreferredSize(new Dimension(400, 130));

        JPanel chartBox = new JPanel(new BorderLayout());
        chartBox.setBorder(BorderFactory.createTitledBorder("History (last 60 readings)"));
        chartBox.add(chart, BorderLayout.CENTER);

        JSplitPane sensorsAndChart = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, chartBox);
        sensorsAndChart.setResizeWeight(0.3);

        actuatorPanel.setLayout(new javax.swing.BoxLayout(actuatorPanel, javax.swing.BoxLayout.Y_AXIS));
        JScrollPane actuatorScroll = new JScrollPane(actuatorPanel);
        actuatorScroll.setBorder(BorderFactory.createTitledBorder("Actuators"));
        actuatorScroll.setPreferredSize(new Dimension(480, 100));

        JPanel details = new JPanel(new BorderLayout());
        details.add(nodeTitle, BorderLayout.NORTH);
        details.add(sensorsAndChart, BorderLayout.CENTER);
        JPanel east = new JPanel(new BorderLayout());
        east.add(actuatorScroll, BorderLayout.CENTER);
        east.add(buildGroupPanel(), BorderLayout.SOUTH);
        details.add(east, BorderLayout.EAST);

        log.setEditable(false);
        log.setLineWrap(true);
        JScrollPane logScroll = new JScrollPane(log);
        logScroll.setBorder(BorderFactory.createTitledBorder("Event log"));

        JSplitPane top = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, nodeScroll, details);
        top.setResizeWeight(0.0);
        top.setDividerLocation(190);
        JSplitPane all = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, logScroll);
        all.setResizeWeight(0.8);
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        JButton logoutButton = new JButton("Log out");
        logoutButton.setToolTipText("Disconnect and return to the login window");
        logoutButton.addActionListener(e -> logout());
        JButton quitButton = new JButton("Quit");
        quitButton.setToolTipText("Disconnect and close the program");
        quitButton.addActionListener(e -> quit());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(logoutButton);
        buttons.add(quitButton);
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.add(statusLabel, BorderLayout.CENTER);
        topBar.add(buttons, BorderLayout.EAST);
        if (role.mayViewLog()) {
            diagnostics = new DiagnosticsWindow(getTitle(), () -> client.requestDiagnostics());
            JButton diagButton = new JButton("Diagnostics");
            diagButton.setToolTipText("Connected clients and the broker's event log");
            diagButton.addActionListener(e -> diagnostics.open());
            buttons.add(diagButton, 0);
        }
        JPanel north = new JPanel(new BorderLayout());
        north.add(topBar, BorderLayout.NORTH);
        north.add(securityBar, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);
        add(all, BorderLayout.CENTER);
    }

    /** Lets the user send one command to many actuators: all of a kind, on this node or on every node. */
    private JPanel buildGroupPanel() {
        deviceBox.addItem(ALL_DEVICES);
        Set<String> actions = new LinkedHashSet<>();
        for (ActuatorType type : ActuatorType.values()) {
            if (role.maySeeActuator(type.id())) {
                deviceBox.addItem(type.id());
                actions.addAll(type.states());
            }
        }
        actions.forEach(actionBox::addItem);
        JButton send = new JButton("Send");
        send.addActionListener(e -> sendGroupCommand());

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        panel.setBorder(BorderFactory.createTitledBorder("Group command (many actuators at once)"));
        panel.add(scopeBox);
        panel.add(deviceBox);
        panel.add(actionBox);
        panel.add(send);
        return panel;
    }

    private void sendGroupCommand() {
        String nodeSelector = Protocol.WILDCARD;
        if (SCOPE_NODE.equals(scopeBox.getSelectedItem())) {
            if (selected == null) {
                log("Select a node first, or choose \"" + SCOPE_ALL + "\"");
                return;
            }
            nodeSelector = selected.id();
        }
        String device = (String) deviceBox.getSelectedItem();
        String actuatorSelector = ALL_DEVICES.equals(device) ? Protocol.WILDCARD : device + ":" + Protocol.WILDCARD;
        String action = (String) actionBox.getSelectedItem();
        if (client.sendCommand(nodeSelector, actuatorSelector, action)) {
            log("Sent " + action + " to " + nodeSelector + " / " + actuatorSelector);
        } else {
            log("NOT SENT (no connection to the broker): " + action + " to " + nodeSelector + " / " + actuatorSelector);
        }
    }

    /** Disconnects, closes this window and shows the login window again, so another user can log in. */
    private void logout() {
        leaving = true;
        closeDiagnostics();
        if (client != null) {
            client.close();
        }
        dispose();
        ControlPanelGuiMain.showLogin();
    }

    /** Disconnects and ends the program. */
    private void quit() {
        leaving = true;
        closeDiagnostics();
        if (client != null) {
            client.close();
        }
        dispose();
        System.exit(0);
    }

    private void closeDiagnostics() {
        if (diagnostics != null) {
            diagnostics.dispose();
        }
    }

    private void setStatus(boolean connected, String text) {
        statusLabel.setText("  " + text);
        statusLabel.setForeground(connected ? ON_COLOR : ALERT);
    }

    // ------------------------------------------------------------------ showing a node

    private void showNode(NodeState node) {
        selected = node;
        actuatorPanel.removeAll();
        actuatorStateLabels.clear();
        if (node == null) {
            nodeTitle.setText("Select a node on the left");
            tableModel.setNode(null);
            chart.setSeries(null);
        } else {
            nodeTitle.setText("Node " + node.id());
            tableModel.setNode(node);
            if (tableModel.getRowCount() > 0) {
                sensorTable.setRowSelectionInterval(0, 0);
            } else {
                chart.setSeries(null);
            }
            node.actuatorStates().forEach((actuatorId, state) -> actuatorPanel.add(actuatorRow(node, actuatorId, state)));
        }
        actuatorPanel.revalidate();
        actuatorPanel.repaint();
    }

    private JPanel actuatorRow(NodeState node, String actuatorId, String state) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        JLabel name = new JLabel(actuatorId);
        name.setPreferredSize(new Dimension(90, 20));
        JLabel stateLabel = new JLabel();
        stateLabel.setPreferredSize(new Dimension(80, 20));
        styleState(stateLabel, actuatorId, state);
        actuatorStateLabels.put(actuatorId, stateLabel);
        row.add(name);
        row.add(stateLabel);

        // one button per state
        for (String action : ActuatorType.statesOf(actuatorId)) {
            JButton button = new JButton(action);
            button.addActionListener(e -> send(node, actuatorId, action));
            restrictToRole(button, actuatorId, action);
            row.add(button);
        }

        // a device with a target value gets a number field and a SET button
        ActuatorType type = ActuatorType.find(actuatorId).orElse(null);
        if (type != null && type.range() != null) {
            ActuatorType.Range range = type.range();
            JSpinner target = new JSpinner(new SpinnerNumberModel(range.initial(), range.min(), range.max(), 1.0));
            target.setPreferredSize(new Dimension(64, 26));
            target.setToolTipText("Target " + range.unit() + " (" + range.min() + " to " + range.max() + ")");
            JButton set = new JButton("SET");
            set.addActionListener(e -> send(node, actuatorId,
                    "SET:" + String.format(Locale.ROOT, "%.1f", ((Number) target.getValue()).doubleValue())));
            restrictToRole(set, actuatorId, "SET:" + (long) range.initial());
            row.add(target);
            row.add(set);
        }
        return row;
    }

    /** Greys out a button when this panel's role may not send that action. */
    private void restrictToRole(JButton button, String actuatorId, String action) {
        if (!role.mayControl(actuatorId, action)) {
            button.setEnabled(false);
            button.setToolTipText("Role " + role + " may not do this");
        }
    }

    private static void styleState(JLabel label, String actuatorId, String state) {
        label.setText(ActuatorType.describe(actuatorId, state));
        label.setForeground(ActuatorType.isActiveState(actuatorId, state) ? ON_COLOR : Color.GRAY);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
    }

    private void send(NodeState node, String actuatorId, String action) {
        if (client.sendCommand(node.id(), actuatorId, action)) {
            log("Sent " + action + " to " + node.id() + "/" + actuatorId);
        } else {
            log("NOT SENT (no connection to the broker): " + action + " to " + node.id() + "/" + actuatorId);
        }
    }

    private void log(String text) {
        log.append("[" + LocalTime.now().format(TIME) + "] " + text + "\n");
        log.setCaretPosition(log.getDocument().getLength());
    }


    @Override
    public void onConnected() {
        SwingUtilities.invokeLater(() -> {
            if (connectedBefore) {
                clearNodes();
                log("Reconnected to the broker");
                if (diagnostics != null) {
                    diagnostics.clearEvents();
                    client.requestLog();
                }
            }
            connectedBefore = true;
            setStatus(true, "Connected as " + role);
        });
    }

    private void clearNodes() {
        nodes.clear();
        nodeListModel.clear();
        showNode(null);
    }

    @Override
    public void onNodeInfo(String nodeId, String[] sensors, String[] actuators) {
        SwingUtilities.invokeLater(() -> {
            if (nodes.containsKey(nodeId)) {
                return;
            }
            nodes.put(nodeId, new NodeState(nodeId, sensors, actuators));
            int position = 0;
            while (position < nodeListModel.size() && nodeListModel.get(position).compareTo(nodeId) < 0) {
                position++;
            }
            nodeListModel.add(position, nodeId);
            log("Node " + nodeId + " connected (" + sensors.length + " sensors, " + actuators.length + " actuators)");
            if (nodeList.getSelectedIndex() < 0) {
                nodeList.setSelectedIndex(0);
            }
        });
    }

    @Override
    public void onNodeGone(String nodeId) {
        SwingUtilities.invokeLater(() -> {
            NodeState removed = nodes.remove(nodeId);
            if (removed == null) {
                return;
            }
            nodeListModel.removeElement(nodeId);
            log("Node " + nodeId + " disconnected");
            if (removed == selected) {
                showNode(null);
            }
        });
    }

    @Override
    public void onReading(String nodeId, String sensorId, String value, long ageMs) {
        SwingUtilities.invokeLater(() -> {
            NodeState node = nodes.get(nodeId);
            if (node == null) {
                return;
            }
            double parsed;
            try {
                parsed = Double.parseDouble(value);
            } catch (NumberFormatException e) {
                log("Ignoring non-numeric reading from " + nodeId + "/" + sensorId + ": " + value);
                return;
            }
            boolean wasAnomaly = node.sensors().stream()
                    .anyMatch(s -> s.id().equals(sensorId) && s.anomaly() != null);
            String anomaly = node.applyReading(sensorId, parsed);
            if (anomaly != null && !wasAnomaly) {
                log("ANOMALY " + nodeId + "/" + sensorId + ": " + anomaly);
            } else if (anomaly == null && wasAnomaly) {
                log("OK again: " + nodeId + "/" + sensorId);
            }
            nodeList.repaint();
            if (node == selected) {
                if (tableModel.getRowCount() > 0) {
                    tableModel.fireTableRowsUpdated(0, tableModel.getRowCount() - 1);
                }
                chart.repaint();
            }
        });
    }

    @Override
    public void onStatus(String nodeId, String actuatorId, String state) {
        SwingUtilities.invokeLater(() -> {
            NodeState node = nodes.get(nodeId);
            if (node == null) {
                return;
            }
            node.applyStatus(actuatorId, state);
            nodeList.repaint();
            log(nodeId + "/" + actuatorId + " is now " + state);
            if (node == selected && actuatorStateLabels.containsKey(actuatorId)) {
                styleState(actuatorStateLabels.get(actuatorId), actuatorId, state);
            }
        });
    }

    @Override
    public void onError(String code, String text) {
        SwingUtilities.invokeLater(() -> log("Broker error " + code + ": " + text));
    }

    @Override
    public void onSecurity(String state, int secondsLeft) {
        SwingUtilities.invokeLater(() -> securityBar.update(state, secondsLeft));
    }

    @Override
    public void onLog(long timeMillis, String category, String text) {
        SwingUtilities.invokeLater(() -> {
            if (diagnostics != null) {
                diagnostics.addEvent(timeMillis, category, text);
            }
        });
    }

    @Override
    public void onDiagnostic(String kind, String id, String clientRole, long connectedSeconds, long idleSeconds,
                             String detail) {
        SwingUtilities.invokeLater(() -> {
            if (diagnostics != null) {
                diagnostics.addClient(kind, id, clientRole, connectedSeconds, idleSeconds, detail);
            }
        });
    }

    @Override
    public void onGroupResult(int delivered, int skipped) {
        SwingUtilities.invokeLater(() -> log("Group command reached " + delivered + " actuators (" + skipped + " skipped)"));
    }

    @Override
    public void onDisconnected(String reason, boolean willRetry) {
        SwingUtilities.invokeLater(() -> {
            if (leaving) {
                return;
            }
            log(reason + (willRetry ? " - reconnecting..." : ""));
            setStatus(false, willRetry ? "DISCONNECTED - trying to reconnect..." : "DISCONNECTED");
            if (!willRetry) {
                JOptionPane.showMessageDialog(this, reason, "Disconnected", JOptionPane.WARNING_MESSAGE);
            }
        });
    }

    // ------------------------------------------------------------------ table and list helpers

    /** Table of the selected node's sensors: id, latest value and status. */
    private static final class SensorTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Sensor", "Value", "Status"};
        private List<SensorSeries> rows = List.of();

        void setNode(NodeState node) {
            rows = node == null ? List.of() : node.sensors();
            fireTableDataChanged();
        }

        SensorSeries seriesAt(int row) {
            return row >= 0 && row < rows.size() ? rows.get(row) : null;
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int row, int column) {
            SensorSeries series = rows.get(row);
            return switch (column) {
                case 0 -> series.id();
                case 1 -> series.latest() == null ? "-" : SensorType.describe(series.id(), series.latest());
                default -> series.anomaly() == null ? "OK" : series.anomaly();
            };
        }
    }

    /** Colours a table row red while its sensor is in anomaly. */
    private final class SensorRowRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            SensorSeries series = tableModel.seriesAt(row);
            if (!isSelected) {
                c.setForeground(series != null && series.anomaly() != null ? ALERT : table.getForeground());
            }
            return c;
        }
    }

    /** Shows nodes with an active anomaly in red. */
    private final class NodeCellRenderer extends javax.swing.DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            NodeState node = nodes.get(String.valueOf(value));
            String suffix = node == null ? "" : node.alarmActive() ? "  (ALARM)" : node.hasAnomaly() ? "  (anomaly)" : "";
            boolean alert = !suffix.isEmpty();
            Component c = super.getListCellRendererComponent(list, value + suffix,
                    index, isSelected, cellHasFocus);
            if (alert && !isSelected) {
                c.setForeground(ALERT);
            }
            return c;
        }
    }
}
