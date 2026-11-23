package ntnu.bidata.iir.sensornode;

/** A sensor that measures a level and can be set externally. */

public class LevelSensor extends Sensor {
    private volatile double level;

    public LevelSensor(String id, double level) {
        super(id);
        this.level = level;
    }

    public void set(double level) { this.level = level; }

    @Override
    public double read() { return level; }
}
