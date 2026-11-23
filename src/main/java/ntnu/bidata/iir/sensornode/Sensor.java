package ntnu.bidata.iir.sensornode;

/** A simulated sensor. Add a new sensor type by subclassing and registering it in SensorFactory. */
public abstract class Sensor {
    private final String id; // e.g. "temp:1"

    protected Sensor(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** Next simulated measurement. */
    public abstract double read();
}
