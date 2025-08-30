import json
import re

class Node:
    def __init__(self, classname: str):
        self.classname = classname
        
        

class AdjacencyList:
    def __init__(self):
        self.nodes = {}
        self.edges = []

    def get_number_of_nodes(self, file: json):
        """Extract nodes and edges from a Knit dependency JSON file."""
        
        number_of_nodes = len(file)
        return number_of_nodes
    
    def extract_consumer_from_provider(self, provider_str):
        """
        Extract the consumer class (right-hand side of '->') from the provider string.
        Handles common formatting and strips generics and surrounding punctuation.
        """
        if not provider_str or '->' not in provider_str:
            return None
        rhs = provider_str.split('->', 1)[1].strip()
        rhs = re.sub(r'<[^<>]*>', '', rhs).strip()
        return rhs.strip('() ')

    def get_nodes_and_edges(self, data: dict):
        """Extract edges from a Knit dependency JSON object."""
        for classname, details in data.items():
            normalized_classname = self.normalize_classname(classname)
            if normalized_classname not in self.nodes:
                self.nodes[normalized_classname] = normalized_classname

            for provider_entry in details.get('providers', []):
                provider = provider_entry.get('provider', '')
                consumer = self.extract_consumer_from_provider(provider)
                if consumer:
                    normalized_consumer = self.normalize_classname(consumer)
                    self.edges.append((normalized_consumer, normalized_classname))


        return self.nodes, self.edges
    
    def build_adjacency_list(self, file: json):

        number_of_nodes = self.get_number_of_nodes(file)
        nodes, edges = self.get_nodes_and_edges(file)
        adjacency_list = {}

    # Add vertices to the dictionary
        for node in nodes:
            adjacency_list[node] = []

        # Add edges to the dictionary
        for edge in edges:
            vertex1, vertex2 = edge
            if vertex1 in adjacency_list:
                adjacency_list[vertex1].append(vertex2)
            else:
                adjacency_list[vertex1] = [vertex2]

        # Display the adjacency list
        for vertex, neighbors in adjacency_list.items():
            print(f"{vertex} -> {' '.join(map(str, neighbors))}")
        
        return adjacency_list
    
    def normalize_classname(self, classname):
        """
        Normalize the class name to ensure consistency between different formats.

        :param classname: The class name (e.g., "knit.Loadable" or "knit/Loadable").
        :return: The normalized class name (e.g., "knit_Loadable").
        """
        return classname.replace('.', '_').replace('/', '_')