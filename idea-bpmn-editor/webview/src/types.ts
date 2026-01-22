// Type definitions for the BPMN Editor

export interface DelegateVariable {
    name: string;
    type: string;
    required: boolean;
    description?: string;
    defaultValue?: string;
}

export interface DelegateCategory {
    id: string;
    displayName: string;
    color: string;
    icon: string;
}

export interface JavaDelegate {
    name: string;
    className: string;
    packageName: string;
    filePath: string;
    lineNumber: number;
    description: string;
    displayName: string;
    category: DelegateCategory;
    inputVariables: DelegateVariable[];
    outputVariables: DelegateVariable[];
    valid: boolean;
    lastModified: number;
}

export interface SelectedElement {
    id: string;
    type: string;
    name?: string;
    delegateExpression?: string;
}

export interface IdeMessage {
    type: string;
    [key: string]: unknown;
}

// Global window interface for IDE communication
declare global {
    interface Window {
        ideCallback?: (message: string) => void;
        loadBpmn?: (xml: string) => void;
        setDelegates?: (delegates: JavaDelegate[]) => void;
        executeCommand?: (command: string) => void;
    }
}
