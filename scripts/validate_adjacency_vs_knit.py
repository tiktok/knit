#!/usr/bin/env python3

"""
Validate that the generated adjacency list from knit.json matches what knit.json describes.

Rules:
- Nodes are the normalized class keys in knit.json.
- Edges are derived from each providers[*].provider field as:
  (consumer, classname) where consumer is the RHS of '->' and classname is the JSON key.

The script compares the expected set of edges with the edges implied by the adjacency list.
It prints a summary and exits with non-zero code when mismatches exist.
"""

import argparse
import json
import sys
from typing import Dict, Any, List, Set, Tuple

from scripts.adjacency_list import AdjacencyList


def normalize(name: str) -> str:
    return name.replace('.', '_').replace('/', '_')


def extract_expected_edges(data: Dict[str, Any]) -> Tuple[Set[str], Set[Tuple[str, str]]]:
    nodes: Set[str] = set()
    edges: Set[Tuple[str, str]] = set()

    adj = AdjacencyList()
    for classname, details in data.items():
        n_class = normalize(classname)
        nodes.add(n_class)
        for provider_entry in details.get('providers', []):
            provider = provider_entry.get('provider', '')
            consumer = adj.extract_consumer_from_provider(provider)
            if consumer:
                n_consumer = normalize(consumer)
                edges.add((n_consumer, n_class))
                # Include consumer nodes as they legitimately appear in the graph
                nodes.add(n_consumer)
    return nodes, edges


def edges_from_adjacency(adj_list: Dict[str, List[str]]) -> Tuple[Set[str], Set[Tuple[str, str]]]:
    nodes: Set[str] = set(adj_list.keys())
    edges: Set[Tuple[str, str]] = set()
    for src, neighbors in adj_list.items():
        for dst in neighbors:
            edges.add((src, dst))
    return nodes, edges


def validate_knit_file(json_file: str) -> Tuple[bool, str]:
    """Return (ok, report) comparing adjacency vs knit.json for a given file."""
    with open(json_file, "r") as f:
        data = json.load(f)

    builder = AdjacencyList()
    adj_list = builder.build_adjacency_list(data)

    adj_nodes, adj_edges = edges_from_adjacency(adj_list)
    exp_nodes, exp_edges = extract_expected_edges(data)

    missing_nodes = exp_nodes - adj_nodes
    extra_nodes = adj_nodes - exp_nodes
    missing_edges = exp_edges - adj_edges
    extra_edges = adj_edges - exp_edges

    def fmt_edge(e: Tuple[str, str]) -> str:
        return f"{e[0]} -> {e[1]}"

    lines = []
    lines.append("Validation summary:")
    lines.append(f"  Nodes expected: {len(exp_nodes)}, built: {len(adj_nodes)}")
    lines.append(f"  Edges expected: {len(exp_edges)}, built: {len(adj_edges)}")

    if missing_nodes:
        lines.append("\nMissing nodes (in knit.json but not in adjacency):")
        for n in sorted(missing_nodes):
            lines.append(f"  - {n}")

    if extra_nodes:
        lines.append("\nExtra nodes (in adjacency but not in knit.json):")
        for n in sorted(extra_nodes):
            lines.append(f"  - {n}")

    if missing_edges:
        lines.append("\nMissing edges (present in knit.json providers, absent in adjacency):")
        for e in sorted(missing_edges):
            lines.append(f"  - {fmt_edge(e)}")

    if extra_edges:
        lines.append("\nExtra edges (present in adjacency, not in knit.json providers):")
        for e in sorted(extra_edges):
            lines.append(f"  - {fmt_edge(e)}")

    ok = not (missing_nodes or extra_nodes or missing_edges or extra_edges)
    lines.append("\nRESULT: " + ("PASS — adjacency list matches knit.json" if ok else "FAIL — adjacency list does not match knit.json"))
    return ok, "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description="Validate adjacency list vs knit.json")
    parser.add_argument("json_file", help="Path to knit.json (e.g., demo-jvm/build/knit.json)")
    args = parser.parse_args()

    ok, report = validate_knit_file(args.json_file)
    print(report)
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
