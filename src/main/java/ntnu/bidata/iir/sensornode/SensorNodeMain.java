package ntnu.bidata.iir.sensornode;

import ntnu.bidata.iir.common.Protocol;

public final class SensorNodeMain {
    private SensorNodeMain() { }

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "localhost";
        String nodeId = args.length > 1 ? args[1] : "node1";
        String sensors = args.length > 2 ? args[2] : "temp:1,hum:1";
        String actuators = args.length > 3 ? args[3] : "fan:1,heater:1";

        SensorNode node = new SensorNode(nodeId,
                sensors.split(Protocol.LIST_SEPARATOR), actuators.split(Protocol.LIST_SEPARATOR));
        new NodeClient(host, node).run();
    }
}


