package ntnu.bidata.iir.common;

/** All message types in the protocol. :) */
public enum MessageType {
    // sensor node -> broker
    REGISTER, // REGISTER|nodeId|sensors|actuators
    READING, // node->broker: READING|sensorId|value|ageMs broker->panel:
             // READING|nodeId|sensorId|value|ageMs
    STATUS, // node->broker: STATUS|actuatorId|state broker->panel:
            // STATUS|nodeId|actuatorId|state

    // control panel -> broker
    LOGIN, // LOGIN|panelId|role|password
    SUBSCRIBE, // SUBSCRIBE|nodeId
    LIST_NODES, // LIST_NODES
    COMMAND, // panel->broker: COMMAND|nodeSelector|actuatorSelector|action broker->node:
             // COMMAND|actuatorId

    ARM, // ARM|code arm the house (entrance motion then starts the entry countdown)
    DISARM, // DISARM|code cancel the countdown, or silence an intruder alarm
    GET_LOG, // GET_LOG ask for the event log (history, then live pushes).
             // Technician and above
    GET_DIAG, // GET_DIAG ask for the list of connected clients. Technician and above

    // any client -> broker, and the answer
    PING, // PING
    PONG, // PONG

    // broker -> anyone
    NODE_LIST, // NODE_LIST|id1, id2,...
    NODE_INFO, // NODE_INFO|nodeId|sensors|actuators (pushed when a node joins, and on login)
    NODE_GONE, // NODE_GONE|nodeId (pushed when a node disconnects)
    SECURITY, // SECURITY|state|secondsLeft state: DISARMED, ARMED, ENTRY, ALARM (pushed
              // on change, every second during ENTRY, and on login)
    LOG, // LOG|epochMillis|category|text one event log entry
    DIAG, // DIAG|kind|id|role|connectedSec|idleSec|detail one connected client
    OK, // OK or, after a group command, OK|delivered|skipped
    ERROR // ERROR|code|text
}
