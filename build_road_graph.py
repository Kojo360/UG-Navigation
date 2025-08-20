#!/usr/bin/env python3
"""Build a road graph (nodes & edges) from a GeoJSON file of OSM highway LineStrings.

Outputs:
  data/road_nodes.csv  (id,lat,lon)
  data/road_edges.csv  (fromId,toId,distanceMeters,speedKph,undirected,highway)

Usage:
  python build_road_graph.py --geojson export.geojson --mode walk
  python build_road_graph.py --geojson export.geojson --mode drive

Modes:
  walk  - include all highway types
  drive - exclude pedestrian-only ways (footway,path,pedestrian,steps)

Notes:
  - Nodes deduplicated by rounding coords to 1e-6 degrees (~0.11m latitude).
  - Speeds (km/h) are coarse defaults; adjust SPEED_MAP as needed.
  - Edges currently undirected (can be split later for oneway support).
"""
import argparse, json, math, csv
from pathlib import Path

SPEED_MAP = {
    'footway': 5,
    'path': 5,
    'pedestrian': 5,
    'steps': 3,
    'service': 20,
    'residential': 30,
    'living_street': 15,
    'tertiary': 40,
    'secondary': 50,
}

DRIVE_EXCLUDE = {'footway','path','pedestrian','steps'}

def haversine(lat1, lon1, lat2, lon2):
    R = 6371000
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dl = math.radians(lon2 - lon1)
    a = math.sin(dphi/2)**2 + math.cos(p1)*math.cos(p2)*math.sin(dl/2)**2
    return R * 2 * math.atan2(math.sqrt(a), math.sqrt(1-a))

def norm_key(lat, lon):
    return round(lat,6), round(lon,6)

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--geojson', required=True)
    ap.add_argument('--mode', choices=['walk','drive'], default='walk')
    args = ap.parse_args()

    path = Path(args.geojson)
    if not path.exists():
        raise SystemExit(f"GeoJSON not found: {path}")

    data = json.loads(path.read_text(encoding='utf-8'))
    feats = data.get('features', [])

    node_id = 1
    coord_to_id = {}
    nodes = {}
    edges = []
    walk_mode = args.mode == 'walk'

    for f in feats:
        geom = f.get('geometry') or {}
        if geom.get('type') != 'LineString':
            continue
        coords = geom.get('coordinates') or []
        if len(coords) < 2:
            continue
        props = f.get('properties') or {}
        hw = props.get('highway', 'footway')
        if not walk_mode and hw in DRIVE_EXCLUDE:
            continue
        speed = SPEED_MAP.get(hw, 20)
        prev_id = None
        prev_lat = prev_lon = None
        for lon, lat in coords:
            k = norm_key(lat, lon)
            nid = coord_to_id.get(k)
            if nid is None:
                nid = node_id
                node_id += 1
                coord_to_id[k] = nid
                nodes[nid] = (lat, lon)
            if prev_id is not None:
                dist = haversine(prev_lat, prev_lon, lat, lon)
                if dist > 0.5:  # ignore zero-length / duplicates
                    edges.append((prev_id, nid, dist, speed, 1, hw))
            prev_id = nid
            prev_lat, prev_lon = lat, lon

    # Ensure data directory
    Path('data').mkdir(exist_ok=True)

    # Mode-specific filenames so walk & drive graphs can coexist
    suffix = args.mode
    nodes_path = Path(f'data/road_{suffix}_nodes.csv')
    edges_path = Path(f'data/road_{suffix}_edges.csv')

    # Write nodes
    with open(nodes_path, 'w', newline='', encoding='utf-8') as f:
        w = csv.writer(f)
        w.writerow(['id','lat','lon'])
        for i,(lat,lon) in nodes.items():
            w.writerow([i, f"{lat:.7f}", f"{lon:.7f}"])

    # Write edges
    with open(edges_path, 'w', newline='', encoding='utf-8') as f:
        w = csv.writer(f)
        w.writerow(['fromId','toId','distanceMeters','speedKph','undirected','highway'])
        for a,b,d,s,u,hw in edges:
            w.writerow([a,b, round(d,1), s, u, hw])

    print(f"Mode={args.mode} nodes={len(nodes)} edges={len(edges)} -> {nodes_path.name}, {edges_path.name}")

if __name__ == '__main__':
    main()
