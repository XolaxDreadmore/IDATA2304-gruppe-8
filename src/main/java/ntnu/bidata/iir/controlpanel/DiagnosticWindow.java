package ntnu.bidata.iir.controlpanel;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;


final class DiagnosticWindow extends JFrame {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int MAX_LOG_ROWS = 1000;
    private static final int REFRESH_MS = 5000;

    private final DefaultTableModel clients = new DefaultTableModel(new Object[] {"kind", "Id", "Role", "Connected (s)",
            "Idle (s)", "Detail"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {return false;}
    };
    private final DefaultTableModel events = new DefaultTableModel(new Object[]{"Time", "Category", "Event"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {return false;}
    };
    private final JTable eventTable = new JTable(events);
    private final Timer refreshTimer;

    /** @param requestClients ask for the list of clients and the clients recieves through "addClient" */
    DiagnosticWindow(String title, Runnable requestClients) {
        super("Diagnostics - " + title);
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        setSize(900, 620);
        setLocationByPlatform(true);

        JTable clientTable = new JTable(clients);
        clientTable.getColumnModel().getColumn(5).setPreferredWidth(300);
        JScrollPane clientScroll = new JScrollPane(clientTable);
        JButton refresh = new JButton("Refresh now");
        refresh.addActionListener(e -> refreshClients(requestClients));
        JPanel clientHeader = new JPanel(new FlowLayout(FlowLayout.LEFT));
        clientHeader.add(refresh);
        JPanel clientBox = new JPanel(new BorderLayout());
        clientBox.setBorder(BorderFactory.createTitledBorder("Connected clients (refreshed every 5 s)"));
        clientBox.add(clientHeader, BorderLayout.NORTH);
        clientBox.add(clientScroll, BorderLayout.CENTER);

        eventTable.getColumnModel().getColumn(0).setPreferredWidth(70);
        eventTable.getColumnModel().getColumn(1).setPreferredWidth(90);
        eventTable.getColumnModel().getColumn(2).setPreferredWidth(700);
        JScrollPane eventScroll = new JScrollPane(eventTable);
        eventScroll.setBorder(BorderFactory.createTitledBorder("Event log"));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, clientBox, eventScroll);
        split.setDividerLocation(230);
        split.setResizeWeight(0.35);
        add(split,BorderLayout.CENTER);

        refreshTimer = new Timer(REFRESH_MS, e -> refreshClients(requestClients));
        refreshTimer.setInitialDelay(0);
    }

    /** shows the window and starts refreshing the client list */
    void open() {
    setVisible(true);
    toFront();
    refreshTimer.start();
    }

    @Override
    public void dispose() {
        refreshTimer.stop();
        super.dispose();
    }

    @Override
    public void setVisible(boolean visible) {
        if (!visible) {
            refreshTimer.stop();
        }
        super.setVisible(visible);
    }

    private void refreshClients(Runnable requestClients) {
        clients.setRowCount(0);
        requestClients.run();
    }

    void addClient(String kind, String id, String role, long connectedSeconds, long idlesSeconds, String detail) {
        clients.addRow(new Object[]{kind, id, role, connectedSeconds, idlesSeconds, detail});
    }

    void addEvent(long timeMillis, String category, String text) {
        String time = LocalTime.ofInstant(Instant.ofEpochMilli(timeMillis), ZoneId.systemDefault()).format(TIME);
        events.addRow(new Object[]{time, category, text});
        if (events.getRowCount() > MAX_LOG_ROWS) {
            events.removeRow(0);
        }
        eventTable.scrollRectToVisible(eventTable.getCellRect(events.getRowCount() - 1, 0, true));
    }

    /** Forgets the old log, before a reconnect */
    void clearEvents() {events.setRowCount(0);}
}