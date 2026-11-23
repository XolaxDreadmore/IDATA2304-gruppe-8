package ntnu.bidata.iir.common;

/**
 * Access levels of a control panel. Decides which actuators a panel may command
 * and which sensors it may see.
 * 
 * Each actuator type has an access level (see ActuatorType.Access) and each
 * role has a rank;
 * a role may control an actuator when its rank is at least the level needed for
 * the action:
 * ADMIN, OWNER (3): everything, including doors, locks and alarms
 * TECHNICIAN (2): everything except raising security devices. It may reset an
 * alarm and close or lock a door, but not open or unlock one.
 * GUEST (1): only basic comfort devices (fan, light, extractor), and only the
 * comfort sensors (temperature, humidity, light level). A guest does not even
 * see the other actuators (doors, locks, alarms, heaters...) or their state.
 * Actuator types build does not know are treated as security devices.
 */
public enum Role {
    ADMIN(3), OWNER(3), TECHNICIAN(2), GUEST(1);

    private final int rank;

    Role(int rank) {
        this.rank = rank;
    }

    /**
     * @param actuatorId for example "fan:1"; only the part before ':' (the type)
     *                   matters
     * @param action     the requested state, for example "ON", "OFF" or "SET:22"
     */
    public boolean mayControl(String actuatorId, String action) {
        int required = ActuatorType.find(actuatorId)
                .map(type -> type.requiredAccess(action).rank())
                .orElse(ActuatorType.Access.SECURITY.rank());
        return rank >= required;
    }

    /**
     * Whether this role may see readings of the given sensor. Unknown sensor types
     * are hidden from guests.
     */
    public boolean maySee(String sensorId) {
        return rank >= TECHNICIAN.rank || SensorType.find(sensorId).map(SensorType::guestVisible).orElse(false);
    }

    /**
     * Whether this role may see an actuator and its state. Technician and above see
     * every actuator; a guest only sees the devices it is allowed to control, so
     * doors, locks, alarms and heating stay hidden.
     * Unknown actuator types are hidden from guests.
     */
    public boolean maySeeActuator(String actuatorId) {
        return rank >= TECHNICIAN.rank
                || ActuatorType.find(actuatorId).map(type -> type.access().rank() <= rank).orElse(false);
    }

    /**
     * Whether this role may read the broker's event log and diagnostics (technician
     * and above, not guests).
     */
    public boolean mayViewLog() {
        return rank >= TECHNICIAN.rank;
    }

    /** Case-insensitive parse; throws ProtocolException for an unknown role. */
    public static Role parse(String text) {
        try {
            return valueOf(text.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ProtocolException("Unknown role: " + text);
        }
    }
}
