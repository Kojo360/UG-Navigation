
# UG-Navigation

An algorithm and Java application to help users find the best route from one location to another on the University of Ghana (UG) campus.

## New: Road graph & batch export

This repository now supports generating a road network from OSM GeoJSON and exporting pairwise road distances for all POIs.

Build road graphs (creates mode-specific CSVs):

```bash
python build_road_graph.py --geojson "export (3).geojson" --mode walk
python build_road_graph.py --geojson "export (3).geojson" --mode drive
```

Outputs:

- `data/road_walk_nodes.csv`, `data/road_walk_edges.csv`
- `data/road_drive_nodes.csv`, `data/road_drive_edges.csv`

Export batch distances (choose walk or drive when prompted):

```bash
java ug.campus.Main roadbatch
```

This writes `data/batch_distances_walk.csv` or `data/batch_distances_drive.csv` depending on your choice.

Interactive routing:

```bash
java ug.campus.Main
```

Then follow prompts to select mode and enter source/destination.

## Notes

- The road graph builder deduplicates coordinates and assigns coarse default speeds per highway type.
- If you need both walk and drive graphs concurrently, the scripts now write separate files so they can coexist.

