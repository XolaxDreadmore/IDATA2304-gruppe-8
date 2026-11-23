package ntnu.bidata.iir.broker;

record BrokerContext(Registry registry, AlarmControl alarms, FireSafety fireSafety, EntryGuard entryGuard,
        CommandRouter router, EventLog log) {
}
