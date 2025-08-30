import type { KnitNode } from './interfaces';

export class Node implements KnitNode {
  name: string;
  isOptimistic: boolean = false;
  isSourceError: boolean = false;
  errorMessage?: string;
  isInLastUpdate: boolean = false;
  hasUpstreamError: boolean = false;

  constructor(name: string) {
    this.name = name;
  }
}

export type { KnitNode } from './interfaces';
