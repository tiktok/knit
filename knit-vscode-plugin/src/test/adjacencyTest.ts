import { buildFromDump, normalizeClassname, extractConsumerFromProvider } from "../knit/adjacency";

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

function runAdjacencyTests() {
  // Test normalizeClassname
  assert(normalizeClassname("knit/demo/AddCommand") === "knit_demo_AddCommand", "normalizeClassname should convert '/' and '.' to '_'");

  // Test extractConsumerFromProvider
  const providerStr = "knit.demo.AddCommand.<init> -> knit.demo.GitCommand";
  const consumer = extractConsumerFromProvider(providerStr);
  assert(consumer === "knit.demo.GitCommand", "extractConsumerFromProvider should return 'knit.demo.GitCommand'");

  // Test buildFromDump
  const { nodes, edges } = buildFromDump(sampleJson);
  assert(Object.keys(nodes).length === 3, "buildFromDump should create 3 nodes (including consumer)");
  assert("knit_demo_AddCommand" in nodes, "Node 'knit_demo_AddCommand' should be present");
  assert("knit_demo_GitCommand" in nodes, "Node 'knit_demo_GitCommand' should be present");
  assert(edges.some(e => e[0] === "knit_demo_GitCommand" && e[1] === "knit_demo_AddCommand"), "Edge ('knit_demo_GitCommand', 'knit_demo_AddCommand') should be present");

  console.log("All adjacency.ts tests passed!");
}

try {
  runAdjacencyTests();
} catch (e) {
  console.error(e);
}