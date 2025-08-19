package ug.campus;

import java.util.*;

public class RouteTester {
    public static void main(String[] args) throws Exception {
        Graph graph = new Graph();
        Main.loadNodes(graph, "data/nodes.csv");
        Main.loadEdges(graph, "data/edges.csv");
        RouteFinder finder = new RouteFinder(graph);
        int ecobank = findNodeId(graph, "Ecobank");
        int balme = findNodeId(graph, "Balme Library");
        System.out.println("Ecobank id=" + ecobank + ", Balme id=" + balme);
        List<Integer> pathD = finder.dijkstra(ecobank, balme);
        List<Integer> pathA = finder.aStar(ecobank, balme);
        System.out.println("Dijkstra path: " + pathD);
        System.out.printf("Dijkstra dist=%.1f m\n", finder.totalDistance(pathD));
        System.out.println("A* path: " + pathA);
        System.out.printf("A* dist=%.1f m\n", finder.totalDistance(pathA));
    }

    static int findNodeId(Graph graph, String name) {
        for (Node node : graph.nodes.values()) {
            if (node.name != null && node.name.equalsIgnoreCase(name)) return node.id;
        }
        return -1;
    }
}
