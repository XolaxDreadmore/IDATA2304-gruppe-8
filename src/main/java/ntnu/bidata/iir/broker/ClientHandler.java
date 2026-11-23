package ntnu.bidata.iir.broker;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import ntnu.bidata.iir.common.Connection;
import ntnu.bidata.iir.common.Message;
import ntnu.bidata.iir.common.MessageType;
import ntnu.bidata.iir.common.Protocol;
import ntnu.bidata.iir.common.ProtocolException;
import ntnu.bidata.iir.common.Role;

/**
 * Handles one TCP client (sensor node or control panel) for its whole lifetime.
 */
final class ClientHandler implements Runnable {
    private enum Kind {
        UNKNOWN, SENSOR_NODE, PANEL
    }

    private final Connection connection;
    private final Registry registry;
    private final FireSafety fireSafety;
    private final EntryGuard entryGuard;
    private final AlarmControl alarms;
    private final CommandRouter router;
    private final EventLog log;
    private final long connectedAt = System.currentTimeMillis();
    private volatile long lastSeen = connectedAt; // time of the last message received, for diagnostics
    private volatile Kind kind = Kind.UNKNOWN;
    private volatile String id;
    private volatile Role role; // panels only
    private volatile String sensors = ""; // nodes only, e.g. "temp:1,hum:1"
    private volatile String actuators = ""; // nodes only, e.g. "fan:1,heater:1"
    /**
     * Last reported state of every actuator of this node, so a panel that
     * subscribes later is brought up to date.
     */
    private final Map<String, String> actuatorStates = new ConcurrentHashMap<>();

    ClientHandler(Connection connection, BrokerContext context) {
        this.connection = connection;
        this.registry = context.registry();
        this.fireSafety = context.fireSafety();
        this.entryGuard = context.entryGuard();
        this.alarms = context.alarms();
        this.router = context.router();
        this.log = context.log();
    }

    /**
     * Sends a message; if the connection turned out to be broken it is closed so
     * the handler cleans up.
     */
    void send(Message message) {
        if (!connection.send(message)) {
            connection.close();
        }
    }

    String id() {
        return id;
    }

    /** One DIAG line describing this client, for the diagnostics view. */
    Message diagMessage() {
        long now = System.currentTimeMillis();
        boolean isNode = kind == Kind.SENSOR_NODE;
        String detail = isNode ? countOf(sensors) + " sensors, " + countOf(actuators) + " actuators"
                : connection.remoteAddress();
        return Message.of(MessageType.DIAG, isNode ? "NODE" : "PANEL", String.valueOf(id),
                isNode || role == null ? "" : role.name(),
                String.valueOf((now - connectedAt) / 1000), String.valueOf((now - lastSeen) / 1000),
                detail.replace(Protocol.SEPARATOR, "/"));
    }

    private static int countOf(String list) {
        return list.isBlank() ? 0 : list.split(Protocol.LIST_SEPARATOR).length;
    }

    /** The actuator ids this node announced at registration (sensor nodes only). */
    List<String> actuatorIds() {
        return actuators.isBlank() ? List.of() : List.of(actuators.split(Protocol.LIST_SEPARATOR));
    }

    /**
     * The role used to decide what this panel may see; a panel that is not fully
     * logged in sees the least.
     */
    Role viewerRole() {
        Role current = role;
        return current == null ? Role.GUEST : current;
    }

    /**
     * NODE_INFO for this node as seen by a viewer with the given role: only the
     * sensors and actuators the role may see.
     */
    Message nodeInfoFor(Role viewer) {
        String visibleSensors = Arrays.stream(sensors.split(Protocol.LIST_SEPARATOR))
                .filter(sensor -> !sensor.isBlank() && viewer.maySee(sensor))
                .collect(Collectors.joining(Protocol.LIST_SEPARATOR));
        String visibleActuators = Arrays.stream(actuators.split(Protocol.LIST_SEPARATOR))
                .filter(actuator -> !actuator.isBlank() && viewer.maySeeActuator(actuator))
                .collect(Collectors.joining(Protocol.LIST_SEPARATOR));
        return Message.of(MessageType.NODE_INFO, id, visibleSensors, visibleActuators);
    }

    /**
     * STATUS messages describing the current state of every actuator of this node.
     */
    List<Message> statusMessages() {
        return actuatorStates.entrySet().stream()
                .map(entry -> Message.of(MessageType.STATUS, id, entry.getKey(), entry.getValue()))
                .toList();
    }

    /**
     * Forwards a READING or STATUS to this panel, unless the panel's role may not
     * see that sensor or actuator.
     */
    void deliverToViewer(Message message) {
        if (message.type() == MessageType.READING && !viewerRole().maySee(message.arg(1))) {
            return;
        }
        if (message.type() == MessageType.STATUS && !viewerRole().maySeeActuator(message.arg(1))) {
            return;
        }
        send(message);
    }

