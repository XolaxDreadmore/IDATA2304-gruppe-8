package ntnu.bidata.iir.common;

import java.util.Optional;

/** every kind of sensors the system knows about, how they behave
 *  and what is considered normal. */
public enum SensorType {
    // id, label, unit, start, min, max, step, pull, normalMin, normalMax, maxJump, guestVisible
    // temperature slowly returns to the start value so that it dont wander off forever, but remains aprox the same.
    TEMP ("temp", "Temperature", "°C", 21, -10, 40, 0.4, 0.02, 5.0, 30.0, 3.0, true),
    HUM ("hum", "Humidity", "%", 45, 10, 90, 1.5, 0.0, 20.0, 80.0, 10.0, true),
    LUX ("lux", "Light level", "lux", 100, 0, 1000, 0.0, 0.0, 0.0, 1000.0, 1000.0, true),
    SMOKE ("smoke", "Smoke", "%", 0.5, 0.0, 100.0, 0.05, 0.1, 0.0, 5.0, 50.0, false),
    MOTION ("motion", "Motion", "", 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0, 1.5, false),
    DOOR ("door", "Door", "", 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0, 1.5, false);

    private final String id;
    private final String label;
    private final String unit;
    private final double start;
    private final double min;
    private final double max;
    private final double step;
    private final double pull;
    private final double normalMin;
    private final double normalMax;
    private final double maxJump;
    private final boolean guestVisible;

    SensorType(String id, String label, String unit, double start, double min, double max, double step,
               double pull, double normalMin, double normalMax, double maxJump, boolean guestVisible){
        this.id = id;
        this.label = label;
        this.unit = unit;
        this.start = start;
        this.min = min;
        this.max = max;
        this.step = step;
        this.pull = pull;
        this.normalMin = normalMin;
        this.normalMax = normalMax;
        this.maxJump = maxJump;
        this.guestVisible = guestVisible;
    }
     public String id() { return id; }

     public String label() { return label; }

    public String unit() { return unit; }

    /** Simulation: first value, physical limits and largest random change per reading. */
    public double start() { return start; }

    public double min() { return min; }

    public double max() { return max; }

    public double step() { return step; }

    /** Simulation: how much the value drifts back to start per reading. */
    public double pull() { return pull; }

    /** if a guest can see this sensor or not */
    public boolean guestVisible() { return guestVisible; }

    /** if the sensor is a switch (motion or door) */
    public boolean isSwitch() { return this == MOTION || this == DOOR; }

    public boolean isLevel() { return this == LUX; }

    public String describe(double value) {
        return switch (this) {
            case MOTION -> value > 0.5 ? "detected" : "none";
            case DOOR -> value > 0.5 ? "open" : "closed";
            case LUX -> String.format(java.util.Locale.ROOT, "%.0f %s", value, unit);
            default -> String.format(java.util.Locale.ROOT, "%.1f %s", value, unit);
        };
    }

    /** Find the sensor type for a given sensor ID. The ID is of the form "type:instance", e.g. "temp:1". */
    public static String describe(String sensorId, double value) {
        return find(sensorId).map(type -> type.describe(value)).orElse(String.format(java.util.Locale.ROOT, "%.1f", value));
    }

    /**
     * @param sensorId "temp:1" or "temp" or "motion:2"
     * @return the sensor type, or empty if unknown
     */
    public static Optional<SensorType> find(String sensorId) {
        String typeId = sensorId.split(":", 2)[0];
        for (SensorType candidate : values()) {
            if (candidate.id.equals(typeId)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}
