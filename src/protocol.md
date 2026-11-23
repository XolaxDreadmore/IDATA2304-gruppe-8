# SHA Smart Home Protocol (version 1.2 - review as a group)

## 1. Introduction
This document describes the SHA protocol, an application-layer protocol on top of TCP that lets
simulated sensor/actuator nodes report data to, and be controlled from, control panels through a
central broker.

## 2. Terminology
| Term | Meaning |
|---|---|
| Broker | Central server. Accepts all connections, routes data and commands, enforces access levels. |
| Sensor node | Simulated device with several sensors and actuators. Identified by a unique node id (e.g. `node1`). |
| Control panel | User interface client (CLI or Swing GUI) used by a person with a role. |
| Sensor id / actuator id | `type:instance`, e.g. `temp:2`, `fan:1`. The type is the part before `:`. |
| Role | Access level of a panel: `ADMIN`, `OWNER`, `TECHNICIAN`, `GUEST`. |
| Subscription | A panel's request to receive READING/STATUS messages of one node. |
| Selector | A command target that may name many things: a node selector is `nodeId`, `a,b,c` or `*`; an actuator selector is `id`, `type:*` (e.g. `light:*`), `a,b` or `*`. |
| Heartbeat | `PING`/`PONG` messages that prove the other side is alive. |
| House code | A short code (demo: fixed, plain text) that arms and disarms the house. Not the same as a password. |
| Security state | `DISARMED`, `ARMED`, `ENTRY` (countdown after motion at the entrance) or `ALARM`. Kept by the broker, one state for the whole house. |
| Event log | The broker's list of the last 500 events (connections, logins, commands, errors, alarms). |

## 3. Transport protocol
TCP only. *Why:* commands such as "unlock door" must not be lost or reordered, and TCP also tells
us when a peer disappears. The cost (some latency/overhead for frequent readings) is acceptable
for readings every 2 seconds.

## 4. Port
TCP port **5050** on the broker.

## 5. Architecture
- Actors: broker, sensor nodes, control panels.
- The broker is the only server. Sensor nodes and panels are clients that open one TCP
  connection each to the broker; they never talk to each other directly.
- *Why a broker:* nodes and panels only need the broker's address (minimal configuration), and
  there is one place to route messages and check permissions.

## 6. Flow of information
1. Node connects and sends `REGISTER`; broker answers `OK` and pushes `NODE_INFO` to all panels.
2. Panel connects and sends `LOGIN`; broker answers `OK`, then sends `NODE_INFO` for every node already connected.
3. Panel sends `SUBSCRIBE|nodeId` for each node it wants data from (the GUI does this automatically on every `NODE_INFO`).
4. Node **pushes** a `READING` per sensor every 2 s; the broker forwards it to subscribers with the node id added.
5. Panel sends `COMMAND` with a node selector, an actuator selector and an action. The broker resolves the selectors, checks node, actuator, action and role for every target, forwards the command to each node and answers the panel (`OK`, or `OK|delivered|skipped` for a group command, or `ERROR`). A node applies it and pushes `STATUS`, which is forwarded to subscribers.
   A group command is *multicast* (`light:*` on one node, or a list) or *broadcast* (`*`/`*`). Targets the role may not control are skipped, not failed: `OK|3|2` means 3 actuators were commanded and 2 skipped. If nothing at all could be delivered the panel gets the first error instead.
