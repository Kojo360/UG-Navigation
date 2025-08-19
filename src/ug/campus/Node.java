package ug.campus;

public class Node {
    public final int id;
    public final String name;
    public final double lat;
    public final double lon;
    public final String type;

    public Node(int id, String name, double lat, double lon, String type) {
        this.id = id;
        this.name = name;
        this.lat = lat;
        this.lon = lon;
        this.type = type;
    }
}
