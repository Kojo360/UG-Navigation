package ug.campus;

import java.io.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        Graph graph = new Graph();
        loadNodes(graph, "data/nodes.csv");
        loadEdges(graph, "data/edges.csv");
        RouteFinder finder = new RouteFinder(graph);
        Scanner sc = new Scanner(System.in);
        System.out.print("Enter source name: ");
        String srcName = sc.nextLine();
        System.out.print("Enter destination name: ");
        String destName = sc.nextLine();
        System.out.print("Enter landmark keyword (optional): ");
        String keyword = sc.nextLine();
        int src = findNodeId(graph, srcName);
        int dest = findNodeId(graph, destName);
        if (src == -1 || dest == -1) {
            System.out.println("Invalid source or destination name.");
            return;
        }
        List<List<Integer>> routes;
        if (!keyword.isEmpty()) {
            List<Integer> route = finder.routeWithLandmark(src, dest, keyword);
            routes = new ArrayList<>();
            if (!route.isEmpty()) {
                routes.add(route);
            }
        } else {
            routes = finder.kAlternatives(src, dest, 3);
        }
        
        // Filter out empty routes
        routes.removeIf(List::isEmpty);
        
        if (routes.isEmpty()) {
            System.out.println("No routes found between the specified locations.");
            return;
        }
        
        routes = finder.sortRoutes(routes);
        for (int i = 0; i < routes.size(); i++) {
            List<Integer> path = routes.get(i);
            if (path.isEmpty()) continue;
            System.out.printf("Route %d: ", i+1);
            for (int j = 0; j < path.size(); j++) {
                int id = path.get(j);
                String nodeName = graph.nodes.get(id).name;
                if (nodeName == null || nodeName.trim().isEmpty()) {
                    nodeName = "Location " + id;
                }
                System.out.print(nodeName);
                if (j < path.size() - 1) System.out.print(" -> ");
            }
            System.out.println();
            System.out.printf("Distance: %.1f m, Time: %.1f min\n", finder.totalDistance(path), finder.totalTime(path)/60);
            System.out.println();
        }
    }

    static void loadNodes(Graph graph, String file) throws Exception {
        BufferedReader br = new BufferedReader(new FileReader(file));
        String line = br.readLine();
        int nodeCounter = 1;
        while ((line = br.readLine()) != null) {
            String[] t = line.split(",");
            int id = nodeCounter++;  // Use sequential IDs instead of parsing node/xxx format
            String name = t[1];
            double lat = Double.parseDouble(t[2]);
            double lon = Double.parseDouble(t[3]);
            String type = t[4];
            graph.addNode(new Node(id, name, lat, lon, type));
        }
        br.close();
    }

    static void loadEdges(Graph graph, String file) throws Exception {
        BufferedReader br = new BufferedReader(new FileReader(file));
        String line = br.readLine();
        while ((line = br.readLine()) != null) {
            String[] t = line.split(",");
            int fromId = Integer.parseInt(t[0]);
            int toId = Integer.parseInt(t[1]);
            double dist = Double.parseDouble(t[2]);
            double speed = Double.parseDouble(t[3]);
            boolean undirected = t[4].equals("1");
            graph.addEdge(new Edge(fromId, toId, dist, speed, undirected));
        }
        br.close();
    }

    static int findNodeId(Graph graph, String name) {
        for (Node node : graph.nodes.values()) {
            if (node.name.equalsIgnoreCase(name)) return node.id;
        }
        return -1;
    }
}
