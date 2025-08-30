import { Node } from './Node';

export class AdjacencyList {
  nodes: { [key: string]: Node } = {};
  edges: Array<[string, string]> = [];

  getNumberOfNodes(file: Record<string, any>): number {
    return Object.keys(file).length;
  }

  extractConsumerFromProvider(providerStr: string): string | null {
    if (!providerStr || providerStr.indexOf('->') === -1) return null;
    let rhs = providerStr.split('->', 2)[1].trim();
    rhs = rhs.replace(/<[^<>]*>/g, '').trim();
    return rhs.replace(/[() ]/g, '').trim();
  }

  getNodesAndEdges(data: Record<string, any>): [{ [key: string]: string }, Array<[string, string]>] {
    this.nodes = {};
    this.edges = [];

    for (const classname in data) {
      const details = data[classname];
      const normalizedClassname = this.normalizeClassname(classname);
      if (!this.nodes[normalizedClassname]) {
        this.nodes[normalizedClassname] = new Node(normalizedClassname);
      }

      const status = typeof details === 'object' ? details['status'] : undefined;
      if (status && typeof status === 'object') {
        const node = this.nodes[normalizedClassname];
        if (typeof status['error'] === 'boolean') node.isSourceError = status['error'];
        if (typeof status['optimistic'] === 'boolean') node.isOptimistic = status['optimistic'];
        const msg = status['message'] || status['errorMessage'] || status['reason'];
        if (typeof msg === 'string') node.errorMessage = msg;
      }
      for (const providerEntry of details['providers'] || []) {
        const provider = providerEntry['provider'] || '';
        const consumer = this.extractConsumerFromProvider(provider);
        if (consumer) {
          const normalizedConsumer = this.normalizeClassname(consumer);
          this.edges.push([normalizedConsumer, normalizedClassname]);
        }
      }
    }

    const nodesMap: { [key: string]: string } = {};
    Object.keys(this.nodes).forEach(name => nodesMap[name] = name);
    return [nodesMap, this.edges];
  }

  buildAdjacencyList(file: Record<string, any>): { [key: string]: string[] } {
    this.getNumberOfNodes(file);
    const [nodes, edges] = this.getNodesAndEdges(file);

    const adjacencyList: { [key: string]: string[] } = {};
    for (const node in nodes) {
      adjacencyList[node] = [];
    }

    for (const [vertex1, vertex2] of edges) {
      if (adjacencyList[vertex1]) {
        adjacencyList[vertex1].push(vertex2);
      } else {
        adjacencyList[vertex1] = [vertex2];
      }
    }

    Object.keys(adjacencyList).forEach(vertex => {
      if (adjacencyList[vertex].length) {
        adjacencyList[vertex] = Array.from(new Set(adjacencyList[vertex])).sort();
      }
    });

    return adjacencyList;
  }

  buildAdjacencyWithMeta(file: Record<string, any>): [{ [key: string]: string[] }, { [key: string]: any }] {
    const adj = this.buildAdjacencyList(file);
    this.propagateErrorFlagsFromAdj(adj);
    return [adj, this.getNodeStatus()];
  }

  getNodeStatus(): { [key: string]: any } {
    const out: { [key: string]: any } = {};
    for (const name in this.nodes) {
      const n = this.nodes[name];
      const entry: { [key: string]: any } = {
        source_error: n.isSourceError,
        upstream_error: n.hasUpstreamError,
        optimistic: n.isOptimistic,
        error: n.isSourceError, // legacy
        impacted: n.hasUpstreamError // legacy
      };
      if (n.errorMessage) entry['error_message'] = n.errorMessage;
      out[name] = entry;
    }
    return out;
  }

  getNodes(): { [key: string]: Node } {
    return { ...this.nodes };
  }

  resetLastUpdateFlags(): void {
    Object.values(this.nodes).forEach(n => n.isInLastUpdate = false);
  }

  markFullBuildUpdate(): void {
    Object.values(this.nodes).forEach(n => n.isInLastUpdate = true);
  }

  applyStatusChange(changeData: Record<string, any>): void {
    for (const classname in changeData) {
      const details = changeData[classname];
      const nName = this.normalizeClassname(classname);
      if (!this.nodes[nName]) this.nodes[nName] = new Node(nName);
      const node = this.nodes[nName];
      const status = typeof details === 'object' ? details['status'] || {} : {};
      if (typeof status['error'] === 'boolean') node.isSourceError = status['error'];
      if (typeof status['optimistic'] === 'boolean') node.isOptimistic = status['optimistic'];
      const msg = status['message'] || status['errorMessage'] || status['reason'];
      if (typeof msg === 'string') node.errorMessage = msg;
      node.isInLastUpdate = true;
    }
  }

  propagateErrorFlagsFromAdj(adj: { [key: string]: string[] }): void {
    Object.values(this.nodes).forEach(n => n.hasUpstreamError = false);

    // Build reverse adjacency: dst -> [src]
    const rev: { [key: string]: string[] } = {};
    for (const src in adj) {
      for (const dst of adj[src]) {
        if (!rev[dst]) rev[dst] = [];
        rev[dst].push(src);
      }
    }

    // Seed queue with error nodes that exist in graph
    const q: string[] = [];
    const seen: Set<string> = new Set();
    for (const name in this.nodes) {
      const node = this.nodes[name];
      if (node.isSourceError && rev[name]) {
        q.push(name);
        seen.add(name);
      }
    }

    // BFS on reverse graph to mark upstream dependents as impacted
    while (q.length) {
      const err = q.shift()!;
      for (const dependent of rev[err] || []) {
        if (!this.nodes[dependent]) this.nodes[dependent] = new Node(dependent);
        const depNode = this.nodes[dependent];
        if (!depNode.hasUpstreamError) depNode.hasUpstreamError = true;
        if (!seen.has(dependent)) {
          seen.add(dependent);
          q.push(dependent);
        }
      }
    }
  }

  normalizeClassname(classname: string): string {
    return classname.replace(/\./g, '_').replace(/\//g, '_');
  }
}