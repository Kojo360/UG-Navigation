#!/usr/bin/env python3
"""Fix CSV formatting issues in nodes.csv"""

import csv
import sys

def fix_nodes_csv():
    input_file = 'data/nodes.csv'
    output_file = 'data/nodes_fixed.csv'
    
    with open(input_file, 'r', encoding='utf-8') as infile:
        # Read all lines to manually parse problematic ones
        lines = infile.readlines()
    
    # Parse header
    header = lines[0].strip().split(',')
    
    fixed_rows = []
    for i, line in enumerate(lines[1:], 1):
        line = line.strip()
        if not line:
            continue
            
        # Try to parse the line manually
        try:
            # Use csv module to handle quoted fields properly
            reader = csv.reader([line])
            row = next(reader)
            
            if len(row) >= 5:
                id_val = row[0]
                name = row[1]
                lat = row[2]
                lon = row[3]
                node_type = row[4]
                
                # Validate numeric fields
                float(lat)
                float(lon)
                int(id_val)
                
                fixed_rows.append([id_val, name, lat, lon, node_type])
            else:
                print(f"Skipping malformed line {i}: {line}")
                
        except Exception as e:
            print(f"Error parsing line {i}: {line}")
            print(f"Error: {e}")
    
    # Write fixed CSV
    with open(output_file, 'w', newline='', encoding='utf-8') as outfile:
        writer = csv.writer(outfile)
        writer.writerow(header)
        writer.writerows(fixed_rows)
    
    print(f"Fixed CSV written to {output_file}")
    print(f"Original lines: {len(lines)}")
    print(f"Fixed rows: {len(fixed_rows)}")

if __name__ == '__main__':
    fix_nodes_csv()
