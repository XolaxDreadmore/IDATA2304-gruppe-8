package ntnu.bidata.iir.broker;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import ntnu.bidata.iir.common.Connection;
import ntnu.bidata.iir.common.Protocol;

/** Entry point of the broker. Usage: BrokerMain [port] */
public final class BrokerMain {
    private BrokerMain() {
    }

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : Protocol.PORT;
        Registry registry = new Registry();
        EventLog log = new EventLog();
        AlarmControl alarms = new AlarmControl(registry, log);
        FireSafety fireSafety = new FireSafety(alarms, log);
        EntryGuard entryGuard = new EntryGuard(registry, alarms, log, System::currentTimeMillis);
        CommandRouter router = new CommandRouter(registry);
        BrokerContext context = new BrokerContext(registry, alarms, fireSafety, entryGuard, router, log);

        ScheduledExecutorService clock = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "entry-guard-clock");
            thread.setDaemon(true);
            return thread;
        });
        clock.scheduleAtFixedRate(() -> {
            try {
                entryGuard.tick();
            } catch (RuntimeException e) {
                System.out.println("entry guard tick failed: " + e);
            }
        }, 1, 1, TimeUnit.SECONDS);
        log.add("SYSTEM", "Broker started on port " + port);

        try (ServerSocket server = new ServerSocket(port);
                ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            System.out.println("Broker listening on port " + port);
            while (true) {
                Connection connection = new Connection(server.accept());
                pool.submit(new ClientHandler(connection, context));
            }
        }
    }
}
