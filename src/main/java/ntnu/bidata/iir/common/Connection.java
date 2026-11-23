package ntnu.bidata.iir.common;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

/**
 * Line-based wrapper around a TCP socket that sends and receives Message
 * objects.
 * Used by the broker, sensor nodes and control panels so framing lives in one
 * place.
 */
public class Connection implements Closeable {
    private final Socket socket;
    private final BufferedReader in;
    private final PrintWriter out;

    public Connection(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
    }

    /** Opens a connection; gives up after Protocol.CONNECT_TIMEOUT_MS. */
    public static Connection connect(String host, int port) throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), Protocol.CONNECT_TIMEOUT_MS);
            return new Connection(socket);
        } catch (IOException e) {
            socket.close();
            throw e;
        }
    }

    /**
     * After this, receive() throws SocketTimeoutException when nothing arrives for
     * that long.
     */
    public void setReadTimeout(int millis) throws SocketException {
        socket.setSoTimeout(millis);
    }

    /**
     * Thread-safe: several threads may send on the same connection.
     * 
     * @return false if the message could not be written (the connection is broken)
     */
    public synchronized boolean send(Message message) {
        out.println(message.serialize());
        return !out.checkError(); // PrintWriter never throws; checkError reports a failed write
    }

    /**
     * Blocks until a line arrives. Returns null when the peer closed the
     * connection.
     * Throws ProtocolException for a malformed line (the caller decides how to
     * react) and
     * SocketTimeoutException when a read timeout is set and nothing arrived in
     * time.
     */
    public Message receive() throws IOException {
        String line = in.readLine();
        return line == null ? null : Message.parse(line);
    }

    public String remoteAddress() {
        return String.valueOf(socket.getRemoteSocketAddress());
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // nothing sensible to do
        }
    }
}
