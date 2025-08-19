package ug.campus;

import java.util.*;

public class Graph {
    public Map<Integer, Node> nodes = new HashMap<>();
    public Map<Integer, List<Edge>> adj = new HashMap<>();

    public void addNode(Node node) {
        nodes.put(node.id, node);
        adj.putIfAbsent(node.id, new ArrayList<>());
    }

    public void addEdge(Edge edge) {
        adj.get(edge.fromId).add(edge);
        if (edge.undirected) {
            adj.get(edge.toId).add(new Edge(edge.toId, edge.fromId, edge.distanceMeters, edge.speedKph, true));
        }
    }
}
