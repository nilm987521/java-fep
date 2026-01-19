import * as vscode from 'vscode';
import { getDelegateRegistry, disposeDelegateRegistry } from './scanner';
import { BpmnEditorProvider } from './providers/BpmnEditorProvider';
import { DelegatesTreeProvider, BpmnFilesTreeProvider } from './providers/DelegatesTreeProvider';
import { registerCommands } from './commands';

/**
 * Extension activation
 */
export async function activate(context: vscode.ExtensionContext): Promise<void> {
  console.log('FEP BPMN Editor is activating...');

  // Register custom editor provider
  context.subscriptions.push(
    BpmnEditorProvider.register(context)
  );

  // Register tree view providers
  const delegatesTreeProvider = new DelegatesTreeProvider();
  const bpmnFilesTreeProvider = new BpmnFilesTreeProvider();

  context.subscriptions.push(
    vscode.window.registerTreeDataProvider('fep-bpmn.delegatesView', delegatesTreeProvider),
    vscode.window.registerTreeDataProvider('fep-bpmn.bpmnFilesView', bpmnFilesTreeProvider)
  );

  // Register commands
  registerCommands(context);

  // Initialize delegate registry
  const registry = getDelegateRegistry();

  // Get configuration
  const config = vscode.workspace.getConfiguration('fep-bpmn');
  const autoScan = config.get<boolean>('autoScan', true);

  if (autoScan) {
    // Perform initial scan
    await vscode.window.withProgress({
      location: vscode.ProgressLocation.Window,
      title: 'Scanning Java Delegates...'
    }, async () => {
      await registry.initialize();
    });

    const result = registry.getLastScanResult();
    if (result) {
      console.log(`Found ${result.delegates.length} Java Delegates`);
    }
  }

  // Listen for configuration changes
  context.subscriptions.push(
    vscode.workspace.onDidChangeConfiguration(e => {
      if (e.affectsConfiguration('fep-bpmn')) {
        // Refresh on config change
        registry.refresh();
      }
    })
  );

  // Status bar item
  const statusBarItem = vscode.window.createStatusBarItem(
    vscode.StatusBarAlignment.Left,
    100
  );
  statusBarItem.text = '$(symbol-event) BPMN';
  statusBarItem.tooltip = 'FEP BPMN Editor';
  statusBarItem.command = 'fep-bpmn.scanDelegates';
  context.subscriptions.push(statusBarItem);

  // Show status bar when viewing BPMN files
  context.subscriptions.push(
    vscode.window.onDidChangeActiveTextEditor(editor => {
      if (editor && editor.document.fileName.endsWith('.bpmn')) {
        statusBarItem.show();
      } else {
        statusBarItem.hide();
      }
    })
  );

  // Check current editor
  if (vscode.window.activeTextEditor?.document.fileName.endsWith('.bpmn')) {
    statusBarItem.show();
  }

  console.log('FEP BPMN Editor activated successfully!');
}

/**
 * Extension deactivation
 */
export function deactivate(): void {
  disposeDelegateRegistry();
  console.log('FEP BPMN Editor deactivated');
}
