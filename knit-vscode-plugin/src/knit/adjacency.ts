import type { AdjacencyList, KnitNode } from './interfaces';

export type KnitProvider = { provider: string; parameters?: string[] };
export type KnitStatus = { error?: boolean; optimistic?: boolean; message?: string; errorMessage?: string; reason?: string };
export type KnitNodeEntry = {
  parent?: string[];
  providers?: KnitProvider[];
  status?: KnitStatus;
  delta?: Record<string, unknown>;
};
export type KnitDump = Record<string, KnitNodeEntry>;

export function normalizeClassname(name: string): string {
  return name.replace(/[./]/g, '_');
}

export function extractConsumerFromProvider(provider: string | undefined): string | undefined {
  if (!provider) {
    return undefined;
  }
  const idx = provider.indexOf('->');
  if (idx === -1) {
    return undefined;
  }
  let rhs = provider.slice(idx + 2).trim();
  rhs = rhs.replace(/<[^<>]*>/g, '').trim();
  return rhs.replace(/[() ]/g, '').trim();
}

export function buildFromDump(dump: KnitDump): AdjacencyList {
  const nodes: Record<string, KnitNode> = {};
  const edgeSet = new Set<string>();

  const ensureNode = (name: string) => {
    if (!nodes[name]) {
      nodes[name] = {
        name,
        isOptimistic: false,
        isSourceError: false,
        isInLastUpdate: false,
        hasUpstreamError: false,
      };
    }
  };

  for (const [classname, details] of Object.entries(dump)) {
    const nClass = normalizeClassname(classname);
    ensureNode(nClass);

    const st = details?.status ?? {};
    if (typeof st.error === 'boolean') {
      nodes[nClass].isSourceError = !!st.error;
    }
    if (typeof st.optimistic === 'boolean') {
      nodes[nClass].isOptimistic = !!st.optimistic;
    }
    const msg = st.message || st.errorMessage || st.reason;
    if (typeof msg === 'string') {
      nodes[nClass].errorMessage = msg;
    }

    const providers = details?.providers ?? [];
    for (const p of providers) {
      const consumer = extractConsumerFromProvider(p.provider);
      if (!consumer) {
        continue;
      }
      const nConsumer = normalizeClassname(consumer);
      ensureNode(nConsumer);
      edgeSet.add(`${nConsumer}\u0000${nClass}`);
    }
  }

  const edges = Array.from(edgeSet).map(e => e.split('\u0000') as [string, string]);

  propagateUpstreamError(edges, nodes);

  return { nodes, edges };
}

export function propagateUpstreamError(edges: [string, string][], nodes: Record<string, KnitNode>): void {
  // Build reverse adjacency: dst -> [src]
  const rev: Record<string, string[]> = {};
  for (const n of Object.values(nodes)) {
    n.hasUpstreamError = false;
  }
  for (const [src, dst] of edges) {
    (rev[dst] ||= []).push(src);
    if (!nodes[src]) {
      nodes[src] = { name: src, isOptimistic: false, isSourceError: false, isInLastUpdate: false, hasUpstreamError: false };
    }
    if (!nodes[dst]) {
      nodes[dst] = { name: dst, isOptimistic: false, isSourceError: false, isInLastUpdate: false, hasUpstreamError: false };
    }
  }

  const queue: string[] = [];
  const seen = new Set<string>();
  for (const [name, n] of Object.entries(nodes)) {
    if (n.isSourceError && rev[name]) {
      queue.push(name);
      seen.add(name);
    }
  }

  while (queue.length) {
    const cur = queue.shift()!;
    for (const dep of rev[cur] ?? []) {
      const node = nodes[dep] || (nodes[dep] = { name: dep, isOptimistic: false, isSourceError: false, isInLastUpdate: false, hasUpstreamError: false });
      if (!node.hasUpstreamError) {
        node.hasUpstreamError = true;
      }
      if (!seen.has(dep)) {
        seen.add(dep);
        queue.push(dep);
      }
    }
  }
}

// Update node metadata based on a change dump's status entries
export function applyChangeStatus(nodes: Record<string, KnitNode>, change: KnitDump): void {
  for (const [classname, details] of Object.entries(change)) {
    const name = normalizeClassname(classname);
    const st = details?.status ?? {};
    if (!nodes[name]) {
      nodes[name] = { name, isOptimistic: false, isSourceError: false, isInLastUpdate: false, hasUpstreamError: false };
    }
    if (typeof st.error === 'boolean') {
      nodes[name].isSourceError = !!st.error;
    }
    if (typeof st.optimistic === 'boolean') {
      nodes[name].isOptimistic = !!st.optimistic;
    }
    const msg = (st as any)?.message || (st as any)?.errorMessage || (st as any)?.reason;
    if (typeof msg === 'string') {
      nodes[name].errorMessage = msg;
    }
    nodes[name].isInLastUpdate = true;
  }
}