6. **Heartbeat:** every 3 s each client sends `PING`, the broker answers `PONG` (and the other way round for waiting clients). A side that hears nothing for 9 s treats the connection as dead and closes it.
7. When a node's connection closes the broker sends `NODE_GONE` to all panels.
8. **Fire alarm rule (broker):** for every `READING` of a `smoke` sensor the broker compares the value with two limits. When a smoke value rises above 5.0 and no other smoke sensor is already above it, the broker sends `COMMAND|alarmId|ON` to every alarm actuator of every connected node; the nodes answer with `STATUS` as for any command. Readings older than 5 s (see `ageMs`, section 9) are ignored by this rule, so a buffered backlog can not trigger a false alarm. When the last smoke sensor that was above the limit falls below 2.0, the broker sends `COMMAND|alarmId|OFF` the same way. The alarms are started once per incident, so a person with the right role may still switch one off by hand while the smoke remains. If the node that was the source of the smoke disconnects, the alarms are switched off as well.
9. **Retained state:** the broker remembers the last `STATUS` of every actuator. When a panel subscribes to a node it first gets `OK`, then one `STATUS` per actuator, so a panel that joins late shows the true state instead of the start state.
10. **Entrance alarm (broker):** any panel may send `ARM|code`. While the state is `ARMED`, the *start* of motion (a reading changing from 0 to 1) at an entrance node (`maindoor`) changes the state to `ENTRY` and starts a 30 s countdown. The broker pushes `SECURITY|ENTRY|secondsLeft` to all panels every second. `DISARM|code` in time returns to `DISARMED`. If the time runs out, or 3 wrong codes are entered, the state becomes `ALARM` and the broker switches on every alarm actuator in the house; a correct `DISARM|code` silences them. Fresh motion only: readings older than 5 s (`ageMs`) are ignored, and arming while the motion switch is already ON does not trigger anything until it has been OFF and ON again.
11. **Shared alarms:** fire (section 8, 6.8) and intruder alarms use the same alarm actuators. The broker remembers *why* the alarms are on (`FIRE`, `INTRUDER`) and only switches them off when no reason is left, so a cleared fire cannot silence an intruder alarm and the other way round. A node that joins while an alarm is going gets its alarms switched on.
12. **Security state for new panels:** after `LOGIN` the broker sends `SECURITY|state|secondsLeft` (right after the `NODE_INFO`s), so a panel that joins during an alarm shows it at once.
13. **Event log and diagnostics (technician and above):** `GET_LOG` makes the broker send the log history as `LOG` messages, then `OK`, and from then on every new entry as it happens. `GET_DIAG` is answered with one `DIAG` per connected node and panel (with how long it has been connected and how long it has been silent), then `OK`.
Panels can also **pull** the node list with `LIST_NODES`.
Mix of push (readings, status, node announcements) and publish-subscribe (readings/status per node), plus request/response (commands, list).

## 7. Type of protocol
Connection-oriented (TCP) and **stateful**: the broker remembers per connection whether it is a node or a panel, its id, its role, its capabilities and its subscriptions. Messages other than `REGISTER`/`LOGIN` are rejected (401) before that.

## 8. Types and constants
- Separator between fields: `|`. Separator inside list fields: `,`.
- Ids have the form `type:instance`, e.g. `light:2`. The type decides everything below.
- **Actuator types** (each state is also the action that sets it; the first state is the start state):

| Type | States | Access level | Simulated effect while active |
|---|---|---|---|
| `fan` | OFF, ON | basic | temperature falls |
| `light` | OFF, ON | basic | the node's `lux` sensor rises by 300 per light that is on |
| `extractor` | OFF, ON | basic | smoke falls (ventilation) |
| `heater` | OFF, ON, `SET:<n>` (5-35 °C, default 22) | standard | temperature moves toward the target and holds it |
| `cooler` | OFF, ON, `SET:<n>` (5-35 °C, default 20) | standard | temperature moves toward the target and holds it |
| `humidifier` | OFF, ON | standard | humidity rises |
| `window` | CLOSED, OPEN | standard | none |
| `door` | CLOSED, OPEN | security (closing allowed from standard) | none |
| `lock` | LOCKED, UNLOCKED | security (locking allowed from standard) | none |
| `alarm` | OFF, ON | security (reset OFF allowed from standard) | none (also switched by the fire alarm rule, section 6) |
| `motion` | OFF, ON | basic | **Simulation helper**: the test switch of the room's motion sensor. ON = movement; the node then turns on every `light` in the same node (OFF turns them off) and reports `STATUS` for each |
| `fire` | OUT, BURNING | security | smoke rises. **Simulation helper**, not a real device: starts a test fire in a room |

