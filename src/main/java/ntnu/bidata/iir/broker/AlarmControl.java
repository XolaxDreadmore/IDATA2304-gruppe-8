package ntnu.bidata.iir.broker;

import java.util.LinkedHashSet;
import java.util.Set;
import ntnu.bidata.iir.common.ActuatorType;
import ntnu.bidata.iir.common.Message;
import ntnu.bidata.iir.common.MessageType;

/**
 * Switches every alarm actuator in the house on and off on behalf of the safety
 * rules.
 * Each rule gives its own reason ("FIRE", "INTRUDER"); the alarms go ON when
 * first reason appers and OFF only when the last reason is gone, so the fire
 * rule can never silence an intruder alarm (or the other way around).
 */
final class AlarmControl {
    private final Registry registry;
    private final EventLog log;
    private final Set<String> reasons = new LinkedHashSet<>();

    AlarmControl(Registry registry, EventLog log) {
        this.registry = registry;
        this.log = log;
    }

    synchronized void raise(String reason) {
        boolean first = reasons.isEmpty();
        if (reasons.add(reason)) {
            log.add("ALARM", "Alarm reason added: " + reason + (first ? " -> all alarms ON" : " (alarms already on)"));
            if (first) {
                setAlarms("ON");
            }
        }
    }

    synchronized void clear(String reason) {
        if (reasons.remove(reason)) {
            log.add("ALARM", "Alarm reason gone: " + reason
                    + (reasons.isEmpty() ? " -> all alarms OFF" : " (still on for " + reasons + ")"));
            if (reasons.isEmpty()) {
                setAlarms("OFF");
            }
        }
    }

    synchronized boolean active() {
        return !reasons.isEmpty();
    }

    /**
     * A node that joins while an alarm is going gets its alarms switched on too.
     */
    synchronized void syncNode(ClientHandler node) {
        if (!reasons.isEmpty()) {
            sendToAlarms(node, "ON");
        }
    }

    private void setAlarms(String action) {
        for (ClientHandler node : registry.allNodes()) {
            sendToAlarms(node, action);
        }
    }

    private static void sendToAlarms(ClientHandler node, String action) {
        for (String actuatorId : node.actuatorIds()) {
            if (ActuatorType.find(actuatorId).orElse(null) == ActuatorType.ALARM) {
                node.send(Message.of(MessageType.COMMAND, actuatorId, action));
            }
        }
    }
}
