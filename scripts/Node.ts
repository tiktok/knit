export class Node {
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