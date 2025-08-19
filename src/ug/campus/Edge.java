package ug.campus;

public class Edge {
    public final int fromId;
    public final int toId;
    public double distanceMeters;
    public final double speedKph;
    public final boolean undirected;

    public Edge(int fromId, int toId, double distanceMeters, double speedKph, boolean undirected) {
        this.fromId = fromId;
        this.toId = toId;
        this.distanceMeters = distanceMeters;
        this.speedKph = speedKph;
        this.undirected = undirected;
    }
}
