import csv

# Clean the nodes.csv and create sequential IDs
input_file = 'data/nodes.csv'
output_file = 'data/nodes_clean.csv'

with open(input_file, 'r', encoding='utf-8') as infile, \
     open(output_file, 'w', newline='', encoding='utf-8') as outfile:
    
    reader = csv.reader(infile)
    writer = csv.writer(outfile)
    
    # Write header
    writer.writerow(['id', 'name', 'lat', 'lon', 'type'])
    
    # Skip header
    next(reader)
    
    node_id = 1
    for row in reader:
        try:
            if len(row) >= 5:
                original_id, name, lat_str, lon_str, node_type = row[:5]
                lat = float(lat_str.strip())
                lon = float(lon_str.strip())
                writer.writerow([node_id, name.strip(), lat, lon, node_type.strip()])
                node_id += 1
        except ValueError as e:
            print(f"Skipping row due to error: {row[:5] if len(row) >= 5 else row} - {e}")

print(f"Cleaned CSV created with {node_id-1} nodes")
