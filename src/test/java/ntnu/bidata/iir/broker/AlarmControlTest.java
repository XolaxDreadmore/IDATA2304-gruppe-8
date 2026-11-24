package ntnu.bidata.iir.broker;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AlarmControlTest {
    private final AlarmControl alarms = new AlarmControl(new Registry(), new EventLog());

    @Test
    public void alarmStaysOnUntilEveryReasonIsGone() {
        alarms.raise("FIRE");
        alarms.raise("INTRUDER");
        alarms.clear("FIRE");
        assertTrue("the intruder alarm must not be silenced by the fire rule", alarms.active());
        alarms.clear("INTRUDER");
        assertFalse(alarms.active());
    }

    @Test
    public void clearingAReasonThatWasNeverRaisedChangesNothing() {
        alarms.clear("FIRE");
        assertFalse(alarms.active());
    }
}