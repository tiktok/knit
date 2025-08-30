import * as vscode from 'vscode';
import { spawn } from 'child_process';
import * as path from 'path';
import * as os from 'os';

let panel: vscode.WebviewPanel | undefined;
// Keep track of open diagram panels
const diagramPanels = new Set<vscode.WebviewPanel>(); 

export function activate(context: vscode.ExtensionContext) {
  // Hello World command
  let helloCmd = vscode.commands.registerCommand(
    "knit-vscode-plugin.helloWorld",
    () => {
      vscode.window.showInformationMessage("Hello World from Knit!");
    }
  );

  // Gradle shadowJar watcher
  let watchCmd = vscode.commands.registerCommand("knit.watchJar", () => {
    const terminal = vscode.window.createOutputChannel("Knit Watch");
    const workspaceFolder = vscode.workspace.workspaceFolders
      ? vscode.workspace.workspaceFolders[0].uri.fsPath
      : undefined;

    if (!workspaceFolder) {
      vscode.window.showErrorMessage("No workspace folder is open!");
      return;
    }

    const repoRoot = path.resolve(workspaceFolder, "..");
    const gradleCmd = os.platform() === "win32" ? "gradlew.bat" : "./gradlew";
    const gradle = spawn(gradleCmd, ["shadowJar", "--continuous"], {
      cwd: repoRoot,
      shell: true,
    });

    gradle.stdout.on("data", (data) => terminal.append(data.toString()));
    gradle.stderr.on("data", (data) => terminal.append(`[ERR] ${data.toString()}`));
    gradle.on("close", (code) => terminal.appendLine(`Gradle exited with code ${code}`));
    terminal.show(true);
  });

  // Open WebviewPanel command
let openPanelCmd = vscode.commands.registerCommand("knit.openDiagramPanel", async () => {
  const panel = vscode.window.createWebviewPanel(
    "mermaidPanel",              // viewType
    "Knit Dependency Graph",     // title
    vscode.ViewColumn.Two,
    { enableScripts: true }
  );

  // Add panel to the set
  diagramPanels.add(panel);

  panel.onDidDispose(() => {
    // Remove from set when disposed
    diagramPanels.delete(panel);
  });

  // Load diagram from file or fallback
  let diagram = `graph TD\nA[Knit Plugin Activated] --> B[Panel Ready]\nB --> C[Edit .mmd to update diagram]`;
  if (vscode.workspace.workspaceFolders) {
    const root = vscode.workspace.workspaceFolders[0].uri;
    const diagramFile = vscode.Uri.joinPath(root, "diagram.mmd");
    try {
      const data = await vscode.workspace.fs.readFile(diagramFile);
      diagram = new TextDecoder("utf-8").decode(data);
    } catch {}
  }

  panel.webview.html = getHtml(diagram);

  // Watch for changes only while this panel is open
  const disposable = vscode.workspace.onDidChangeTextDocument(event => {
  if (event.document.fileName.endsWith(".mmd")) {
    const newDiagram = event.document.getText();
    diagramPanels.forEach(panel => {
      panel.webview.postMessage({ type: "update", diagram: newDiagram });
    });
  }
});

  panel.onDidDispose(() => disposable.dispose());
});

  // Close WebviewPanel command
let closePanelCmd = vscode.commands.registerCommand("knit.closeDiagramPanel", () => {
  // Dispose all currently open diagram panels
  diagramPanels.forEach(panel => panel.dispose());
  diagramPanels.clear();
});

  context.subscriptions.push(helloCmd, watchCmd, openPanelCmd, closePanelCmd);

  // Optional: open automatically on activation
  vscode.commands.executeCommand("knit.openDiagramPanel");
}

// Generate the webview HTML
function getHtml(diagram: string): string {
  diagram = diagram.trim();
  return `
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<script src="https://cdn.jsdelivr.net/npm/mermaid/dist/mermaid.min.js"></script>
<style>
  body { padding: 10px; font-family: sans-serif; }
</style>
</head>
<body>
<div class="mermaid"></div>
<script>
mermaid.initialize({ startOnLoad: false });

function renderDiagram(diagram) {
  const container = document.querySelector('.mermaid');
  container.innerHTML = diagram;
  mermaid.init(undefined, container);
}

// Initial render
renderDiagram(\`${diagram}\`);

// Listen for updates
window.addEventListener('message', event => {
  const msg = event.data;
  if (msg.type === 'update') {
    renderDiagram(\`${msg.diagram}\`);
  }
});
</script>
</body>
</html>
`;
}

export function deactivate() {}