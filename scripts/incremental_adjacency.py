from typing import Dict, List, Any

from scripts.adjacency_list import AdjacencyList


class IncrementalAdjacency:
    """
    Maintains and updates an adjacency list incrementally using change files.
    """

    def __init__(self):
        self.helper = AdjacencyList()

    def normalize(self, name: str) -> str:
        return self.helper.normalize_classname(name)

    def ensure_node(self, adj: Dict[str, List[str]], node: str):
        if node not in adj:
            adj[node] = []

    def add_edge(self, adj: Dict[str, List[str]], src: str, dst: str):
        self.ensure_node(adj, src)
        self.ensure_node(adj, dst)
        if dst not in adj[src]:
            adj[src].append(dst)
            adj[src].sort()

    def remove_edge(self, adj: Dict[str, List[str]], src: str, dst: str):
        if src in adj and dst in adj[src]:
            adj[src].remove(dst)

    def remove_node(self, adj: Dict[str, List[str]], node: str):
        if node in adj:
            del adj[node]
        # Remove any inbound references
        for n, neighbors in list(adj.items()):
            if node in neighbors:
                neighbors.remove(node)

    def apply_change(self, adj: Dict[str, List[str]], change_data: Dict[str, Any]) -> Dict[str, List[str]]:
        """
        Apply a single change JSON (map keyed by class) to the adjacency list.
        Uses delta.providersAdded/providersRemoved to adjust edges.
        Removes nodes when status.removed is true; ensures nodes on added.
        """
        for classname, details in change_data.items():
            n_class = self.normalize(classname)
            status = details.get("status", {})
            if status.get("removed"):
                self.remove_node(adj, n_class)
                continue

            # Ensure node exists (for added or modified)
            self.ensure_node(adj, n_class)

            delta = details.get("delta", {})
            for provider_str in delta.get("providersAdded", []):
                consumer = self.helper.extract_consumer_from_provider(provider_str)
                if consumer:
                    self.add_edge(adj, self.normalize(consumer), n_class)

            for provider_str in delta.get("providersRemoved", []):
                consumer = self.helper.extract_consumer_from_provider(provider_str)
                if consumer:
                    self.remove_edge(adj, self.normalize(consumer), n_class)

        return adj
