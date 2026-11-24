# Smart Home Automation (IDATA2304)
Packages under `src/main/java/ntnu/bidata/iir/`: `common`, `broker`, `sensornode`, `controlpanel`.
Protocol: see `protocol.md`.

## Run everything from IDE
Run `SmartHomeLauncher`. Demo logins:
OWNER `owner123`, ADMIN `admin123`, TECHNICIAN `tech123`, GUEST `guest123`, HOUSE SECURITY CODE `1234`.

## Run the parts separately (also work across computers)
- `BrokerMain`
- `SensorNodeMain [host] [nodeId] [sensors] [actuators]` e.g. `localhost node3 temp:1,hum:1 fan:1,heater:1`
- `ControlPanelGuiMain` (GUI) or `ControlPanelMain [host] [panelId] [role] [password]` (CLI)
