import React, { useEffect, useCallback } from 'react';
import { useEditorStore, postMessage, getVsCodeApi } from './stores/editorStore';
import { BpmnEditor } from './components/BpmnEditor';
import { ComponentPalette } from './components/ComponentPalette';
import { PropertiesPanel } from './components/PropertiesPanel';
import { Toolbar } from './components/Toolbar';

/**
 * Main Application Component
 */
export const App: React.FC = () => {
  const {
    showPalette,
    showProperties,
    setBpmnXml,
    setFileName,
    setFilePath,
    setDelegates,
    setDirty
  } = useEditorStore();

  // Handle messages from extension
  useEffect(() => {
    const handleMessage = (event: MessageEvent) => {
      const message = event.data;

      switch (message.type) {
        case 'loadBpmn':
          setBpmnXml(message.content);
          setFileName(message.fileName || '');
          setFilePath(message.filePath || '');
          setDirty(false);
          break;

        case 'delegates':
          if (message.data) {
            setDelegates(
              message.data.delegates || [],
              message.data.categories || [],
              message.data.lastScanned
            );
          }
          break;
      }
    };

    window.addEventListener('message', handleMessage);

    // Notify extension that we're ready
    postMessage({ type: 'ready' });

    return () => {
      window.removeEventListener('message', handleMessage);
    };
  }, []);

  // Handle keyboard shortcuts
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      // Save: Ctrl+S or Cmd+S
      if ((e.ctrlKey || e.metaKey) && e.key === 's') {
        e.preventDefault();
        const editor = (window as any).bpmnEditor;
        if (editor) {
          editor.save();
        }
      }

      // Undo: Ctrl+Z
      if ((e.ctrlKey || e.metaKey) && e.key === 'z' && !e.shiftKey) {
        const editor = (window as any).bpmnEditor;
        if (editor) {
          const modeler = editor.getModeler();
          if (modeler) {
            const commandStack = modeler.get('commandStack');
            commandStack.undo();
          }
        }
      }

      // Redo: Ctrl+Shift+Z or Ctrl+Y
      if (
        ((e.ctrlKey || e.metaKey) && e.shiftKey && e.key === 'z') ||
        ((e.ctrlKey || e.metaKey) && e.key === 'y')
      ) {
        const editor = (window as any).bpmnEditor;
        if (editor) {
          const modeler = editor.getModeler();
          if (modeler) {
            const commandStack = modeler.get('commandStack');
            commandStack.redo();
          }
        }
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);

  // Handle content changes
  const handleContentChange = useCallback((xml: string) => {
    // Content changed, mark as dirty
    // The save will be triggered by user action
  }, []);

  return (
    <div className="app">
      <Toolbar />
      <div className="editor-container">
        {/* Left Panel - Component Palette */}
        {showPalette && (
          <div className="left-panel">
            <ComponentPalette />
          </div>
        )}

        {/* Center - BPMN Editor */}
        <div className="center-panel">
          <BpmnEditor onContentChange={handleContentChange} />
        </div>

        {/* Right Panel - Properties */}
        {showProperties && (
          <div className="right-panel">
            <PropertiesPanel />
          </div>
        )}
      </div>

      <style>{`
        .app {
          display: flex;
          flex-direction: column;
          height: 100vh;
          width: 100vw;
          overflow: hidden;
          background: var(--vscode-editor-background);
          color: var(--vscode-editor-foreground);
        }

        .editor-container {
          display: flex;
          flex: 1;
          overflow: hidden;
        }

        .left-panel {
          width: 250px;
          min-width: 200px;
          max-width: 400px;
          border-right: 1px solid var(--vscode-panel-border);
          overflow: hidden;
          display: flex;
          flex-direction: column;
        }

        .center-panel {
          flex: 1;
          overflow: hidden;
          position: relative;
        }

        .right-panel {
          width: 280px;
          min-width: 200px;
          max-width: 400px;
          border-left: 1px solid var(--vscode-panel-border);
          overflow: hidden;
        }

        /* BPMN.js overrides for dark theme */
        .djs-container {
          background: var(--vscode-editor-background) !important;
        }

        .djs-palette {
          background: var(--vscode-sideBar-background) !important;
          border-color: var(--vscode-panel-border) !important;
        }

        .djs-palette-entries {
          background: var(--vscode-sideBar-background) !important;
        }

        .djs-context-pad {
          background: var(--vscode-editorWidget-background) !important;
          border-color: var(--vscode-panel-border) !important;
        }

        .djs-popup {
          background: var(--vscode-editorWidget-background) !important;
          border-color: var(--vscode-panel-border) !important;
        }

        .djs-popup .entry:hover {
          background: var(--vscode-list-hoverBackground) !important;
        }

        /* Selection highlight */
        .djs-element.selected .djs-outline {
          stroke: var(--vscode-focusBorder) !important;
          stroke-width: 2px !important;
        }

        /* Hover highlight */
        .djs-element.hover .djs-outline {
          stroke: var(--vscode-focusBorder) !important;
          stroke-width: 1px !important;
          stroke-dasharray: 4 !important;
        }

        /* Connection lines */
        .djs-connection .djs-visual path {
          stroke: var(--vscode-editor-foreground) !important;
        }

        /* Labels */
        .djs-label text {
          fill: var(--vscode-editor-foreground) !important;
        }

        /* Resizer */
        .djs-resizer rect {
          fill: var(--vscode-focusBorder) !important;
        }
      `}</style>
    </div>
  );
};

export default App;
