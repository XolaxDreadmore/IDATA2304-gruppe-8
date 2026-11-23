package ntnu.bidata.iir.controlpanel;

import ntnu.bidata.iir.common.ActuatorType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


final class NodeState {
    private final String id;
    private final Map<String, SensorSeries> sensors = new LinkedHashMap<>();
    private final Map<String, String> actuatorStates = new LinkedHashMap<>();

    NodeState(String id, String[] sensorIds, String [] actuatorIds) {
        this.id = id;
        for (String sensorId : sensorIds) {
            sensors.put(sensorId, new SensorSeries(sensorId));
        }
        for (String actuatorId : actuatorIds) {
            actuatorStates.put(actuatorId, ActuatorType.defaultStateOf(actuatorId));
        }
    }

    String id() {return id;}

    List<SensorSeries> sensors() {return new ArrayList<>(sensors.values());}

    Map<String, String> ActuatorStates() {return actuatorStates;}

    /** @return a description if this reading is an anomaly */
    String applyReading(String sensorId, double value) {
        SensorSeries series = sensors.computeIfAbsent(sensorId,SensorSeries::new);
        String anomaly = AnomalyDetector.check(sensorId, value, series.latest());
        series.add(value, anomaly);
        return anomaly;
    }

    void applyStatus(String actuatorId, String state) {actuatorStates.put(actuatorId,state);}

    /** True while any alarm actuator of this node is on */
    boolean alarmActive() {
        return actuatorStates.entrySet().stream().anyMatch(e -> ActuatorType.find(e.getKey())
                .orElse(null)) == ActuatorType.ALARM && ActuatorType.isActiveState(e.getKey(), e.getValue());
    }

    boolean hasAnomaly() {return sensors.values().stream().anyMatch(s -> s.anomaly() != null);}
}
