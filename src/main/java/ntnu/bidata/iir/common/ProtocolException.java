package ntnu.bidata.iir.common;

/** Thrown when a received line does not follow the protocol. :) */
public class ProtocolException extends RuntimeException {
    public ProtocolException(String message) {
        super(message);
    }
}
