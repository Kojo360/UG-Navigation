package ug.campus;

import java.io.*;

/** Loader for road graph built by build_road_graph.py */
public class RoadGraphLoader {
    public static Graph load(String nodesCsv, String edgesCsv) throws Exception {
        Graph g = new Graph();
        // Load road nodes (id,lat,lon)
        try (BufferedReader br = new BufferedReader(new FileReader(nodesCsv))) {
            String line = br.readLine(); // header
            while ((line = br.readLine()) != null) {
                String[] t = line.split(",");
                if (t.length < 3) continue;
                int id;
                try { id = Integer.parseInt(t[0]); } catch (NumberFormatException e) { continue; }
                double lat = Double.parseDouble(t[1]);
                double lon = Double.parseDouble(t[2]);
                // Use placeholder name
                g.addNode(new Node(id, "road-"+id, lat, lon, "road"));
            }
        }
        // Load road edges (fromId,toId,distanceMeters,speedKph,undirected)
        try (BufferedReader br = new BufferedReader(new FileReader(edgesCsv))) {
            String line = br.readLine();
            while ((line = br.readLine()) != null) {
                String[] t = line.split(",");
                if (t.length < 5) continue;
                int a = Integer.parseInt(t[0]);
                int b = Integer.parseInt(t[1]);
                double dist = Double.parseDouble(t[2]);
                double speed = Double.parseDouble(t[3]);
                boolean undirected = t[4].equals("1");
                if (!g.nodes.containsKey(a) || !g.nodes.containsKey(b)) continue;
                g.addEdge(new Edge(a,b,dist,speed,undirected));
            }
        }
        return g;
    }
}
