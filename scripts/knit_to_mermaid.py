#!/usr/bin/env python3

import os
import json
import argparse
from adjacency_list import AdjacencyList  # Import the AdjacencyList class
from visualiser import Visualiser  # Import the Visualiser class

def generate_mermaid_from_json(json_file_path, output_file_path="", direction="TD"):
    """Generate a Mermaid diagram from a Knit dependency JSON file."""
    if not os.path.exists(json_file_path):
        print(f"ERROR: JSON file not found: {json_file_path}")
        return

    # Determine output file path if not provided
    if not output_file_path:
        output_dir = os.path.dirname(json_file_path)
        base_name = os.path.splitext(os.path.basename(json_file_path))[0]
        output_file_path = os.path.join(output_dir, f"{base_name}.mmd")

    try:
        # Read and parse the JSON file
        with open(json_file_path, 'r') as f:
            component_dumps = json.load(f)

        # Use AdjacencyList to build the adjacency list
        adj_list = AdjacencyList()
        adjacency_list = adj_list.build_adjacency_list(component_dumps)

        # Use Visualiser to generate the Mermaid diagram
        visualiser = Visualiser()
        mermaid_content = visualiser.build_mermaid_diagram(adjacency_list, direction)

        # Write to output file
        os.makedirs(os.path.dirname(output_file_path), exist_ok=True)
        with open(output_file_path, 'w') as f:
            f.write(mermaid_content)

        print(f"Generated Mermaid diagram at: {output_file_path}")
    except Exception as e:
        print(f"ERROR: (generate_mermaid_from_json) Failed to generate Mermaid diagram: {str(e)}")

def main():
    parser = argparse.ArgumentParser(description='Generate Mermaid diagram from Knit dependency JSON')
    parser.add_argument('json_file', help='Path to the Knit dependency JSON file')
    parser.add_argument('-o', '--output', help='Output file path for the Mermaid diagram', default="")
    parser.add_argument('-d', '--direction', help='Diagram direction (TD, LR, RL, BT)', default="TD")

    args = parser.parse_args()

    generate_mermaid_from_json(args.json_file, args.output, args.direction)

if __name__ == '__main__':
    main()