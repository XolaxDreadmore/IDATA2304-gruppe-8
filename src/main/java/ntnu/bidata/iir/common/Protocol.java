package ntnu.bidata.iir.common;

/** Constants shared by every node. :) */
public final class Protocol {
    public static final int PORT = 5050;
    public static final String SEPARATOR = "|";
    public static final String LIST_SEPARATOR = ",";
    /** In a command target: "all nodes" or "all actuators". */
    public static final String WILDCARD = "*";

    /** Every client sends PING this often; the broker answers PONG. */
    public static final int HEARTBEAT_INTERVAL_MS = 3000;
    /**
     * A connection that stays silent this long (three missed heartbeats) is treated
     * as dead.
     */
    public static final int READ_TIMEOUT_MS = 9000;
    public static final int CONNECT_TIMEOUT_MS = 3000;
    /** Reconnect attempts back off 1 s, 2 s, 4 s ... up to this delay. */
    public static final int MAX_RECONNECT_DELAY_MS = 10000;

    public static final String ERR_BAD_REQUEST = "400";
    public static final String ERR_UNAUTHENTICATED = "401";
    public static final String ERR_FORBIDDEN = "403";
    public static final String ERR_NOT_FOUND = "404";
    public static final String ERR_CONFLICT = "409";

    private Protocol() {
    }
}
