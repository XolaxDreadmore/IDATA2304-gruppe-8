package ntnu.bidata.iir.sensornode;

import ntnu.bidata.iir.common.Connection;
import ntnu.bidata.iir.common.Message;
import ntnu.bidata.iir.common.MessageType;
import ntnu.bidata.iir.common.Protocol;
import ntnu.bidata.iir.common.ProtocolException;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class NodeClient {
    private static final int SAMPLE_INTERVAL_SECONDS = 2;
    private static final int MAX_BACKLOG = 500;

    private record Reading(String sensorId, double value, long capturedAtMs) {}

    private final String host;
    private final SensorNode node;
    private final Object lock = new Object();
    private final Deque<Reading> backlog = new ArrayDeque<>();
    private Connection connection;

    NodeClient(String host, SensorNode node) {
        this.host = host;
        this.node = node;
    }

    void run() {
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, node.id() + "-timer");
            thread.setDaemon(true);
            return thread;
        });
        timer.scheduleAtFixedRate(guarded(this::sampleSensors), 1, SAMPLE_INTERVAL_SECONDS, TimeUnit.SECONDS);
        timer.scheduleAtFixedRate(guarded(this::heartbeat), Protocol.HEARTBEAT_INTERVAL_MS,
                Protocol.HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
        try {
            connectLoop();
        } finally {
            timer.shutdownNow();
        }
    }

    private void connectLoop() {
        long delayMs = 1000;
        while (!Thread.currentThread().isInterrupted()) {
            Connection current = null;
            try {
                current = Connection.connect(host, Protocol.PORT);
                current.setReadTimeout(Protocol.READ_TIMEOUT_MS);
                register(current);
                goOnline(current);
                delayMs = 1000;
                listen(current);
                System.out.println(node.id() + ": the broker closed the connection");
            } catch (IOException e) {
                System.out.println(node.id() + ": " + e.getClass().getSimpleName() + ": " + e.getMessage());
            } finally {
                goOffline(current);
            }
            System.out.println(node.id() + ": offline, retrying in " + delayMs / 1000 + " s");
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            delayMs = Math.min(delayMs * 2, Protocol.MAX_RECONNECT_DELAY_MS);
        }
    }
    private void register(Connection c) throws IOException {
        c.send(Message.of(MessageType.REGISTER, node.id(), String.join(Protocol.LIST_SEPARATOR, node.sensors().keySet()),
                String.join(Protocol.LIST_SEPARATOR, node.actuators().keySet())));
        while (true) {
            Message reply;
            try {
                reply = c.receive();
            } catch (ProtocolException e) {
                continue;
            }
            if (reply == null) {
                throw new IOException("the broker closed the connection during registration");
            }
            if (reply.type() == MessageType.OK) {
                return;
            }
            if (reply.type() == MessageType.ERROR) {
                throw new IOException("registration rejected: " + reply.arg(1));
            }
        }
    }

    private void goOnline(Connection c) {
        synchronized (lock) {
            connection = c;
            for (Actuator actuator : node.actuators().values()) {
                c.send(Message.of(MessageType.STATUS, actuator.id(), actuator.state()));
            }
            int flushed = 0;
            long now = System.currentTimeMillis();
            while (!backlog.isEmpty() && sendReading(c, backlog.peekFirst(),now)) {
                backlog.removeFirst();
                flushed++;
            }
            System.out.println(node.id() + ": online" + (flushed > 0 ? ", sent " + flushed + " buffered readings" : ""));
        }
    }

    private void goOffline(Connection c) {
        synchronized (lock) {
            if (connection == c) {
                connection = null;
            }
        }
        if (c != null) {
            c.close();
        }
    }

    private void sampleSensors() {
        long now = System.currentTimeMillis();
        for (Sensor sensor : node.sensors().values()) {
            Reading reading = new Reading(sensor.id(), node.read(sensor.id()), now);
            synchronized (lock) {
                if (connection != null && backlog.isEmpty() && sendReading(connection, reading, now)) {
                    continue;
                }
                backlog.addLast(reading);
                if (backlog.size() > MAX_BACKLOG) {
                    backlog.removeFirst();
                }
            }
        }
    }

    private boolean sendReading(Connection c, Reading reading, long nowMs) {
        String age = Long.toString(Math.max(0, nowMs - reading.capturedAtMs()));
        boolean sent = c.send(Message.of(MessageType.READING, reading.sensorId(),
                String.format(Locale.ROOT, "%.1f", reading.value()), age));
        if (!sent) {
            c.close();
        }
        return sent;
    }

    private void heartbeat() {
        synchronized (lock) {
            if (connection != null && !connection.send(Message.of(MessageType.PING))) {
                connection.close();
            }
        }
    }

    private void listen(Connection c) throws IOException {
        while (true) {
            Message m;
            try {
                m = c.receive();
            } catch (ProtocolException e) {
                System.out.println(node.id() + ": ignoring malformed message: " + e.getMessage());
                continue;
            }
            if (m == null) {
                return;
            }
            switch (m.type()) {
                case COMMAND -> onCommand(c, m);
                case ERROR -> System.out.println(node.id() + ": broker error: " + m.args());
                default -> { } // OK and PONG need no action
            }
        }
    }

    private void onCommand(Connection c, Message m) {
        try {
            String actuatorId = m.arg(0);
            List<Actuator> changed = node.applyCommand(actuatorId, m.arg(1));
            if (changed.isEmpty()) {
                System.out.println(node.id() + ": rejected command " + m.args());
                return;
            }
            for (Actuator actuator : changed) {
                System.out.println(node.id() + ": " + actuator.id() + " -> " + actuator.state());
                c.send(Message.of(MessageType.STATUS, actuator.id(), actuator.state()));
            }
        } catch (ProtocolException e) {
            System.out.println(node.id() + ": rejected command: " + e.getMessage());
        }
    }

    private static Runnable guarded(Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (RuntimeException e ){
                System.out.println("timer task failed: " + e);
            }
        };
    }
}

