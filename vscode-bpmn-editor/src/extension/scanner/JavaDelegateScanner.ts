import * as vscode from 'vscode';
import * as fs from 'fs';
import * as path from 'path';
import {
  JavaDelegateComponent,
  ProcessVariable,
  ScannerConfig,
  ScanResult,
  DelegateCategory,
  DEFAULT_CATEGORY_RULES,
  DEFAULT_CATEGORIES
} from './types';

/**
 * Scans Java source files to find Camunda JavaDelegate implementations
 */
export class JavaDelegateScanner {
  private config: ScannerConfig;
  private outputChannel: vscode.OutputChannel;

  constructor(config?: Partial<ScannerConfig>) {
    this.config = {
      scanPaths: config?.scanPaths || ['**/delegate/**/*.java', '**/bpmn/**/*.java'],
      delegateAnnotations: config?.delegateAnnotations || ['@Component', '@Service', '@Named'],
      delegateInterface: config?.delegateInterface || 'JavaDelegate',
      categories: config?.categories || DEFAULT_CATEGORIES,
      autoScan: config?.autoScan ?? true
    };
    this.outputChannel = vscode.window.createOutputChannel('FEP BPMN Scanner');
  }

  /**
   * Scan workspace for Java Delegates
   */
  async scan(): Promise<ScanResult> {
    const startTime = Date.now();
    const delegates: JavaDelegateComponent[] = [];
    const errors: string[] = [];
    let filesScanned = 0;

    this.outputChannel.appendLine(`[${new Date().toISOString()}] Starting delegate scan...`);
    this.outputChannel.appendLine(`Scan paths: ${this.config.scanPaths.join(', ')}`);

    try {
      // Find all Java files matching patterns
      const javaFiles = await this.findJavaFiles();
      this.outputChannel.appendLine(`Found ${javaFiles.length} Java files to scan`);

      // Scan each file
      for (const file of javaFiles) {
        try {
          filesScanned++;
          const delegate = await this.scanFile(file);
          if (delegate) {
            delegates.push(delegate);
            this.outputChannel.appendLine(`  Found delegate: ${delegate.name} (${delegate.className})`);
          }
        } catch (error) {
          const errorMsg = `Error scanning ${file.fsPath}: ${error}`;
          errors.push(errorMsg);
          this.outputChannel.appendLine(`  ERROR: ${errorMsg}`);
        }
      }

    } catch (error) {
      const errorMsg = `Scan failed: ${error}`;
      errors.push(errorMsg);
      this.outputChannel.appendLine(`ERROR: ${errorMsg}`);
    }

    const scanDurationMs = Date.now() - startTime;
    this.outputChannel.appendLine(`Scan completed in ${scanDurationMs}ms. Found ${delegates.length} delegates.`);

    return {
      delegates,
      stats: {
        filesScanned,
        delegatesFound: delegates.length,
        scanDurationMs,
        errors
      },
      scannedAt: Date.now()
    };
  }

  /**
   * Find all Java files matching scan patterns
   */
  private async findJavaFiles(): Promise<vscode.Uri[]> {
    const allFiles: vscode.Uri[] = [];

    for (const pattern of this.config.scanPaths) {
      const files = await vscode.workspace.findFiles(pattern, '**/node_modules/**');
      allFiles.push(...files);
    }

    // Deduplicate
    const seen = new Set<string>();
    return allFiles.filter(file => {
      if (seen.has(file.fsPath)) {
        return false;
      }
      seen.add(file.fsPath);
      return true;
    });
  }

