import unittest
from scripts.adjacency_list import AdjacencyList


class TestAdjacencyList(unittest.TestCase):
    def setUp(self):
        self.sample_json = {
            "knit/demo/AddCommand": {
                "parent": ["knit.demo.GitCommand"],
                "providers": [
                    {
                        "provider": "knit.demo.AddCommand.<init> -> knit.demo.GitCommand",
                        "parameters": [
                            "knit.demo.MemoryFileSystem",
                            "knit.demo.MemoryObjectStore",
                            "knit.demo.StagingArea",
                        ],
                    }
                ],
            },
            "knit/demo/AuditLogger": {
                "parent": ["java.lang.Object"],
                "providers": [
                    {
                        "provider": "knit.demo.AuditLogger.<init> -> knit.demo.AuditLogger",
                        "parameters": ["knit.demo.EventBus"],
                    }
                ],
            },
        }
        self.adjacency_list = AdjacencyList()

    def test_get_number_of_nodes(self):
        number_of_nodes = self.adjacency_list.get_number_of_nodes(self.sample_json)
        self.assertEqual(number_of_nodes, 2)

    def test_extract_consumer_from_provider(self):
        provider_str = "knit.demo.AddCommand.<init> -> knit.demo.GitCommand"
        consumer = self.adjacency_list.extract_consumer_from_provider(provider_str)
        self.assertEqual(consumer, "knit.demo.GitCommand")

    def test_get_nodes_and_edges(self):
        nodes, edges = self.adjacency_list.get_nodes_and_edges(self.sample_json)
        self.assertEqual(len(nodes), 2)
        self.assertIn("knit_demo_AddCommand", nodes)
        self.assertIn(("knit_demo_GitCommand", "knit_demo_AddCommand"), edges)

    def test_build_adjacency_list(self):
        adj_list = self.adjacency_list.build_adjacency_list(self.sample_json)
        self.assertIn("knit_demo_GitCommand", adj_list)
        self.assertIn("knit_demo_AddCommand", adj_list["knit_demo_GitCommand"])


if __name__ == "__main__":
    unittest.main()