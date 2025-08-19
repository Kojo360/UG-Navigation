package ug.campus;

import java.io.*;
import java.util.*;

public class BatchRouteTester {
    public static void main(String[] args) throws Exception {
        Graph graph = new Graph();
        Main.loadNodes(graph, "data/nodes.csv");
        Main.loadEdges(graph, "data/edges.csv");
        RouteFinder finder = new RouteFinder(graph);

        List<String[]> pairs = Arrays.asList(
            new String[]{"Ecobank", "Balme Library"},
            new String[]{"Balme Library", "Ecobank"},
            new String[]{"SG-SSB", "Balme Library"},
            new String[]{"Total", "Balme Library"},
            new String[]{"Legon Police Station", "Balme Library"},
            new String[]{"Hall Library", "Balme Library"},
            new String[]{"CalBank", "Ecobank"},
            new String[]{"GCB Bank", "Balme Library"}
        );

        File out = new File("data/route_test_results.csv");
        try (PrintWriter pw = new PrintWriter(new FileWriter(out))) {
            pw.println("srcName,srcId,destName,destId,algorithm,distanceMeters,path");
            for (String[] p : pairs) {
                String srcName = p[0];
                String destName = p[1];
                int srcId = findNodeId(graph, srcName);
                int destId = findNodeId(graph, destName);
                if (srcId == -1 || destId == -1) {
                    pw.printf("%s,%d,%s,%d,%s,%s,%s\n", srcName, srcId, destName, destId, "NA", "", "");
                    continue;
                }

                List<Integer> dpath = finder.dijkstra(srcId, destId);
                double ddist = dpath.isEmpty() ? -1.0 : finder.totalDistance(dpath);
                pw.printf("%s,%d,%s,%d,%s,%.1f,%s\n", srcName, srcId, destName, destId, "Dijkstra", ddist, pathToString(dpath));

                List<Integer> apath = finder.aStar(srcId, destId);
                double adist = apath.isEmpty() ? -1.0 : finder.totalDistance(apath);
                pw.printf("%s,%d,%s,%d,%s,%.1f,%s\n", srcName, srcId, destName, destId, "AStar", adist, pathToString(apath));
            }
        }

        System.out.println("Wrote data/route_test_results.csv");
    }

    static int findNodeId(Graph graph, String name) {
        for (Node node : graph.nodes.values()) {
            if (node.name != null && node.name.equalsIgnoreCase(name)) return node.id;
        }
        return -1;
    }

    static String pathToString(List<Integer> path) {
        if (path == null || path.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < path.size(); i++) {
            if (i > 0) sb.append("|");
            sb.append(path.get(i));
        }
        return sb.toString();
    }
}
