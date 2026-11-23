package ntnu.bidata.iir.broker;

import java.util.HashSet;
import java.util.Set;
import ntnu.bidata.iir.common.SensorType;

/**
 * House-wide fire alarm rule, run by the broker on every smoke reading:
 * <ul>
 * <li>When any smoke sensor rises above ALARM_ABOVE, every alarm actuator in
 * the house is switched ON.</li>
 * <li>When the last smoke sensor that was above the limit falls below
 * CLEAR_BELOW, the alarms are switched OFF again. The gap between the two
 * limits stops the alarm from flickering.</li>
 * </ul>
 * The alarms are triggered once per incident (not on every reading), so someone
 * with the right role can still switch an alarm off by hand while the smoke is
 * still here.
 */
final class FireSafety {
    static final double ALARM_ABOVE = SensorType.SMOKE.normalMax();
    static final double CLEAR_BELOW = 2.0;
    /**
     * Readings that a node buffered while offline are history, not news: they do
     * not start or stop the alarm.
     */
    static final long MAX_READING_AGE_MS = 5000;

    private static final String REASON = "FIRE";

    private final AlarmControl alarms;
    private final EventLog log;
    private final Set<String> smoking = new HashSet<>(); // "node/sensor" of every smoke sensor above the limit

    FireSafety(AlarmControl alarms, EventLog log) {
        this.alarms = alarms;
        this.log = log;
    }

    /**
     * Call for every reading from a node; readings of other sensor types and old
     * buffered ones are ignored.
     */
    synchronized void onReading(String nodeId, String sensorId, double value, long ageMs) {
        if (ageMs > MAX_READING_AGE_MS || SensorType.find(sensorId).orElse(null) != SensorType.SMOKE) {
            return;
        }
        String key = nodeId + "/" + sensorId;
        if (value > ALARM_ABOVE) {
            boolean firstSource = smoking.isEmpty();
            smoking.add(key);
            if (firstSource) {
                log.add("FIRE", String.format(java.util.Locale.ROOT, "Smoke at %s is %.1f (limit %.1f)", key, value,
                        ALARM_ABOVE));
                alarms.raise(REASON);
            }
        } else if (value < CLEAR_BELOW && smoking.remove(key) && smoking.isEmpty()) {
            log.add("FIRE", String.format(java.util.Locale.ROOT, "Smoke cleared at %s (%.1f)", key, value));
            alarms.clear(REASON);
        }
    }

    /**
     * Call when a node disconnects: a smoke that is gone can no longer keep the
     * alarm on.
     */
    synchronized void forget(String nodeId) {
        boolean removed = smoking.removeIf(key -> key.startsWith(nodeId + "/"));
        if (removed && smoking.isEmpty()) {
            log.add("FIRE", "Node " + nodeId + " with smoke left");
            alarms.clear(REASON);
        }
    }
}
