package ug.campus;

import java.util.*;

public class RouteFinder {
    private Graph graph;

    public RouteFinder(Graph graph) {
        this.graph = graph;
    }

    // Small helper for priority queue entries
    private static class PQNode {
        int id;
        double priority; // can represent g or f depending on algorithm
        PQNode(int id, double priority) { this.id = id; this.priority = priority; }
    }

    // Dijkstra's algorithm
    public List<Integer> dijkstra(int src, int dest) {
        return dijkstraWithEdgePenalty(src, dest, null);
    }

    private List<Integer> dijkstraWithEdgePenalty(int src, int dest, java.util.function.Function<Edge, Double> weightFn) {
        Map<Integer, Double> dist = new HashMap<>();
        Map<Integer, Integer> prev = new HashMap<>();
        for (int id : graph.nodes.keySet()) dist.put(id, Double.POSITIVE_INFINITY);
        dist.put(src, 0.0);

        PriorityQueue<PQNode> pq = new PriorityQueue<>(Comparator.comparingDouble(n -> n.priority));
        pq.add(new PQNode(src, 0.0));
        while (!pq.isEmpty()) {
            PQNode curr = pq.poll();
            int u = curr.id;
            // stale entry check
            if (curr.priority > dist.get(u)) continue;
            if (u == dest) break;
            for (Edge e : graph.adj.get(u)) {
                double w = (weightFn == null) ? e.distanceMeters : weightFn.apply(e);
                double alt = dist.get(u) + w;
                if (alt < dist.get(e.toId)) {
                    dist.put(e.toId, alt);
                    prev.put(e.toId, u);
                    pq.add(new PQNode(e.toId, alt));
                }
            }
        }
        List<Integer> path = new ArrayList<>();
        if (!prev.containsKey(dest) && dest != src) {
            return path; // No path found
        }
        Integer u = dest;
        while (u != null) {
            path.add(u);
            if (u == src) break;
            u = prev.get(u);
        }
        Collections.reverse(path);
        return path;
    }

    // A* algorithm
    public List<Integer> aStar(int src, int dest) {
        Map<Integer, Double> gScore = new HashMap<>(); // g(n)
        Map<Integer, Integer> prev = new HashMap<>();
        for (int id : graph.nodes.keySet()) gScore.put(id, Double.POSITIVE_INFINITY);
        gScore.put(src, 0.0);

        PriorityQueue<PQNode> pq = new PriorityQueue<>(Comparator.comparingDouble(n -> n.priority));
        pq.add(new PQNode(src, heuristic(src, dest))); // f = g + h (g=0)

        while (!pq.isEmpty()) {
            PQNode curr = pq.poll();
            int u = curr.id;
            double currF = curr.priority;
            // stale entry: compare to current best f = g + h
            double currentBestF = gScore.get(u) + heuristic(u, dest);
            if (currF > currentBestF) continue;
            if (u == dest) break;
            for (Edge e : graph.adj.get(u)) {
                int v = e.toId;
                double tentativeG = gScore.get(u) + e.distanceMeters;
                if (tentativeG < gScore.get(v)) {
                    gScore.put(v, tentativeG);
                    prev.put(v, u);
                    double f = tentativeG + heuristic(v, dest);
                    pq.add(new PQNode(v, f));
                }
            }
        }
        List<Integer> path = new ArrayList<>();
        if (!prev.containsKey(dest) && dest != src) {
            return path; // No path found
        }
        Integer u = dest;
        while (u != null) {
            path.add(u);
            if (u == src) break;
            u = prev.get(u);
        }
        Collections.reverse(path);
        return path;
    }

    private double heuristic(int fromId, int toId) {
        Node a = graph.nodes.get(fromId);
        Node b = graph.nodes.get(toId);
        double dLat = Math.toRadians(b.lat - a.lat);
        double dLon = Math.toRadians(b.lon - a.lon);
        double r = 6371000; // Earth radius in meters
        double h = Math.sin(dLat/2)*Math.sin(dLat/2) + Math.cos(Math.toRadians(a.lat))*Math.cos(Math.toRadians(b.lat))*Math.sin(dLon/2)*Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1-h));
        return r * c;
    }

    // K-alternative routes without mutating base edge distances
    public List<List<Integer>> kAlternatives(int src, int dest, int k) {
        List<List<Integer>> routes = new ArrayList<>();
        Map<Edge, Integer> penaltyCount = new HashMap<>();
        for (int i = 0; i < k; i++) {
            List<Integer> path = dijkstraWithEdgePenalty(src, dest, e -> e.distanceMeters + 1000.0 * penaltyCount.getOrDefault(e, 0));
            if (path.isEmpty()) break;
            routes.add(path);
            // increment penalties for edges in path
            for (int j = 0; j < path.size()-1; j++) {
                int a = path.get(j);
                int b = path.get(j+1);
                for (Edge e : graph.adj.get(a)) {
                    if (e.toId == b) {
                        penaltyCount.put(e, penaltyCount.getOrDefault(e, 0) + 1);
                        break;
                    }
                }
            }
        }
        return routes;
    }

    // Diagnostic: compute unpenalized shortest distance only
    public double shortestDistance(int src, int dest) {
        List<Integer> path = dijkstra(src, dest);
        return totalDistance(path);
    }

    // Landmark filter
    public List<Integer> routeWithLandmark(int src, int dest, String keyword) {
        for (Node node : graph.nodes.values()) {
            if (node.name.contains(keyword)) {
                List<Integer> first = dijkstra(src, node.id);
                List<Integer> second = dijkstra(node.id, dest);
                if (!first.isEmpty() && !second.isEmpty()) {
                    first.remove(first.size()-1);
                    first.addAll(second);
                    return first;
                }
            }
        }
        return dijkstra(src, dest);
    }

    // Sorting routes by time then distance
    public List<List<Integer>> sortRoutes(List<List<Integer>> routes) {
        routes.sort(Comparator.comparingDouble(this::totalTime).thenComparingDouble(this::totalDistance));
        return routes;
    }

    public double totalDistance(List<Integer> path) {
        double dist = 0;
        for (int i = 0; i < path.size()-1; i++) {
            for (Edge e : graph.adj.get(path.get(i))) {
                if (e.toId == path.get(i+1)) dist += e.distanceMeters;
            }
        }
        return dist;
    }

    public double totalTime(List<Integer> path) {
        double time = 0;
        for (int i = 0; i < path.size()-1; i++) {
            for (Edge e : graph.adj.get(path.get(i))) {
                if (e.toId == path.get(i+1)) time += e.distanceMeters / (e.speedKph * 1000 / 3600);
            }
        }
        return time;
    }
}