- **Setpoint actions:** a device with a range accepts `SET:<number>`, e.g. `SET:22.5`. The number must be inside the range. The node reports the new state as `STATUS|heater:1|SET:22.5`. While a setpoint is active the simulated device steers the temperature toward the target and holds it there, like a thermostat.
- **Sensor types:** `temp` (°C), `hum` (%), `lux` (light level in lux: a steady base value of 100, plus 300 for every `light` of the same node that is ON, so it only changes when a light is switched), `smoke` (%), `motion` (0 = none, 1 = movement, follows the `motion` switch of the same instance number, e.g. `motion:1` sensor <- `motion:1` switch), `door` (door contact: 0 = closed, 1 = open, follows the `door` actuator with the same instance number; shown as CLOSED/OPEN in the panel). A `door` *sensor* and a `door` *actuator* are different things that share the name: the sensor reports, the actuator is commanded. Smoke, motion and door sensors are *not visible to guests*; they only get the comfort sensors temperature, humidity and light level (see below).
- **Roles and rights.** Rank: ADMIN/OWNER 3, TECHNICIAN 2, GUEST 1. An action needs a rank at least the actuator's access level, but security devices have a lower level for the *safe* action (reset an alarm, close a door, lock a lock). Raising an alarm or opening/unlocking needs security level.

| Role | fan, light, extractor | heater, cooler, humidifier, window | alarm OFF / door CLOSED / lock LOCKED | alarm ON / door OPEN / lock UNLOCKED | Sees smoke, motion, door sensors and all actuators |
|---|---|---|---|---|---|
| ADMIN, OWNER | yes | yes | yes | yes | yes |
| TECHNICIAN | yes | yes | yes | **no** (403) | yes |
| GUEST | yes | no (403) | no (403) | no (403) | **no** |

  Everyone logged in may arm and disarm with the house code (a guest has to be able to get in). **Event log and diagnostics** (`GET_LOG`, `GET_DIAG`): ADMIN, OWNER and TECHNICIAN; a guest gets `403`.

  The broker removes sensors *and actuators* the role may not see from `NODE_INFO` and drops their `READING`s and `STATUS`es (also the replay when a panel subscribes), so a guest panel never receives them. A guest only sees the actuators it may control (fan, light, extractor and the motion test switch): doors, locks, alarms, heaters and their state are hidden. A command to a hidden actuator still gets `403`. A type the broker does not know is treated as a security device.
- **Adding a new device type:** one new line in `ActuatorType` or `SensorType` (see README). No change to the message format is needed, because types are only part of the ids.
- Error codes: `400` bad request (malformed message, or an action the actuator type does not accept), `401` not logged in or wrong password, `403` forbidden for this role, `404` unknown node or actuator, `409` id already in use / already registered. Actuator ids and selectors may contain `*`.

## 9. Message format
Plain text, UTF-8, **one message per line** (`\n`), fields separated by `|`, first field is the message type. Fields must not contain `|`, `\r` or `\n`. *Why:* human readable (easy to debug with netcat), trivial to parse, new types and fields are easy to add.

| Message | Sent by -> to | Format |
|---|---|---|
| REGISTER | node -> broker | `REGISTER\|nodeId\|sensors\|actuators` (lists comma separated) |
| LOGIN | panel -> broker | `LOGIN\|panelId\|role\|password` |
| SUBSCRIBE | panel -> broker | `SUBSCRIBE\|nodeId` |
| LIST_NODES | panel -> broker | `LIST_NODES` |
| COMMAND | panel -> broker | `COMMAND\|nodeSelector\|actuatorSelector\|action` (selectors: section 2) |
| COMMAND | broker -> node | `COMMAND\|actuatorId\|action` |
| READING | node -> broker | `READING\|sensorId\|value\|ageMs` (decimal with dot, e.g. `21.5`; `ageMs` = how old the measurement is, `0` when live, larger when it was buffered during an outage; may be omitted = 0) |
| READING | broker -> panel | `READING\|nodeId\|sensorId\|value\|ageMs` |
| STATUS | node -> broker | `STATUS\|actuatorId\|state` |
| STATUS | broker -> panel | `STATUS\|nodeId\|actuatorId\|state` |
| NODE_INFO | broker -> panel | `NODE_INFO\|nodeId\|sensors\|actuators` |
| NODE_GONE | broker -> panel | `NODE_GONE\|nodeId` |
| NODE_LIST | broker -> panel | `NODE_LIST\|id1,id2,...` |
| ARM | panel -> broker | `ARM\|code` (answer `OK`, or `ERROR\|401` wrong code, `ERROR\|409` if an alarm or countdown is running) |
| DISARM | panel -> broker | `DISARM\|code` (answer `OK`, or `ERROR\|401\|Wrong code (n attempts left)`) |
| SECURITY | broker -> panel | `SECURITY\|state\|secondsLeft` (`secondsLeft` is 0 unless the state is `ENTRY`). Pushed on every change, every second during `ENTRY`, and after login |
| GET_LOG | panel -> broker | `GET_LOG` (technician and above) |
| LOG | broker -> panel | `LOG\|epochMillis\|category\|text` (categories: SYSTEM, CONNECT, AUTH, COMMAND, SECURITY, ALARM, FIRE, NETWORK, ERROR) |
| GET_DIAG | panel -> broker | `GET_DIAG` (technician and above) |
| DIAG | broker -> panel | `DIAG\|kind\|id\|role\|connectedSec\|idleSec\|detail` (`kind` is `NODE` or `PANEL`) |
| PING / PONG | both directions | `PING` is answered with `PONG` |
| OK | broker -> client | `OK`, or `OK\|delivered\|skipped` as the answer to a group command |
| ERROR | broker -> client | `ERROR\|code\|text` |

