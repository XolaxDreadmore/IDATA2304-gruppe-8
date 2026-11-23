package ntnu.bidata.iir.common;

import java.util.List;
import java.util.StringJoiner;
import java.util.regex.Pattern;

/**
 * One protocol message: a type plus string arguments.
 * Wire format: one line, fields separated by '|', first field is the type name.
 */
public record Message(MessageType type, List<String> args) {
    private static final Pattern SPLIT = Pattern.compile(Pattern.quote(Protocol.SEPARATOR));

    public Message {
        args = List.copyOf(args);
    }

    public static Message of(MessageType type, String... args) {
        return new Message(type, List.of(args));
    }

    /**
     * Returns argument i, or throws ProtocolException if the message is too short.
     */
    public String arg(int index) {
        if (index < 0 || index >= args.size()) {
            throw new ProtocolException(type + " is missing field " + index);
        }
        return args.get(index);
    }

    public String serialize() {
        StringJoiner joiner = new StringJoiner(Protocol.SEPARATOR);
        joiner.add(type.name());
        for (String arg : args) {
            if (arg.contains(Protocol.SEPARATOR) || arg.contains("/n") || arg.contains("/r")) {
                throw new ProtocolException("Illegal character in field: " + arg);
            }
            joiner.add(arg);
        }
        return joiner.toString();
    }

    public static Message parse(String line) {
        if (line == null || line.isBlank()) {
            throw new ProtocolException("Empty message");
        }
        String[] parts = SPLIT.split(line.strip(), -1);
        MessageType type;
        try {
            type = MessageType.valueOf(parts[0]);
        } catch (IllegalArgumentException e) {
            throw new ProtocolException("Unknown message type: " + parts[0]);
        }
        return new Message(type, List.of(parts).subList(1, parts.length));
    }
}
