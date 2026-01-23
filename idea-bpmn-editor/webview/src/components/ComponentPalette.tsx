import React, { useState } from 'react';
import { useEditorStore } from '../stores/editorStore';
import { JavaDelegate } from '../types';
import { sendToIde } from '../App';

// Category definitions
const categories = [
    { id: 'validate', displayName: '驗證類', color: '#4CAF50' },
    { id: 'account', displayName: '帳務類', color: '#2196F3' },
    { id: 'message', displayName: '電文類', color: '#FF9800' },
    { id: 'communication', displayName: '通訊類', color: '#9C27B0' },
    { id: 'log', displayName: '日誌類', color: '#795548' },
    { id: 'other', displayName: '其他', color: '#607D8B' }
];

const ComponentPalette: React.FC = () => {
    const { paletteFilter, setPaletteFilter, getDelegatesByCategory } = useEditorStore();
    const [collapsedCategories, setCollapsedCategories] = useState<Set<string>>(new Set());

    const delegatesByCategory = getDelegatesByCategory();

    const toggleCategory = (categoryId: string) => {
        const newCollapsed = new Set(collapsedCategories);
        if (newCollapsed.has(categoryId)) {
            newCollapsed.delete(categoryId);
        } else {
            newCollapsed.add(categoryId);
        }
        setCollapsedCategories(newCollapsed);
    };

    const handleDragStart = (e: React.DragEvent, delegate: JavaDelegate) => {
        e.dataTransfer.setData('application/json', JSON.stringify(delegate));
        e.dataTransfer.effectAllowed = 'copy';
    };

    const handleDoubleClick = (delegate: JavaDelegate) => {
        sendToIde({
            type: 'openDelegate',
            filePath: delegate.filePath,
            lineNumber: delegate.lineNumber
        });
    };

    const handleClick = (delegate: JavaDelegate) => {
        // Add delegate at center of canvas
        const event = new CustomEvent('add-delegate', { detail: delegate });
        window.dispatchEvent(event);
    };

    return (
        <div className="palette">
            <div className="palette-header">
                <input
                    type="text"
                    className="palette-search"
                    placeholder="Search delegates..."
                    value={paletteFilter}
                    onChange={(e) => setPaletteFilter(e.target.value)}
                />
            </div>
            <div className="palette-content">
                {(() => {
                    // Check if there are any delegates at all
                    let totalDelegates = 0;
                    delegatesByCategory.forEach(list => {
                        totalDelegates += list.length;
                    });

                    if (totalDelegates === 0) {
                        return (
                            <div className="empty-state">
                                <div className="empty-state-icon">📦</div>
                                <div>No delegates found</div>
                                <div style={{ fontSize: '12px', marginTop: '8px', color: '#888' }}>
                                    Click refresh button to scan project
                                </div>
                            </div>
                        );
                    }

                    return categories.map((category) => {
                        const delegates = delegatesByCategory.get(category.id) || [];
                        if (delegates.length === 0) return null;

                        const isCollapsed = collapsedCategories.has(category.id);

                        return (
                            <div className="category" key={category.id}>
                                <div
                                    className="category-header"
                                    onClick={() => toggleCategory(category.id)}
                                >
                                    <svg
                                        className={`category-icon ${isCollapsed ? 'collapsed' : ''}`}
                                        viewBox="0 0 24 24"
                                        fill="currentColor"
                                    >
                                        <path d="M7 10l5 5 5-5H7z" />
                                    </svg>
                                    <span>{category.displayName}</span>
                                    <span
                                        className="category-badge"
                                        style={{ background: category.color, color: 'white' }}
                                    >
                                        {delegates.length}
                                    </span>
                                </div>
                                {!isCollapsed && (
                                    <div className="delegate-list">
                                        {delegates.map((delegate) => (
                                            <div
                                                key={delegate.name}
                                                className="delegate-item"
                                                draggable
                                                onDragStart={(e) => handleDragStart(e, delegate)}
                                                onClick={() => handleClick(delegate)}
                                                onDoubleClick={() => handleDoubleClick(delegate)}
                                                title={delegate.description || delegate.displayName}
                                            >
                                                <div className="delegate-name">
                                                    {delegate.displayName}
                                                </div>
                                                <div className="delegate-expression">
                                                    ${'{'}${delegate.name}{'}'}
                                                </div>
                                            </div>
                                        ))}
                                    </div>
                                )}
                            </div>
                        );
                    });
                })()}
            </div>
        </div>
    );
};

export default ComponentPalette;