## 10. Errors and how nodes react
- **Malformed line / unknown type / missing field (broker):** reply `ERROR|400|...`, keep the connection open.
- **Message not allowed for this client type (e.g. a node sends SUBSCRIBE):** `ERROR|400`.
- **Not registered/logged in:** `ERROR|401`. **Wrong password:** `ERROR|401`, connection stays open for a retry.
- **Role not allowed to control the actuator:** `ERROR|403`, nothing is forwarded.
- **Unknown node or actuator:** `ERROR|404`.
- **Duplicate node/panel id:** `ERROR|409`.
- **Action not valid for the actuator type** (e.g. `OPEN` for a fan): `ERROR|400|fan:1 does not accept OPEN (allowed: OFF/ON)` from the broker, nothing is forwarded. A node that still receives an invalid command ignores and logs it.
- **Panel/node receives a malformed line:** logged and ignored.
- **Wrong house code:** `ERROR|401|Wrong code (2 attempts left)`. While the state is `ARMED` or `ENTRY` the third wrong code sets the state to `ALARM`. A wrong code is never written to the log.
- **ARM during `ENTRY` or `ALARM`:** `ERROR|409`. **Log or diagnostics for a guest:** `ERROR|403`.
- **Invalid value** (`SET:99` outside the range, `SET:abc`, `SET` on a fan): `ERROR|400` from the broker, nothing is forwarded.
- **Connection lost or silent (broker):** if a client sends nothing (not even `PING`) for 9 s, or a write fails, the broker closes the connection, removes the client and tells panels (`NODE_GONE`).
- **Connection lost or silent (node):** the node keeps sampling its sensors, stores the readings in a backlog (max 500, oldest dropped first) and reconnects with back-off 1, 2, 4, 8, 10, 10... s. After `REGISTER` it reports `STATUS` for all actuators and sends the backlog with its real `ageMs`. A new connection with the same id is accepted once the broker has noticed the old one is dead; until then the node gets `409` and retries.
- **Connection lost or silent (panel):** the panel shows "DISCONNECTED - trying to reconnect...", reconnects with the same back-off, logs in again and receives all nodes again. Commands sent while offline are *not* queued; the panel reports "NOT SENT". A wrong password does not retry.

## 11. Realistic scenario
A guest at home wants more air; the owner later checks the room.
1. `node1` (temp:1, hum:1 / fan:1, heater:1, door:1) connects: `REGISTER|node1|temp:1,hum:1|fan:1,heater:1,door:1` -> `OK`.
2. Guest panel logs in: `LOGIN|tablet|GUEST|guest123` -> `OK`, then `NODE_INFO|node1|...`, panel replies `SUBSCRIBE|node1` -> `OK`.
3. Every 2 s: node `READING|temp:1|21.4`, broker to panel `READING|node1|temp:1|21.4`; the GUI draws the chart.
4. Guest presses fan ON: `COMMAND|node1|fan:1|ON` -> broker `COMMAND|fan:1|ON` to node -> node `STATUS|fan:1|ON` -> panels `STATUS|node1|fan:1|ON`. The state label turns green.
5. Guest tries the door: the buttons are disabled in the GUI; a raw `COMMAND|node1|door:1|OPEN` gets `ERROR|403|Role GUEST may not control door:1`.
6. The heater is left on, temperature passes 30 degrees: the panel's anomaly detector flags `temp:1` red and logs it.