    @Override
    public void run() {
        System.out.println("Client connected: " + connection.remoteAddress());
        try {
            connection.setReadTimeout(Protocol.READ_TIMEOUT_MS);
            Message message;
            while ((message = nextMessage()) != null) {
                handle(message);
            }
        } catch (SocketTimeoutException e) {
            System.out.println("Dropping " + describe() + ": nothing received for "
                    + Protocol.READ_TIMEOUT_MS / 1000 + " s");
            log.add("NETWORK",
                    "Dropped " + describe() + ": nothing received for " + Protocol.READ_TIMEOUT_MS / 1000 + " s");
        } catch (IOException e) {
            System.out.println("Connection error (" + describe() + "): " + e.getMessage());
            log.add("NETWORK", "Connection error (" + describe() + "): " + e.getMessage());
        } finally {
            registry.remove(this);
            log.unwatch(this);
            if (kind == Kind.SENSOR_NODE) {
                fireSafety.forget(id);
                entryGuard.forget(id);
                registry.broadcastToPanels(Message.of(MessageType.NODE_GONE, id));
                log.add("CONNECT", "Node " + id + " disconnected");
            } else if (kind == Kind.PANEL) {
                log.add("CONNECT", "Panel " + id + " (" + role + ") disconnected");
            }
            connection.close();
            System.out.println("Client disconnected: " + describe());
        }
    }

    private String describe() {
        return id != null ? id : connection.remoteAddress();
    }

    /**
     * Reads the next valid message. Malformed lines get an ERROR reply and are
     * skipped.
     */
    private Message nextMessage() throws IOException {
        while (true) {
            try {
                Message received = connection.receive();
                lastSeen = System.currentTimeMillis();
                return received;
            } catch (ProtocolException e) {
                error(Protocol.ERR_BAD_REQUEST, e.getMessage());
            }
        }
    }

    private void handle(Message m) {
        try {
            switch (m.type()) {
                case PING -> send(Message.of(MessageType.PONG));
                case REGISTER -> onRegister(m);
                case LOGIN -> onLogin(m);
                case READING -> onReading(m);
                case STATUS -> onStatus(m);
                case SUBSCRIBE -> onSubscribe(m);
                case LIST_NODES -> onListNodes();
                case COMMAND -> onCommand(m);
                case ARM -> onSecurityCommand(m, true);
                case DISARM -> onSecurityCommand(m, false);
                case GET_LOG -> onGetLog();
                case GET_DIAG -> onGetDiag();
                default -> error(Protocol.ERR_BAD_REQUEST, "Clients may not send " + m.type());
            }
        } catch (ProtocolException e) {
            error(Protocol.ERR_BAD_REQUEST, e.getMessage());
        }
    }

    private void onRegister(Message m) {
        if (kind != Kind.UNKNOWN) {
            error(Protocol.ERR_CONFLICT, "Already registered");
            return;
        }
        String nodeId = m.arg(0);
        sensors = m.arg(1);
        actuators = m.arg(2);
        if (!registry.registerNode(nodeId, this)) {
            error(Protocol.ERR_CONFLICT, "Node id already in use: " + nodeId);
            return;
        }
        kind = Kind.SENSOR_NODE;
        id = nodeId;
        send(Message.of(MessageType.OK));
        registry.announceNode(this);
        alarms.syncNode(this);
        System.out.println("Node registered: " + nodeId);
        log.add("CONNECT", "Node " + nodeId + " registered (" + countOf(sensors) + " sensors, " + countOf(actuators)
                + " actuators)");
    }

    private void onLogin(Message m) {
        if (kind != Kind.UNKNOWN) {
            error(Protocol.ERR_CONFLICT, "Already logged in");
            return;
        }
        String panelId = m.arg(0);
        Role requestedRole = Role.parse(m.arg(1));
        if (!Authenticator.isValid(requestedRole, m.arg(2))) {
            log.add("AUTH", "Login refused for panel " + panelId + " as " + requestedRole + " (wrong password)");
            error(Protocol.ERR_UNAUTHENTICATED, "Wrong password for role " + requestedRole);
            return;
        }
        role = requestedRole; // set before the panel becomes visible to node announcements
        if (!registry.registerPanel(panelId, this)) {
            role = null;
            error(Protocol.ERR_CONFLICT, "Panel id already in use: " + panelId);
            return;
        }
        kind = Kind.PANEL;
        id = panelId;
        send(Message.of(MessageType.OK));
        // Tell the new panel about every node that is already connected.
        registry.allNodes().forEach(node -> send(node.nodeInfoFor(requestedRole)));
        send(entryGuard.stateMessage());
        System.out.println("Panel logged in: " + panelId + " as " + requestedRole);
        log.add("AUTH", "Panel " + panelId + " logged in as " + requestedRole);
    }

