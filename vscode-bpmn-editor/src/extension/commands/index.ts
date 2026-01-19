import * as vscode from 'vscode';
import { getDelegateRegistry, JavaDelegateComponent } from '../scanner';
import { BpmnEditorPanel } from '../webview/WebviewManager';

/**
 * Register all commands
 */
export function registerCommands(context: vscode.ExtensionContext): void {
  // Open BPMN Editor command
  context.subscriptions.push(
    vscode.commands.registerCommand('fep-bpmn.openEditor', async (uri?: vscode.Uri) => {
      let document: vscode.TextDocument | undefined;

      if (uri) {
        document = await vscode.workspace.openTextDocument(uri);
      } else {
        // Try to get current document
        const editor = vscode.window.activeTextEditor;
        if (editor && editor.document.fileName.endsWith('.bpmn')) {
          document = editor.document;
        }
      }

      BpmnEditorPanel.createOrShow(context.extensionUri, document);
    })
  );

  // Scan Delegates command
  context.subscriptions.push(
    vscode.commands.registerCommand('fep-bpmn.scanDelegates', async () => {
      const registry = getDelegateRegistry();

      await vscode.window.withProgress({
        location: vscode.ProgressLocation.Notification,
        title: 'Scanning Java Delegates...',
        cancellable: false
      }, async () => {
        const result = await registry.refresh();

        if (result.stats.errors.length > 0) {
          vscode.window.showWarningMessage(
            `Found ${result.delegates.length} delegates with ${result.stats.errors.length} errors`
          );
        } else {
          vscode.window.showInformationMessage(
            `Found ${result.delegates.length} Java Delegates in ${result.stats.scanDurationMs}ms`
          );
        }
      });
    })
  );

  // New BPMN File command
  context.subscriptions.push(
    vscode.commands.registerCommand('fep-bpmn.newBpmnFile', async () => {
      const uri = await vscode.window.showSaveDialog({
        filters: { 'BPMN Files': ['bpmn'] },
        defaultUri: vscode.Uri.file('new-process.bpmn')
      });

      if (uri) {
        // Create empty BPMN file
        const emptyBpmn = getEmptyBpmnTemplate();
        await vscode.workspace.fs.writeFile(uri, Buffer.from(emptyBpmn, 'utf-8'));

        // Open in editor
        await vscode.commands.executeCommand('vscode.openWith', uri, 'fep-bpmn.editor');
      }
    })
  );

  // Export SVG command
  context.subscriptions.push(
    vscode.commands.registerCommand('fep-bpmn.exportSvg', async () => {
      if (BpmnEditorPanel.currentPanel) {
        // Request export from webview
        // The panel will handle the actual export
        vscode.window.showInformationMessage('Use the export button in the editor toolbar');
      }
    })
  );

  // Export PNG command
  context.subscriptions.push(
    vscode.commands.registerCommand('fep-bpmn.exportPng', async () => {
      if (BpmnEditorPanel.currentPanel) {
        vscode.window.showInformationMessage('Use the export button in the editor toolbar');
      }
    })
  );

  // Open Delegate file command
  context.subscriptions.push(
    vscode.commands.registerCommand('fep-bpmn.openDelegate', async (delegate: JavaDelegateComponent) => {
      if (delegate && delegate.filePath) {
        const uri = vscode.Uri.file(delegate.filePath);
        await vscode.window.showTextDocument(uri, {
          selection: new vscode.Range(delegate.lineNumber - 1, 0, delegate.lineNumber - 1, 0)
        });
      }
    })
  );

  // Refresh delegates command
  context.subscriptions.push(
    vscode.commands.registerCommand('fep-bpmn.refreshDelegates', async () => {
      const registry = getDelegateRegistry();
      await registry.refresh();
    })
  );

  // Insert Delegate command (for quick pick)
  context.subscriptions.push(
    vscode.commands.registerCommand('fep-bpmn.insertDelegate', async () => {
      const registry = getDelegateRegistry();
      const delegates = registry.getAll();

      if (delegates.length === 0) {
        vscode.window.showWarningMessage('No delegates found. Run "Scan Java Delegates" first.');
        return;
      }

      const items = delegates.map(d => ({
        label: d.displayName || d.name,
        description: d.name,
        detail: `${d.category.name} - ${d.description}`,
        delegate: d
      }));

      const selected = await vscode.window.showQuickPick(items, {
        placeHolder: 'Select a Java Delegate',
        matchOnDescription: true,
        matchOnDetail: true
      });

      if (selected) {
        // Copy delegate expression to clipboard
        const expression = `\${${selected.delegate.name}}`;
        await vscode.env.clipboard.writeText(expression);
        vscode.window.showInformationMessage(`Copied: ${expression}`);
      }
    })
  );
}

/**
 * Get empty BPMN template
 */
function getEmptyBpmnTemplate(): string {
  return `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
                  xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                  id="Definitions_1"
                  targetNamespace="http://fep.com/bpmn">
  <bpmn:process id="Process_1" name="New Process" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1" name="Start">
      <bpmn:outgoing>Flow_1</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:endEvent id="EndEvent_1" name="End">
      <bpmn:incoming>Flow_1</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="EndEvent_1" />
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_1">
      <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1">
        <dc:Bounds x="180" y="160" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="186" y="203" width="24" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EndEvent_1_di" bpmnElement="EndEvent_1">
        <dc:Bounds x="432" y="160" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="440" y="203" width="20" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Flow_1_di" bpmnElement="Flow_1">
        <di:waypoint xmlns:di="http://www.omg.org/spec/DD/20100524/DI" x="216" y="178" />
        <di:waypoint xmlns:di="http://www.omg.org/spec/DD/20100524/DI" x="432" y="178" />
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`;
}
