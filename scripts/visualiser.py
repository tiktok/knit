

class Visualiser:
    # build mermaid diagram from adjacency list

    def get_classname(self, full_classname):
        """
        Extract the simple class name from a fully qualified class name.

        :param full_classname: The fully qualified class name (e.g., "knit.demo.AddCommand").
        :return: The simple class name (e.g., "AddCommand").
        """
        return full_classname.split('.')[-1].split('/')[-1]

    def build_mermaid_diagram(self, adjacency_list, direction="TD"):
        """
        Build a Mermaid diagram from an adjacency list.

        :param adjacency_list: A dictionary representing the adjacency list.
        :param direction: The direction of the graph (e.g., "TD" for top-down, "LR" for left-right).
        :return: A string representing the Mermaid diagram.
        """
        mermaid_diagram = [f"graph {direction}"]

        for node, neighbors in adjacency_list.items():
            if not neighbors:
                # Add the node even if it has no edges
                mermaid_diagram.append(f"    {node}")
            for neighbor in neighbors:
                if node != neighbor:
                    mermaid_diagram.append(f"    {node} --> {neighbor}")

        return "\n".join(mermaid_diagram)