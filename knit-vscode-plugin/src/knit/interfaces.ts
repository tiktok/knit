// Shared interfaces between extension (backend) and webview (frontend)

export interface KnitNode {
  name: string;
  isOptimistic: boolean;
  isSourceError: boolean;
  errorMessage?: string;
  isInLastUpdate: boolean;
  hasUpstreamError: boolean;
}

export type Edge = [string, string];

export interface AdjacencyList {
  nodes: Record<string, KnitNode>;
  edges: Edge[];
}
