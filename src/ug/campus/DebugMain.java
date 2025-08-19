package ug.campus;

import java.io.*;
import java.util.*;

public class DebugMain {
    public static void main(String[] args) throws Exception {
        Graph graph = new Graph();
        loadNodes(graph, "data/nodes.csv");
        loadEdges(graph, "data/edges.csv");
        
        System.out.println("Graph loaded successfully!");
        System.out.println("Nodes: " + graph.nodes.size());
        System.out.println("Adjacency lists: " + graph.adj.size());
        
        // Check specific nodes
        int ecobank = findNodeId(graph, "Ecobank");
        int balme = findNodeId(graph, "Balme Library");
        
        System.out.println("Ecobank ID: " + ecobank);
        System.out.println("Balme Library ID: " + balme);
        
        if (ecobank != -1) {
            System.out.println("Ecobank connections: " + graph.adj.get(ecobank).size());
            for (Edge e : graph.adj.get(ecobank)) {
                String targetName = graph.nodes.get(e.toId).name;
                System.out.println("  -> " + e.toId + " (" + targetName + ") - " + e.distanceMeters + "m");
            }
        }
        
        if (balme != -1) {
            System.out.println("Balme Library connections: " + graph.adj.get(balme).size());
            for (Edge e : graph.adj.get(balme)) {
                String targetName = graph.nodes.get(e.toId).name;
                System.out.println("  -> " + e.toId + " (" + targetName + ") - " + e.distanceMeters + "m");
            }
        }
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
