import * as vscode from 'vscode';
import * as path from 'path';
import { getDelegateRegistry, JavaDelegateComponent, DelegateCategory } from '../scanner';

/**
 * Tree item types
 */
type TreeItemType = CategoryItem | DelegateItem;

/**
 * Category tree item
 */
class CategoryItem extends vscode.TreeItem {
  constructor(
    public readonly category: DelegateCategory,
    public readonly delegates: JavaDelegateComponent[]
  ) {
    super(category.name, vscode.TreeItemCollapsibleState.Expanded);

    this.description = `${delegates.length}`;
    this.iconPath = new vscode.ThemeIcon(this.getIconName(category.icon));
    this.contextValue = 'category';
  }

  private getIconName(icon: string): string {
    const iconMap: Record<string, string> = {
      'check-circle': 'pass',
      'credit-card': 'credit-card',
      'mail': 'mail',
      'broadcast': 'broadcast',
      'package': 'package'
    };
    return iconMap[icon] || 'folder';
  }
}

/**
 * Delegate tree item
 */
class DelegateItem extends vscode.TreeItem {
  constructor(public readonly delegate: JavaDelegateComponent) {
    super(delegate.displayName || delegate.name, vscode.TreeItemCollapsibleState.None);

    this.description = delegate.name;
    this.tooltip = new vscode.MarkdownString(this.buildTooltip());
    this.iconPath = new vscode.ThemeIcon('symbol-method');
    this.contextValue = 'delegate';

    // Command to open delegate file
    this.command = {
      command: 'fep-bpmn.openDelegate',
      title: 'Open Delegate',
      arguments: [delegate]
    };
  }

  private buildTooltip(): string {
    const d = this.delegate;
    let md = `### ${d.className}\n\n`;

    if (d.description) {
      md += `${d.description}\n\n`;
    }

    md += `**Package:** \`${d.packageName}\`\n\n`;
    md += `**Bean Name:** \`${d.name}\`\n\n`;
    md += `**Category:** ${d.category.name}\n\n`;

    if (d.inputVariables.length > 0) {
      md += `**Input Variables:**\n`;
      d.inputVariables.forEach(v => {
        md += `- \`${v.name}\` (${v.type})\n`;
      });
      md += '\n';
    }

    if (d.outputVariables.length > 0) {
      md += `**Output Variables:**\n`;
      d.outputVariables.forEach(v => {
        md += `- \`${v.name}\` (${v.type})`;
        if (v.possibleValues) {
          md += ` [${v.possibleValues.join(', ')}]`;
        }
        md += '\n';
      });
    }

    md += `\n---\n*Click to open source file*`;

    return md;
  }
}

/**
 * Tree data provider for Java Delegates
 */
export class DelegatesTreeProvider implements vscode.TreeDataProvider<TreeItemType> {
  private _onDidChangeTreeData = new vscode.EventEmitter<TreeItemType | undefined | null | void>();
  readonly onDidChangeTreeData = this._onDidChangeTreeData.event;

  constructor() {
    // Listen for delegate changes
    const registry = getDelegateRegistry();
    registry.onDidChange(() => {
      this._onDidChangeTreeData.fire();
    });
  }

  /**
   * Refresh the tree
   */
  refresh(): void {
    this._onDidChangeTreeData.fire();
  }

  /**
   * Get tree item
   */
  getTreeItem(element: TreeItemType): vscode.TreeItem {
    return element;
  }

  /**
   * Get children
   */
  getChildren(element?: TreeItemType): Thenable<TreeItemType[]> {
    const registry = getDelegateRegistry();

    if (!element) {
      // Root level - show categories
      const categories = registry.getCategories();
      return Promise.resolve(
        categories.map(c => new CategoryItem(c.category, c.delegates))
      );
    }

    if (element instanceof CategoryItem) {
      // Category level - show delegates
      return Promise.resolve(
        element.delegates.map(d => new DelegateItem(d))
      );
    }

    return Promise.resolve([]);
  }

  /**
   * Get parent (for reveal)
   */
  getParent(element: TreeItemType): TreeItemType | undefined {
    if (element instanceof DelegateItem) {
      const registry = getDelegateRegistry();
      const categories = registry.getCategories();
      const category = categories.find(c =>
        c.delegates.some(d => d.name === element.delegate.name)
      );
      if (category) {
        return new CategoryItem(category.category, category.delegates);
      }
    }
    return undefined;
  }
}

/**
 * Tree data provider for BPMN files
 */
export class BpmnFilesTreeProvider implements vscode.TreeDataProvider<vscode.TreeItem> {
  private _onDidChangeTreeData = new vscode.EventEmitter<vscode.TreeItem | undefined | null | void>();
  readonly onDidChangeTreeData = this._onDidChangeTreeData.event;

  private fileWatcher: vscode.FileSystemWatcher;

  constructor() {
    // Watch for BPMN file changes
    this.fileWatcher = vscode.workspace.createFileSystemWatcher('**/*.bpmn');
    this.fileWatcher.onDidCreate(() => this.refresh());
    this.fileWatcher.onDidDelete(() => this.refresh());
    this.fileWatcher.onDidChange(() => this.refresh());
  }

  refresh(): void {
    this._onDidChangeTreeData.fire();
  }

  getTreeItem(element: vscode.TreeItem): vscode.TreeItem {
    return element;
  }

  async getChildren(): Promise<vscode.TreeItem[]> {
    const files = await vscode.workspace.findFiles('**/*.bpmn', '**/node_modules/**');

    return files.map(file => {
      const item = new vscode.TreeItem(
        path.basename(file.fsPath),
        vscode.TreeItemCollapsibleState.None
      );

      item.resourceUri = file;
      item.iconPath = new vscode.ThemeIcon('symbol-event');
      item.description = vscode.workspace.asRelativePath(path.dirname(file.fsPath));
      item.tooltip = file.fsPath;
      item.command = {
        command: 'vscode.openWith',
        title: 'Open BPMN',
        arguments: [file, 'fep-bpmn.editor']
      };

      return item;
    });
  }

  dispose(): void {
    this.fileWatcher.dispose();
  }
}
