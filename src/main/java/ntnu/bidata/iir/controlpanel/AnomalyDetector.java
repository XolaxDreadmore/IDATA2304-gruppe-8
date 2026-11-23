package ntnu.bidata.iir.controlpanel;

import ntnu.bidata.iir.common.SensorType;
import java.util.Locale;

/** flags suspicious sensor readings outside the normal range or a sudden jump */
public class AnomalyDetector {
    private AnomalyDetector() { }

    static String check(String sensorId, double value, Double previous) {
        SensorType type = SensorType.find(sensorId).orElse(null);
        if (type == null) {
            return null;
        }
        if (value < type.normalMin()) {
            return String.format(Locale.ROOT, "%s too low (%.1f < %.1f)", type.label(), value, type.normalMin());
        }
        if (value > type.normalMax()) {
            return String.format(Locale.ROOT, "%s too high (%.1f > %.1f)", type.label(), value, type.normalMax());
        }
        if (previous != null && Math.abs(value - previous) > type.maxJump()) {
            return String.format(Locale.ROOT, "%s jumped from %.1f to %.1f", type.label(), previous, value);
        }
        return null;
    }
}
