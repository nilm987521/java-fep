import { create } from 'zustand';

/**
 * Delegate component from Java source
 */
export interface JavaDelegate {
  name: string;
  className: string;
  packageName: string;
  filePath: string;
  lineNumber: number;
  description: string;
  displayName: string;
  category: {
    id: string;
    name: string;
    color: string;
    icon: string;
  };
  icon: string;
  inputVariables: Array<{
    name: string;
    type: string;
    required: boolean;
    description?: string;
  }>;
  outputVariables: Array<{
    name: string;
    type: string;
    possibleValues?: string[];
    description?: string;
  }>;
}

/**
 * Category grouping
 */
export interface DelegateCategory {
  id: string;
  name: string;
  color: string;
  icon: string;
}

/**
 * Selected BPMN element
 */
export interface SelectedElement {
  id: string;
  type: string;
  name?: string;
  delegateExpression?: string;
  businessObject?: any;
}

/**
 * Editor state
 */
interface EditorState {
  // BPMN content
  bpmnXml: string;
  fileName: string;
  filePath: string;
  isDirty: boolean;

  // Delegates
  delegates: JavaDelegate[];
  categories: DelegateCategory[];
  lastScanned?: number;

  // Selection
  selectedElement: SelectedElement | null;

  // UI state
  showPalette: boolean;
  showProperties: boolean;
  showMinimap: boolean;
  paletteFilter: string;

  // Actions
  setBpmnXml: (xml: string) => void;
  setFileName: (name: string) => void;
  setFilePath: (path: string) => void;
  setDirty: (dirty: boolean) => void;
  setDelegates: (delegates: JavaDelegate[], categories: DelegateCategory[], lastScanned?: number) => void;
  setSelectedElement: (element: SelectedElement | null) => void;
  togglePalette: () => void;
  toggleProperties: () => void;
  toggleMinimap: () => void;
  setPaletteFilter: (filter: string) => void;
  getFilteredDelegates: () => JavaDelegate[];
  getDelegatesByCategory: () => Map<string, JavaDelegate[]>;
}

/**
 * Editor store
 */
export const useEditorStore = create<EditorState>((set, get) => ({
  // Initial state
  bpmnXml: '',
  fileName: '',
  filePath: '',
  isDirty: false,
  delegates: [],
  categories: [],
  lastScanned: undefined,
  selectedElement: null,
  showPalette: true,
  showProperties: true,
  showMinimap: true,
  paletteFilter: '',

  // Actions
  setBpmnXml: (xml) => set({ bpmnXml: xml }),

  setFileName: (name) => set({ fileName: name }),

  setFilePath: (path) => set({ filePath: path }),

  setDirty: (dirty) => set({ isDirty: dirty }),

  setDelegates: (delegates, categories, lastScanned) =>
    set({ delegates, categories, lastScanned }),

  setSelectedElement: (element) => set({ selectedElement: element }),

  togglePalette: () => set((state) => ({ showPalette: !state.showPalette })),

  toggleProperties: () => set((state) => ({ showProperties: !state.showProperties })),

  toggleMinimap: () => set((state) => ({ showMinimap: !state.showMinimap })),

  setPaletteFilter: (filter) => set({ paletteFilter: filter }),

  getFilteredDelegates: () => {
    const { delegates, paletteFilter } = get();
    if (!paletteFilter) {
      return delegates;
    }
    const lowerFilter = paletteFilter.toLowerCase();
    return delegates.filter(
      (d) =>
        d.name.toLowerCase().includes(lowerFilter) ||
        d.displayName.toLowerCase().includes(lowerFilter) ||
        d.description.toLowerCase().includes(lowerFilter)
    );
  },

  getDelegatesByCategory: () => {
    const delegates = get().getFilteredDelegates();
    const map = new Map<string, JavaDelegate[]>();

    for (const delegate of delegates) {
      const categoryId = delegate.category.id;
      if (!map.has(categoryId)) {
        map.set(categoryId, []);
      }
      map.get(categoryId)!.push(delegate);
    }

    return map;
  }
}));

/**
 * VSCode API interface
 */
declare global {
  interface Window {
    acquireVsCodeApi?: () => VsCodeApi;
  }
}

interface VsCodeApi {
  postMessage: (message: any) => void;
  getState: () => any;
  setState: (state: any) => void;
}

let vscodeApi: VsCodeApi | null = null;

/**
 * Get VSCode API instance
 */
export function getVsCodeApi(): VsCodeApi {
  if (!vscodeApi && window.acquireVsCodeApi) {
    vscodeApi = window.acquireVsCodeApi();
  }
  return vscodeApi!;
}

/**
 * Post message to extension
 */
export function postMessage(message: any): void {
  const api = getVsCodeApi();
  if (api) {
    api.postMessage(message);
  }
}
