// The module 'vscode' contains the VS Code extensibility API
// Import the module and reference it with the alias vscode in your code below
import * as vscode from 'vscode';
import { spawn } from "child_process";
import * as path from "path";
import * as os from "os";

// This method is called when your extension is activated
// Your extension is activated the very first time the command is executed
// export function activate(context: vscode.ExtensionContext) {

// 	// Use the console to output diagnostic information (console.log) and errors (console.error)
// 	// This line of code will only be executed once when your extension is activated
// 	console.log('Congratulations, your extension "knit-vscode-plugin" is now active!');

// 	// The command has been defined in the package.json file
// 	// Now provide the implementation of the command with registerCommand
// 	// The commandId parameter must match the command field in package.json
// 	const disposable = vscode.commands.registerCommand('knit-vscode-plugin.helloWorld', () => {
// 		// The code you place here will be executed every time your command is executed
// 		// Display a message box to the user
// 		vscode.window.showInformationMessage('Hello World from knit-vscode-plugin!');
// 	});

// 	context.subscriptions.push(disposable);
// }

export function activate(context: vscode.ExtensionContext) {
  // Hello World command
  let helloCmd = vscode.commands.registerCommand(
    "knit-vscode-plugin.helloWorld",
    () => {
      vscode.window.showInformationMessage("Hello World from Knit!");
    }
  );

  // Command to start Gradle shadowJar --continuous
  let watchCmd = vscode.commands.registerCommand("knit.watchJar", () => {
    const terminal = vscode.window.createOutputChannel("Knit Watch");

    // // Spawn gradle process
    // const gradle = spawn("gradlew.bat", ["shadowJar", "--continuous"], {
    //   cwd: vscode.workspace.rootPath,
    //   shell: true,
    // });

    // const gradleCmd = os.platform() === "win32" ? "gradlew.bat" : "./gradlew";
    // const repoRoot = path.resolve(vscode.workspace.rootPath!, ".."); // adjust if needed

    // const gradle = spawn(gradleCmd, ["shadowJar", "--continuous"], {
    //   cwd: repoRoot,
    //   shell: true,
    // });

    // Get the first workspace folder (the repo root)
    const workspaceFolder = vscode.workspace.workspaceFolders
    ? vscode.workspace.workspaceFolders[0].uri.fsPath 
    : undefined;

    if (!workspaceFolder) {
      vscode.window.showErrorMessage("No workspace folder is open!");
      return;
    }

    // Adjust cwd if gradlew.bat is in the parent folder
    const repoRoot = path.resolve(workspaceFolder, ".."); // move up one level if needed

    const gradleCmd = os.platform() === "win32" ? "gradlew.bat" : "./gradlew";

    const gradle = spawn(gradleCmd, ["shadowJar", "--continuous"], {
      cwd: repoRoot,
      shell: true,
    });

    gradle.stdout.on("data", (data) => {
      terminal.append(data.toString());
    });

    gradle.stderr.on("data", (data) => {
      terminal.append(`[ERR] ${data.toString()}`);
    });

    gradle.on("close", (code) => {
      terminal.appendLine(`Gradle exited with code ${code}`);
    });

    terminal.show(true);
  });

  context.subscriptions.push(helloCmd, watchCmd);
}


// This method is called when your extension is deactivated
export function deactivate() {}
