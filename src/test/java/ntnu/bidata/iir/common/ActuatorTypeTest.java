package ntnu.bidata.iir.common;

import org.junit.Test;

import static org.junit.Assert.*;

public class ActuatorTypeTest {
    @Test
    public void findsTypeFromInstanceId() {
        assertEquals(ActuatorType.HEATER, ActuatorType.find("heater:2").orElseThrow());
        assertTrue(ActuatorType.find("toaster:1").isEmpty());
    }

    @Test
    public void firstStateIsTheDefaultAndInactive() {
        assertEquals("OFF", ActuatorType.FAN.defaultState());
        assertFalse(ActuatorType.FAN.isActive("OFF"));
        assertTrue(ActuatorType.FAN.isActive("ON"));
    }

    @Test
    public void setpointIsParsedAndRangeChecked() {
        assertEquals(22.5, ActuatorType.HEATER.parseSet("SET:22.5").orElseThrow(), 1e-9);
        assertTrue(ActuatorType.HEATER.parseSet("SET:99").isEmpty());
        assertTrue(ActuatorType.HEATER.parseSet("SET:abc").isEmpty());
        assertFalse(ActuatorType.FAN.accepts("SET:5"));
        assertTrue(ActuatorType.HEATER.accepts("SET:22"));
    }

    @Test
    public void invalidActionIsNotAccepted() {
        assertFalse(ActuatorType.DOOR.accepts("ON"));
        assertTrue(ActuatorType.DOOR.accepts("OPEN"));
    }
}
