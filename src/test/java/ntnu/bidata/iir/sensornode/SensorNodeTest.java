package ntnu.bidata.iir.sensornode;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SensorNodeTest {
    private static SensorNode node() {
        return new SensorNode("t", new String[] {"temp:1"}, new String[] {"heater:1", "fan:1"});
    }

    @Test
    public void blankSpecsAreIgnored() {
        SensorNode n = new SensorNode("t", new String[] {"", "temp:1"}, new String[] {""});
        assertTrue(n.read("temp:1") > -50);
    }

    @Test
    public void heaterWithSetpointDrivesTemoperatureTowardsTarget() {
        SensorNode n = node();
        n.command("heater:1", "SET:30");
        double first = n.read("temp:1");
        double last = first;
        double highest = first;
        for (int i = 0; i < 300; i++) {
            last = n.read("temp:1");
            highest = Math.max(highest, last);
        }
        assertTrue(last > first + 3, "temperature should rise towards the setpoint");
        // adds random noise to the simulation that it will allow some margin above the target.
        assertTrue(highest < 30 + 4, "temperature should stop being pushed once the target is reached");
    }

    @Test
    public void motionSwitchTurnsRoomLightsOnAndOff() {
        SensorNode n = new SensorNode("room", new String[]{"motion:1"}, new String[]{"motion:1", "light:1", "light:2", "fan:1"});
        assertEquals(0.0, n.read("motion:1"), 1e-9);

        var changed = n.applyCommand("motion:1", "ON");
        assertEquals(3, changed.size(), "motion switch and two lights");
        assertEquals(1.0, n.read("motion:1"), 1e-9);
        assertEquals("ON", n.actuators().get("light:1").state());
        assertEquals("ON", n.actuators().get("light:2").state());
        assertEquals("OFF", n.actuators().get("fan:1").state());

        n.applyCommand("motion:1", "OFF");
        assertEquals(0.0, n.read("motion:1"), 1e-9);
        assertEquals("OFF", n.actuators().get("light:1").state());
    }

    @Test
    public void rejectedCommandChangesNothing() {
        assertTrue(node().applyCommand("fan:1", "OPEN").isEmpty());
        assertTrue(node().applyCommand("radon:1", "ON").isEmpty());
    }
}
