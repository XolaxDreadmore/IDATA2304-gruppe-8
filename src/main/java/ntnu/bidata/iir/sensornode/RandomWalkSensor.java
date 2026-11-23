package ntnu.bidata.iir.sensornode;

import java.util.concurrent.ThreadLocalRandom;

/** Simulated sensor whose value drifts randomly inside [min, max]. */
public class RandomWalkSensor extends Sensor {
    private final double min;
    private final double max;
    private final double step;
    private double value;
    private volatile double drift;
    private final double baseline; // the start value
    private final double pull; // the amount of the distance to the baseline recovered per reading


    public RandomWalkSensor(String id, double start, double min, double max, double step, double pull) {
        super(id);
        this.value = start;
        this.min = min;
        this.max = max;
        this.step = step;
        this.baseline = start;
        this.pull = pull;
    }

    /** Constant push per reading */
    public void setDrift(double drift) {this.drift = drift;}

    /** The latest value, without producing a new reading. */
    public synchronized double current() { return value; }

    @Override
    public synchronized double read() {
        value += ThreadLocalRandom.current().nextDouble(-step, step) + drift - pull * (value - baseline);
        value = Math.max(min, Math.min(max, value));
        return value;
    }
}
