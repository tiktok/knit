import type { AdjacencyList } from './interfaces';
import { extractConsumerFromProvider, normalizeClassname } from './adjacency';

export type ChangeDump = Record<string, {
  status?: { removed?: boolean } & Record<string, any>;
  delta?: { providersAdded?: string[]; providersRemoved?: string[] } & Record<string, any>;
}>;

export class IncrementalAdjacency {
  ensureNode(adj: AdjacencyList, node: string) {
    if (!adj.nodes[node]) {
      adj.nodes[node] = {
        name: node,
        isOptimistic: false,
        isSourceError: false,
        isInLastUpdate: false,
        hasUpstreamError: false,
      };
    }
  }

  addEdge(adj: AdjacencyList, src: string, dst: string) {
    const key = `${src}\u0000${dst}`;
    const exists = adj.edges.some(([s, d]) => s === src && d === dst);
    if (!exists) {
      adj.edges.push([src, dst]);
    }
  }

  removeEdge(adj: AdjacencyList, src: string, dst: string) {
    adj.edges = adj.edges.filter(([s, d]) => !(s === src && d === dst));
  }

  removeNode(adj: AdjacencyList, node: string) {
    delete adj.nodes[node];
    adj.edges = adj.edges.filter(([s, d]) => s !== node && d !== node);
  }

  applyChange(adj: AdjacencyList, change: ChangeDump): AdjacencyList {
    for (const [classname, details] of Object.entries(change)) {
      const nClass = normalizeClassname(classname);
      const status = (details as any)?.status ?? {};
      if (status?.removed) {
        this.removeNode(adj, nClass);
        continue;
      }
      // ensure node for added/modified
      this.ensureNode(adj, nClass);

      const delta = (details as any)?.delta ?? {};
      for (const providerStr of (delta.providersAdded ?? [])) {
        const consumer = extractConsumerFromProvider(providerStr);
        if (consumer) {
          this.addEdge(adj, normalizeClassname(consumer), nClass);
        }
      }
      for (const providerStr of (delta.providersRemoved ?? [])) {
        const consumer = extractConsumerFromProvider(providerStr);
        if (consumer) {
          this.removeEdge(adj, normalizeClassname(consumer), nClass);
        }
      }
    }
    return adj;
  }
}
