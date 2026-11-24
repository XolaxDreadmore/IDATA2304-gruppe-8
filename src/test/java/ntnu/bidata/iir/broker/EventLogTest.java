package ntnu.bidata.iir.broker;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EventLogTest {
    @Test
    public void keepsOnlyTheNewestEntries() {
        EventLog log = new EventLog();
        for (int i = 0; i < EventLog.MAX_ENTRIES + 20; i++) {
            log.add("TEST", "entry " + i);
        }
        assertEquals(EventLog.MAX_ENTRIES, log.snapshot().size());
        assertEquals("entry 20", log.snapshot().get(0).text());
    }

    @Test
    public void messageNeverContainsTheFieldSeparator() {
        EventLog log = new EventLog();
        log.add("TEST", "a|b\nc");
        var message = log.snapshot().get(0).toMessage();
        assertEquals(3, message.args().size());
        assertEquals("a/b c", message.arg(2));
    }
}