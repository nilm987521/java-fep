import * as vscode from 'vscode';
import * as path from 'path';
import { getDelegateRegistry } from '../scanner';

/**
 * Manages the BPMN Editor Webview Panel
 */
export class BpmnEditorPanel {
  public static currentPanel: BpmnEditorPanel | undefined;
  private static readonly viewType = 'fep-bpmn.editor';

  private readonly _panel: vscode.WebviewPanel;
  private readonly _extensionUri: vscode.Uri;
  private _document: vscode.TextDocument | undefined;
  private _disposables: vscode.Disposable[] = [];

  public static createOrShow(extensionUri: vscode.Uri, document?: vscode.TextDocument): BpmnEditorPanel {
    const column = vscode.window.activeTextEditor
      ? vscode.window.activeTextEditor.viewColumn
      : undefined;

    // If we already have a panel, show it
    if (BpmnEditorPanel.currentPanel) {
      BpmnEditorPanel.currentPanel._panel.reveal(column);
      if (document) {
        BpmnEditorPanel.currentPanel.loadDocument(document);
      }
      return BpmnEditorPanel.currentPanel;
    }

    // Otherwise, create a new panel
    const panel = vscode.window.createWebviewPanel(
      BpmnEditorPanel.viewType,
      'BPMN Editor',
      column || vscode.ViewColumn.One,
      {
        enableScripts: true,
        retainContextWhenHidden: true,
        localResourceRoots: [
          vscode.Uri.joinPath(extensionUri, 'dist'),
          vscode.Uri.joinPath(extensionUri, 'resources'),
          vscode.Uri.joinPath(extensionUri, 'node_modules')
        ]
      }
    );

    BpmnEditorPanel.currentPanel = new BpmnEditorPanel(panel, extensionUri, document);
    return BpmnEditorPanel.currentPanel;
  }

  private constructor(
    panel: vscode.WebviewPanel,
    extensionUri: vscode.Uri,
    document?: vscode.TextDocument
  ) {
    this._panel = panel;
    this._extensionUri = extensionUri;
    this._document = document;

    // Set the webview's initial html content
    this._update();

    // Listen for when the panel is disposed
    this._panel.onDidDispose(() => this.dispose(), null, this._disposables);

    // Update the content based on view state changes
    this._panel.onDidChangeViewState(
      () => {
        if (this._panel.visible) {
          this._update();
        }
      },
      null,
      this._disposables
    );

    // Handle messages from the webview
    this._panel.webview.onDidReceiveMessage(
      message => this.handleMessage(message),
      null,
      this._disposables
    );

    // Listen for delegate registry changes
    const registry = getDelegateRegistry();
    registry.onDidChange(() => {
      this.sendDelegates();
    }, null, this._disposables);
  }

  /**
   * Load a BPMN document
   */
  public loadDocument(document: vscode.TextDocument): void {
    this._document = document;
    this._panel.title = path.basename(document.fileName);
    this.sendBpmnContent();
  }

  /**
   * Handle messages from webview
   */
  private async handleMessage(message: any): Promise<void> {
    switch (message.type) {
      case 'ready':
        // Webview is ready, send initial data
        await this.sendInitialData();
        break;

      case 'save':
        // Save BPMN content
        await this.saveBpmnContent(message.content);
        break;

      case 'requestDelegates':
        // Send delegates to webview
        this.sendDelegates();
        break;

      case 'openFile':
        // Open file in editor
        const uri = vscode.Uri.file(message.path);
        await vscode.window.showTextDocument(uri, {
          selection: message.line ? new vscode.Range(message.line - 1, 0, message.line - 1, 0) : undefined
        });
        break;

      case 'showInfo':
        vscode.window.showInformationMessage(message.message);
        break;

      case 'showError':
        vscode.window.showErrorMessage(message.message);
        break;

      case 'exportSvg':
        await this.exportSvg(message.content);
        break;

      case 'exportPng':
        await this.exportPng(message.content);
        break;
    }
  }

  /**
   * Send initial data to webview
   */
  private async sendInitialData(): Promise<void> {
    this.sendBpmnContent();
    this.sendDelegates();
  }

  /**
   * Send BPMN content to webview
   */
  private sendBpmnContent(): void {
    if (this._document) {
      this._panel.webview.postMessage({
        type: 'loadBpmn',
        content: this._document.getText(),
        fileName: path.basename(this._document.fileName)
      });
    }
  }

