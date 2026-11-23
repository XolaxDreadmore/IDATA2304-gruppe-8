package ntnu.bidata.iir.broker;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import ntnu.bidata.iir.common.Message;
import ntnu.bidata.iir.common.MessageType;
import ntnu.bidata.iir.common.Protocol;

/**
 * The broker's event log: the last MAX_ENTRIES thigs that happened
 * (connections, logins, commands, errors, alarms). Panels that may view it get
 * the history and then every new entry as it happens.
 * Passwords and codes are never written to the log.
 */
final class EventLog {
    static final int MAX_ENTRIES = 500;

    /** One log line. */
    record Entry(long time, String category, String text) {
        Message toMessage() {
            return Message.of(MessageType.LOG, String.valueOf(time), category,
                    text.replace(Protocol.SEPARATOR, "/").replace('\n', ' ').replace('\r', ' '));
        }
    }

    private final Deque<Entry> entries = new ArrayDeque<>();
    private final List<ClientHandler> watchers = new CopyOnWriteArrayList<>();

    /**
     * Records an event, keeps only the newest MAX_ENTRIES and pushes the entry to
     * everyone watching.
     */
    void add(String category, String text) {
        Entry entry = new Entry(System.currentTimeMillis(), category, text);
        synchronized (this) {
            entries.addLast(entry);
            while (entries.size() > MAX_ENTRIES) {
                entries.removeFirst();
            }
        }
        for (ClientHandler watcher : watchers) {
            watcher.send(entry.toMessage());
        }
    }

    /** Oldest first. */
    synchronized List<Entry> snapshot() {
        return List.copyOf(entries);
    }

    /**
     * Starts pushing new entries to this panel; returns the history it should be
     * sent first.
     */
    synchronized List<Entry> watch(ClientHandler panel) {
        if (!watchers.contains(panel)) {
            watchers.add(panel);
        }
        return List.copyOf(entries);
    }

    void unwatch(ClientHandler panel) {
        watchers.remove(panel);
    }
}
