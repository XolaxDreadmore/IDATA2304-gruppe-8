package ntnu.bidata.iir.common;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Every kind of actuator the system knows about
 */

public enum ActuatorType {
    FAN("fan", Access.BASIC, "temp", -0.2, "OFF", "ON"),
    LIGHT("light", Access.BASIC, "lux", 300.0, "OFF", "ON"),
    EXTRACTOR("extractor",  Access.BASIC,    "smoke", -1.0, "OFF", "ON"),
    HEATER("heater", Access.STANDARD, Access.STANDARD, new Range(5, 35, 22, "°C"), "temp",  0.3, "OFF", "ON"),
    COOLER("cooler", Access.STANDARD, Access.STANDARD, new Range(5, 35, 20, "°C"), "temp", -0.5, "OFF", "ON"),
    HUMIDIFIER("humidifier", Access.STANDARD, "hum",0.8, "OFF", "ON"),
    WINDOW("window", Access.STANDARD, "",0.0, "CLOSED", "OPEN"),
    DOOR("door", Access.SECURITY, Access.STANDARD, null, "", 0.0, "CLOSED", "OPEN"),
    LOCK("lock", Access.SECURITY, Access.STANDARD, null, "", 0.0, "LOCKED", "UNLOCKED"),
    ALARM("alarm", Access.SECURITY, Access.STANDARD, null, "", 0.0, "OFF", "ON"),
    MOTION     ("motion",     Access.BASIC,    "",      0.0, "OFF", "ON"),
    FIRE       ("fire",       Access.SECURITY, "smoke",  2.0, "OUT", "BURNING");

    /** the levels of access*/
    public enum Access {
        BASIC(1), STANDARD(2), SECURITY(3);

        private final int rank;

        Access(int rank) {
            this.rank = rank;
        }

        public int rank() {
            return rank;
        }
    }

    /** The values the SET action may take. */
    public record Range(double min, double max, double initial, String unit) { }

    private final String id;
    private final Access access;
    private final Access resetAccess;
    private final Range range;
    private final String affectedSensor;
    private final double driftWhenActive;
    private final List<String> states;

    ActuatorType(String id, Access access, String affectedSensor, double driftWhenActive, String... states) {
        this(id, access, access, null, affectedSensor, driftWhenActive, states);
    }

    ActuatorType(String id, Access access, Access resetAccess, Range range, String affectedSensor, double driftWhenActive, String... states) {
        this.id = id;
        this.access = access;
        this.resetAccess = resetAccess;
        this.range = range;
        this.affectedSensor = affectedSensor;
        this.driftWhenActive = driftWhenActive;
        this.states = List.of(states);
    }

    public String id() {
        return id;
    }

    /** security level needed to perform any action. */
    public Access access() {return access;}

    /** Level needed to move the device back to its first state. */
    public Access resetAccess() {
        return resetAccess;
    }

    /** The level needed to perform an action */
    public Access requiredAccess(String action) {
        return isActive(action) ? access : resetAccess;
    }

    /** The range of the actuators */
    public Range range() {
        return range;
    }

    /** Sensor type which is affected */
    public String affectedSensor() {
        return affectedSensor;
    }

    public double driftWhenActive() {
        return driftWhenActive;
    }

    /** The states this actuator have */
    public List<String> states() {
        return states;
    }

    public String defaultState() {
        return states.get(0);
    }

    public boolean isActive(String state) {
        return !states.get(0).equals(state);
    }

    /** True if the action is one of the states, or a SET inside the range. */
    public boolean accepts(String action) {
        return states.contains(action) || parseSet(action).isPresent();
    }

    /** readable list of valid actions. */
    public String allowedActions() {
        String text = String.join("/", states);
        if (range != null) {
            text += "/SET:" + format(range.min()) + "-" + format(range.max());
        }
        return text;
    }

    public OptionalDouble parseSet(String action) {
        if (range == null || !action.startsWith("SET:")) {
            return OptionalDouble.empty();
        }
        return numberInRange(action.substring("SET:".length()));
    }

    /** reports the state */
    public String stateFor(String action) {
        OptionalDouble value = parseSet(action);
        return value.isPresent() ? format(value.getAsDouble()) : action;
    }

    /** If the state is a target value it uses that value. empty for ON/OFF . */
    public OptionalDouble setpointOf(String state) {
        if (range == null || states.contains(state)) {
            return OptionalDouble.empty();
        }
        return numberInRange(state);
    }

    private OptionalDouble numberInRange(String text) {
        if (!text.matches("-?\\d{1,3}(\\.\\d{1,2})?")) {
            return OptionalDouble.empty();
        }
        double value = Double.parseDouble(text);
        return value >= range.min() && value <= range.max() ? OptionalDouble.of(value) : OptionalDouble.empty();
    }

    private static String format(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    /** @param actuatorId name of the actuator. Empty if the type of actuator is not known. */
    public static Optional<ActuatorType> find(String actuatorId) {
        String type = actuatorId.split(":", 2)[0];
        for (ActuatorType candidate : values()) {
            if (candidate.id.equals(type)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /** Start state, with OFF as a fallback. */
    public static String defaultStateOf(String actuatorId) {
        return find(actuatorId).map(ActuatorType::defaultState).orElse("OFF");
    }

    /** Possible states, with ON/OFF as a fallback. */
    public static List<String> statesOf(String actuatorId) {
        return find(actuatorId).map(ActuatorType::states).orElse(List.of("OFF", "ON"));
    }

    public static boolean isActiveState(String actuatorId, String state) {
        return find(actuatorId).map(type -> type.isActive(state)).orElse(!"OFF".equals(state));
    }

    /** The state with its unit when it is a target value. */
    public static String describe(String actuatorId, String state) {
        return find(actuatorId)
                .filter(type -> type.setpointOf(state).isPresent())
                .map(type -> state + " " + type.range().unit())
                .orElse(state);
    }
}
