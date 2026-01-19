import React, { useState, useCallback } from 'react';
import { useEditorStore, JavaDelegate, postMessage } from '../stores/editorStore';

interface ComponentPaletteProps {
  onDelegateSelect?: (delegate: JavaDelegate) => void;
}

/**
 * Component Palette - displays Java Delegates grouped by category
 */
export const ComponentPalette: React.FC<ComponentPaletteProps> = ({
  onDelegateSelect
}) => {
  const {
    delegates,
    categories,
    paletteFilter,
    setPaletteFilter,
    getDelegatesByCategory
  } = useEditorStore();

  const [expandedCategories, setExpandedCategories] = useState<Set<string>>(
    new Set(categories.map((c) => c.id))
  );
  const [draggedDelegate, setDraggedDelegate] = useState<JavaDelegate | null>(null);

  // Toggle category expansion
  const toggleCategory = useCallback((categoryId: string) => {
    setExpandedCategories((prev) => {
      const next = new Set(prev);
      if (next.has(categoryId)) {
        next.delete(categoryId);
      } else {
        next.add(categoryId);
      }
      return next;
    });
  }, []);

  // Handle drag start
  const handleDragStart = useCallback(
    (e: React.DragEvent, delegate: JavaDelegate) => {
      setDraggedDelegate(delegate);
      e.dataTransfer.setData('application/json', JSON.stringify(delegate));
      e.dataTransfer.effectAllowed = 'copy';
    },
    []
  );

  // Handle drag end
  const handleDragEnd = useCallback(() => {
    setDraggedDelegate(null);
  }, []);

  // Handle click on delegate
  const handleDelegateClick = useCallback(
    (delegate: JavaDelegate) => {
      onDelegateSelect?.(delegate);

      // Add to diagram
      const editor = (window as any).bpmnEditor;
      if (editor && editor.addDelegateTask) {
        editor.addDelegateTask(delegate);
      }
    },
    [onDelegateSelect]
  );

  // Handle double-click to open file
  const handleDelegateDoubleClick = useCallback((delegate: JavaDelegate) => {
    postMessage({
      type: 'openDelegate',
      filePath: delegate.filePath,
      lineNumber: delegate.lineNumber
    });
  }, []);

  // Get icon component
  const getIcon = (iconName: string) => {
    const icons: Record<string, string> = {
      'check-circle': '✓',
      'credit-card': '💳',
      'mail': '✉',
      'broadcast': '📡',
      'package': '📦'
    };
    return icons[iconName] || '•';
  };

  const delegatesByCategory = getDelegatesByCategory();

  return (
    <div className="component-palette">
      {/* Search box */}
      <div className="palette-search">
        <input
          type="text"
          placeholder="Search delegates..."
          value={paletteFilter}
          onChange={(e) => setPaletteFilter(e.target.value)}
          className="search-input"
        />
        {paletteFilter && (
          <button
            className="clear-button"
            onClick={() => setPaletteFilter('')}
            title="Clear search"
          >
            ×
          </button>
        )}
      </div>

      {/* Delegate count */}
      <div className="palette-info">
        {delegates.length} delegates found
      </div>

      {/* Categories */}
      <div className="palette-categories">
        {categories.map((category) => {
          const categoryDelegates = delegatesByCategory.get(category.id) || [];
          const isExpanded = expandedCategories.has(category.id);

          if (categoryDelegates.length === 0) {
            return null;
          }

          return (
            <div key={category.id} className="category">
              {/* Category header */}
              <div
                className="category-header"
                onClick={() => toggleCategory(category.id)}
                style={{ borderLeftColor: category.color }}
              >
                <span className="category-icon">
                  {isExpanded ? '▼' : '▶'}
                </span>
                <span className="category-name">{category.name}</span>
                <span className="category-count">{categoryDelegates.length}</span>
              </div>

              {/* Delegates */}
              {isExpanded && (
                <div className="category-delegates">
                  {categoryDelegates.map((delegate) => (
                    <div
                      key={delegate.name}
                      className={`delegate-item ${
                        draggedDelegate?.name === delegate.name ? 'dragging' : ''
                      }`}
                      draggable
                      onDragStart={(e) => handleDragStart(e, delegate)}
                      onDragEnd={handleDragEnd}
                      onClick={() => handleDelegateClick(delegate)}
                      onDoubleClick={() => handleDelegateDoubleClick(delegate)}
                      title={`${delegate.description}\n\nDouble-click to open source file`}
                    >
                      <span
                        className="delegate-icon"
                        style={{ color: delegate.category.color }}
                      >
                        {getIcon(delegate.icon)}
                      </span>
                      <div className="delegate-info">
                        <span className="delegate-name">
                          {delegate.displayName || delegate.name}
                        </span>
                        <span className="delegate-bean">${'{'}${delegate.name}{'}'}</span>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          );
        })}
      </div>

      {/* Empty state */}
      {delegates.length === 0 && (
        <div className="palette-empty">
          <p>No delegates found.</p>
          <p>Run "Scan Java Delegates" command.</p>
        </div>
      )}

      <style>{`
        .component-palette {
          display: flex;
          flex-direction: column;
          height: 100%;
          background: var(--vscode-sideBar-background);
          color: var(--vscode-sideBar-foreground);
          font-size: 13px;
        }

        .palette-search {
          display: flex;
          align-items: center;
          padding: 8px;
          border-bottom: 1px solid var(--vscode-sideBar-border);
        }

        .search-input {
          flex: 1;
          padding: 4px 8px;
          background: var(--vscode-input-background);
          color: var(--vscode-input-foreground);
          border: 1px solid var(--vscode-input-border);
          border-radius: 2px;
          outline: none;
        }

        .search-input:focus {
          border-color: var(--vscode-focusBorder);
        }

        .clear-button {
          margin-left: 4px;
          padding: 2px 6px;
          background: transparent;
          color: var(--vscode-foreground);
          border: none;
          cursor: pointer;
          opacity: 0.7;
        }

        .clear-button:hover {
          opacity: 1;
        }

        .palette-info {
          padding: 4px 8px;
          font-size: 11px;
          color: var(--vscode-descriptionForeground);
          border-bottom: 1px solid var(--vscode-sideBar-border);
        }

        .palette-categories {
          flex: 1;
          overflow-y: auto;
        }

        .category {
          border-bottom: 1px solid var(--vscode-sideBar-border);
        }

        .category-header {
          display: flex;
          align-items: center;
          padding: 8px;
          cursor: pointer;
          border-left: 3px solid transparent;
          user-select: none;
        }

        .category-header:hover {
          background: var(--vscode-list-hoverBackground);
        }

        .category-icon {
          margin-right: 4px;
          font-size: 10px;
          opacity: 0.7;
        }

        .category-name {
          flex: 1;
          font-weight: 500;
        }

        .category-count {
          padding: 2px 6px;
          background: var(--vscode-badge-background);
          color: var(--vscode-badge-foreground);
          border-radius: 10px;
          font-size: 11px;
        }

        .category-delegates {
          padding: 4px 0;
        }

        .delegate-item {
          display: flex;
          align-items: center;
          padding: 6px 8px 6px 20px;
          cursor: grab;
          user-select: none;
        }

        .delegate-item:hover {
          background: var(--vscode-list-hoverBackground);
        }

        .delegate-item.dragging {
          opacity: 0.5;
        }

        .delegate-icon {
          margin-right: 8px;
          font-size: 14px;
        }

        .delegate-info {
          display: flex;
          flex-direction: column;
          min-width: 0;
        }

        .delegate-name {
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }

        .delegate-bean {
          font-size: 10px;
          color: var(--vscode-descriptionForeground);
          font-family: var(--vscode-editor-font-family);
        }

        .palette-empty {
          padding: 20px;
          text-align: center;
          color: var(--vscode-descriptionForeground);
        }

        .palette-empty p {
          margin: 4px 0;
        }
      `}</style>
    </div>
  );
};

export default ComponentPalette;
