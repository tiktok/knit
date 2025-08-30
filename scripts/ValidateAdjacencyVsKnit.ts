import { AdjacencyList } from './AdjacencyList';

function normalize(name: string): string {
  return name.replace(/\./g, '_').replace(/\//g, '_');
}

type Edge = [string, string];

function extractExpectedEdges(data: Record<string, any>): [Set<string>, Set<string>] {
  const nodes = new Set<string>();
  const edges = new Set<string>();
  const adj = new AdjacencyList();

  for (const classname in data) {
    const details = data[classname];
    const nClass = normalize(classname);
    nodes.add(nClass);
    for (const providerEntry of details.providers || []) {
      const provider = providerEntry.provider || '';
      const consumer = adj.extractConsumerFromProvider(provider);
      if (consumer) {
        const nConsumer = normalize(consumer);
        edges.add(`${nConsumer}->${nClass}`);
        nodes.add(nConsumer);
      }
    }
  }
  return [nodes, edges];
}

function edgesFromAdjacency(adjList: Record<string, string[]>): [Set<string>, Set<string>] {
  const nodes = new Set<string>(Object.keys(adjList));
  const edges = new Set<string>();
  for (const src in adjList) {
    for (const dst of adjList[src]) {
      edges.add(`${src}->${dst}`);
    }
  }
  return [nodes, edges];
}

function validateKnitData(data: Record<string, any>): [boolean, string] {
  const builder = new AdjacencyList();
  const adjList = builder.buildAdjacencyList(data);

  const [adjNodes, adjEdges] = edgesFromAdjacency(adjList);
  const [expNodes, expEdges] = extractExpectedEdges(data);

  const missingNodes = Array.from(expNodes).filter(n => !adjNodes.has(n));
  const extraNodes = Array.from(adjNodes).filter(n => !expNodes.has(n));
  const missingEdges = Array.from(expEdges).filter(e => !adjEdges.has(e));
  const extraEdges = Array.from(adjEdges).filter(e => !expEdges.has(e));

  const lines: string[] = [];
  lines.push("Validation summary:");
  lines.push(`  Nodes expected: ${expNodes.size}, built: ${adjNodes.size}`);
  lines.push(`  Edges expected: ${expEdges.size}, built: ${adjEdges.size}`);

  if (missingNodes.length) {
    lines.push("\nMissing nodes (in knit.json but not in adjacency):");
    missingNodes.sort().forEach(n => lines.push(`  - ${n}`));
  }

  if (extraNodes.length) {
    lines.push("\nExtra nodes (in adjacency but not in knit.json):");
    extraNodes.sort().forEach(n => lines.push(`  - ${n}`));
  }

  if (missingEdges.length) {
    lines.push("\nMissing edges (present in knit.json providers, absent in adjacency):");
    missingEdges.sort().forEach(e => lines.push(`  - ${e.replace('->', ' -> ')}`));
  }

  if (extraEdges.length) {
    lines.push("\nExtra edges (present in adjacency, not in knit.json providers):");
    extraEdges.sort().forEach(e => lines.push(`  - ${e.replace('->', ' -> ')}`));
  }

  const ok = !(missingNodes.length || extraNodes.length || missingEdges.length || extraEdges.length);
  lines.push("\nRESULT: " + (ok ? "PASS — adjacency list matches knit.json" : "FAIL — adjacency list does not match knit.json"));
  return [ok, lines.join('\n')];
}

// Example usage in browser or webview:
function runValidation(jsonData: Record<string, any>) {
  const [ok, report] = validateKnitData(jsonData);
  console.log(report);
  // Optionally display in DOM:
  // document.getElementById('output').textContent = report;
}

// To use, call runValidation with your parsed knit.json data:
// fetch('demo-jvm/build/knit.json')
//   .then(res => res.json())
//   .then(data => runValidation(data));