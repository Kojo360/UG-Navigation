import csv
import math

# Load nodes and create ID mapping
nodes = {}
id_mapping = {}
with open('data/nodes.csv', 'r', encoding='utf-8') as f:
    reader = csv.DictReader(f)
    counter = 1
    for row in reader:
        try:
            # Skip rows with empty or invalid coordinates
            if row['lat'].strip() and row['lon'].strip():
                lat = float(row['lat'].strip())
                lon = float(row['lon'].strip())
                nodes[counter] = (lat, lon)
                id_mapping[row['id']] = counter
                counter += 1
        except ValueError:
            print(f"Skipping node {row['id']} due to invalid coordinates: {row['lat']}, {row['lon']}")
            continue

def haversine(lat1, lon1, lat2, lon2):
    R = 6371000  # meters
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlambda = math.radians(lon2 - lon1)
    a = math.sin(dphi/2)**2 + math.cos(phi1)*math.cos(phi2)*math.sin(dlambda/2)**2
    return R * 2 * math.atan2(math.sqrt(a), math.sqrt(1-a))

max_distance = 500  # meters, increased for better connectivity

edges = []
node_ids = list(nodes.keys())
print(f"Processing {len(node_ids)} nodes...")

for i in range(len(node_ids)):
    for j in range(i+1, len(node_ids)):
        id1, id2 = node_ids[i], node_ids[j]
        lat1, lon1 = nodes[id1]
        lat2, lon2 = nodes[id2]
        dist = haversine(lat1, lon1, lat2, lon2)
        if dist <= max_distance:
            edges.append((id1, id2, dist))

print(f"Found {len(edges)} connections within {max_distance}m")

with open('data/edges.csv', 'w', newline='', encoding='utf-8') as f:
    writer = csv.writer(f)
    writer.writerow(['fromId', 'toId', 'distanceMeters', 'speedKph', 'undirected'])
    for fromId, toId, dist in edges:
        speed = 5  # walking speed in km/h
        undirected = 1
        writer.writerow([fromId, toId, round(dist, 1), speed, undirected])

print("edges.csv generated successfully!")
