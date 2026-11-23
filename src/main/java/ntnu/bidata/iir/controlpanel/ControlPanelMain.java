package ntnu.bidata.iir.controlpanel;

import java.io.IOException;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentSkipListMap;
import ntnu.bidata.iir.common.Role;

/**
 * Command-line control panel, built on the same PanelClient as the GUI (so it
 * also reconnects by itself).
 * Usage: ControlPanelMain [host] [panelId] [role] [password]
 * Commands:
 * nodes list the nodes with their sensors and actuators
 * cmd NODES ACTUATORS ACTION NODES: id, "a,b,c" or * ACTUATORS: id, type:* or *
 * arm CODE | disarm CODE arm or disarm the house (the entrance motion sensor
 * then starts a countdown)
 * log | diag event log and connected clients (technician and above)
 * readings on|off show every sensor reading (off by default, there are many)
 * quit
 * Examples: cmd kitchen fan:1 ON cmd kitchen,attic light:* OFF cmd garage
 * heater:1 SET:22 cmd * * OFF
 */
public final class ControlPanelMain {
    private ControlPanelMain() {
    }

    public static void main(String[] args) throws IOException {
        String host = args.length > 0 ? args[0] : "localhost";
        String panelId = args.length > 1 ? args[1] : "panel1";
        Role role = Role.parse(args.length > 2 ? args[2] : "OWNER");
        String password = args.length > 3 ? args[3] : "owner123";

        ConsoleListener listener = new ConsoleListener();
        try (PanelClient client = PanelClient.connect(host, panelId, role, password, listener)) {
            runMenu(client, listener);
        }
    }

    private static void runMenu(PanelClient client, ConsoleListener listener) {
        System.out.println(
                "Commands: nodes | cmd NODES ACTUATORS ACTION | arm CODE | disarm CODE | log | diag | readings on|off | quit");
        Scanner scanner = new Scanner(System.in);
        while (scanner.hasNextLine()) {
            String[] p = scanner.nextLine().trim().split("\\s+");
            switch (p[0]) {
                case "nodes" -> listener.printNodes();
                case "readings" -> listener.showReadings = p.length > 1 && p[1].equalsIgnoreCase("on");
                case "cmd" -> {
                    if (p.length != 4) {
                        System.out.println("Usage: cmd NODES ACTUATORS ACTION");
                    } else if (!client.sendCommand(p[1], p[2], p[3].toUpperCase())) {
                        System.out.println("NOT SENT: no connection to the broker");
                    }
                }
                case "arm", "disarm" -> {
                    if (p.length != 2) {
                        System.out.println("Usage: " + p[0] + " CODE");
                    } else if (!(p[0].equals("arm") ? client.arm(p[1]) : client.disarm(p[1]))) {
                        System.out.println("NOT SENT: no connection to the broker");
                    }
                }
                case "log" -> client.requestLog();
                case "diag" -> client.requestDiagnostics();
                case "quit" -> {
                    return;
                }
                default -> System.out.println("Unknown command");
            }
        }
    }

    /** Prints everything that happens. */
    private static final class ConsoleListener implements PanelClient.Listener {
        private final Map<String, String> nodes = new ConcurrentSkipListMap<>();
        private volatile boolean showReadings;

        void printNodes() {
            nodes.forEach((id, description) -> System.out.println(id + "  " + description));
        }

        @Override
        public void onConnected() {
            nodes.clear();
            System.out.println("Connected.");
        }

        @Override
        public void onNodeInfo(String nodeId, String[] sensors, String[] actuators) {
            String description = "sensors=" + String.join(",", sensors) + " actuators=" + String.join(",", actuators);
            nodes.put(nodeId, description);
            System.out.println("+ " + nodeId + "  " + description);
        }

        @Override
        public void onNodeGone(String nodeId) {
            nodes.remove(nodeId);
            System.out.println("- " + nodeId + " disconnected");
        }

        @Override
        public void onReading(String nodeId, String sensorId, String value, long ageMs) {
            if (showReadings) {
                System.out.println(
                        nodeId + "/" + sensorId + " = " + value + (ageMs > 0 ? " (" + ageMs + " ms old)" : ""));
            }
        }

        @Override
        public void onStatus(String nodeId, String actuatorId, String state) {
            System.out.println(nodeId + "/" + actuatorId + " is " + state);
        }

        @Override
        public void onGroupResult(int delivered, int skipped) {
            System.out.println("Group command reached " + delivered + " actuators (" + skipped + " skipped)");
        }

        @Override
        public void onError(String code, String text) {
            System.out.println("Error " + code + ": " + text);
        }

        @Override
        public void onSecurity(String state, int secondsLeft) {
            System.out.println("SECURITY: " + state + ("ENTRY".equals(state) ? " (" + secondsLeft + " s left)" : ""));
        }

        @Override
        public void onLog(long timeMillis, String category, String text) {
            System.out.println("[" + java.time.LocalTime.ofInstant(java.time.Instant.ofEpochMilli(timeMillis),
                    java.time.ZoneId.systemDefault()).withNano(0) + "] " + category + ": " + text);
        }

        @Override
        public void onDiagnostic(String kind, String id, String role, long connectedSeconds, long idleSeconds,
                String detail) {
            System.out.println(kind + " " + id + (role.isEmpty() ? "" : " (" + role + ")") + "  connected "
                    + connectedSeconds + " s, idle " + idleSeconds + " s  " + detail);
        }

        @Override
        public void onDisconnected(String reason, boolean willRetry) {
            System.out.println(reason + (willRetry ? " - reconnecting..." : " - giving up"));
        }
    }
}
