package ntnu.bidata.iir.sensornode;

/** A sensor that is either on or off . */
public class SwitchSensor extends Sensor {
    private volatile boolean on;

    public SwitchSensor(String id) {
        super(id);
    }

    public void set(boolean on) {
        this.on = on;
    }

    @Override
    public double read() {
        return on ? 1.0 : 0.0;
    }
}