    /**
     * READING from a node is forwarded to subscribers, tagged with the node id;
     * ageMs is 0 for a fresh reading.
     */
    private void onReading(Message m) {
        if (!require(Kind.SENSOR_NODE)) {
            return;
        }
        String ageMs = m.args().size() > 2 ? m.arg(2) : "0";
        registry.publish(id, Message.of(MessageType.READING, id, m.arg(0), m.arg(1), ageMs));
        try {
            double value = Double.parseDouble(m.arg(1));
            long age = Long.parseLong(ageMs);
            fireSafety.onReading(id, m.arg(0), value, age);
            entryGuard.onReading(id, m.arg(0), value, age);
        } catch (NumberFormatException e) {
            // not a number, so there is nothing to evaluate
        }
    }

    /** STATUS from a node is remembered and forwarded to subscribers. */
    private void onStatus(Message m) {
        if (!require(Kind.SENSOR_NODE)) {
            return;
        }
        actuatorStates.put(m.arg(0), m.arg(1));
        registry.publish(id, Message.of(MessageType.STATUS, id, m.arg(0), m.arg(1)));
    }

    private void onSubscribe(Message m) {
        if (!require(Kind.PANEL)) {
            return;
        }
        String nodeId = m.arg(0);
        ClientHandler node = registry.node(nodeId);
        if (node == null) {
            error(Protocol.ERR_NOT_FOUND, "No such node: " + nodeId);
            return;
        }
        registry.subscribe(nodeId, this);
        send(Message.of(MessageType.OK));
        node.statusMessages().forEach(this::deliverToViewer); // bring the panel up to date with
                                                              // the actuators it may see
    }

    private void onListNodes() {
        if (!require(Kind.PANEL)) {
            return;
        }
        String joined = String.join(Protocol.LIST_SEPARATOR, registry.nodeIds());
        send(Message.of(MessageType.NODE_LIST, joined));
    }

    private void onCommand(Message m) {
        if (!require(Kind.PANEL)) {
            return;
        }
        String nodeSelector = m.arg(0);
        String actuatorSelector = m.arg(1);
        CommandRouter.Result result = router.route(role, nodeSelector, actuatorSelector, m.arg(2));
        if (result.delivered() == 0) {
            error(result.errorCode(), result.errorText());
        } else if (CommandRouter.isGroup(nodeSelector, actuatorSelector)) {
            logCommand(nodeSelector, actuatorSelector, m.arg(2), result);
            send(Message.of(MessageType.OK, String.valueOf(result.delivered()), String.valueOf(result.skipped())));
        } else {
            logCommand(nodeSelector, actuatorSelector, m.arg(2), result);
            send(Message.of(MessageType.OK));
        }
    }

    private void logCommand(String nodeSelector, String actuatorSelector, String action, CommandRouter.Result result) {
        log.add("COMMAND", id + " (" + role + "): " + action + " -> " + nodeSelector + " / " + actuatorSelector
                + " (" + result.delivered() + " delivered, " + result.skipped() + " skipped)");
    }

    /** ARM|code or DISARM|code. Any logged-in role may use the house code. */
    private void onSecurityCommand(Message m, boolean arm) {
        if (!require(Kind.PANEL)) {
            return;
        }
        String who = id + " (" + role + ")";
        EntryGuard.Result result = arm ? entryGuard.arm(m.arg(0), who) : entryGuard.disarm(m.arg(0), who);
        if (result.ok()) {
            send(Message.of(MessageType.OK));
        } else {
            error(result.errorCode(), result.errorText());
        }
    }

    /**
     * GET_LOG: the history, then OK, and from now on every new entry as it happens.
     */
    private void onGetLog() {
        if (!require(Kind.PANEL) || !requireLogAccess()) {
            return;
        }
        for (EventLog.Entry entry : log.watch(this)) {
            send(entry.toMessage());
        }
        send(Message.of(MessageType.OK));
    }

    private void onGetDiag() {
        if (!require(Kind.PANEL) || !requireLogAccess()) {
            return;
        }
        registry.allClients().forEach(client -> send(client.diagMessage()));
        send(Message.of(MessageType.OK));
    }

    private boolean requireLogAccess() {
        if (role.mayViewLog()) {
            return true;
        }
        error(Protocol.ERR_FORBIDDEN, "Role " + role + " may not view the log");
        return false;
    }

    private boolean require(Kind required) {
        if (kind == required) {
            return true;
        }
        if (kind == Kind.UNKNOWN) {
            error(Protocol.ERR_UNAUTHENTICATED, "Register or log in first");
        } else {
            error(Protocol.ERR_BAD_REQUEST, "Not allowed for this client type");
        }
        return false;
    }

    private void error(String code, String text) {
        log.add("ERROR", "To " + describe() + ": " + code + " " + text);
        send(Message.of(MessageType.ERROR, code, text.replace(Protocol.SEPARATOR, "/")));
    }
}
