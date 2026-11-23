package ntnu.bidata.iir.broker;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import ntnu.bidata.iir.common.Message;

/** Thread safe record of who is connected and who subscribes to which node. */
final class Registry {
    private final Map<String, ClientHandler> nodes = new ConcurrentHashMap<>();
    private final Map<String, ClientHandler> panels = new ConcurrentHashMap<>();
    private final Map<String, Set<ClientHandler>> subscriptions = new ConcurrentHashMap<>();

    /** @return false if a node with this id is already connected. */
    boolean registerNode(String id, ClientHandler handler) {
        return nodes.putIfAbsent(id, handler) == null;
    }

    /** @return false if a panel with this id is already connected. */
    boolean registerPanel(String id, ClientHandler handler) {
        return panels.putIfAbsent(id, handler) == null;
    }

    /** Sends a message to every logged-in panel. */
    void broadcastToPanels(Message message) {
        for (ClientHandler panel : panels.values()) {
            panel.send(message);
        }
    }

    /**
     * Tells every logged-in panel about a node, showing each panel only the sensors
     * its role may see.
     */
    void announceNode(ClientHandler node) {
        for (ClientHandler panel : panels.values()) {
            panel.send(node.nodeInfoFor(panel.viewerRole()));
        }
    }

    /** Every connected node and panel, for the diagnostics view. */
    List<ClientHandler> allClients() {
        List<ClientHandler> all = new java.util.ArrayList<>(nodes.values());
        all.addAll(panels.values());
        return all;
    }

    List<ClientHandler> allNodes() {
        return nodes.values().stream().toList();
    }

    ClientHandler node(String id) {
        return nodes.get(id);
    }

    List<String> nodeIds() {
        return nodes.keySet().stream().sorted().toList();
    }

    void subscribe(String nodeId, ClientHandler panel) {
        subscriptions.computeIfAbsent(nodeId, k -> ConcurrentHashMap.newKeySet()).add(panel);
    }

    /**
     * Sends a message to every panel subscribed to the given node (each panel
     * filters by its role).
     */
    void publish(String nodeId, Message message) {
        for (ClientHandler panel : subscriptions.getOrDefault(nodeId, Set.of())) {
            panel.deliverToViewer(message);
        }
    }

    void remove(ClientHandler handler) {
        nodes.values().remove(handler);
        panels.values().remove(handler);
        subscriptions.values().forEach(set -> set.remove(handler));
    }
}
