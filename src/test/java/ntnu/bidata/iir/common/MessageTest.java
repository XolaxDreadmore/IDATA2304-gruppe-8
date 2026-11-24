package ntnu.bidata.iir.common;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


public class MessageTest {
    @Test
    public void serializeAndParseRoundTrip() {
        Message m = Message.of(MessageType.READING, "temp:1", "21.5", "0");
        assertEquals("READING|temp:1|21.5|0", m.serialize());
        assertEquals(m, Message.parse(m.serialize()));
    }

    @Test
    public  void messageWithoutArguments() {
        assertEquals(MessageType.PING, Message.parse("PING").type());
    }

    @Test
    public void unknownTypeIsRejected() {
        assertThrows(ProtocolException.class, () -> Message.parse("DANCE|now"));
    }

    @Test
    public void emptyLineIsRejected() {
        assertThrows(ProtocolException.class, () -> Message.parse(""));
    }
}