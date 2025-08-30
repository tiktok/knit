import re
from dataclasses import dataclass
from typing import Any, Dict, List, Tuple, Optional, Deque, Set


@dataclass
class Node:
    """Graph node with metadata.

    name is the normalized classname used across the graph.
    is_optimistic/is_error are derived from status in knit.json or change files.
    is_in_last_update indicates participation in the most recent apply.
    has_upstream_error is derived: True if this node depends (directly or transitively) on any error node.
    """
    name: str
    is_optimistic: bool = False
    # True if this node itself is a source of an error (binding/compile-time in the dump)
    is_source_error: bool = False
    # Optional detail message about the source error, if available
    error_message: Optional[str] = None
    is_in_last_update: bool = False
    has_upstream_error: bool = False


class AdjacencyList:
    def __init__(self) -> None:
        self.nodes: Dict[str, Node] = {}
        self.edges: List[Tuple[str, str]] = []

    def get_number_of_nodes(self, file: Dict[str, Any]) -> int:
        return len(file)

    def extract_consumer_from_provider(self, provider_str: str) -> Optional[str]:
        if not provider_str or '->' not in provider_str:
            return None
        rhs = provider_str.split('->', 1)[1].strip()
        rhs = re.sub(r'<[^<>]*>', '', rhs).strip()
        return rhs.strip('() ')

    def get_nodes_and_edges(self, data: Dict[str, Any]) -> Tuple[Dict[str, str], List[Tuple[str, str]]]:
        # Reset state so repeated calls don't accumulate
        self.nodes = {}
        self.edges = []

        for classname, details in data.items():
            normalized_classname = self.normalize_classname(classname)
            if normalized_classname not in self.nodes:
                self.nodes[normalized_classname] = Node(name=normalized_classname)

            status: Optional[Dict[str, Any]] = details.get('status') if isinstance(details, dict) else None
            if isinstance(status, dict):
                node = self.nodes[normalized_classname]
                if isinstance(status.get('error'), bool):
                    node.is_source_error = bool(status['error'])
                if isinstance(status.get('optimistic'), bool):
                    node.is_optimistic = bool(status['optimistic'])
                # Attempt to capture an error message if present under common keys
                msg = status.get('message') or status.get('errorMessage') or status.get('reason')
                if isinstance(msg, str):
                    node.error_message = msg

            for provider_entry in details.get('providers', []):
                provider = provider_entry.get('provider', '')
                consumer = self.extract_consumer_from_provider(provider)
                if consumer:
                    normalized_consumer = self.normalize_classname(consumer)
                    self.edges.append((normalized_consumer, normalized_classname))

        nodes_map: Dict[str, str] = {name: name for name in self.nodes.keys()}
        return nodes_map, self.edges

    def build_adjacency_list(self, file: Dict[str, Any]) -> Dict[str, List[str]]:
        _ = self.get_number_of_nodes(file)
        nodes, edges = self.get_nodes_and_edges(file)

        adjacency_list: Dict[str, List[str]] = {}
        for node in nodes:
            adjacency_list[node] = []

        for vertex1, vertex2 in edges:
            if vertex1 in adjacency_list:
                adjacency_list[vertex1].append(vertex2)
            else:
                adjacency_list[vertex1] = [vertex2]

        for vertex in list(adjacency_list.keys()):
            if adjacency_list[vertex]:
                adjacency_list[vertex] = sorted(list(dict.fromkeys(adjacency_list[vertex])))

        return adjacency_list

    def build_adjacency_with_meta(self, file: Dict[str, Any]) -> Tuple[Dict[str, List[str]], Dict[str, Dict[str, bool]]]:
        adj = self.build_adjacency_list(file)
        self.propagate_error_flags_from_adj(adj)
        return adj, self.get_node_status()

    def get_node_status(self) -> Dict[str, Dict[str, Any]]:
        # Expose clearer keys; include legacy aliases for compatibility
        out: Dict[str, Dict[str, Any]] = {}
        for name, n in self.nodes.items():
            entry: Dict[str, Any] = {
                "source_error": n.is_source_error,
                "upstream_error": n.has_upstream_error,
                "optimistic": n.is_optimistic,
            }
            if n.error_message:
                entry["error_message"] = n.error_message
            # legacy aliases
            entry["error"] = n.is_source_error
            entry["impacted"] = n.has_upstream_error
            out[name] = entry
        return out

    def get_nodes(self) -> Dict[str, Node]:
        return dict(self.nodes)

    def reset_last_update_flags(self) -> None:
        for n in self.nodes.values():
            n.is_in_last_update = False

    def mark_full_build_update(self, update_id: Optional[str] = None, ts: Optional[float] = None) -> None:
        for n in self.nodes.values():
            n.is_in_last_update = True

    def apply_status_change(self, change_data: Dict[str, Any], update_id: Optional[str] = None, ts: Optional[float] = None) -> None:
        for classname, details in change_data.items():
            n_name = self.normalize_classname(classname)
            if n_name not in self.nodes:
                self.nodes[n_name] = Node(name=n_name)
            node = self.nodes[n_name]
            status = details.get('status', {}) if isinstance(details, dict) else {}
            if isinstance(status, dict):
                if isinstance(status.get('error'), bool):
                    node.is_source_error = bool(status['error'])
                if isinstance(status.get('optimistic'), bool):
                    node.is_optimistic = bool(status['optimistic'])
                msg = status.get('message') or status.get('errorMessage') or status.get('reason')
                if isinstance(msg, str):
                    node.error_message = msg
            node.is_in_last_update = True

    def propagate_error_flags_from_adj(self, adj: Dict[str, List[str]]) -> None:
        # Reset derived flags
        for n in self.nodes.values():
            n.has_upstream_error = False

        # Build reverse adjacency: dst -> [src]
        rev: Dict[str, List[str]] = {}
        for src, neighbors in adj.items():
            for dst in neighbors:
                rev.setdefault(dst, []).append(src)

        # Seed queue with error nodes that exist in graph
        from collections import deque
        q: Deque[str] = deque()
        seen: Set[str] = set()
        for name, node in self.nodes.items():
            if node.is_source_error and name in rev:
                q.append(name)
                seen.add(name)

        # BFS on reverse graph to mark upstream dependents as impacted
        while q:
            err = q.popleft()
            for dependent in rev.get(err, []):
                if dependent not in self.nodes:
                    self.nodes[dependent] = Node(name=dependent)
                dep_node = self.nodes[dependent]
                if not dep_node.has_upstream_error:
                    dep_node.has_upstream_error = True
                if dependent not in seen:
                    seen.add(dependent)
                    q.append(dependent)

    def normalize_classname(self, classname: str) -> str:
        return classname.replace('.', '_').replace('/', '_')