**Scenario 2 - someone comes home at night.** The house is armed (`ARM|1234` -> `OK`, all panels get `SECURITY|ARMED|0`).
1. The entrance motion sensor reports `READING|motion:1|1|0`. The broker sees a fresh rising edge at `maindoor`, sets `ENTRY` and pushes `SECURITY|ENTRY|30` to every panel; the panels show "ENTRY DETECTED - enter the code within 30 s".
2. Every second `SECURITY|ENTRY|29`, `SECURITY|ENTRY|28`...
3a. The owner types the code: `DISARM|1234` -> `OK`, then `SECURITY|DISARMED|0`. Nothing else happens.
3b. Nobody types a code: at 0 the broker sends `SECURITY|ALARM|0` and `COMMAND|alarm:1|ON` to every node with an alarm; each node answers `STATUS|alarm:1|ON`. Later `DISARM|1234` gives `COMMAND|alarm:1|OFF` to the same alarms and `SECURITY|DISARMED|0`.
4. A technician's panel receives `LOG|...|SECURITY|Motion at maindoor/motion:1 while armed...` and the following entries live.

## 12. Reliability
Provided by TCP (ordering, retransmission). No application-level acknowledgement of commands beyond `OK` from the broker and the node's `STATUS`. On top of that: heartbeat (3 s) and read timeout (9 s) to detect half-open or frozen peers that TCP alone would not report, automatic reconnect with back-off, buffering of readings during outages, retained actuator state, and `ageMs` so late data can be told from live data. A frozen broker (process stopped) is detected by the nodes after 9 s.

## 13. Security
The house code (`ARM`/`DISARM`) is a fixed value held by the broker and travels in plain text, like the passwords. A wrong code is counted but never logged; three wrong codes raise the alarm, which limits guessing. The event log is only sent to technician, owner and admin panels.

Role-based access control enforced by the broker (see section 8). Passwords are fixed per role and sent in plain text; the connection is not encrypted. This is a deliberate simplification for the first version. *Planned extra:* TLS (`SSLSocket`) and per-user accounts.

## 14. Assumptions
- Node ids are chosen by whoever starts the node and must be unique while connected.
- Every actuator has a fixed list of states per type (section 8); a door or window is `CLOSED`/`OPEN`, a lock `LOCKED`/`UNLOCKED`.
- A node starts every actuator in the first state of its type; panels assume this until a `STATUS` arrives.
- Heater, fan, cooler and humidifier change the simulated temperature or humidity of their own node (section 8).
- Guests only see the sensor types marked `guestVisible`: temperature, humidity and light level (not smoke, motion or door), and only the actuators they may control. As a consequence a guest panel does not show the state of the fire or intruder alarm; it does still show the house security state (`SECURITY`) and the entry countdown. This is decided per sensor *type*, not per individual sensor.
- Anomaly detection runs in the control panel (range and jump rules per sensor type).
- The fire alarm rule runs in the broker (not in the nodes) because the alarms sit on other nodes than the smoke sensors. Its limits (5.0 on, 2.0 off) are constants in `FireSafety`.
- A node may report several `STATUS` messages for one command (the motion switch also changes the lights). Panels simply apply each of them.
- The simulated temperature slowly returns to its start value (21 °C) when nothing heats or cools; a heater left ON still pushes the room above 30 °C, which the panel flags as an anomaly until a fan or cooler is switched on.
- Smoke in the simulation settles back to a low baseline on its own; only a `fire` actuator in the same node keeps it up, and an `extractor` clears it faster.
- A node's actuator state after the broker restarts is re-sent by the node (`STATUS` after `REGISTER`); the broker's retained state is kept only while it runs.
- A group command is not atomic: some targets can succeed while others are skipped; the reply only counts them.
- The entrance is protected by the broker, not by a node, because the alarms are on other nodes than the motion sensor. Which nodes count as entrance nodes (`maindoor`), the 30 s entry delay, the 3 attempts and the house code are constants in `EntryGuard` and `Authenticator`.
- The security state is kept in memory only: a restarted broker starts `DISARMED` and nodes that reconnect are not re-armed.
- Motion is simulated with the `motion` switch (section 8); a real sensor would simply report 1 while it sees movement.
- The event log keeps the last 500 entries in memory and is lost when the broker stops.
