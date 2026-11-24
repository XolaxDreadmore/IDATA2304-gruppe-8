package ntnu.bidata.iir.broker;

import org.junit.Test;

import static org.junit.Assert.*;

public class EntryGuardTest {
    private final long[] now = {0};
    private final EventLog log = new EventLog();
    private final AlarmControl alarms = new AlarmControl(new Registry(), log);
    private final EntryGuard guard = new EntryGuard(new Registry(), alarms, log, () -> now[0]);

    private void motion(String node, double value) {
        guard.onReading(node, "motion:1", value, 0);
    }

    private void advance(int seconds) {
        for (int i = 0; i < seconds; i++) {
            now[0] += 1000;
            guard.tick();
        }
    }

    @Test
    public void wrongCodeDoesNotArm() {
        assertFalse(guard.arm("0000", "t").ok());
        assertEquals(EntryGuard.State.DISARMED, guard.state());
    }

    @Test
    public void motionWhileDisarmedDoesNothing() {
        motion("maindoor", 1);
        assertEquals(EntryGuard.State.DISARMED, guard.state());
    }

    @Test
    public void motionAtOtherNodeIsIgnored() {
        guard.arm("1234", "t");
        motion("garage", 1);
        assertEquals(EntryGuard.State.ARMED, guard.state());
    }

    @Test
    public void motionWhenArmedStartsCountdownAndAlarmFollowsWhenTimeRunsOut() {
        guard.arm("1234", "t");
        motion("maindoor", 1);
        assertEquals(EntryGuard.State.ENTRY, guard.state());
        assertEquals("30", guard.stateMessage().arg(1));
        advance(29);
        assertEquals(EntryGuard.State.ENTRY, guard.state());
        assertFalse(alarms.active());
        advance(1);
        assertEquals(EntryGuard.State.ALARM, guard.state());
        assertTrue(alarms.active());
    }

    @Test
    public void correctCodeInTimeCancelsTheCountdown() {
        guard.arm("1234", "t");
        motion("maindoor", 1);
        advance(10);
        assertTrue(guard.disarm("1234", "t").ok());
        advance(60);
        assertEquals(EntryGuard.State.DISARMED, guard.state());
        assertFalse(alarms.active());
    }

    @Test
    public void threeWrongCodesTriggerTheAlarmAtOnce() {
        guard.arm("1234", "t");
        motion("maindoor", 1);
        assertFalse(guard.disarm("1", "t").ok());
        assertFalse(guard.disarm("2", "t").ok());
        assertEquals(EntryGuard.State.ENTRY, guard.state());
        assertFalse(guard.disarm("3", "t").ok());
        assertEquals(EntryGuard.State.ALARM, guard.state());
        assertTrue(alarms.active());
    }

    @Test
    public void correctCodeSilencesAnIntruderAlarm() {
        guard.arm("1234", "t");
        motion("maindoor", 1);
        advance(30);
        assertTrue(alarms.active());
        assertTrue(guard.disarm("1234", "t").ok());
        assertEquals(EntryGuard.State.DISARMED, guard.state());
        assertFalse(alarms.active());
    }

    @Test
    public void armingWhileMotionIsStillOnDoesNotTrigger() {
        motion("maindoor", 1);
        guard.arm("1234", "t");
        motion("maindoor", 1);
        assertEquals(EntryGuard.State.ARMED, guard.state());
        motion("maindoor", 0);
        motion("maindoor", 1);
        assertEquals(EntryGuard.State.ENTRY, guard.state());
    }

    @Test
    public void oldBufferedMotionIsIgnored() {
        guard.arm("1234", "t");
        guard.onReading("maindoor", "motion:1", 1, 60_000);
        assertEquals(EntryGuard.State.ARMED, guard.state());
    }

    @Test
    public void cannotArmDuringAnAlarm() {
        guard.arm("1234", "t");
        motion("maindoor", 1);
        advance(30);
        assertFalse(guard.arm("1234", "t").ok());
    }
}

