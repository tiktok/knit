from visualiser import Visualiser

# Example adjacency list
adjacency_list = {
    "knit.demo.AddCommand": [],
    "knit.demo.AuditLogger": [],
    "knit.demo.GitCommand": ["knit.demo.AddCommand"],
    "knit.demo.AuditLogger": ["knit.demo.AuditLogger"]
}

# Create an instance of Visualiser
visualiser = Visualiser()

# Build the Mermaid diagram
mermaid_diagram = visualiser.build_mermaid_diagram(adjacency_list, direction="TD")

# Print the Mermaid diagram
print(mermaid_diagram)