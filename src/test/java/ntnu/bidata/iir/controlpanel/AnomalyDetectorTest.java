package ntnu.bidata.iir.controlpanel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class AnomalyDetectorTest {
    @Test
    public void normalValueIsFine() {
        assertNull(AnomalyDetector.check("temp:1", 21.0, 20.5));
    }

    @Test
    public void tooHighIsReported() {
        assertNotNull(AnomalyDetector.check("temp:1", 80.0, null));
    }

    @Test
    public void tooLowIsReported() {
        assertNotNull(AnomalyDetector.check("temp:1", -40.0, null));
    }

    @Test
    public void smokeAboveThresholdIsReported() {
        assertNotNull(AnomalyDetector.check("smoke:1", 8.0, 7.9));
    }

    @Test
    public void unknownSensorIsIgnored() {
        assertNull(AnomalyDetector.check("radon:1", 1000.0, null));
    }
}

