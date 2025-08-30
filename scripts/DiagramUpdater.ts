// DiagramUpdater.ts

import { AdjacencyList } from './AdjacencyList';
//import { Visualiser } from './Visualiser';
import { IncrementalAdjacency } from './IncrementalAdjacency';

export class DiagramUpdater {
  jsonFileUrl: string;
  outputCallback: (content: string) => void;
  direction: string;
  pollInterval: number;
  lastModifiedTime: number | null = null;
  changesDirUrl: string | null = null;
  lastChangeSeen: string | null = null;
  running: boolean = false;
  adjacencyList: AdjacencyList;
  //visualiser: Visualiser;
  incremental: IncrementalAdjacency;
  currentAdj: Record<string, string[]> | null = null;
  intervalId: number | null = null;

  constructor(
    jsonFileUrl: string,
    outputCallback: (content: string) => void,
    direction = "TD",
    pollInterval = 2000,
    changesDirUrl?: string
  ) {
    this.jsonFileUrl = jsonFileUrl;
    this.outputCallback = outputCallback;
    this.direction = direction;
    this.pollInterval = pollInterval;
    this.changesDirUrl = changesDirUrl || null;
    this.adjacencyList = new AdjacencyList();
    //this.visualiser = new Visualiser();
    this.incremental = new IncrementalAdjacency();
  }

  async fetchJson(url: string): Promise<any> {
    const response = await fetch(url, { cache: "no-store" });
    if (!response.ok) throw new Error(`Failed to fetch ${url}`);
    return await response.json();
  }

  async checkForChanges(): Promise<boolean> {
    // In browser, we can't get mtime, so we refetch and compare content hash
    try {
      const data = await this.fetchJson(this.jsonFileUrl);
      const hash = JSON.stringify(data);
      if (this.lastModifiedTime === null || hash !== String(this.lastModifiedTime)) {
        this.lastModifiedTime = hash as any;
        return true;
      }
    } catch (e) {
      console.error(`ERROR: JSON file not found: ${this.jsonFileUrl}`);
    }
    return false;
  }

  async updateDiagramFull(): Promise<void> {
    try {
      const componentDumps = await this.fetchJson(this.jsonFileUrl);
      const adjacencyList = this.adjacencyList.buildAdjacencyList(componentDumps);
      this.currentAdj = adjacencyList;
      this.adjacencyList.resetLastUpdateFlags();
      this.adjacencyList.markFullBuildUpdate();
      this.adjacencyList.propagateErrorFlagsFromAdj(this.currentAdj);
      //const mermaidContent = this.visualiser.buildMermaidDiagram(adjacencyList, this.direction);
      //this.outputCallback(mermaidContent);
      console.log(`Updated diagram`);
    } catch (e) {
      console.error(`ERROR: Failed to update diagram: ${e}`);
    }
  }

  async latestChangeFile(): Promise<string | null> {
    if (!this.changesDirUrl) return null;
    try {
      const latestTxtUrl = this.changesDirUrl + "/latest.txt";
      const response = await fetch(latestTxtUrl, { cache: "no-store" });
      if (!response.ok) return null;
      const name = (await response.text()).trim();
      const changePath = this.changesDirUrl + "/" + name;
      const changeResp = await fetch(changePath, { cache: "no-store" });
      if (!changeResp.ok) return null;
      return changePath;
    } catch {
      return null;
    }
  }

  async checkForChangeFiles(): Promise<string | null> {
    const changePath = await this.latestChangeFile();
    if (!changePath) return null;
    if (changePath !== this.lastChangeSeen) {
      this.lastChangeSeen = changePath;
      return changePath;
    }
    return null;
  }

  async applyIncrementalIfAny(): Promise<boolean> {
    if (!this.currentAdj) return false;
    const changeFile = await this.checkForChangeFiles();
    if (!changeFile) return false;
    try {
      const changeData = await this.fetchJson(changeFile);
      this.adjacencyList.resetLastUpdateFlags();
      this.adjacencyList.applyStatusChange(changeData);
      this.currentAdj = this.incremental.applyChange(this.currentAdj, changeData);
      this.adjacencyList.propagateErrorFlagsFromAdj(this.currentAdj);
      //const mermaidContent = this.visualiser.buildMermaidDiagram(this.currentAdj, this.direction);
      //this.outputCallback(mermaidContent);
      const nodeCount = Object.keys(this.currentAdj).length;
      const edgeCount = Object.values(this.currentAdj).reduce((acc, v) => acc + v.length, 0);
      console.log(`Applied change -> nodes=${nodeCount}, edges=${edgeCount}; updated diagram.`);
      return true;
    } catch (e) {
      console.error(`ERROR: Failed to apply change file: ${e}`);
      return false;
    }
  }

  async start(): Promise<void> {
    if (this.running) {
      console.log("DiagramUpdater is already running.");
      return;
    }
    this.running = true;
    console.log(`Starting DiagramUpdater for ${this.jsonFileUrl}...`);
    await this.updateDiagramFull();

    this.intervalId = window.setInterval(async () => {
      const applied = await this.applyIncrementalIfAny();
      if (await this.checkForChanges() && !applied) {
        console.log(`Changes detected in ${this.jsonFileUrl}. Updating diagram...`);
        await this.updateDiagramFull();
      }
    }, this.pollInterval);
  }

  stop(): void {
    this.running = false;
    if (this.intervalId !== null) window.clearInterval(this.intervalId);
    console.log("DiagramUpdater stopped.");
  }
}