  /**
   * Send delegates to webview
   */
  private sendDelegates(): void {
    const registry = getDelegateRegistry();
    this._panel.webview.postMessage({
      type: 'delegates',
      data: JSON.parse(registry.toJSON())
    });
  }

  /**
   * Save BPMN content
   */
  private async saveBpmnContent(content: string): Promise<void> {
    if (!this._document) {
      // Create new file
      const uri = await vscode.window.showSaveDialog({
        filters: { 'BPMN Files': ['bpmn'] },
        defaultUri: vscode.Uri.file('new-process.bpmn')
      });

      if (uri) {
        await vscode.workspace.fs.writeFile(uri, Buffer.from(content, 'utf-8'));
        const doc = await vscode.workspace.openTextDocument(uri);
        this.loadDocument(doc);
        vscode.window.showInformationMessage(`Saved: ${path.basename(uri.fsPath)}`);
      }
    } else {
      // Update existing file
      const edit = new vscode.WorkspaceEdit();
      edit.replace(
        this._document.uri,
        new vscode.Range(0, 0, this._document.lineCount, 0),
        content
      );
      await vscode.workspace.applyEdit(edit);
      await this._document.save();
      vscode.window.showInformationMessage(`Saved: ${path.basename(this._document.fileName)}`);
    }
  }

  /**
   * Export as SVG
   */
  private async exportSvg(svgContent: string): Promise<void> {
    const defaultName = this._document
      ? path.basename(this._document.fileName, '.bpmn') + '.svg'
      : 'diagram.svg';

    const uri = await vscode.window.showSaveDialog({
      filters: { 'SVG Files': ['svg'] },
      defaultUri: vscode.Uri.file(defaultName)
    });

    if (uri) {
      await vscode.workspace.fs.writeFile(uri, Buffer.from(svgContent, 'utf-8'));
      vscode.window.showInformationMessage(`Exported: ${path.basename(uri.fsPath)}`);
    }
  }

  /**
   * Export as PNG
   */
  private async exportPng(pngDataUrl: string): Promise<void> {
    const defaultName = this._document
      ? path.basename(this._document.fileName, '.bpmn') + '.png'
      : 'diagram.png';

    const uri = await vscode.window.showSaveDialog({
      filters: { 'PNG Files': ['png'] },
      defaultUri: vscode.Uri.file(defaultName)
    });

    if (uri) {
      // Remove data URL prefix
      const base64Data = pngDataUrl.replace(/^data:image\/png;base64,/, '');
      const buffer = Buffer.from(base64Data, 'base64');
      await vscode.workspace.fs.writeFile(uri, buffer);
      vscode.window.showInformationMessage(`Exported: ${path.basename(uri.fsPath)}`);
    }
  }

  /**
   * Update webview content
   */
  private _update(): void {
    this._panel.webview.html = this._getHtmlForWebview(this._panel.webview);
  }

  /**
   * Generate webview HTML
   */
  private _getHtmlForWebview(webview: vscode.Webview): string {
    // Get resource URIs
    const scriptUri = webview.asWebviewUri(
      vscode.Uri.joinPath(this._extensionUri, 'dist', 'webview.js')
    );

    const styleUri = webview.asWebviewUri(
      vscode.Uri.joinPath(this._extensionUri, 'dist', 'webview.css')
    );

    // Use a nonce to only allow specific scripts to be run
    const nonce = getNonce();

    return `<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src ${webview.cspSource} 'unsafe-inline'; script-src 'nonce-${nonce}'; img-src ${webview.cspSource} data:; font-src ${webview.cspSource};">
    <title>FEP BPMN Editor</title>
    <link href="${styleUri}" rel="stylesheet">
    <style>
      html, body, #root {
        height: 100%;
        width: 100%;
        margin: 0;
        padding: 0;
        overflow: hidden;
      }
    </style>
</head>
<body>
    <div id="root"></div>
    <script nonce="${nonce}" src="${scriptUri}"></script>
</body>
</html>`;
  }

  /**
   * Dispose panel
   */
  public dispose(): void {
    BpmnEditorPanel.currentPanel = undefined;
    this._panel.dispose();

    while (this._disposables.length) {
      const disposable = this._disposables.pop();
      if (disposable) {
        disposable.dispose();
      }
    }
  }
}

/**
 * Generate a random nonce
 */
function getNonce(): string {
  let text = '';
  const possible = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
  for (let i = 0; i < 32; i++) {
    text += possible.charAt(Math.floor(Math.random() * possible.length));
  }
  return text;
}
