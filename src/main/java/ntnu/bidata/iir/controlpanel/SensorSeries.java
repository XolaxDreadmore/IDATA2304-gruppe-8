package ntnu.bidata.iir.controlpanel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

final class SensorSeries {
    private static final int MAX_POINTS = 60;

    private final String id;
    private final Deque<Double> history = new ArrayDeque<>();
    private String anomaly; // null when the latest reading is normal

    SensorSeries(String id) {
        this.id = id;
    }

    String id() {
        return id;
    }

    void add(double value, String anomalyText) {
        history.addLast(value);
        if (history.size() > MAX_POINTS) {
            history.removeFirst();
        }
        this.anomaly = anomalyText;
    }

    Double latest() {
        return history.peekLast();
    }

    List<Double> history() {
        return new ArrayList<>(history);
    }

    /** @return description of the current anomaly, or null if the sensor looks normal */
    String anomaly() {
        return anomaly;
    }
}