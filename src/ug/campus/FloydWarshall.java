package ug.campus;

import java.util.*;

public class FloydWarshall {
    public double[][] dist;
    public int[][] next;
    public int n;

    public FloydWarshall(Graph graph) {
        n = graph.nodes.size();
        dist = new double[n][n];
        next = new int[n][n];
        for (int i = 0; i < n; i++) {
            Arrays.fill(dist[i], Double.POSITIVE_INFINITY);
            Arrays.fill(next[i], -1);
        }
        for (Node node : graph.nodes.values()) {
            dist[node.id-1][node.id-1] = 0;
            next[node.id-1][node.id-1] = node.id-1;
        }
        for (List<Edge> edges : graph.adj.values()) {
            for (Edge edge : edges) {
                dist[edge.fromId-1][edge.toId-1] = edge.distanceMeters;
                next[edge.fromId-1][edge.toId-1] = edge.toId-1;
            }
        }
        for (int k = 0; k < n; k++) {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    if (dist[i][k] + dist[k][j] < dist[i][j]) {
                        dist[i][j] = dist[i][k] + dist[k][j];
                        next[i][j] = next[i][k];
                    }
                }
            }
        }
    }

    public List<Integer> getPath(int u, int v) {
        if (next[u][v] == -1) return null;
        List<Integer> path = new ArrayList<>();
        while (u != v) {
            path.add(u+1);
            u = next[u][v];
        }
        path.add(v+1);
        return path;
    }
}
