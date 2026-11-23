package ntnu.bidata.iir.sensornode;

import ntnu.bidata.iir.common.ActuatorType;
import ntnu.bidata.iir.common.SensorType;

import java.util.*;

public class SensorNode {
    private final String id;
    private final Map<String, Sensor> sensors = new LinkedHashMap<>();
    private final Map<String, Actuator> actuators = new LinkedHashMap<>();

    public SensorNode (String id, String[] sensorSpecs, String[] actuatorSpecs) {
        this.id = id;
        for (String spec : sensorSpecs) {
            if (!spec.isBlank()) {
                sensors.put(spec, SensorFactory.create(spec));
            }
        }
        for (String spec : actuatorSpecs) {
            if (!spec.isBlank()) {
                actuators.put(spec, ActuatorFactory.create(spec));
            }
        }
    }

    public String id() {return id;}

    public Map<String, Sensor> sensors() {return sensors;}

    public Map<String, Actuator> actuators() {return actuators;}

    /**
     * Applies an action to an actuator.
     *
     * @return true if the actuator exists and the action is valid for its type
     */
    public boolean command(String actuatorId, String action) {
        return !applyCommand(actuatorId, action).isEmpty();
    }

    /** applies an action and returns every actuator that has its state change because of it.*/
    public List<Actuator> applyCommand(String actuatorId, String action) {
        Actuator actuator = actuators.get(actuatorId);
        List<Actuator> changed = new ArrayList<>();
        if (actuator == null || !actuator.apply(action)) {
            return changed;
        }
        changed.add(actuator);
        boolean active = actuator.type().isActive(actuator.state());
        if (sensors.get(actuatorId) instanceof SwitchSensor sensor) {
            sensor.set(active);
        }
        if (actuator.type() == ActuatorType.MOTION) {
            boolean motion = active;
            for (Actuator other : actuators.values()) {
                if (other.type() == ActuatorType.LIGHT && other.apply(motion ? "ON" : "OFF")) {
                    changed.add(other);
                }
            }
        }
        return changed;
    }

    /** takes reading of a sensor and active actuators and moves the sensors it affects. i.e if you use a heater
     *  it will act as a thermostat and steer the sensor towards its set value
     */

    public double read(String sensorId) {
        Sensor sensor = sensors.get(sensorId);
        if (sensor instanceof RandomWalkSensor walker) {
            walker.setDrift(driftFor(walker));
        } else if (sensor instanceof LevelSensor level) {
            level.set(levelFor(level));
        }
        return sensor.read();
    }

    /**
     * Takes the base level of the sensors type plus what every active actuator adds for a specific node.
     */
    private double levelFor(LevelSensor sensor) {
        String sensorType = sensor.id().split(":", 2)[0];
        double level = SensorType.find(sensorType).map(SensorType::start).orElse(0.0);
        for (Actuator actuator : actuators.values()) {
            ActuatorType type = actuator.type();
            if (sensorType.equals(type.affectedSensor()) && type.isActive(actuator.state())) {
                level += type.driftWhenActive();
            }
        }
        return level;
    }

    private double driftFor(RandomWalkSensor sensor) {
        String sensorType = sensor.id().split(":", 2) [0];
        double drift = 0;
        for (Actuator actuator: actuators.values()) {
            ActuatorType type = actuator.type();
            if (!sensorType.equals(type.affectedSensor()) || !type.isActive(actuator.state())) {
                continue;
            }
            OptionalDouble target = type.setpointOf(actuator.state());
            if (target.isPresent()) {
                double strength = Math.abs(type.driftWhenActive());
                drift += Math.max(-strength, Math.min(strength, 0.5 * (target.getAsDouble() - sensor.current())));
                continue;
            }
            drift += type.driftWhenActive();
        }
        return drift;
    }
}
