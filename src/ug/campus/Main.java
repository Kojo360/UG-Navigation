package ug.campus;

import java.io.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);
        System.out.print("Mode (walk/drive/roadbatch) [walk]: ");
        String mode = sc.nextLine().trim().toLowerCase();
        if (mode.isEmpty()) mode = "walk";

        Graph graph;
        if (mode.startsWith("road")) {
            // Determine which road graph to load
            String baseMode;
            if (mode.equals("roadbatch")) {
                System.out.print("Batch mode: walk or drive? [drive]: ");
                String batchMode = sc.nextLine().trim().toLowerCase();
                if (batchMode.isEmpty()) batchMode = "drive";
                baseMode = batchMode;
            } else {
                baseMode = (mode.contains("drive") ? "drive" : "walk");
            }
            String nodesFile = "data/road_" + baseMode + "_nodes.csv";
            String edgesFile = "data/road_" + baseMode + "_edges.csv";
            File nf = new File(nodesFile);
            File ef = new File(edgesFile);
            if (!nf.exists() || !ef.exists()) {
                System.out.println("Missing road graph files for mode '"+baseMode+"'. Please run: python build_road_graph.py --geojson <file> --mode "+baseMode);
                return;
            }
            graph = RoadGraphLoader.load(nodesFile, edgesFile);
            System.out.println("Loaded road graph ("+baseMode+") nodes=" + graph.nodes.size());
            if (mode.equals("roadbatch")) {
                // Snap POIs and export distance matrix then exit
                Graph poiGraph = new Graph();
                loadNodes(poiGraph, "data/nodes.csv");
                Map<Integer,Integer> snap = snapPOIs(poiGraph, graph, 30.0);
                exportBatchDistances(poiGraph, graph, snap, "data/batch_distances_"+baseMode+".csv");
                System.out.println("Batch distances exported -> data/batch_distances_"+baseMode+".csv");
                return;
            }
        } else {
            graph = new Graph();
            loadNodes(graph, "data/nodes.csv");
            loadEdges(graph, "data/edges.csv");
        }
        RouteFinder finder = new RouteFinder(graph);
        System.out.print("Enter source name: ");
        String srcName = sc.nextLine();
        System.out.print("Enter destination name: ");
        String destName = sc.nextLine();
        System.out.print("Enter landmark keyword (optional): ");
        String keyword = sc.nextLine();
    // Resolve destination first (no context) then use that to refine source selection
    int dest = resolveNode(graph, destName, null, sc);
    int src = resolveNode(graph, srcName, dest, sc);
        if (src == -1 || dest == -1) {
            System.out.println("Invalid source or destination name.");
            return;
        }
        Node srcNode = graph.nodes.get(src);
        Node destNode = graph.nodes.get(dest);
        double straight = haversine(srcNode.lat, srcNode.lon, destNode.lat, destNode.lon);
        System.out.printf("From %s -> %s (straight-line %.1f m)\n", nameOrPlaceholder(srcNode), nameOrPlaceholder(destNode), straight);

        boolean generateAlternatives = false;
        if (keyword.isEmpty()) {
            System.out.print("Generate up to 3 alternatives? (y/N): ");
            String altAns = sc.nextLine().trim().toLowerCase();
            generateAlternatives = altAns.equals("y") || altAns.equals("yes");
        }
        List<List<Integer>> routes;
        if (!keyword.isEmpty()) {
            List<Integer> route = finder.routeWithLandmark(src, dest, keyword);
            routes = new ArrayList<>();
            if (!route.isEmpty()) {
                routes.add(route);
            }
        } else {
            if (generateAlternatives) {
                routes = finder.kAlternatives(src, dest, 3);
            } else {
                routes = new ArrayList<>();
                routes.add(finder.dijkstra(src, dest));
            }
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
        if (!routes.isEmpty()) {
            double best = finder.totalDistance(routes.get(0));
            if (straight > 0 && best/straight > 3) {
                System.out.printf("Warning: path/straight ratio = %.1f (check node resolution or graph).\n", best/straight);
            }
        }
    }

    static void loadNodes(Graph graph, String file) throws Exception {
        BufferedReader br = new BufferedReader(new FileReader(file));
        String line = br.readLine();
        while ((line = br.readLine()) != null) {
            String[] t = parseCSVLine(line);
            if (t.length < 5) continue; // Skip malformed lines
            int id;
            try { id = Integer.parseInt(t[0]); } catch (NumberFormatException nfe) { continue; }
            String name = t[1];
            double lat = Double.parseDouble(t[2]);
            double lon = Double.parseDouble(t[3]);
            String type = t[4];
            if (name == null || name.trim().isEmpty()) {
                if (type != null && !type.trim().isEmpty()) name = "Unnamed " + type.trim();
                else name = "Unnamed-" + id;
            }
            graph.addNode(new Node(id, name, lat, lon, type));
        }
        br.close();
    }
    
    static String[] parseCSVLine(String line) {
        java.util.List<String> result = new java.util.ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());
        return result.toArray(new String[0]);
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

    // --- Enhanced disambiguation logic ---
    static int resolveNode(Graph graph, String rawQuery, Integer otherNodeId, Scanner sc) {
        if (rawQuery == null || rawQuery.trim().isEmpty()) return -1;
        String q = rawQuery.trim().toLowerCase();

        // Collect candidates: exact, startsWith, contains (in that priority)
        List<Node> exact = new ArrayList<>();
        List<Node> starts = new ArrayList<>();
        List<Node> contains = new ArrayList<>();
        for (Node n : graph.nodes.values()) {
            if (n.name == null || n.name.isEmpty()) continue;
            String ln = n.name.toLowerCase();
            if (ln.equals(q)) exact.add(n);
            else if (ln.startsWith(q)) starts.add(n);
            else if (ln.contains(q)) contains.add(n);
        }
        List<Node> candidates = !exact.isEmpty() ? exact : !starts.isEmpty() ? starts : contains;
        if (candidates.isEmpty()) return -1;
        if (candidates.size() == 1) return candidates.get(0).id;

    // Collapse near-duplicate candidates (same normalized base within 60m)
    candidates = clusterCandidates(candidates, 60.0);

    // If we know the other endpoint, rank by geographic distance to it
        if (otherNodeId != null && graph.nodes.containsKey(otherNodeId)) {
            Node other = graph.nodes.get(otherNodeId);
            candidates.sort(Comparator.comparingDouble(c -> haversine(c.lat, c.lon, other.lat, other.lon)));
        } else {
            // Otherwise sort alphabetically for determinism
            candidates.sort(Comparator.comparing(n -> n.name.toLowerCase()));
        }

        // If top two are very close (< 40m) keep both; else just pick top automatically
        if (candidates.size() > 1 && otherNodeId != null) {
            Node best = candidates.get(0);
            Node second = candidates.get(1);
            double d = haversine(best.lat, best.lon, second.lat, second.lon);
            if (d > 40) {
                return best.id; // confident pick
            }
        }

        // Interactive choice
        System.out.println("Multiple matches for '" + rawQuery + "':");
        for (int i = 0; i < candidates.size(); i++) {
            Node n = candidates.get(i);
            double distToOther = -1;
            if (otherNodeId != null) {
                Node other = graph.nodes.get(otherNodeId);
                distToOther = haversine(n.lat, n.lon, other.lat, other.lon);
            }
            System.out.printf("  [%d] %s (id=%d, type=%s%s)\n", i+1, n.name, n.id, n.type,
                    distToOther >= 0 ? String.format(", %.0fm from other", distToOther) : "");
        }
        System.out.print("Choose number (1-" + candidates.size() + ") or ENTER for 1: ");
        String ans = sc.nextLine().trim();
        int idx = 1;
        if (!ans.isEmpty()) {
            try { idx = Integer.parseInt(ans); } catch (NumberFormatException ignored) {}
            if (idx < 1 || idx > candidates.size()) idx = 1;
        }
        return candidates.get(idx - 1).id;
    }

    static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000.0;
        double p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2);
        double dphi = Math.toRadians(lat2 - lat1);
        double dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dphi/2)*Math.sin(dphi/2) + Math.cos(p1)*Math.cos(p2)*Math.sin(dl/2)*Math.sin(dl/2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
    }

    static String nameOrPlaceholder(Node n) {
        if (n.name != null && !n.name.trim().isEmpty()) return n.name;
        return "Unnamed-" + n.id;
    }

    static List<Node> clusterCandidates(List<Node> nodes, double distThresholdMeters) {
        List<Node> result = new ArrayList<>();
        boolean[] used = new boolean[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) {
            if (used[i]) continue;
            Node base = nodes.get(i);
            List<Node> cluster = new ArrayList<>();
            cluster.add(base);
            used[i] = true;
            String normBase = normalizeName(base.name);
            for (int j = i+1; j < nodes.size(); j++) {
                if (used[j]) continue;
                Node cand = nodes.get(j);
                if (!normalizeName(cand.name).equals(normBase)) continue;
                double d = haversine(base.lat, base.lon, cand.lat, cand.lon);
                if (d <= distThresholdMeters) {
                    cluster.add(cand);
                    used[j] = true;
                }
            }
            // pick representative: prefer non-fountain/library actual type, then shortest name
            Node rep = cluster.get(0);
            for (Node c : cluster) {
                if (isPreferred(c, rep)) rep = c;
            }
            result.add(rep);
        }
        return result;
    }

    static boolean isPreferred(Node a, Node b) {
        // prefer if a has more specific type (not 'fountain' or 'unknown') while b is generic
        String ag = genericTypeGroup(a.type);
        String bg = genericTypeGroup(b.type);
        if (!ag.equals(bg)) {
            if (bg.equals("generic") && !ag.equals("generic")) return true;
        }
        // prefer shorter cleaned name
        return normalizeName(a.name).length() < normalizeName(b.name).length();
    }

    static String genericTypeGroup(String t) {
        if (t == null) return "generic";
        t = t.toLowerCase();
        if (t.equals("fountain") || t.equals("unknown")) return "generic";
        return t;
    }

    static String normalizeName(String name) {
        if (name == null) return "";
        String n = name.toLowerCase();
        n = n.replace("the ", "");
        n = n.replace("extension", "");
        n = n.replace("fountain", "");
        n = n.replace("branch", "");
        n = n.replaceAll("[^a-z0-9 ]", "");
        n = n.replaceAll("\\s+", " ").trim();
        return n;
    }

    // --- POI snapping and batch distance export ---
    static Map<Integer,Integer> snapPOIs(Graph pois, Graph road, double maxMeters) {
        Map<Integer,Integer> mapping = new HashMap<>();
        for (Node p : pois.nodes.values()) {
            int bestId = -1; double bestDist = maxMeters;
            for (Node r : road.nodes.values()) {
                double d = haversine(p.lat, p.lon, r.lat, r.lon);
                if (d < bestDist) { bestDist = d; bestId = r.id; }
            }
            if (bestId != -1) mapping.put(p.id, bestId);
        }
        return mapping;
    }

    static void exportBatchDistances(Graph pois, Graph road, Map<Integer,Integer> snap, String outCsv) throws Exception {
        RouteFinder rf = new RouteFinder(road);
        List<Node> list = new ArrayList<>(pois.nodes.values());
        list.sort(Comparator.comparingInt(n -> n.id));
        try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.File(outCsv), java.nio.charset.StandardCharsets.UTF_8)) {
            pw.println("fromId,toId,straightMeters,roadMeters,ratio");
            for (int i = 0; i < list.size(); i++) {
                Node a = list.get(i);
                Integer ra = snap.get(a.id); if (ra == null) continue;
                for (int j = i+1; j < list.size(); j++) {
                    Node b = list.get(j);
                    Integer rb = snap.get(b.id); if (rb == null) continue;
                    double straight = haversine(a.lat, a.lon, b.lat, b.lon);
                    double roadDist = rf.totalDistance(rf.dijkstra(ra, rb));
                    if (roadDist <= 0) continue;
                    double ratio = roadDist / (straight > 0 ? straight : roadDist);
                    pw.printf(Locale.US, "%d,%d,%.1f,%.1f,%.2f%n", a.id, b.id, straight, roadDist, ratio);
                }
            }
        }
    }
}
