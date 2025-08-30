#!/usr/bin/env python3

import json
import os
import argparse
import re

def sanitize_id(name):
    """Convert a fully qualified name to a valid Mermaid node ID"""
    sanitized = re.sub(r'[.\/$<>() ,]', '_', name)
    return f"node_{sanitized}"

def get_simple_name(fqn):
    """Extract the simple name from a fully qualified name"""
    last_dot = fqn.rfind('.')
    last_slash = fqn.rfind('/')
    last_separator = max(last_dot, last_slash)
    return fqn[last_separator + 1:] if last_separator >= 0 else fqn

def escape_quotes(text):
    """Escape quotes in a string for Mermaid"""
    return text.replace('"', '\\"')

def build_mermaid_diagram(component_dumps, direction="TD"):
    """Build a Mermaid diagram from the component dumps"""
    lines = [f"graph {direction}"]
    created_nodes = set()

    for component_name, component_dump in component_dumps.items():
        component_id = sanitize_id(component_name)

        # Add component node if not already added
        if component_id not in created_nodes:
            lines.append(f'  {component_id}["{get_simple_name(component_name)}"]')
            created_nodes.add(component_id)

        # Add parent relationships
        if component_dump.get('parent'):
            for parent_name in component_dump['parent']:
                parent_id = sanitize_id(parent_name)

                # Add parent node if not already added
                if parent_id not in created_nodes:
                    lines.append(f'  {parent_id}["{get_simple_name(parent_name)}"]')
                    created_nodes.add(parent_id)

                # Draw relationship from parent to component
                lines.append(f'  {parent_id} -->|"parent"| {component_id}')

        # Add composite relationships
        if component_dump.get('composite'):
            for prop_name, composite_name in component_dump['composite'].items():
                composite_id = sanitize_id(composite_name)

                # Add composite node if not already added
                if composite_id not in created_nodes:
                    lines.append(f'  {composite_id}["{get_simple_name(composite_name)}"]')
                    created_nodes.add(composite_id)

                # Draw relationship from component to composite
                lines.append(f'  {component_id} -->|"{prop_name}"| {composite_id}')

        # Add provider nodes and relationships
        if component_dump.get('providers'):
            for provider in component_dump['providers']:
                provided_type = provider.get('provider', '').split(' ')[-1].split(')')[0]
                provider_id = f"provider_{sanitize_id(component_name)}_{sanitize_id(provided_type)}"

                lines.append(f'  {provider_id}(["{escape_quotes(provider.get("provider", ""))}"])')
                lines.append(f'  {component_id} -->|"provides"| {provider_id}')

                # Add parameters for this provider
                if provider.get('parameters'):
                    for param in provider['parameters']:
                        param_id = sanitize_id(param)
                        if param_id not in created_nodes:
                            lines.append(f'  {param_id}["{get_simple_name(param)}"]')
                            created_nodes.add(param_id)

                        lines.append(f'  {provider_id} -->|"requires"| {param_id}')

        # Add injection relationships
        if component_dump.get('injections'):
            for injection_name, injection in component_dump['injections'].items():
                injection_id = f"injection_{sanitize_id(component_name)}_{sanitize_id(injection_name)}"

                lines.append(f'  {injection_id}(["{escape_quotes(injection.get("methodId", ""))}"])')
                lines.append(f'  {component_id} -->|"injects"| {injection_id}')

                # Add injection parameters recursively
                add_injection_parameters(lines, injection_id, injection, created_nodes)

    return "\n".join(lines)

def add_injection_parameters(lines, parent_id, injection, created_nodes):
    """Recursively add injection parameters to the diagram"""
    if not injection.get('parameters'):
        return

    for index, param in enumerate(injection['parameters']):
        param_id = f"{parent_id}_param{index}"

        lines.append(f'  {param_id}(["{escape_quotes(param.get("methodId", ""))}"])')
        lines.append(f'  {parent_id} -->|"param {index + 1}"| {param_id}')

        # Recursively add parameters for this parameter
        add_injection_parameters(lines, param_id, param, created_nodes)

def generate_mermaid_from_json(json_file_path, output_file_path="", direction="TD"):
    """Generate a Mermaid diagram from a Knit dependency JSON file"""
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

        # Generate the Mermaid diagram content
        mermaid_content = build_mermaid_diagram(component_dumps, direction)

        # Write to output file
        os.makedirs(os.path.dirname(output_file_path), exist_ok=True)
        with open(output_file_path, 'w') as f:
            f.write(mermaid_content)

        print(f"Generated Mermaid diagram at: {output_file_path}")
    except Exception as e:
        print(f"ERROR: Failed to generate Mermaid diagram: {str(e)}")

def main():
    parser = argparse.ArgumentParser(description='Generate Mermaid diagram from Knit dependency JSON')
    parser.add_argument('json_file', help='Path to the Knit dependency JSON file')
    parser.add_argument('-o', '--output', help='Output file path for the Mermaid diagram', default="")
    parser.add_argument('-d', '--direction', help='Diagram direction (TD, LR, RL, BT)', default="TD")

    args = parser.parse_args()

    generate_mermaid_from_json(args.json_file, args.output, args.direction)

if __name__ == '__main__':
    main()