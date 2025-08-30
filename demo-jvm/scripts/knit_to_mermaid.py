import json
import os
import re
import time
import argparse

def sanitize_id(name):
    """Convert a fully qualified name to a valid Mermaid node ID"""
    sanitized = re.sub(r'[.\/$<>() ,<>]', '_', name)
    return f"node_{sanitized}"

def get_simple_name(fqn):
    """Extract the simple name from a fully qualified name"""
    fqn = fqn.replace('/', '.')
    last_dot = fqn.rfind('.')
    return fqn[last_dot + 1:] if last_dot >= 0 else fqn

def extract_consumer_from_provider(provider_str):
    """
    Extract the consumer class (right-hand side of '->') from the provider string.
    Handles common formatting and strips generics and surrounding punctuation.
    """
    if not provider_str or '->' not in provider_str:
        return None
    rhs = provider_str.split('->', 1)[1].strip()
    rhs = re.sub(r'<[^<>]*>', '', rhs).strip()
    return rhs.strip('() ')

def build_mermaid_diagram(component_dumps, direction="TD"):
    """
    Build a Mermaid class-to-class diagram showing only consumer -> provider edges.
    - Only class nodes (no parameters or parent info).
    - Consumers point to providers: consumer --> provider.
    - Skip self-references and avoid duplicate nodes/edges.
    """
    lines = [f"graph {direction}"]
    created_nodes = set()
    edges = set()

    for provider_class, dump in component_dumps.items():
        provider_class = provider_class.replace('/', '.')
        provider_id = sanitize_id(provider_class)
        provider_label = get_simple_name(provider_class)

        # Ensure provider node exists
        if provider_id not in created_nodes:
            lines.append(f'  {provider_id}["{provider_label}"]')
            created_nodes.add(provider_id)

        for provider_entry in dump.get('providers', []):
            consumer = extract_consumer_from_provider(provider_entry.get('provider', ''))
            if not consumer:
                continue

            consumer = consumer.replace('/', '.')
            if consumer == provider_class:  # Skip self-references
                continue

            consumer_id = sanitize_id(consumer)
            consumer_label = get_simple_name(consumer)

            # Add consumer node if missing
            if consumer_id not in created_nodes:
                lines.append(f'  {consumer_id}["{consumer_label}"]')
                created_nodes.add(consumer_id)

            edge_key = (consumer_id, provider_id)
            if edge_key not in edges:
                lines.append(f'  {consumer_id} --> {provider_id}')
                edges.add(edge_key)

    return "\n".join(lines)

def generate_mermaid_from_json(json_file_path, output_file_path="", direction="TD"):
    """Generate a Mermaid diagram from a Knit dependency JSON file."""
    if not os.path.exists(json_file_path):
        print(f"ERROR: JSON file not found: {json_file_path}")
        return None

    if not output_file_path:
        output_dir = os.path.dirname(json_file_path)
        base_name = os.path.splitext(os.path.basename(json_file_path))[0]
        output_file_path = os.path.join(output_dir, f"{base_name}.mmd")

    try:
        with open(json_file_path, 'r', encoding='utf-8') as f:
            component_dumps = json.load(f)

        mermaid_content = build_mermaid_diagram(component_dumps, direction)

        os.makedirs(os.path.dirname(output_file_path), exist_ok=True)
        prev_content = None
        if os.path.exists(output_file_path):
            with open(output_file_path, 'r', encoding='utf-8') as f:
                prev_content = f.read()

        if mermaid_content != prev_content:
            with open(output_file_path, 'w', encoding='utf-8') as f:
                f.write(mermaid_content)
            print(f"Generated Mermaid diagram at: {output_file_path}")
        else:
            print(f"No changes detected; {output_file_path} not modified.")

        return mermaid_content
    except Exception as e:
        print(f"ERROR: Failed to generate Mermaid diagram: {str(e)}")
        return None

def watch_and_generate(json_file, output_file, direction, poll_interval=1.0):
    """Watch the JSON file for changes and regenerate the diagram on modification."""
    last_json_text = None
    print(f"Watching {json_file} for changes (press Ctrl-C to stop)...")
    try:
        while True:
            try:
                with open(json_file, 'r', encoding='utf-8') as f:
                    current_text = f.read()
            except Exception:
                current_text = None

            if current_text is not None and current_text != last_json_text:
                generate_mermaid_from_json(json_file, output_file, direction)
                last_json_text = current_text

            time.sleep(poll_interval)
    except KeyboardInterrupt:
        print("Stopped watching.")
    except Exception as e:
        print(f"ERROR while watching file: {e}")

def main():
    parser = argparse.ArgumentParser(description='Generate Mermaid diagram from Knit dependency JSON (class-to-class consumer->provider)')
    parser.add_argument('json_file', help='Path to the Knit dependency JSON file')
    parser.add_argument('-o', '--output', help='Output file path for the Mermaid diagram', default="")
    parser.add_argument('-d', '--direction', help='Diagram direction (TD, LR, RL, BT)', default="TD")
    parser.add_argument('-w', '--watch', help='Watch the JSON file and regenerate on changes', action='store_true')

    args = parser.parse_args()

    if args.watch:
        watch_and_generate(args.json_file, args.output, args.direction)
    else:
        generate_mermaid_from_json(args.json_file, args.output, args.direction)

if __name__ == '__main__':
    main()