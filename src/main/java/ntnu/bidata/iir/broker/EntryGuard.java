package ntnu.bidata.iir.broker;

import java.util.HashSet;
import java.util.Set;
import java.util.function.LongSupplier;
import ntnu.bidata.iir.common.Message;
import ntnu.bidata.iir.common.MessageType;
import ntnu.bidata.iir.common.Protocol;
import ntnu.bidata.iir.common.SensorType;

/**
 * Burglar protection for the entrance, run by the broker.
 * 
 * <pre>
 * DISARMED --ARM(code)--> ARMED --motion at an entrance node--> ENTRY (countdown)
 * ENTRY --DISARM(code) in time--> DISARMED   ENTRY --time runs out--> ALARM
 * ARMED/ENTRY --MAX_WRONG_CODES wrong codes--> ALARM
 * ALARM --DISARM(code)--> DISARMED (alarms off)
 * </pre>
 * 
 * Motion only counts when it starts (a rising edge): arming while the motion
 * switch is still ON does not trigger anything until it has gone OFF and ON
 * again. The alarms themselves are switched through AlarmControl with the
 * reason "INTRUDER". The state is kept in memory only (a restarted broker is
 * DISARMED).
 */
final class EntryGuard {
    enum State {
        DISARMED, ARMED, ENTRY, ALARM
    }

    /**
     * Reply to ARM / DISARM: either ok, or an error code and text for the panel.
     */
    record Result(boolean ok, String errorCode, String errorText) {
        static Result success() {
            return new Result(true, null, null);
        }

        static Result failure(String code, String text) {
            return new Result(false, code, text);
        }
    }

    /** Nodes whose motion sensor guards the way in. Edit to protect other doors. */
    static final Set<String> ENTRY_NODES = Set.of("maindoor");
    static final int ENTRY_DELAY_SECONDS = 30;
    static final int MAX_WRONG_CODES = 3;
    private static final String REASON = "INTRUDER";

    private final Registry registry;
    private final AlarmControl alarms;
    private final EventLog log;
    private final LongSupplier clock; // milliseconds; replaced in tests
    private final Set<String> moving = new HashSet<>(); // entrance motion sensors that currently report motion
    private State state = State.DISARMED;
    private long deadline;
    private int wrongCodes;

    EntryGuard(Registry registry, AlarmControl alarms, EventLog log, LongSupplier clock) {
        this.registry = registry;
        this.alarms = alarms;
        this.log = log;
        this.clock = clock;
    }

    synchronized State state() {
        return state;
    }

    /**
     * Current state as the message panels receive: SECURITY|state|secondsLeft (0
     * unless ENTRY).
     */
    synchronized Message stateMessage() {
        return Message.of(MessageType.SECURITY, state.name(), String.valueOf(secondsLeft()));
    }

    /**
     * Call for every reading from a node. Only fresh motion readings from entrance
     * nodes matter.
     */
    synchronized void onReading(String nodeId, String sensorId, double value, long ageMs) {
        if (!ENTRY_NODES.contains(nodeId) || SensorType.find(sensorId).orElse(null) != SensorType.MOTION
                || ageMs > FireSafety.MAX_READING_AGE_MS) {
            return;
        }
        String key = nodeId + "/" + sensorId;
        boolean motion = value > 0.5;
        boolean started = motion && moving.add(key);
        if (!motion) {
            moving.remove(key);
        }
        if (started && state == State.ARMED) {
            state = State.ENTRY;
            deadline = clock.getAsLong() + ENTRY_DELAY_SECONDS * 1000L;
            log.add("SECURITY",
                    "Motion at " + key + " while armed: enter the code within " + ENTRY_DELAY_SECONDS + " s");
            announce();
        }
    }

    /** Call when a node disconnects. */
    synchronized void forget(String nodeId) {
        moving.removeIf(key -> key.startsWith(nodeId + "/"));
    }

    synchronized Result arm(String code, String who) {
        if (!Authenticator.isValidHouseCode(code)) {
            log.add("SECURITY", who + " tried to arm with a wrong code");
            return Result.failure(Protocol.ERR_UNAUTHENTICATED, "Wrong code");
        }
        if (state == State.ARMED) {
            return Result.success();
        }
        if (state != State.DISARMED) {
            return Result.failure(Protocol.ERR_CONFLICT, "Cannot arm while the state is " + state);
        }
        state = State.ARMED;
        wrongCodes = 0;
        log.add("SECURITY", "House armed by " + who);
        announce();
        return Result.success();
    }

    synchronized Result disarm(String code, String who) {
        if (!Authenticator.isValidHouseCode(code)) {
            return wrongCode(who);
        }
        wrongCodes = 0;
        if (state == State.DISARMED) {
            return Result.success();
        }
        State before = state;
        state = State.DISARMED;
        log.add("SECURITY", "House disarmed by " + who + " (was " + before + ")");
        if (before == State.ALARM) {
            alarms.clear(REASON);
        }
        announce();
        return Result.success();
    }

    /**
     * Call about once a second: counts the entry delay down and starts the alarm
     * when it is over.
     */
    synchronized void tick() {
        if (state != State.ENTRY) {
            return;
        }
        if (clock.getAsLong() >= deadline) {
            log.add("SECURITY", "Entry time ran out without a code");
            triggerAlarm();
        } else {
            announce();
        }
    }

    private Result wrongCode(String who) {
        if (state != State.ARMED && state != State.ENTRY) {
            log.add("SECURITY", who + " entered a wrong code");
            return Result.failure(Protocol.ERR_UNAUTHENTICATED, "Wrong code");
        }
        wrongCodes++;
        log.add("SECURITY", who + " entered a wrong code (" + wrongCodes + " of " + MAX_WRONG_CODES + ")");
        if (wrongCodes >= MAX_WRONG_CODES) {
            triggerAlarm();
            return Result.failure(Protocol.ERR_UNAUTHENTICATED, "Wrong code - too many attempts, alarm triggered");
        }
        return Result.failure(Protocol.ERR_UNAUTHENTICATED,
                "Wrong code (" + (MAX_WRONG_CODES - wrongCodes) + " attempts left)");
    }

    private void triggerAlarm() {
        state = State.ALARM;
        alarms.raise(REASON);
        announce();
    }

    private int secondsLeft() {
        if (state != State.ENTRY) {
            return 0;
        }
        return (int) Math.max(0, (deadline - clock.getAsLong() + 999) / 1000);
    }

    private void announce() {
        registry.broadcastToPanels(stateMessage());
    }
}
