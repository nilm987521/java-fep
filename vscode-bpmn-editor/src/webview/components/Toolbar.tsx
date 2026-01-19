import React, { useCallback } from 'react';
import { useEditorStore, postMessage } from '../stores/editorStore';

/**
 * Editor Toolbar
 */
export const Toolbar: React.FC = () => {
  const {
    fileName,
    isDirty,
    showPalette,
    showProperties,
    togglePalette,
    toggleProperties,
    setDirty
  } = useEditorStore();

  // Save
  const handleSave = useCallback(async () => {
    const editor = (window as any).bpmnEditor;
    if (editor) {
      await editor.save();
    }
  }, []);

  // Export SVG
  const handleExportSvg = useCallback(async () => {
    const editor = (window as any).bpmnEditor;
    if (editor) {
      await editor.exportSvg();
    }
  }, []);

  // Zoom controls
  const handleZoomIn = useCallback(() => {
    const editor = (window as any).bpmnEditor;
    if (editor) {
      const modeler = editor.getModeler();
      if (modeler) {
        const canvas = modeler.get('canvas');
        canvas.zoom(canvas.zoom() * 1.2);
      }
    }
  }, []);

  const handleZoomOut = useCallback(() => {
    const editor = (window as any).bpmnEditor;
    if (editor) {
      const modeler = editor.getModeler();
      if (modeler) {
        const canvas = modeler.get('canvas');
        canvas.zoom(canvas.zoom() / 1.2);
      }
    }
  }, []);

  const handleZoomFit = useCallback(() => {
    const editor = (window as any).bpmnEditor;
    if (editor) {
      const modeler = editor.getModeler();
      if (modeler) {
        const canvas = modeler.get('canvas');
        canvas.zoom('fit-viewport');
      }
    }
  }, []);

  const handleZoomReset = useCallback(() => {
    const editor = (window as any).bpmnEditor;
    if (editor) {
      const modeler = editor.getModeler();
      if (modeler) {
        const canvas = modeler.get('canvas');
        canvas.zoom(1);
      }
    }
  }, []);

  // Undo/Redo
  const handleUndo = useCallback(() => {
    const editor = (window as any).bpmnEditor;
    if (editor) {
      const modeler = editor.getModeler();
      if (modeler) {
        const commandStack = modeler.get('commandStack');
        commandStack.undo();
      }
    }
  }, []);

  const handleRedo = useCallback(() => {
    const editor = (window as any).bpmnEditor;
    if (editor) {
      const modeler = editor.getModeler();
      if (modeler) {
        const commandStack = modeler.get('commandStack');
        commandStack.redo();
      }
    }
  }, []);

  // Rescan delegates
  const handleRescan = useCallback(() => {
    postMessage({ type: 'requestDelegates' });
  }, []);

  return (
    <div className="toolbar">
      {/* File info */}
      <div className="toolbar-section file-info">
        <span className="file-name">{fileName || 'Untitled'}</span>
        {isDirty && <span className="dirty-indicator" title="Unsaved changes">●</span>}
      </div>

      {/* File actions */}
      <div className="toolbar-section">
        <button onClick={handleSave} title="Save (Ctrl+S)" className="toolbar-btn">
          <span className="icon">💾</span>
          <span className="label">Save</span>
        </button>
        <button onClick={handleExportSvg} title="Export as SVG" className="toolbar-btn">
          <span className="icon">📤</span>
          <span className="label">Export</span>
        </button>
      </div>

      <div className="toolbar-divider" />

      {/* Edit actions */}
      <div className="toolbar-section">
        <button onClick={handleUndo} title="Undo (Ctrl+Z)" className="toolbar-btn icon-only">
          ↩
        </button>
        <button onClick={handleRedo} title="Redo (Ctrl+Y)" className="toolbar-btn icon-only">
          ↪
        </button>
      </div>

      <div className="toolbar-divider" />

      {/* Zoom controls */}
      <div className="toolbar-section">
        <button onClick={handleZoomOut} title="Zoom Out" className="toolbar-btn icon-only">
          −
        </button>
        <button onClick={handleZoomReset} title="Reset Zoom" className="toolbar-btn icon-only">
          100%
        </button>
        <button onClick={handleZoomIn} title="Zoom In" className="toolbar-btn icon-only">
          +
        </button>
        <button onClick={handleZoomFit} title="Fit to Screen" className="toolbar-btn icon-only">
          ⛶
        </button>
      </div>

      <div className="toolbar-divider" />

      {/* Scan delegates */}
      <div className="toolbar-section">
        <button onClick={handleRescan} title="Rescan Java Delegates" className="toolbar-btn">
          <span className="icon">🔄</span>
          <span className="label">Scan</span>
        </button>
      </div>

      {/* Spacer */}
      <div className="toolbar-spacer" />

      {/* Panel toggles */}
      <div className="toolbar-section">
        <button
          onClick={togglePalette}
          title="Toggle Palette"
          className={`toolbar-btn toggle ${showPalette ? 'active' : ''}`}
        >
          <span className="icon">📋</span>
          <span className="label">Palette</span>
        </button>
        <button
          onClick={toggleProperties}
          title="Toggle Properties"
          className={`toolbar-btn toggle ${showProperties ? 'active' : ''}`}
        >
          <span className="icon">⚙</span>
          <span className="label">Properties</span>
        </button>
      </div>

      <style>{`
        .toolbar {
          display: flex;
          align-items: center;
          height: 36px;
          padding: 0 8px;
          background: var(--vscode-editor-background);
          border-bottom: 1px solid var(--vscode-panel-border);
        }

        .toolbar-section {
          display: flex;
          align-items: center;
          gap: 4px;
        }

        .file-info {
          margin-right: 8px;
        }

        .file-name {
          font-weight: 500;
          max-width: 200px;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        .dirty-indicator {
          margin-left: 4px;
          color: var(--vscode-notificationsInfoIcon-foreground);
        }

        .toolbar-divider {
          width: 1px;
          height: 20px;
          margin: 0 8px;
          background: var(--vscode-panel-border);
        }

        .toolbar-spacer {
          flex: 1;
        }

        .toolbar-btn {
          display: flex;
          align-items: center;
          gap: 4px;
          padding: 4px 8px;
          background: transparent;
          color: var(--vscode-foreground);
          border: none;
          border-radius: 3px;
          cursor: pointer;
          font-size: 12px;
        }

        .toolbar-btn:hover {
          background: var(--vscode-toolbar-hoverBackground);
        }

        .toolbar-btn.icon-only {
          padding: 4px 6px;
        }

        .toolbar-btn.toggle.active {
          background: var(--vscode-toolbar-activeBackground);
        }

        .toolbar-btn .icon {
          font-size: 14px;
        }

        .toolbar-btn .label {
          display: none;
        }

        @media (min-width: 800px) {
          .toolbar-btn .label {
            display: inline;
          }
        }
      `}</style>
    </div>
  );
};

export default Toolbar;
