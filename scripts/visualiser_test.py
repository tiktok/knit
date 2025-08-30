import unittest
from scripts.visualiser import Visualiser


class TestVisualiser(unittest.TestCase):
    def test_mermaid_builds(self):
        adjacency_list = {
            "knit.demo.AddCommand": [],
            "knit.demo.AuditLogger": [],
            "knit.demo.GitCommand": ["knit.demo.AddCommand"],
        }
        visualiser = Visualiser()
        mermaid_diagram = visualiser.build_mermaid_diagram(adjacency_list, direction="TD")
        self.assertIn("graph TD", mermaid_diagram)
        self.assertIn("knit.demo.GitCommand --> knit.demo.AddCommand", mermaid_diagram)


if __name__ == "__main__":
    unittest.main()