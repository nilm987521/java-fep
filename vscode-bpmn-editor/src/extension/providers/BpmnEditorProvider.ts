import * as vscode from 'vscode';
import * as path from 'path';
import { getDelegateRegistry } from '../scanner';

/**
 * Custom Editor Provider for .bpmn files
 */
export class BpmnEditorProvider implements vscode.CustomTextEditorProvider {
  public static readonly viewType = 'fep-bpmn.editor';

  private static readonly scratchCharacters = ['/', '## '];

  constructor(private readonly context: vscode.ExtensionContext) {}

  /**
   * Register the provider
   */
  public static register(context: vscode.ExtensionContext): vscode.Disposable {
    const provider = new BpmnEditorProvider(context);
    return vscode.window.registerCustomEditorProvider(
      BpmnEditorProvider.viewType,
      provider,
      {
        webviewOptions: {
          retainContextWhenHidden: true
        },
        supportsMultipleEditorsPerDocument: false
      }
    );
  }

  /**
   * Called when opening a document
   */
  async resolveCustomTextEditor(
    document: vscode.TextDocument,
    webviewPanel: vscode.WebviewPanel,
    _token: vscode.CancellationToken
  ): Promise<void> {
    // Setup webview
    webviewPanel.webview.options = {
      enableScripts: true,
      localResourceRoots: [
        vscode.Uri.joinPath(this.context.extensionUri, 'dist'),
        vscode.Uri.joinPath(this.context.extensionUri, 'resources'),
        vscode.Uri.joinPath(this.context.extensionUri, 'node_modules')
      ]
    };

    // Set webview HTML
    webviewPanel.webview.html = this.getHtmlForWebview(webviewPanel.webview);

    // Track document changes
    const changeDocumentSubscription = vscode.workspace.onDidChangeTextDocument(e => {
      if (e.document.uri.toString() === document.uri.toString()) {
        // Document changed externally, update webview
        this.updateWebview(webviewPanel.webview, document);
      }
    });

    // Track delegate registry changes
    const registry = getDelegateRegistry();
    const delegateSubscription = registry.onDidChange(() => {
      this.sendDelegates(webviewPanel.webview);
    });

    // Cleanup on dispose
    webviewPanel.onDidDispose(() => {
      changeDocumentSubscription.dispose();
      delegateSubscription.dispose();
    });

    // Handle messages from webview
    webviewPanel.webview.onDidReceiveMessage(async message => {
      switch (message.type) {
        case 'ready':
          // Webview is ready
          this.updateWebview(webviewPanel.webview, document);
          this.sendDelegates(webviewPanel.webview);
          break;

        case 'update':
          // Content changed in webview
          await this.updateDocument(document, message.content);
          break;

        case 'save':
          // Explicit save request
          await this.updateDocument(document, message.content);
          await document.save();
          break;

        case 'requestDelegates':
          this.sendDelegates(webviewPanel.webview);
          break;

        case 'openDelegate':
          // Open delegate file
          if (message.filePath) {
            const uri = vscode.Uri.file(message.filePath);
            await vscode.window.showTextDocument(uri, {
              selection: message.lineNumber
                ? new vscode.Range(message.lineNumber - 1, 0, message.lineNumber - 1, 0)
                : undefined
            });
          }
          break;

        case 'exportSvg':
          await this.exportSvg(document, message.content);
          break;

        case 'exportPng':
          await this.exportPng(document, message.content);
          break;

        case 'showInfo':
          vscode.window.showInformationMessage(message.message);
          break;

        case 'showError':
          vscode.window.showErrorMessage(message.message);
          break;
      }
    });
  }

  /**
   * Update webview with document content
   */
  private updateWebview(webview: vscode.Webview, document: vscode.TextDocument): void {
    webview.postMessage({
      type: 'loadBpmn',
      content: document.getText(),
      fileName: path.basename(document.fileName),
      filePath: document.uri.fsPath
    });
  }

  /**
   * Send delegates to webview
   */
  private sendDelegates(webview: vscode.Webview): void {
    const registry = getDelegateRegistry();
    webview.postMessage({
      type: 'delegates',
      data: JSON.parse(registry.toJSON())
    });
  }

  /**
   * Update document with new content
   */
  private async updateDocument(document: vscode.TextDocument, content: string): Promise<void> {
    const edit = new vscode.WorkspaceEdit();
    edit.replace(
      document.uri,
      new vscode.Range(0, 0, document.lineCount, 0),
      content
    );
    await vscode.workspace.applyEdit(edit);
  }

  /**
   * Export as SVG
   */
  private async exportSvg(document: vscode.TextDocument, svgContent: string): Promise<void> {
    const defaultName = path.basename(document.fileName, '.bpmn') + '.svg';
    const defaultUri = vscode.Uri.file(
      path.join(path.dirname(document.uri.fsPath), defaultName)
    );

    const uri = await vscode.window.showSaveDialog({
      filters: { 'SVG Files': ['svg'] },
      defaultUri
    });

    if (uri) {
      await vscode.workspace.fs.writeFile(uri, Buffer.from(svgContent, 'utf-8'));
      vscode.window.showInformationMessage(`Exported: ${path.basename(uri.fsPath)}`);
    }
  }

  /**
   * Export as PNG
   */
  private async exportPng(document: vscode.TextDocument, pngDataUrl: string): Promise<void> {
    const defaultName = path.basename(document.fileName, '.bpmn') + '.png';
    const defaultUri = vscode.Uri.file(
      path.join(path.dirname(document.uri.fsPath), defaultName)
    );

    const uri = await vscode.window.showSaveDialog({
      filters: { 'PNG Files': ['png'] },
      defaultUri
    });

    if (uri) {
      const base64Data = pngDataUrl.replace(/^data:image\/png;base64,/, '');
      const buffer = Buffer.from(base64Data, 'base64');
      await vscode.workspace.fs.writeFile(uri, buffer);
      vscode.window.showInformationMessage(`Exported: ${path.basename(uri.fsPath)}`);
    }
  }

  /**
   * Generate webview HTML
   */
  private getHtmlForWebview(webview: vscode.Webview): string {
    const scriptUri = webview.asWebviewUri(
      vscode.Uri.joinPath(this.context.extensionUri, 'dist', 'webview.js')
    );

    const nonce = getNonce();

    return `<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src ${webview.cspSource} 'unsafe-inline'; script-src 'nonce-${nonce}'; img-src ${webview.cspSource} data: blob:; font-src ${webview.cspSource} data:;">
    <title>FEP BPMN Editor</title>
    <style>
      html, body, #root {
        height: 100%;
        width: 100%;
        margin: 0;
        padding: 0;
        overflow: hidden;
        background: var(--vscode-editor-background);
        color: var(--vscode-editor-foreground);
      }

      /* Loading indicator */
      .loading {
        display: flex;
        align-items: center;
        justify-content: center;
        height: 100%;
        font-size: 14px;
        color: var(--vscode-descriptionForeground);
      }

      .loading::after {
        content: 'Loading BPMN Editor...';
      }
    </style>
</head>
<body>
    <div id="root"><div class="loading"></div></div>
    <script nonce="${nonce}" src="${scriptUri}"></script>
</body>
</html>`;
  }
}

/**
 * Generate random nonce
 */
function getNonce(): string {
  let text = '';
  const possible = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
  for (let i = 0; i < 32; i++) {
    text += possible.charAt(Math.floor(Math.random() * possible.length));
  }
  return text;
}