  /**
   * Scan a single Java file for delegate definition
   */
  private async scanFile(uri: vscode.Uri): Promise<JavaDelegateComponent | null> {
    const content = await fs.promises.readFile(uri.fsPath, 'utf-8');
    const lines = content.split('\n');

    // Check if file contains JavaDelegate
    if (!this.isJavaDelegate(content)) {
      return null;
    }

    // Extract information
    const packageName = this.extractPackage(content);
    const className = this.extractClassName(content);
    const componentName = this.extractComponentName(content);

    if (!className || !componentName) {
      return null;
    }

    const javadoc = this.extractJavadoc(content);
    const lineNumber = this.findClassLineNumber(lines);
    const inputVariables = this.extractInputVariables(content);
    const outputVariables = this.extractOutputVariables(content);
    const category = this.determineCategory(className, componentName);

    const stats = await fs.promises.stat(uri.fsPath);

    return {
      name: componentName,
      className,
      packageName: packageName || '',
      filePath: uri.fsPath,
      lineNumber,
      description: javadoc.description,
      displayName: javadoc.displayName || this.generateDisplayName(className),
      category,
      icon: category.icon,
      inputVariables,
      outputVariables,
      isValid: true,
      lastModified: stats.mtimeMs
    };
  }

  /**
   * Check if content contains JavaDelegate implementation
   */
  private isJavaDelegate(content: string): boolean {
    const hasInterface = new RegExp(`implements\\s+.*${this.config.delegateInterface}`).test(content);
    const hasAnnotation = this.config.delegateAnnotations.some(ann =>
      content.includes(ann)
    );
    return hasInterface && hasAnnotation;
  }

  /**
   * Extract package name
   */
  private extractPackage(content: string): string | null {
    const match = content.match(/package\s+([\w.]+)\s*;/);
    return match ? match[1] : null;
  }

  /**
   * Extract class name
   */
  private extractClassName(content: string): string | null {
    const match = content.match(/public\s+class\s+(\w+)/);
    return match ? match[1] : null;
  }

