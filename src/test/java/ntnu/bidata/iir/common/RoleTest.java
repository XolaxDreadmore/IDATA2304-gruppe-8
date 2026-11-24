package ntnu.bidata.iir.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RoleTest {
    @Test
    public void ownerMayDoEverything() {
        assertTrue(Role.OWNER.mayControl("door:1", "OPEN"));
        assertTrue(Role.OWNER.mayControl("alarm:1", "ON"));
    }

    @Test
    public void technicianMayResetButNotRaiseSecurity() {
        assertTrue(Role.TECHNICIAN.mayControl("alarm:1", "OFF"));
        assertFalse(Role.TECHNICIAN.mayControl("alarm:1", "ON"));
        assertTrue(Role.TECHNICIAN.mayControl("door:1", "CLOSED"));
        assertFalse(Role.TECHNICIAN.mayControl("door:1", "OPEN"));
        assertFalse(Role.TECHNICIAN.mayControl("lock:1", "UNLOCKED"));
        assertTrue(Role.TECHNICIAN.mayControl("heater:1", "SET:22"));
    }

    @Test
    public void guestOnlyBasicDevices() {
        assertTrue(Role.GUEST.mayControl("fan:1", "ON"));
        assertTrue(Role.GUEST.mayControl("light:2", "ON"));
        assertFalse(Role.GUEST.mayControl("heater:1", "ON"));
        assertFalse(Role.GUEST.mayControl("door:1", "CLOSED"));
    }

    @Test
    public void guestDoesNotSeeSmokeSensor() {
        assertFalse(Role.GUEST.maySee("smoke:1"));
        assertTrue(Role.GUEST.maySee("temp:1"));
        assertTrue(Role.TECHNICIAN.maySee("smoke:1"));
    }

    @Test
    public void unknownActuatorTypeIsTreatedAsSecurity() {
        assertFalse(Role.GUEST.mayControl("mystery:1", "ON"));
    }

    @Test
    void onlyTechnicianAndAboveMayViewTheLog() {
        assertTrue(Role.ADMIN.mayViewLog());
        assertTrue(Role.OWNER.mayViewLog());
        assertTrue(Role.TECHNICIAN.mayViewLog());
        assertFalse(Role.GUEST.mayViewLog());
    }
}
