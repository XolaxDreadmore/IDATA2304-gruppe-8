package ntnu.bidata.iir;

import ntnu.bidata.iir.broker.BrokerMain;
import ntnu.bidata.iir.controlpanel.ControlPanelGuiMain;
import ntnu.bidata.iir.sensornode.SensorNodeMain;

public final class SmartHomeLauncher {
    private static final long STARTUP_DELAY_MS = 500;

    /** One row per room: node id, sensors, actuators. Add a room or device by editing this table. */
    private static final String[][] HOUSE = {
            {"attic",      "temp:1,hum:1,smoke:1,lux:1", "heater:1,humidifier:1,window:1,light:1,extractor:1,fire:1"},
            {"kitchen",    "temp:1,hum:1,smoke:1,lux:1,door:1,motion:1", "fan:1,light:1,door:1,extractor:1,fire:1,motion:1"},
            {"livingroom", "temp:1,smoke:1,lux:1,motion:1", "fan:1,cooler:1,light:1,light:2,extractor:1,fire:1,motion:1"},
            {"garage",     "temp:1,lux:1,door:1,motion:1", "door:1,lock:1,alarm:1,light:1,fan:1,extractor:1,motion:1"},
            {"maindoor",   "temp:1,lux:1,door:1,motion:1", "door:1,alarm:1,light:1,motion:1"},
    };

    private SmartHomeLauncher() { }

    public static void main(String[] args) throws Exception {
        startInBackground("broker", () -> BrokerMain.main(new String[0]));
        Thread.sleep(STARTUP_DELAY_MS);

        for (String[] room : HOUSE) {
            startInBackground(room[0], () -> SensorNodeMain.main(
                    new String[] {"localhost", room[0], room[1], room[2]}));
        }
        Thread.sleep(STARTUP_DELAY_MS);

        ControlPanelGuiMain.main(new String[0]);
    }

    /** Runs a blocking task on a daemon thread so it stops when the GUI closes. */
    private static void startInBackground(String name, ThrowingRunnable task) {
        Thread thread = new Thread(() -> {
            try {
                task.run();
            } catch (Exception e) {
                System.err.println(name + " stopped: " + e.getMessage());
            }
        }, name);
        thread.setDaemon(true);
        thread.start();
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}