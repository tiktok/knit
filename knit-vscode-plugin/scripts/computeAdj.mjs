// Run after: npm run build
// Computes adjacency from demo-jvm/build/knit.json (+ latest change if present)
import fs from 'fs/promises';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const adjMod = await import(path.join(__dirname, '..', 'out', 'knit', 'adjacency.js'));
const incMod = await import(path.join(__dirname, '..', 'out', 'knit', 'incremental.js'));

const { buildFromDump, applyChangeStatus, propagateUpstreamError } = adjMod;
const { IncrementalAdjacency } = incMod;

async function main() {
  const repoRoot = path.resolve(__dirname, '..', '..');
  const basePath = path.join(repoRoot, 'demo-jvm', 'build', 'knit.json');
  const changesDir = path.join(repoRoot, 'demo-jvm', 'build', 'changes');

  const baseRaw = await fs.readFile(basePath, 'utf8');
  const baseDump = JSON.parse(baseRaw);
  let graph = buildFromDump(baseDump);

  try {
    const latestTxt = await fs.readFile(path.join(changesDir, 'latest.txt'), 'utf8');
    const latestFile = latestTxt.trim();
    const changeRaw = await fs.readFile(path.join(changesDir, latestFile), 'utf8');
    const changeDump = JSON.parse(changeRaw);
    applyChangeStatus(graph.nodes, changeDump);
    const inc = new IncrementalAdjacency();
    graph = inc.applyChange(graph, changeDump);
    propagateUpstreamError(graph.edges, graph.nodes);
  } catch {
    // no changes
  }

  const nodeCount = Object.keys(graph.nodes).length;
  const edgeCount = graph.edges.length;
  console.log(JSON.stringify({ nodeCount, edgeCount, sampleEdges: graph.edges.slice(0, 10) }, null, 2));
}

main().catch(e => { console.error(e); process.exit(1); });
