export interface Node {
  name: string
  is_optimistic: boolean
  is_source_error: boolean
  error_message?: string
  is_in_last_update: boolean
  has_upstream_error: boolean
  // D3 simulation properties
  x?: number
  y?: number
  fx?: number | null
  fy?: number | null
}

export interface AdjacencyList {
  nodes: Record<string, Node>
  edges: [string, string][]
}

export interface VSCodeMessage {
  type: "navigateToNode" | "exportGraph" | "update"
  nodeId?: string
  data?: any
}

export interface LayoutOptions {
  algorithm: "force" | "hierarchical" | "circular"
  nodeSpacing: number
  edgeLength: number
}

export interface FilterOptions {
  showErrors: boolean
  showNormal: boolean
  showOptimistic: boolean
  searchTerm: string
}