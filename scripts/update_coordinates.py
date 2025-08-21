"""Merge / update nodes.csv from Overpass Turbo GeoJSON export.

Usage (from repo root):
  python update_coordinates.py --geojson data/nodes_update.geojson

Behavior:
  - Loads existing data/nodes.csv (preserves original row order & IDs)
  - Parses GeoJSON features (Points only)
  - Extracts (name, lat, lon, type) where type comes primarily from 'amenity'
	* Falls back to 'office' if amenity missing and office tag present
	* Skips features without coordinates
  - Deduplicates against existing nodes using a composite key and spatial threshold
	* Key: (normalized_name, type) when name present; else coordinate hash bucket
	* If existing match within 10m -> optionally updates coordinates (small drift)
	* If farther than 10m or different key -> appended as new node with next ID
  - Writes updated nodes.csv in-place and a merge report at data/nodes_update_report.csv

Notes:
  - Existing IDs are preserved; new IDs continue after the last existing ID.
  - Edges are NOT auto-regenerated here to let you control when to run generate_edges.py.
"""

from __future__ import annotations

import argparse
import csv
import json
import math
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Tuple, Optional

NODES_CSV = Path('data/nodes.csv')
REPORT_CSV = Path('data/nodes_update_report.csv')

DIST_DUP_THRESHOLD_METERS = 10.0  # treat within this distance as same physical feature
COORD_UPDATE_THRESHOLD_METERS = 2.5  # only shift coords if farther than this (avoid noise)


@dataclass
class Node:
	id: int
	name: str
	lat: float
	lon: float
	type: str

	def key(self) -> Tuple[str, str]:
		return (self.name.strip().lower(), self.type.strip().lower())


def haversine(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
	R = 6371000.0
	phi1, phi2 = math.radians(lat1), math.radians(lat2)
	dphi = math.radians(lat2 - lat1)
	dlambda = math.radians(lon2 - lon1)
	a = math.sin(dphi / 2) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(dlambda / 2) ** 2
	return R * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))


def load_existing_nodes(path: Path) -> List[Node]:
	nodes: List[Node] = []
	with path.open('r', encoding='utf-8') as f:
		reader = csv.DictReader(f)
		for row in reader:
			try:
				nodes.append(Node(
					id=int(row['id']),
					name=row['name'].strip(),
					lat=float(row['lat']),
					lon=float(row['lon']),
					type=row['type'].strip()
				))
			except Exception as e:
				print(f"Skipping row due to parse error: {row} -> {e}")
	return nodes


def parse_feature(feat: dict) -> Optional[Tuple[str, float, float, str]]:
	if feat.get('geometry', {}).get('type') != 'Point':
		return None
	coords = feat['geometry'].get('coordinates')
	if not (isinstance(coords, list) and len(coords) == 2):
		return None
	lon, lat = coords[0], coords[1]
	props = feat.get('properties', {})
	name = props.get('name') or props.get('alt_name') or ''
	amenity = props.get('amenity')
	office = props.get('office')
	node_type = (amenity or office or 'unknown').strip()
	if not name and node_type == 'unknown':  # useless anonymous unknown
		return None
	return name.strip(), float(lat), float(lon), node_type


def load_geojson(path: Path) -> List[Tuple[str, float, float, str]]:
	with path.open('r', encoding='utf-8') as f:
		data = json.load(f)
	feats = data.get('features', [])
	parsed: List[Tuple[str, float, float, str]] = []
	for feat in feats:
		p = parse_feature(feat)
		if p:
			parsed.append(p)
	return parsed


def build_index(nodes: List[Node]) -> Dict[Tuple[str, str], List[Node]]:
	idx: Dict[Tuple[str, str], List[Node]] = {}
	for n in nodes:
		if n.name:
			idx.setdefault(n.key(), []).append(n)
	return idx


