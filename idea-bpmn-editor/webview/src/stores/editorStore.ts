import { create } from 'zustand';
import { JavaDelegate, SelectedElement, DelegateCategory } from '../types';

interface EditorState {
    // BPMN content
    bpmnXml: string;
    fileName: string;
    filePath: string;
    isDirty: boolean;

    // Delegates
    delegates: JavaDelegate[];
    lastScanned?: number;

    // Selection
    selectedElement: SelectedElement | null;

    // UI state
    showPalette: boolean;
    showProperties: boolean;
    paletteFilter: string;

    // Actions
    setBpmnXml: (xml: string) => void;
    setFileName: (name: string) => void;
    setFilePath: (path: string) => void;
    setDirty: (dirty: boolean) => void;
    setDelegates: (delegates: JavaDelegate[]) => void;
    setSelectedElement: (element: SelectedElement | null) => void;
    togglePalette: () => void;
    toggleProperties: () => void;
    setPaletteFilter: (filter: string) => void;

    // Computed
    getFilteredDelegates: () => JavaDelegate[];
    getDelegatesByCategory: () => Map<string, JavaDelegate[]>;
    getDelegateByName: (name: string) => JavaDelegate | undefined;
}

// Default categories
const defaultCategories: DelegateCategory[] = [
    { id: 'validate', displayName: '驗證類', color: '#4CAF50', icon: 'check-circle' },
    { id: 'account', displayName: '帳務類', color: '#2196F3', icon: 'credit-card' },
    { id: 'message', displayName: '電文類', color: '#FF9800', icon: 'mail' },
    { id: 'communication', displayName: '通訊類', color: '#9C27B0', icon: 'broadcast' },
    { id: 'log', displayName: '日誌類', color: '#795548', icon: 'file-text' },
    { id: 'other', displayName: '其他', color: '#607D8B', icon: 'package' }
];

export const useEditorStore = create<EditorState>((set, get) => ({
    // Initial state
    bpmnXml: '',
    fileName: 'untitled.bpmn',
    filePath: '',
    isDirty: false,
    delegates: [],
    lastScanned: undefined,
    selectedElement: null,
    showPalette: true,
    showProperties: true,
    paletteFilter: '',

    // Actions
    setBpmnXml: (xml) => set({ bpmnXml: xml }),
    setFileName: (name) => set({ fileName: name }),
    setFilePath: (path) => set({ filePath: path }),
    setDirty: (dirty) => set({ isDirty: dirty }),
    setDelegates: (delegates) => set({ delegates, lastScanned: Date.now() }),
    setSelectedElement: (element) => set({ selectedElement: element }),
    togglePalette: () => set((state) => ({ showPalette: !state.showPalette })),
    toggleProperties: () => set((state) => ({ showProperties: !state.showProperties })),
    setPaletteFilter: (filter) => set({ paletteFilter: filter }),

    // Computed
    getFilteredDelegates: () => {
        const { delegates, paletteFilter } = get();
        if (!paletteFilter) return delegates;
        const lowerFilter = paletteFilter.toLowerCase();
        return delegates.filter(d =>
            d.name.toLowerCase().includes(lowerFilter) ||
            d.displayName.toLowerCase().includes(lowerFilter) ||
            d.description.toLowerCase().includes(lowerFilter)
        );
    },

    getDelegatesByCategory: () => {
        const filteredDelegates = get().getFilteredDelegates();
        const byCategory = new Map<string, JavaDelegate[]>();

        // Initialize all categories
        defaultCategories.forEach(cat => {
            byCategory.set(cat.id, []);
        });

        // Group delegates
        filteredDelegates.forEach(delegate => {
            const categoryId = delegate.category?.id || 'other';
            const list = byCategory.get(categoryId) || [];
            list.push(delegate);
            byCategory.set(categoryId, list);
        });

        return byCategory;
    },

    getDelegateByName: (name) => {
        const { delegates } = get();
        return delegates.find(d => d.name === name);
    }
}));
