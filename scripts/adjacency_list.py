import re
from dataclasses import dataclass
from typing import Any, Dict, List, Tuple, Optional


@dataclass
class Node:
    """Graph node with metadata.

    name is the normalized classname used across the graph.
    is_optimistic/is_error are derived from status in knit.json or change files.
    is_in_last_update indicates participation in the most recent apply.
    """
    name: str
    is_optimistic: bool = False
    is_error: bool = False
    is_in_last_update: bool = False


class AdjacencyList:
    def __init__(self):
        self.nodes: Dict[str, Node] = {}
        self.edges: List[Tuple[str, str]] = []

    def get_number_of_nodes(self, file: Dict[str, Any]):
        """Extract nodes and edges from a Knit dependency JSON file."""
        return len(file)

    def extract_consumer_from_provider(self, provider_str: str) -> Optional[str]:
        """Extract the consumer class (right-hand side of '->') from the provider string."""
        if not provider_str or '->' not in provider_str:
            return None
        rhs = provider_str.split('->', 1)[1].strip()
        rhs = re.sub(r'<[^<>]*>', '', rhs).strip()
        return rhs.strip('() ')

    def get_nodes_and_edges(self, data: Dict[str, Any]) -> Tuple[Dict[str, str], List[Tuple[str, str]]]:
        """Extract nodes and edges from a Knit dependency JSON object.

        Resets internal state on each invocation to avoid duplication across updates.
        """
        # Reset state so repeated calls don't accumulate
        self.nodes = {}
        self.edges = []

        for classname, details in data.items():
            normalized_classname = self.normalize_classname(classname)
            # Ensure node object exists
            if normalized_classname not in self.nodes:
                self.nodes[normalized_classname] = Node(name=normalized_classname)

            # Capture node-level status/optimistic flags if present
            status: Optional[Dict[str, Any]] = details.get('status') if isinstance(details, dict) else None
            if isinstance(status, dict):
                node = self.nodes[normalized_classname]
                if 'error' in status and isinstance(status['error'], bool):
                    node.is_error = bool(status['error'])
                if 'optimistic' in status and isinstance(status['optimistic'], bool):
                    node.is_optimistic = bool(status['optimistic'])

            for provider_entry in details.get('providers', []):
                provider = provider_entry.get('provider', '')
                consumer = self.extract_consumer_from_provider(provider)
                if consumer:
                    normalized_consumer = self.normalize_classname(consumer)
                    self.edges.append((normalized_consumer, normalized_classname))

        # For compatibility, return a simple name->name mapping and edges
        nodes_map: Dict[str, str] = {name: name for name in self.nodes.keys()}
        return nodes_map, self.edges

    def build_adjacency_list(self, file: Dict[str, Any]) -> Dict[str, List[str]]:
        # Build nodes and edges from the JSON object
        _ = self.get_number_of_nodes(file)
        nodes, edges = self.get_nodes_and_edges(file)

        adjacency_list: Dict[str, List[str]] = {}

        # Add vertices to the dictionary
        for node in nodes:
            adjacency_list[node] = []

        # Add edges to the dictionary
        for vertex1, vertex2 in edges:
            if vertex1 in adjacency_list:
                adjacency_list[vertex1].append(vertex2)
            else:
                adjacency_list[vertex1] = [vertex2]

        # Dedupe and sort neighbors for determinism
        for vertex in list(adjacency_list.keys()):
            if adjacency_list[vertex]:
                adjacency_list[vertex] = sorted(list(dict.fromkeys(adjacency_list[vertex])))

        return adjacency_list

    def build_adjacency_with_meta(self, file: Dict[str, Any]) -> Tuple[Dict[str, List[str]], Dict[str, Dict[str, bool]]]:
        """Build adjacency plus node metadata (error/optimistic flags).

        Returns (adjacency, node_status) where node_status is keyed by normalized node name.
        The plain build_adjacency_list API remains unchanged for compatibility.
        """
        adj = self.build_adjacency_list(file)
        return adj, self.get_node_status()

    def get_node_status(self) -> Dict[str, Dict[str, bool]]:
        """Return {node: {"error": bool, "optimistic": bool}} computed from Node objects."""
        return {name: {"error": n.is_error, "optimistic": n.is_optimistic} for name, n in self.nodes.items()}

    def get_nodes(self) -> Dict[str, Node]:
        """Access Node objects keyed by normalized name."""
        return dict(self.nodes)

    def reset_last_update_flags(self):
        """Clear is_in_last_update on all nodes before marking a new update."""
        for n in self.nodes.values():
            n.is_in_last_update = False

    def mark_full_build_update(self, update_id: Optional[str] = None, ts: Optional[float] = None):
        """Mark all nodes as part of the last update (used on full rebuild)."""
        for n in self.nodes.values():
            n.is_in_last_update = True

    def apply_status_change(self, change_data: Dict[str, Any], update_id: Optional[str] = None, ts: Optional[float] = None):
        """Apply status fields from a change file to Node objects and mark touched nodes as last-updated."""
        for classname, details in change_data.items():
            n_name = self.normalize_classname(classname)
            if n_name not in self.nodes:
                self.nodes[n_name] = Node(name=n_name)
            node = self.nodes[n_name]
            status = details.get('status', {}) if isinstance(details, dict) else {}
            if isinstance(status, dict):
                if 'error' in status and isinstance(status['error'], bool):
                    node.is_error = bool(status['error'])
                if 'optimistic' in status and isinstance(status['optimistic'], bool):
                    node.is_optimistic = bool(status['optimistic'])
            node.is_in_last_update = True

    def normalize_classname(self, classname: str) -> str:
        """Normalize the class name for consistency (dots/slashes -> underscores)."""
        return classname.replace('.', '_').replace('/', '_')