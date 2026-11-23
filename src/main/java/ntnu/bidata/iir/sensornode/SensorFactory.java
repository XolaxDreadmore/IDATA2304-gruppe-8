package ntnu.bidata.iir.sensornode;

import ntnu.bidata.iir.common.SensorType;

/** A factory for creating sensors from a sheet. like temperature or light */
public class SensorFactory {
    private SensorFactory() { }

    public static Sensor create(String spec) {
        SensorType type = SensorType.find(spec).orElseThrow(() -> new IllegalArgumentException("Unknown sensor type: " + spec));
        if (type.isLevel()) {
            return new LevelSensor(spec, type.start());
        } else if (type.isSwitch()) {
            return new SwitchSensor(spec);
        } else {
            return new RandomWalkSensor(spec, type.start(), type.min(), type.max(), type.step(), type.pull());
        }
    }
}
