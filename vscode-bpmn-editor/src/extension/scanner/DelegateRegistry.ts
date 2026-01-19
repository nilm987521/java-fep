import * as vscode from 'vscode';
import { JavaDelegateComponent, ScanResult, DelegateCategory } from './types';
import { JavaDelegateScanner } from './JavaDelegateScanner';

/**
 * Registry for managing scanned Java Delegates
 * Provides lookup, caching, and change notification
 */
export class DelegateRegistry implements vscode.Disposable {
  private delegates: Map<string, JavaDelegateComponent> = new Map();
  private scanner: JavaDelegateScanner;
  private lastScanResult: ScanResult | null = null;
  private fileWatcher: vscode.FileSystemWatcher | null = null;
  private disposables: vscode.Disposable[] = [];

  // Events
  private readonly _onDidChange = new vscode.EventEmitter<void>();
  readonly onDidChange = this._onDidChange.event;

  private readonly _onDidScanComplete = new vscode.EventEmitter<ScanResult>();
  readonly onDidScanComplete = this._onDidScanComplete.event;

  constructor() {
    this.scanner = new JavaDelegateScanner();
    this.setupFileWatcher();
  }

  /**
   * Initialize registry and perform initial scan
   */
  async initialize(): Promise<void> {
    await this.refresh();
  }

  /**
   * Refresh delegates by rescanning
   */
  async refresh(): Promise<ScanResult> {
    const result = await this.scanner.scan();
    this.lastScanResult = result;

    // Update registry
    this.delegates.clear();
    for (const delegate of result.delegates) {
      this.delegates.set(delegate.name, delegate);
    }

    this._onDidChange.fire();
    this._onDidScanComplete.fire(result);

    return result;
  }

  /**
   * Get all delegates
   */
  getAll(): JavaDelegateComponent[] {
    return Array.from(this.delegates.values());
  }

  /**
   * Get delegate by name
   */
  getByName(name: string): JavaDelegateComponent | undefined {
    return this.delegates.get(name);
  }

  /**
   * Get delegates by category
   */
  getByCategory(categoryId: string): JavaDelegateComponent[] {
    return this.getAll().filter(d => d.category.id === categoryId);
  }

  /**
   * Get all categories with delegates
   */
  getCategories(): Array<{ category: DelegateCategory; delegates: JavaDelegateComponent[] }> {
    const categoryMap = new Map<string, { category: DelegateCategory; delegates: JavaDelegateComponent[] }>();

    for (const delegate of this.delegates.values()) {
      const catId = delegate.category.id;
      if (!categoryMap.has(catId)) {
        categoryMap.set(catId, {
          category: delegate.category,
          delegates: []
        });
      }
      categoryMap.get(catId)!.delegates.push(delegate);
    }

    return Array.from(categoryMap.values()).sort((a, b) =>
      a.category.name.localeCompare(b.category.name)
    );
  }

  /**
   * Search delegates by query
   */
  search(query: string): JavaDelegateComponent[] {
    const lowerQuery = query.toLowerCase();
    return this.getAll().filter(d =>
      d.name.toLowerCase().includes(lowerQuery) ||
      d.className.toLowerCase().includes(lowerQuery) ||
      d.displayName.toLowerCase().includes(lowerQuery) ||
      d.description.toLowerCase().includes(lowerQuery)
    );
  }

  /**
   * Get delegate for a BPMN element's delegateExpression
   */
  getForDelegateExpression(expression: string): JavaDelegateComponent | undefined {
    // Extract name from ${delegateName}
    const match = expression.match(/\$\{(\w+)\}/);
    if (match) {
      return this.delegates.get(match[1]);
    }
    return undefined;
  }

  /**
   * Get last scan result
   */
  getLastScanResult(): ScanResult | null {
    return this.lastScanResult;
  }

  /**
   * Get delegate count
   */
  get count(): number {
    return this.delegates.size;
  }

  /**
   * Check if registry has delegates
   */
  get isEmpty(): boolean {
    return this.delegates.size === 0;
  }

  /**
   * Export delegates to JSON format for webview
   */
  toJSON(): string {
    return JSON.stringify({
      delegates: this.getAll(),
      categories: this.getCategories().map(c => c.category),
      lastScanned: this.lastScanResult?.scannedAt
    });
  }

  /**
   * Setup file watcher for auto-refresh
   */
  private setupFileWatcher(): void {
    // Watch for changes in Java files
    this.fileWatcher = vscode.workspace.createFileSystemWatcher('**/*.java');

    this.disposables.push(
      this.fileWatcher.onDidChange(() => this.onFileChanged()),
      this.fileWatcher.onDidCreate(() => this.onFileChanged()),
      this.fileWatcher.onDidDelete(() => this.onFileChanged())
    );
  }

  /**
   * Handle file changes with debouncing
   */
  private refreshTimeout: NodeJS.Timeout | null = null;
  private onFileChanged(): void {
    // Debounce refresh
    if (this.refreshTimeout) {
      clearTimeout(this.refreshTimeout);
    }
    this.refreshTimeout = setTimeout(() => {
      this.refresh();
    }, 1000);
  }

  /**
   * Dispose resources
   */
  dispose(): void {
    if (this.refreshTimeout) {
      clearTimeout(this.refreshTimeout);
    }
    this.fileWatcher?.dispose();
    this.scanner.dispose();
    this._onDidChange.dispose();
    this._onDidScanComplete.dispose();
    this.disposables.forEach(d => d.dispose());
  }
}

// Singleton instance
let instance: DelegateRegistry | null = null;

/**
 * Get the singleton DelegateRegistry instance
 */
export function getDelegateRegistry(): DelegateRegistry {
  if (!instance) {
    instance = new DelegateRegistry();
  }
  return instance;
}

/**
 * Dispose the singleton instance
 */
export function disposeDelegateRegistry(): void {
  if (instance) {
    instance.dispose();
    instance = null;
  }
}