  /**
   * Extract component/bean name from annotation
   */
  private extractComponentName(content: string): string | null {
    // Try @Component("name")
    let match = content.match(/@Component\s*\(\s*["'](\w+)["']\s*\)/);
    if (match) {
      return match[1];
    }

    // Try @Component(value = "name")
    match = content.match(/@Component\s*\(\s*value\s*=\s*["'](\w+)["']\s*\)/);
    if (match) {
      return match[1];
    }

    // Try @Service("name")
    match = content.match(/@Service\s*\(\s*["'](\w+)["']\s*\)/);
    if (match) {
      return match[1];
    }

    // Try @Named("name")
    match = content.match(/@Named\s*\(\s*["'](\w+)["']\s*\)/);
    if (match) {
      return match[1];
    }

    // If no name specified, derive from class name
    const className = this.extractClassName(content);
    if (className) {
      // Convert PascalCase to camelCase
      return className.charAt(0).toLowerCase() + className.slice(1);
    }

    return null;
  }

  /**
   * Extract JavaDoc description
   */
  private extractJavadoc(content: string): { description: string; displayName: string } {
    // Find class-level JavaDoc
    const javadocMatch = content.match(/\/\*\*[\s\S]*?\*\/\s*(?:@\w+[^\n]*\n\s*)*public\s+class/);

    if (!javadocMatch) {
      return { description: '', displayName: '' };
    }

    const javadoc = javadocMatch[0];

    // Extract description (first line after /**)
    const descMatch = javadoc.match(/\/\*\*\s*\n?\s*\*\s*(.+)/);
    const description = descMatch ? descMatch[1].trim() : '';

    // Look for display name in description (Chinese characters before colon or in angle brackets)
    const displayMatch = description.match(/(?:對應|BPMN).*?[：:]\s*(\S+)|<p>(.+?)<\/p>|^([^<\n]+)/);
    const displayName = displayMatch
      ? (displayMatch[1] || displayMatch[2] || displayMatch[3] || '').trim()
      : '';

    return { description, displayName };
  }

  /**
   * Find the line number where class is defined
   */
  private findClassLineNumber(lines: string[]): number {
    for (let i = 0; i < lines.length; i++) {
      if (/public\s+class\s+\w+/.test(lines[i])) {
        return i + 1; // 1-indexed
      }
    }
    return 1;
  }

  /**
   * Extract input variables (execution.getVariable calls)
   */
  private extractInputVariables(content: string): ProcessVariable[] {
    const variables: ProcessVariable[] = [];
    const seen = new Set<string>();

    // Pattern: execution.getVariable("name")
    const pattern = /execution\.getVariable\s*\(\s*["'](\w+)["']\s*\)/g;
    let match;

    while ((match = pattern.exec(content)) !== null) {
      const name = match[1];
      if (seen.has(name)) {
        continue;
      }
      seen.add(name);

      // Try to determine type from cast
      const typePattern = new RegExp(
        `\\(\\s*(\\w+)\\s*\\)\\s*execution\\.getVariable\\s*\\(\\s*["']${name}["']`,
        'g'
      );
      const typeMatch = typePattern.exec(content);
      const type = typeMatch ? typeMatch[1] : 'Object';

      variables.push({
        name,
        type,
        required: true,
        description: this.findVariableComment(content, name)
      });
    }

    return variables;
  }

  /**
   * Extract output variables (execution.setVariable calls)
   */
  private extractOutputVariables(content: string): ProcessVariable[] {
    const variables: ProcessVariable[] = [];
    const seen = new Set<string>();

    // Pattern: execution.setVariable("name", value)
    const pattern = /execution\.setVariable\s*\(\s*["'](\w+)["']\s*,\s*([^)]+)\)/g;
    let match;

    while ((match = pattern.exec(content)) !== null) {
      const name = match[1];
      const value = match[2].trim();

      if (seen.has(name)) {
        continue;
      }
      seen.add(name);

      // Determine type from value
      let type = 'Object';
      const possibleValues: string[] = [];

      if (value.startsWith('"') || value.startsWith("'")) {
        type = 'String';
        // Extract string value
        const strMatch = value.match(/["']([^"']+)["']/);
        if (strMatch) {
          possibleValues.push(strMatch[1]);
        }
      } else if (/^\d+L?$/.test(value)) {
        type = value.endsWith('L') ? 'Long' : 'Integer';
      } else if (/^true|false$/.test(value)) {
        type = 'Boolean';
      }

      // Find all possible values for this variable
      const allValuesPattern = new RegExp(
        `execution\\.setVariable\\s*\\(\\s*["']${name}["']\\s*,\\s*["']([^"']+)["']\\s*\\)`,
        'g'
      );
      let valMatch;
      while ((valMatch = allValuesPattern.exec(content)) !== null) {
        if (!possibleValues.includes(valMatch[1])) {
          possibleValues.push(valMatch[1]);
        }
      }

      variables.push({
        name,
        type,
        required: false,
        possibleValues: possibleValues.length > 0 ? possibleValues : undefined,
        description: this.findVariableComment(content, name)
      });
    }

    return variables;
  }

  /**
   * Find comment for a variable
   */
  private findVariableComment(content: string, variableName: string): string | undefined {
    // Look for comment before or on the same line as variable usage
    const pattern = new RegExp(
      `(?:\\/\\/\\s*([^\\n]+)\\s*\\n\\s*)?[^\\n]*["']${variableName}["']`,
      'g'
    );
    const match = pattern.exec(content);
    return match?.[1]?.trim();
  }

  /**
   * Determine category based on class/component name
   */
  private determineCategory(className: string, componentName: string): DelegateCategory {
    const searchString = `${className} ${componentName}`.toLowerCase();

    for (const rule of DEFAULT_CATEGORY_RULES) {
      if (rule.pattern.test(searchString)) {
        return this.config.categories[rule.category] || DEFAULT_CATEGORIES[rule.category];
      }
    }

    return this.config.categories['Other'] || DEFAULT_CATEGORIES['Other'];
  }

  /**
   * Generate display name from class name
   */
  private generateDisplayName(className: string): string {
    // Remove common suffixes
    let name = className
      .replace(/Delegate$/, '')
      .replace(/Handler$/, '')
      .replace(/Processor$/, '');

    // Convert PascalCase to spaced words
    name = name.replace(/([a-z])([A-Z])/g, '$1 $2');

    return name;
  }

  /**
   * Update configuration
   */
  updateConfig(config: Partial<ScannerConfig>): void {
    this.config = { ...this.config, ...config };
  }

  /**
   * Get current configuration
   */
  getConfig(): ScannerConfig {
    return { ...this.config };
  }

  /**
   * Dispose resources
   */
  dispose(): void {
    this.outputChannel.dispose();
  }
}
