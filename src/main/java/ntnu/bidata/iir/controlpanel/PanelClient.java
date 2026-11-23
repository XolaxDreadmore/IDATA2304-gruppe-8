package ntnu.bidata.iir.controlpanel;

import java.io.Closeable;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import ntnu.bidata.iir.common.Connection;
import ntnu.bidata.iir.common.Message;
import ntnu.bidata.iir.common.MessageType;
import ntnu.bidata.iir.common.Protocol;
import ntnu.bidata.iir.common.ProtocolException;
import ntnu.bidata.iir.common.Role;

final class PanelClient implements Closeable {


    interface Listener {
        /** After every successful login. */
        void onConnected();

        void onNodeInfo(String nodeId, String[] sensors, String[] actuators);

        void onNodeGone(String nodeId);

        void onReading(String nodeId, String sensorId, String value, long ageMs);

        void onStatus(String nodeId, String actuatorId, String state);

        /** group command: how many actuators got the message and how many were skipped. */
        void onGroupResult(int delivered, int skipped);

        void onError(String code, String text);

        /** The connection was lost attempts to reconnect. */
        void onDisconnected(String reason, boolean willRetry);

        /** The house security state: DISARMED, ARMED, ENTRY (secondsLeft counts down) or ALARM. */
        default void onSecurity(String state, int secondsLeft) { }

        default void onLog(long timeMillis, String category, String text) { }


        default void onDiagnostic(String kind, String id, String role, long connectedSeconds, long idleSeconds,
                                  String detail) { }
    }

    /** The broker answered LOGIN with an ERROR. */
    private static final class LoginRejectedException extends IOException {
        private final String code;
        private final String text;

        LoginRejectedException(String code, String text) {
            super("Login failed: " + text);
            this.code = code;
            this.text = text;
        }
    }

