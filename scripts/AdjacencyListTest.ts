import { AdjacencyList } from './AdjacencyList';

const sampleJson = {
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
};

function assert(condition: boolean, message: string) {
  if (!condition) {
    throw new Error("Test failed: " + message);
  }
}

function runAdjacencyListTests() {
  const adjacencyList = new AdjacencyList();

  // Test getNumberOfNodes
  const numberOfNodes = adjacencyList.getNumberOfNodes(sampleJson);
  assert(numberOfNodes === 2, "getNumberOfNodes should return 2");

  // Test extractConsumerFromProvider
  const providerStr = "knit.demo.AddCommand.<init> -> knit.demo.GitCommand";
  const consumer = adjacencyList.extractConsumerFromProvider(providerStr);
  assert(consumer === "knit.demo.GitCommand", "extractConsumerFromProvider should return 'knit.demo.GitCommand'");

  // Test getNodesAndEdges
  const [nodes, edges] = adjacencyList.getNodesAndEdges(sampleJson);
  assert(Object.keys(nodes).length === 2, "getNodesAndEdges should return 2 nodes");
  assert("knit_demo_AddCommand" in nodes, "Node 'knit_demo_AddCommand' should be present");
  assert(edges.some(e => e[0] === "knit_demo_GitCommand" && e[1] === "knit_demo_AddCommand"), "Edge ('knit_demo_GitCommand', 'knit_demo_AddCommand') should be present");

  // Test buildAdjacencyList
  const adjList = adjacencyList.buildAdjacencyList(sampleJson);
  assert("knit_demo_GitCommand" in adjList, "Adjacency list should contain 'knit_demo_GitCommand'");
  assert(adjList["knit_demo_GitCommand"].includes("knit_demo_AddCommand"), "'knit_demo_AddCommand' should be in adjList['knit_demo_GitCommand']");

  console.log("All AdjacencyList tests passed!");
}

try {
  runAdjacencyListTests();
} catch (e) {
  console.error(e);
}