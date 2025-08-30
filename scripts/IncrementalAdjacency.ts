// IncrementalAdjacency.ts
import { AdjacencyList } from './AdjacencyList';

export class IncrementalAdjacency {
  helper: AdjacencyList;

  constructor() {
    this.helper = new AdjacencyList();
  }

  normalize(name: string): string {
    return this.helper.normalizeClassname(name);
  }

  ensureNode(adj: Record<string, string[]>, node: string): void {
    if (!(node in adj)) {
      adj[node] = [];
    }
  }

  addEdge(adj: Record<string, string[]>, src: string, dst: string): void {
    this.ensureNode(adj, src);
    this.ensureNode(adj, dst);
    if (!adj[src].includes(dst)) {
      adj[src].push(dst);
      adj[src].sort();
    }
  }

  removeEdge(adj: Record<string, string[]>, src: string, dst: string): void {
    if (src in adj && adj[src].includes(dst)) {
      adj[src] = adj[src].filter(n => n !== dst);
    }
  }

  removeNode(adj: Record<string, string[]>, node: string): void {
    if (node in adj) {
      delete adj[node];
    }
    // Remove any inbound references
    for (const n in adj) {
      adj[n] = adj[n].filter(neighbor => neighbor !== node);
    }
  }

  applyChange(adj: Record<string, string[]>, changeData: Record<string, any>): Record<string, string[]> {
    for (const classname in changeData) {
      const details = changeData[classname];
      const nClass = this.normalize(classname);
      const status = details.status || {};
      if (status.removed) {
        this.removeNode(adj, nClass);
        continue;
      }

      // Ensure node exists (for added or modified)
      this.ensureNode(adj, nClass);

      const delta = details.delta || {};
      for (const providerStr of delta.providersAdded || []) {
        const consumer = this.helper.extractConsumerFromProvider(providerStr);
        if (consumer) {
          this.addEdge(adj, this.normalize(consumer), nClass);
        }
      }

      for (const providerStr of delta.providersRemoved || []) {
        const consumer = this.helper.extractConsumerFromProvider(providerStr);
        if (consumer) {
          this.removeEdge(adj, this.normalize(consumer), nClass);
        }
      }
    }
    return adj;
  }
}