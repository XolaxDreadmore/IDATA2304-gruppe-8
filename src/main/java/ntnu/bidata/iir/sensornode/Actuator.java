package ntnu.bidata.iir.sensornode;

import ntnu.bidata.iir.common.ActuatorType;

public class Actuator {
    private final String id; // e.g. "fan:1"
    private final ActuatorType type;
    private volatile String state;

    public Actuator(String id, ActuatorType type) {
        this.id = id;
        this.type = type;
        this.state = type.defaultState();
    }

    public String id() {
        return id;
    }

    public ActuatorType type() {
        return type;
    }

    public String state() {
        return state;
    }

    /**
     * Applies an action: a state of the type or a SET value inside the type's range.
     *
     * @return true if the action is valid for this type and was applied
     */
    public boolean apply(String action) {
        if (type.accepts(action)) {
            state = type.stateFor(action);
            return true;
        }
        return false;
    }
}
