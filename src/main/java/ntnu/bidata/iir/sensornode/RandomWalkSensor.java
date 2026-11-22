package ntnu.bidata.iir.sensornode;

import java.util.concurrent.ThreadLocalRandom;

/** Simulated sensor whose value drifts randomly inside [min, max]. */
public class RandomWalkSensor extends Sensor {
    private final double min;
    private final double max;
    private final double step;
    private double value;
    private volatile double drift;

    public RandomWalkSensor(String id, double start, double min, double max, double step) {
        super(id);
        this.value = start;
        this.min = min;
        this.max = max;
        this.step = step;
    }

    /** Constant push per reading */
    public void setDrift(double drift) {
        this.drift = drift;
    }

    @Override
    public synchronized double read() {
        value += ThreadLocalRandom.current().nextDouble(-step, step) + drift;
        value = Math.max(min, Math.min(max, value));
        return value;
    }
}
