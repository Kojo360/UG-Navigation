import csv
import math
from pathlib import Path

NODES_CSV = Path('data/nodes.csv')
EDGES_CSV = Path('data/edges.csv')

def haversine(lat1, lon1, lat2, lon2):
    R = 6371000  # meters
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlambda = math.radians(lon2 - lon1)
    a = math.sin(dphi/2)**2 + math.cos(phi1)*math.cos(phi2)*math.sin(dlambda/2)**2
    return R * 2 * math.atan2(math.sqrt(a), math.sqrt(1-a))

def load_nodes():
    nodes = {}
    with NODES_CSV.open('r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            try:
                if row['lat'].strip() and row['lon'].strip():
                    node_id = int(row['id'])  # preserve original id
                    lat = float(row['lat'].strip())
                    lon = float(row['lon'].strip())
                    nodes[node_id] = (lat, lon)
            except Exception as e:
                print(f"Skipping node {row.get('id')} due to parse error: {e}")
    return nodes

def build_edges(nodes, max_distance=500):
    ids = list(nodes.keys())
    edges = []
    print(f"Processing {len(ids)} nodes (preserving original IDs)...")
    for i in range(len(ids)):
        lat1, lon1 = nodes[ids[i]]
        for j in range(i+1, len(ids)):
            lat2, lon2 = nodes[ids[j]]
            dist = haversine(lat1, lon1, lat2, lon2)
            if dist <= max_distance:
                edges.append((ids[i], ids[j], dist))
    print(f"Found {len(edges)} connections within {max_distance}m")
    return edges

def write_edges(edges):
    with EDGES_CSV.open('w', newline='', encoding='utf-8') as f:
        w = csv.writer(f)
        w.writerow(['fromId', 'toId', 'distanceMeters', 'speedKph', 'undirected'])
        for a,b,d in edges:
            w.writerow([a,b, round(d,1), 5, 1])
    print("edges.csv generated successfully (ID-preserving)")

if __name__ == '__main__':
    nodes = load_nodes()
    edges = build_edges(nodes)
    write_edges(edges)
