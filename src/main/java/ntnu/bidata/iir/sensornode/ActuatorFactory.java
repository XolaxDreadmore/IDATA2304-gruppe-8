package ntnu.bidata.iir.sensornode;

import ntnu.bidata.iir.common.ActuatorType;

public final class ActuatorFactory {
    private ActuatorFactory() { }

    public static Actuator create(String spec) {
        ActuatorType type = ActuatorType.find(spec)
                .orElseThrow(() -> new IllegalArgumentException("Unknown actuator type: " + spec));
        return new Actuator(spec, type);
    }
}
