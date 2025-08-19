import csv
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
NODES_CSV = ROOT / 'data' / 'nodes.csv'
GEOJSON = ROOT / 'data' / 'nodes_update.geojson'
BACKUP = ROOT / 'data' / 'nodes.csv.bak'
REPORT = ROOT / 'data' / 'nodes_update_report.csv'


def load_nodes():
    nodes = []
    with open(NODES_CSV, newline='', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            # normalize name for matching
            row['name_norm'] = (row.get('name') or '').strip().lower()
            nodes.append(row)
    return nodes


def write_nodes(nodes):
    # write CSV with original header order
    fieldnames = ['id','name','lat','lon','type']
    # backup existing
    BACKUP.write_bytes(NODES_CSV.read_bytes())
    with open(NODES_CSV, 'w', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        for n in nodes:
            writer.writerow({k: n.get(k, '') for k in fieldnames})


def parse_geojson():
    with open(GEOJSON, encoding='utf-8') as f:
        g = json.load(f)
    feats = []
    for feat in g.get('features', []):
        props = feat.get('properties', {})
        name = (props.get('name') or props.get('alt_name') or '').strip()
        lat = None
        lon = None
        geom = feat.get('geometry') or {}
        if geom.get('type') == 'Point':
            coords = geom.get('coordinates', [])
            if len(coords) >= 2:
                lon, lat = coords[0], coords[1]
        feats.append({'id': feat.get('id') or props.get('@id'), 'name': name, 'name_norm': name.lower(), 'lat': lat, 'lon': lon, 'props': props})
    return feats


def match_and_update(nodes, feats):
    report_rows = []
    name_to_nodes = {}
    for n in nodes:
        name_to_nodes.setdefault(n['name_norm'], []).append(n)

    for f in feats:
        if not f['name_norm']:
            report_rows.append({'feature_id': f['id'], 'feature_name': f['name'], 'matched': 'no_name'})
            continue
        candidates = name_to_nodes.get(f['name_norm'], [])
        if len(candidates) == 1:
            node = candidates[0]
            old_lat, old_lon = node['lat'], node['lon']
            if f['lat'] is not None and f['lon'] is not None:
                node['lat'] = str(f['lat'])
                node['lon'] = str(f['lon'])
                report_rows.append({'feature_id': f['id'], 'feature_name': f['name'], 'matched': node['id'], 'old_lat': old_lat, 'old_lon': old_lon, 'new_lat': node['lat'], 'new_lon': node['lon']})
            else:
                report_rows.append({'feature_id': f['id'], 'feature_name': f['name'], 'matched': node['id'], 'note': 'no_coords'})
        elif len(candidates) > 1:
            # multiple nodes with same name -> choose the closest by simple difference if coords available
            if f['lat'] is None or f['lon'] is None:
                report_rows.append({'feature_id': f['id'], 'feature_name': f['name'], 'matched': 'multiple', 'note': 'no_coords'})
                continue
            best = None
            best_dist = None
            fl = float(f['lat'])
            fn = float(f['lon'])
            for node in candidates:
                try:
                    nl = float(node['lat'])
                    nn = float(node['lon'])
                    d = (nl - fl)**2 + (nn - fn)**2
                except:
                    d = float('inf')
                if best is None or d < best_dist:
                    best = node
                    best_dist = d
            if best:
                old_lat, old_lon = best['lat'], best['lon']
                best['lat'] = str(f['lat'])
                best['lon'] = str(f['lon'])
                report_rows.append({'feature_id': f['id'], 'feature_name': f['name'], 'matched': best['id'], 'old_lat': old_lat, 'old_lon': old_lon, 'new_lat': best['lat'], 'new_lon': best['lon'], 'note': 'multiple_candidates_chosen'})
            else:
                report_rows.append({'feature_id': f['id'], 'feature_name': f['name'], 'matched': 'multiple_none_suitable'})
        else:
            # no exact name match – try fuzzy contains match
            found = None
            for n in nodes:
                if f['name_norm'] and f['name_norm'] in n['name_norm'] and f['lat'] is not None:
                    found = n
                    break
            if found:
                old_lat, old_lon = found['lat'], found['lon']
                found['lat'] = str(f['lat'])
                found['lon'] = str(f['lon'])
                report_rows.append({'feature_id': f['id'], 'feature_name': f['name'], 'matched': found['id'], 'old_lat': old_lat, 'old_lon': old_lon, 'new_lat': found['lat'], 'new_lon': found['lon'], 'note': 'fuzzy_match'})
            else:
                report_rows.append({'feature_id': f['id'], 'feature_name': f['name'], 'matched': 'none'})
    return report_rows


def write_report(rows):
    fieldnames = ['feature_id','feature_name','matched','old_lat','old_lon','new_lat','new_lon','note']
    with open(REPORT, 'w', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        for r in rows:
            writer.writerow(r)


if __name__ == '__main__':
    # non-interactive updater: load nodes and geojson, update, backup, and write report
    if not NODES_CSV.exists():
        print('nodes.csv not found at', NODES_CSV)
        raise SystemExit(1)
    if not GEOJSON.exists():
        print('geojson not found at', GEOJSON)
        raise SystemExit(1)

    nodes = load_nodes()
    feats = parse_geojson()
    report = match_and_update(nodes, feats)
    write_report(report)
    write_nodes(nodes)
    # summary counts
    matched = sum(1 for r in report if r.get('matched') not in (None,'none','no_name'))
    total = len(report)
    print(f'Updated nodes.csv ({matched}/{total} features matched). Report: {REPORT}')
