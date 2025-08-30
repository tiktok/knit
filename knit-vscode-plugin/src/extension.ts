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
    "knitGraphPanel",              // viewType
    "Knit Dependency Graph",       // title
    vscode.ViewColumn.Two,
    {
      enableScripts: true,
      localResourceRoots: [
        vscode.Uri.joinPath(context.extensionUri, 'out'),
        vscode.Uri.joinPath(context.extensionUri, 'resources')
      ]
    }
  );

  // Add panel to the set
  diagramPanels.add(panel);

  panel.onDidDispose(() => {
    // Remove from set when disposed
    diagramPanels.delete(panel);
  });

  // Build html and scripts
  panel.webview.html = getHtml(context, panel.webview);

  // Watch for changes only while this panel is open
  const disposable = vscode.workspace.onDidChangeTextDocument(event => {
    // In future: adapt to your data source changes
    if (event.document.fileName.endsWith(".mmd") || event.document.fileName.endsWith('.json')) {
      diagramPanels.forEach(panel => {
        panel.webview.postMessage({ type: "update" });
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
function getHtml(context: vscode.ExtensionContext, webview: vscode.Webview): string {
  const nonce = getNonce();
  const d3Cdn = 'https://cdn.jsdelivr.net/npm/d3@7/dist/d3.min.js';
  const scriptUri = webview.asWebviewUri(
    vscode.Uri.joinPath(context.extensionUri, 'out', 'webview', 'graph.js')
  );

  const csp = [
    `default-src 'none'`,
    `img-src ${webview.cspSource} https: data:`,
    `style-src ${webview.cspSource} 'unsafe-inline'`,
    `font-src ${webview.cspSource} https:`,
    `script-src ${webview.cspSource} 'nonce-${nonce}'`,
    `connect-src ${webview.cspSource}`
  ].join('; ');

  return `
  <!DOCTYPE html>
  <html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta http-equiv="Content-Security-Policy" content="${csp}" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>Knit Graph</title>
    <style>
      html, body { height: 100%; }
      body { padding: 0; margin: 0; font-family: ui-sans-serif, system-ui, -apple-system, Segoe UI, Roboto, sans-serif; }
      #app { height: 100vh; display: flex; flex-direction: column; }
    </style>
  </head>
  <body>
    <div id="app"></div>
    <script nonce="${nonce}" src="${d3Cdn}"></script>
    <script nonce="${nonce}" type="module" src="${scriptUri}"></script>
  </body>
  </html>`;
}

function getNonce() {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
  let nonce = '';
  for (let i = 0; i < 32; i++) {
    nonce += chars.charAt(Math.floor(Math.random() * chars.length));
  }
  return nonce;
}

export function deactivate() {}