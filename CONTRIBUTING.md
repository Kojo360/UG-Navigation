# Contributing to UG-Navigation

Thank you for contributing! This document explains how to set up the project locally, run the main tools, and what we expect from contributions.

## Development environment

- Java 11+ for the routing application (javac/java).
- Python 3.10+ for data preparation scripts.

## Running locally

Generate road graphs from an Overpass/OSM GeoJSON:

```bash
python build_road_graph.py --geojson "export (3).geojson" --mode walk
python build_road_graph.py --geojson "export (3).geojson" --mode drive
```

Build Java sources and run the interactive UI or batch export:

```bash
javac -d . src\ug\campus\*.java
java ug.campus.Main
# or for batch distances:
java ug.campus.Main roadbatch
```

## Coding style

- Keep changes small and focused. Add tests where appropriate.
- Java: follow the simple project style (no external build system used here). Keep package `ug.campus`.
- Python: prefer stdlib only; if adding dependencies, document them in `requirements.txt`.

## Pull Requests

- Fork the repository, create a feature branch, and open a PR against `main`.
- Use a descriptive title and include the changed files and short rationale.

## Issues & Bugs

- Create an issue with steps to reproduce and sample data if possible.

## Maintainers

- Project owner: Kojo360 (see GitHub repo for contacts)

Thanks for helping improve UG-Navigation!
