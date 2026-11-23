package ntnu.bidata.iir.broker;

import java.util.List;
import ntnu.bidata.iir.common.ActuatorType;
import ntnu.bidata.iir.common.Message;
import ntnu.bidata.iir.common.MessageType;
import ntnu.bidata.iir.common.Protocol;
import ntnu.bidata.iir.common.Role;

/**
 * Finds the actuators a command is meant for, checks the sender's role and the
 * action, and forwards it.
 * 
 * Targets are written as two selectors:
 * <ul>
 * <li>node selector: one id ("kitchen") a list ("kitchen,attic") or "*" for
 * every node (multicast / broadcast).
 * <li>actuator selector: one id ("fan:1"), a type ("fan:*"), a list, or "*" for
 * every actuator of the node</li>
 * </ul>
 * Examples: turn off fan 3 on node 42: {@code 42, fan:3, OFF}. Turn on all fans
 * on nodes 7, 12, and 19:
 * {@code 7,12,19, fan:*, ON}. Turn off everything on node 7: {@code 7, *, OFF}.
 */
final class CommandRouter {

    /**
     * What happened to a command: how many actuators got it, how many were skipped
     * and why the first one was.
     */
    record Result(int delivered, int skipped, String errorCode, String errorText) {
    }

    private final Registry registry;

    CommandRouter(Registry registry) {
        this.registry = registry;
    }

    /** True if the selectors can address more than one actuator. */
    static boolean isGroup(String nodeSelector, String actuatorSelector) {
        return nodeSelector.contains(Protocol.WILDCARD) || nodeSelector.contains(Protocol.LIST_SEPARATOR)
                || actuatorSelector.contains(Protocol.WILDCARD) || actuatorSelector.contains(Protocol.LIST_SEPARATOR);

    }

    Result route(Role role, String nodeSelector, String actuatorSelector, String action) {
        Tally tally = new Tally();
        for (String nodeId : resolveNodes(nodeSelector)) {
            ClientHandler node = registry.node(nodeId);
            if (node == null) {
                tally.fail(Protocol.ERR_NOT_FOUND, "No such note: " + nodeId);
                continue;
            }
            for (String selector : actuatorSelector.split(Protocol.LIST_SEPARATOR)) {
                routeToActuators(role, node, selector.strip(), action, tally);
            }
        }
        if (tally.delivered == 0 && tally.errorCode == null) {
            tally.fail(Protocol.ERR_NOT_FOUND, "No actuator matches " + nodeSelector + " / " + actuatorSelector);
        }
        return new Result(tally.delivered, tally.skipped, tally.errorCode, tally.errorText);
    }

    private List<String> resolveNodes(String selector) {
        if (selector.equals(Protocol.WILDCARD)) {
            return registry.nodeIds();
        }
        return List.of(selector.split(Protocol.LIST_SEPARATOR)).stream().map(String::strip).toList();
    }

    private void routeToActuators(Role role, ClientHandler node, String selector, String action, Tally tally) {
        List<String> available = node.actuatorIds();
        if (isPattern(selector)) {
            for (String actuatorId : available) {
                if (matches(selector, actuatorId)) {
                    deliver(role, node, actuatorId, action, tally);
                }
            }
        } else if (available.contains(selector)) {
            deliver(role, node, selector, action, tally);
        } else {
            tally.fail(Protocol.ERR_NOT_FOUND, "Node " + node.id() + " has no actuator " + selector);
        }
    }

    private static boolean isPattern(String selector) {
        return selector.equals(Protocol.WILDCARD) || selector.endsWith(":" + Protocol.WILDCARD);
    }

    private static boolean matches(String pattern, String actuatorId) {
        if (pattern.equals(Protocol.WILDCARD)) {
            return true;
        }
        String type = pattern.substring(0, pattern.length() - 2); // "fan:*" -> "fan"
        return actuatorId.startsWith(type + ":");
    }

    private void deliver(Role role, ClientHandler node, String actuatorId, String action, Tally tally) {
        if (!role.mayControl(actuatorId, action)) {
            tally.fail(Protocol.ERR_FORBIDDEN, "Role " + role + " may not control " + actuatorId + " (" + action + ")");
            return;
        }
        ActuatorType type = ActuatorType.find(actuatorId).orElse(null);
        if (type != null && !type.accepts(action)) {
            tally.fail(Protocol.ERR_BAD_REQUEST,
                    actuatorId + " does not accept " + action + " (allowed: " + type.allowedActions() + ")");
            return;
        }
        node.send(Message.of(MessageType.COMMAND, actuatorId, action));
        tally.delivered++;
    }

    /** Counts the outcome of one command and remembers the first problem. */
    private static final class Tally {
        private int delivered;
        private int skipped;
        private String errorCode;
        private String errorText;

        void fail(String code, String text) {
            skipped++;
            if (errorCode == null) {
                errorCode = code;
                errorText = text;
            }
        }
    }
}