def merge(nodes: List[Node], new_features: List[Tuple[str, float, float, str]]):
	index = build_index(nodes)
	next_id = max((n.id for n in nodes), default=0) + 1

	added: List[Node] = []
	updated: List[Tuple[Node, float, float]] = []  # node, old_dist, new_dist
	skipped: List[Tuple[str, float, float, str, str]] = []  # reason

	for name, lat, lon, ntype in new_features:
		key = (name.strip().lower(), ntype.strip().lower()) if name else None
		match: Optional[Node] = None
		min_dist = float('inf')
		if key and key in index:
			for candidate in index[key]:
				d = haversine(lat, lon, candidate.lat, candidate.lon)
				if d < min_dist:
					min_dist = d
					match = candidate
		if match and min_dist <= DIST_DUP_THRESHOLD_METERS:
			# maybe update coordinates if difference > threshold
			if min_dist > COORD_UPDATE_THRESHOLD_METERS:
				old_lat, old_lon = match.lat, match.lon
				match.lat, match.lon = lat, lon
				updated.append((match, old_lat, old_lon))
			else:
				skipped.append((name, lat, lon, ntype, f"duplicate within {min_dist:.1f}m"))
			continue
		# Add new node
		new_node = Node(id=next_id, name=name, lat=lat, lon=lon, type=ntype)
		nodes.append(new_node)
		if name:
			index.setdefault(new_node.key(), []).append(new_node)
		added.append(new_node)
		next_id += 1

	return added, updated, skipped


def write_nodes(path: Path, nodes: List[Node]):
	# Preserve original ordering: existing nodes first (already in list order), then appended ones.
	with path.open('w', newline='', encoding='utf-8') as f:
		writer = csv.writer(f)
		writer.writerow(['id', 'name', 'lat', 'lon', 'type'])
		for n in nodes:
			writer.writerow([n.id, n.name, f"{n.lat:.7f}", f"{n.lon:.7f}", n.type])


def write_report(path: Path, added: List[Node], updated: List[Tuple[Node, float, float]], skipped):
	with path.open('w', newline='', encoding='utf-8') as f:
		writer = csv.writer(f)
		writer.writerow(['action', 'id', 'name', 'lat', 'lon', 'type', 'details'])
		for n in added:
			writer.writerow(['added', n.id, n.name, f"{n.lat:.7f}", f"{n.lon:.7f}", n.type, ''])
		for n, old_lat, old_lon in updated:
			writer.writerow(['updated_coords', n.id, n.name, f"{n.lat:.7f}", f"{n.lon:.7f}", n.type, f"old=({old_lat},{old_lon})"])
		for name, lat, lon, ntype, reason in skipped:
			writer.writerow(['skipped', '', name, f"{lat:.7f}", f"{lon:.7f}", ntype, reason])


def main():
	parser = argparse.ArgumentParser(description='Merge Overpass Turbo GeoJSON landmarks into nodes.csv')
	parser.add_argument('--geojson', required=True, help='Path to Overpass GeoJSON file')
	parser.add_argument('--dry-run', action='store_true', help='Do not write nodes.csv, only show summary')
	args = parser.parse_args()

	geojson_path = Path(args.geojson)
	if not NODES_CSV.exists():
		raise SystemExit(f"Missing {NODES_CSV}")
	if not geojson_path.exists():
		raise SystemExit(f"GeoJSON not found: {geojson_path}")

	nodes = load_existing_nodes(NODES_CSV)
	new_feats = load_geojson(geojson_path)
	added, updated, skipped = merge(nodes, new_feats)

	print(f"Existing nodes: {len(nodes) - len(added)}")
	print(f"Parsed features: {len(new_feats)}")
	print(f"Added: {len(added)}  Updated: {len(updated)}  Skipped (dups): {len(skipped)}")

	if not args.dry_run:
		write_nodes(NODES_CSV, nodes)
		write_report(REPORT_CSV, added, updated, skipped)
		print(f"Wrote updated nodes.csv and report -> {REPORT_CSV}")
		print('Next: regenerate edges with: python generate_edges.py')
	else:
		print('Dry run complete (no files written).')


if __name__ == '__main__':
	main()
