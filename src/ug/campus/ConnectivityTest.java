package ug.campus;

import java.io.*;
import java.util.*;

public class ConnectivityTest {
    public static void main(String[] args) throws Exception {
        Graph graph = new Graph();
        loadNodes(graph, "data/nodes.csv");
        loadEdges(graph, "data/edges.csv");
        
        int ecobank = findNodeId(graph, "Ecobank");
        int balme = findNodeId(graph, "Balme Library");
        
        System.out.println("Testing connectivity from Ecobank (" + ecobank + ") to Balme Library (" + balme + ")");
        
        boolean connected = bfs(graph, ecobank, balme);
        System.out.println("Connected: " + connected);
        
        if (connected) {
            System.out.println("Path exists but routing algorithm has bugs.");
        } else {
            System.out.println("No path exists - graph is disconnected.");
        }
    }
    
    static boolean bfs(Graph graph, int start, int end) {
        Set<Integer> visited = new HashSet<>();
        Queue<Integer> queue = new LinkedList<>();
        queue.add(start);
        visited.add(start);
        
        while (!queue.isEmpty()) {
            int current = queue.poll();
            if (current == end) return true;
            
            for (Edge edge : graph.adj.get(current)) {
                if (!visited.contains(edge.toId)) {
                    visited.add(edge.toId);
                    queue.add(edge.toId);
                }
            }
        }
        return false;
    }
    
    static void loadNodes(Graph graph, String file) throws Exception {
        BufferedReader br = new BufferedReader(new FileReader(file));
        String line = br.readLine();
        int nodeCounter = 1;
        while ((line = br.readLine()) != null) {
            String[] t = line.split(",");
            int id = nodeCounter++;
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
