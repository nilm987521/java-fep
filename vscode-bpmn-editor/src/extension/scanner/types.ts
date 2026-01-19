/**
 * Java Delegate component scanned from source code
 */
export interface JavaDelegateComponent {
  /** Spring bean name (from @Component annotation) */
  name: string;

  /** Java class name */
  className: string;

  /** Full package name */
  packageName: string;

  /** Absolute file path */
  filePath: string;

  /** Line number where class is defined */
  lineNumber: number;

  /** Description extracted from JavaDoc */
  description: string;

  /** Short display name */
  displayName: string;

  /** Category for grouping in palette */
  category: DelegateCategory;

  /** Icon identifier */
  icon: string;

  /** Input variables (from execution.getVariable) */
  inputVariables: ProcessVariable[];

  /** Output variables (from execution.setVariable) */
  outputVariables: ProcessVariable[];

  /** Whether this is a valid Camunda delegate */
  isValid: boolean;

  /** Last modified timestamp */
  lastModified: number;
}

/**
 * Process variable definition
 */
export interface ProcessVariable {
  /** Variable name */
  name: string;

  /** Java type (String, Long, Boolean, etc.) */
  type: string;

  /** Whether this variable is required */
  required: boolean;

  /** Default value if any */
  defaultValue?: string;

  /** Description from comments */
  description?: string;

  /** Possible values (for enums) */
  possibleValues?: string[];
}

/**
 * Delegate category for grouping
 */
export interface DelegateCategory {
  /** Category ID */
  id: string;

  /** Display name */
  name: string;

  /** Color for UI */
  color: string;

  /** Icon name */
  icon: string;
}

/**
 * Scanner configuration
 */
export interface ScannerConfig {
  /** Glob patterns for scanning */
  scanPaths: string[];

  /** Annotations that mark delegates */
  delegateAnnotations: string[];

  /** Interface that delegates implement */
  delegateInterface: string;

  /** Category definitions */
  categories: Record<string, DelegateCategory>;

  /** Whether to auto-scan on startup */
  autoScan: boolean;
}

/**
 * Scan result
 */
export interface ScanResult {
  /** All found delegates */
  delegates: JavaDelegateComponent[];

  /** Scan statistics */
  stats: {
    filesScanned: number;
    delegatesFound: number;
    scanDurationMs: number;
    errors: string[];
  };

  /** Timestamp of scan */
  scannedAt: number;
}

/**
 * Category mapping rules
 */
export const DEFAULT_CATEGORY_RULES: Array<{
  pattern: RegExp;
  category: string;
}> = [
  { pattern: /validate|check|verify/i, category: 'Validate' },
  { pattern: /freeze|unfreeze|debit|credit|account|amount/i, category: 'Account' },
  { pattern: /assemble|message|build.*response|parse/i, category: 'Message' },
  { pattern: /send|receive|fisc|communication|connect/i, category: 'Communication' },
  { pattern: /log|audit|record|mark|pending/i, category: 'Other' }
];

/**
 * Default categories
 */
export const DEFAULT_CATEGORIES: Record<string, DelegateCategory> = {
  'Validate': {
    id: 'validate',
    name: '驗證類',
    color: '#4CAF50',
    icon: 'check-circle'
  },
  'Account': {
    id: 'account',
    name: '帳務類',
    color: '#2196F3',
    icon: 'credit-card'
  },
  'Message': {
    id: 'message',
    name: '電文類',
    color: '#FF9800',
    icon: 'mail'
  },
  'Communication': {
    id: 'communication',
    name: '通訊類',
    color: '#9C27B0',
    icon: 'broadcast'
  },
  'Other': {
    id: 'other',
    name: '其他',
    color: '#607D8B',
    icon: 'package'
  }
};