    private final String host;
    private final String panelId;
    private final Role role;
    private final String password;
    private final Listener listener;
    private final ScheduledExecutorService pinger = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "panel-ping");
        thread.setDaemon(true);
        return thread;
    });
    private volatile Connection connection; // null while disconnected
    private volatile boolean closed;

    private PanelClient(String host, String panelId, Role role, String password, Listener listener) {
        this.host = host;
        this.panelId = panelId;
        this.role = role;
        this.password = password;
        this.listener = listener;
    }

    /**
     * Connects and logs in. Blocks until the broker accepts or rejects the login.
     *
     * @throws IOException if the broker is unreachable or rejects the login
     */
    static PanelClient connect(String host, String panelId, Role role, String password, Listener listener)
            throws IOException {
        PanelClient client = new PanelClient(host, panelId, role, password, listener);
        List<Message> early = new ArrayList<>();
        Connection first = client.openAndLogin(early);
        client.start(first, early);
        return client;
    }

    private void start(Connection first, List<Message> early) {
        connection = first;
        listener.onConnected();
        early.forEach(this::dispatch);
        pinger.scheduleAtFixedRate(this::ping, Protocol.HEARTBEAT_INTERVAL_MS, Protocol.HEARTBEAT_INTERVAL_MS,
                TimeUnit.MILLISECONDS);
        Thread reader = new Thread(() -> ioLoop(first), "panel-io");
        reader.setDaemon(true);
        reader.start();
    }

    /**
     * Opens a connection and logs in. Messages that arrive before the answer  are collected in early
     * so the caller can hand them on after onConnected().
     */
    private Connection openAndLogin(List<Message> early) throws IOException {
        Connection c = Connection.connect(host, Protocol.PORT);
        try {
            c.setReadTimeout(Protocol.READ_TIMEOUT_MS);
            c.send(Message.of(MessageType.LOGIN, panelId, role.name(), password));
            while (true) {
                Message m;
                try {
                    m = c.receive();
                } catch (ProtocolException e) {
                    continue;
                }
                if (m == null) {
                    throw new IOException("The broker closed the connection");
                }
                if (m.type() == MessageType.OK) {
                    return c;
                }
                if (m.type() == MessageType.ERROR) {
                    throw new LoginRejectedException(m.arg(0), m.arg(1));
                }
                early.add(m);
            }
        } catch (IOException | RuntimeException e) {
            c.close();
            throw e;
        }
    }

    /** Reads until the connection is lost, then reconnects, again and again until closed. */
    private void ioLoop(Connection first) {
        Connection current = first;
        long delayMs = 1000;
        while (!closed) {
            if (current != null) {
                String reason = readUntilLost(current);
                connection = null;
                current.close();
                if (closed) {
                    return;
                }
                listener.onDisconnected(reason, true);
                current = null;
            }
            try {
                Thread.sleep(delayMs);
                List<Message> early = new ArrayList<>();
                Connection next = openAndLogin(early);
                connection = next;
                listener.onConnected();
                early.forEach(this::dispatch);
                current = next;
                delayMs = 1000;
                continue;
            } catch (InterruptedException e) {
                return;
            } catch (LoginRejectedException e) {
                if (!e.code.equals(Protocol.ERR_CONFLICT)) { // 409: the broker has not yet dropped our old connection
                    listener.onDisconnected("Login rejected: " + e.text, false);
                    return;
                }
            } catch (IOException e) {

            }
            delayMs = Math.min(delayMs * 2, Protocol.MAX_RECONNECT_DELAY_MS);
        }
    }

    /** @return why the connection ended */
    private String readUntilLost(Connection c) {
        try {
            while (true) {
                Message m;
                try {
                    m = c.receive();
                } catch (ProtocolException e) {
                    listener.onError(Protocol.ERR_BAD_REQUEST, "Malformed message from broker: " + e.getMessage());
                    continue;
                }
                if (m == null) {
                    return "The broker closed the connection";
                }
                dispatch(m);
            }
        } catch (SocketTimeoutException e) {
            return "No answer from the broker for " + Protocol.READ_TIMEOUT_MS / 1000 + " s";
        } catch (IOException e) {
            return "Connection lost: " + e.getMessage();
        }
    }

    private void dispatch(Message m) {
        try {
            switch (m.type()) {
                case NODE_INFO -> {
                    listener.onNodeInfo(m.arg(0), splitList(m.arg(1)), splitList(m.arg(2)));
                    sendQuietly(Message.of(MessageType.SUBSCRIBE, m.arg(0)));
                }
                case NODE_GONE -> listener.onNodeGone(m.arg(0));
                case READING -> listener.onReading(m.arg(0), m.arg(1), m.arg(2),
                        m.args().size() > 3 ? toLong(m.arg(3)) : 0);
                case STATUS -> listener.onStatus(m.arg(0), m.arg(1), m.arg(2));
                case OK -> {
                    if (m.args().size() >= 2) { // OK|delivered|skipped answers a group command
                        listener.onGroupResult((int) toLong(m.arg(0)), (int) toLong(m.arg(1)));
                    }
                }
                case SECURITY -> listener.onSecurity(m.arg(0), (int) toLong(m.arg(1)));
                case LOG -> listener.onLog(toLong(m.arg(0)), m.arg(1), m.arg(2));
                case DIAG -> listener.onDiagnostic(m.arg(0), m.arg(1), m.arg(2), toLong(m.arg(3)), toLong(m.arg(4)),
                        m.arg(5));
                case ERROR -> listener.onError(m.arg(0), m.arg(1));
                default -> { } // PONG and NODE_LIST need no action
            }
        } catch (ProtocolException e) {
            listener.onError(Protocol.ERR_BAD_REQUEST, "Malformed message from broker: " + e.getMessage());
        }
    }

    private static String[] splitList(String text) {
        return text.isBlank() ? new String[0] : text.split(Protocol.LIST_SEPARATOR);
    }

    private static long toLong(String text) {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void ping() {
        Connection c = connection;
        if (c != null && !c.send(Message.of(MessageType.PING))) {
            c.close();
        }
    }

    private void sendQuietly(Message message) {
        Connection c = connection;
        if (c != null) {
            c.send(message);
        }
    }

    /** Arms the house with the house code. */
    boolean arm(String code) {
        return sendRequest(Message.of(MessageType.ARM, code));
    }

    /** Cancels the entry countdown or silences an intruder alarm. */
    boolean disarm(String code) {
        return sendRequest(Message.of(MessageType.DISARM, code));
    }

    /** Asks for the event log. */
    boolean requestLog() {
        return sendRequest(Message.of(MessageType.GET_LOG));
    }

    /** Asks for the list of connected clients.*/
    boolean requestDiagnostics() {
        return sendRequest(Message.of(MessageType.GET_DIAG));
    }

    private boolean sendRequest(Message message) {
        Connection c = connection;
        if (c == null) {
            return false;
        }
        boolean sent = c.send(message);
        if (!sent) {
            c.close();
        }
        return sent;
    }

    /**
     * Sends a command.
     *
     * @return false if there is no working connection, so the command was not sent
     */
    boolean sendCommand(String nodeSelector, String actuatorSelector, String action) {
        Connection c = connection;
        if (c == null) {
            return false;
        }
        boolean sent = c.send(Message.of(MessageType.COMMAND, nodeSelector, actuatorSelector, action));
        if (!sent) {
            c.close();
        }
        return sent;
    }

    @Override
    public void close() {
        closed = true;
        pinger.shutdownNow();
        Connection c = connection;
        if (c != null) {
            c.close();
        }
    }
}