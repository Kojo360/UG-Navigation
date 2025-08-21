package ug.campus;

import java.io.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);
        // drive-only mode
        String mode = "drive";

        // Load drive road graph
        String baseMode = "drive";
        String nodesFile = "data/road_" + baseMode + "_nodes.csv";
        String edgesFile = "data/road_" + baseMode + "_edges.csv";
        File nf = new File(nodesFile);
        File ef = new File(edgesFile);
        if (!nf.exists() || !ef.exists()) {
            System.out.println("Missing road graph files for drive mode. Please run: python scripts/build_road_graph.py --geojson <file> --mode drive");
            return;
        }
        Graph graph = RoadGraphLoader.load(nodesFile, edgesFile);
        System.out.println("Loaded road graph (drive) nodes=" + graph.nodes.size());
    // Load POIs for landmark detection
    Graph poiGraph = new Graph();
    loadNodes(poiGraph, "data/nodes.csv");
    System.out.println("Loaded POIs: " + poiGraph.nodes.size());

        RouteFinder finder = new RouteFinder(graph);
        System.out.print("Enter source name: ");
        String srcName = sc.nextLine();
        System.out.print("Enter destination name: ");
        String destName = sc.nextLine();
        System.out.print("Enter landmark keyword (optional): ");
        String keyword = sc.nextLine();
    // Resolve destination first (no context) then use that to refine source selection
    // Use the POI graph so we match named landmarks from data/nodes.csv instead of road node names
    int dest = resolveNode(poiGraph, destName, null, sc);
    if (dest == -1) {
    System.out.println("Invalid destination name: '" + destName + "'.");
    List<Node> cand = collectCandidates(poiGraph, destName, 5);
        if (cand.isEmpty()) {
            System.out.println("No similar names found in the dataset.");
        } else {
            System.out.println("Did you mean:");
            for (Node n : cand) {
                System.out.printf("  - %s (id=%d)\n", n.name, n.id);
            }
        }
        return;
    }
    int src = resolveNode(poiGraph, srcName, dest, sc);
    if (src == -1) {
        System.out.println("Invalid source name: '" + srcName + "'.");
        List<Node> cand = collectCandidates(poiGraph, srcName, 5);
        if (cand.isEmpty()) {
            System.out.println("No similar names found in the dataset.");
        } else {
            System.out.println("Did you mean:");
            for (Node n : cand) {
                System.out.printf("  - %s (id=%d)\n", n.name, n.id);
            }
        }
        return;
    }
    // src/dest were resolved against the POI graph
    Node srcNode = poiGraph.nodes.get(src);
    Node destNode = poiGraph.nodes.get(dest);
    double straight = haversine(srcNode.lat, srcNode.lon, destNode.lat, destNode.lon);

        // Ask to generate the route (single shortest route only)
        boolean doGenerate = true;
        if (keyword.isEmpty()) {
            System.out.print("Generate route? (Y/n): ");
            String resp = sc.nextLine().trim().toLowerCase();
            doGenerate = resp.isEmpty() || resp.equals("y") || resp.equals("yes");
        }
        if (!doGenerate) {
            System.out.println("No route generated.");
            return;
        }
        List<List<Integer>> routes = new ArrayList<>();
        if (!keyword.isEmpty()) {
            List<Integer> route = finder.routeWithLandmark(src, dest, keyword);
            if (!route.isEmpty()) routes.add(route);
        } else {
            routes.add(finder.dijkstra(src, dest));
        }
        
        // Filter out empty routes
        routes.removeIf(List::isEmpty);
        
        if (routes.isEmpty()) {
            System.out.println("No routes found between the specified locations.");
            return;
        }
        
        routes = finder.sortRoutes(routes);
        // Print only the single best (shortest) route
        List<Integer> bestPath = routes.get(0);
        if (bestPath == null || bestPath.isEmpty()) {
            System.out.println("No route found.");
            return;
        }
        // Print header and list of landmarks (exclude straight-line print per request)
        System.out.printf("From %s -> %s (shortest path)\n", nameOrPlaceholder(srcNode), nameOrPlaceholder(destNode));
        // Collect landmarks by snapping each road node to the nearest POI (ordered along the path)
        List<String> landmarks = new ArrayList<>();
        final double LANDMARK_RADIUS = 30.0; // meters
        for (int pid : bestPath) {
            Node roadNode = graph.nodes.get(pid);
            if (roadNode == null) continue;
            double bestD = LANDMARK_RADIUS + 1;
            Node bestPoi = null;
            for (Node poi : poiGraph.nodes.values()) {
                if (poi.name == null) continue;
                double d = haversine(roadNode.lat, roadNode.lon, poi.lat, poi.lon);
                if (d < bestD) { bestD = d; bestPoi = poi; }
            }
            if (bestPoi != null && bestD <= LANDMARK_RADIUS) {
                String nm = nameOrPlaceholder(bestPoi);
                if (!nm.equals(nameOrPlaceholder(srcNode)) && !nm.equals(nameOrPlaceholder(destNode))) {
                    if (!landmarks.contains(nm)) landmarks.add(nm);
                }
            }
        }
    // Print landmarks as a straight sequence from source -> ... -> destination
    List<String> seq = new ArrayList<>();
    seq.add(nameOrPlaceholder(srcNode));
    seq.addAll(landmarks);
    seq.add(nameOrPlaceholder(destNode));
    System.out.println(String.join(" -> ", seq));
        System.out.printf("Distance: %.1f m, Time: %.1f min\n", finder.totalDistance(bestPath), finder.totalTime(bestPath)/60);
        System.out.println();
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
        String q = normalizeName(rawQuery);

        // Collect candidates using normalized names: exact, startsWith, contains (in that priority)
        List<Node> exact = new ArrayList<>();
        List<Node> starts = new ArrayList<>();
        List<Node> contains = new ArrayList<>();
        for (Node n : graph.nodes.values()) {
            if (n.name == null || n.name.isEmpty()) continue;
            String ln = normalizeName(n.name);
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

    // Collect up to `max` candidate nodes matching a textual query (non-interactive)
    static List<Node> collectCandidates(Graph graph, String rawQuery, int max) {
        List<Node> results = new ArrayList<>();
        if (rawQuery == null || rawQuery.trim().isEmpty()) return results;
        String q = normalizeName(rawQuery);
        // priority: exact, startsWith, contains (working on normalized names)
        for (Node n : graph.nodes.values()) {
            if (n.name == null) continue;
            String ln = normalizeName(n.name);
            if (ln.equals(q)) results.add(n);
        }
        if (results.size() >= max) return results.subList(0, Math.min(max, results.size()));
        for (Node n : graph.nodes.values()) {
            if (n.name == null) continue;
            String ln = normalizeName(n.name);
            if (ln.startsWith(q) && !results.contains(n)) results.add(n);
        }
        if (results.size() >= max) return results.subList(0, Math.min(max, results.size()));
        for (Node n : graph.nodes.values()) {
            if (n.name == null) continue;
            String ln = normalizeName(n.name);
            if (ln.contains(q) && !results.contains(n)) results.add(n);
        }
        return results.size() > max ? results.subList(0, max) : results;
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
