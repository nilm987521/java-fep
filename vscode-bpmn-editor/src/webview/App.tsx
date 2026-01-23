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
      console.log('Received message from extension:', message.type, message);

      switch (message.type) {
        case 'loadBpmn':
          console.log('Loading BPMN content, length:', message.content?.length);
          console.log('File name:', message.fileName);
          console.log('File path:', message.filePath);
          setBpmnXml(message.content || '');
          setFileName(message.fileName || '');
          setFilePath(message.filePath || '');
          setDirty(false);
          break;

        case 'delegates':
          console.log('Received delegates:', message.data?.delegates?.length || 0);
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
    console.log('Webview ready, sending ready message to extension');
    postMessage({ type: 'ready' });

    return () => {
      window.removeEventListener('message', handleMessage);
    };
  }, [setBpmnXml, setFileName, setFilePath, setDirty, setDelegates]);

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

        /* ========================================
           BPMN.js Theme Overrides
           Uses VS Code CSS variables for automatic theme support
           ======================================== */

        /* Canvas background */
        .djs-container {
          background: var(--vscode-editor-background) !important;
        }

        /* Palette */
        .djs-palette {
          background: var(--vscode-sideBar-background) !important;
          border-color: var(--vscode-panel-border) !important;
        }

        .djs-palette-entries {
          background: var(--vscode-sideBar-background) !important;
        }

        /* Context Pad */
        .djs-context-pad {
          background: var(--vscode-editorWidget-background) !important;
          border-color: var(--vscode-panel-border) !important;
        }

        /* Popup Menu */
        .djs-popup {
          background: var(--vscode-editorWidget-background) !important;
          border-color: var(--vscode-panel-border) !important;
        }

        .djs-popup .entry:hover {
          background: var(--vscode-list-hoverBackground) !important;
        }

        /* ========================================
           BPMN Shape Styling
           ======================================== */

        /* Sequence Flow (連接線) */
        .djs-connection path.djs-visual {
          stroke: var(--vscode-editor-foreground) !important;
          stroke-width: 2px !important;
        }

        /* Sequence Flow 箭頭 */
        .djs-connection marker path {
          fill: var(--vscode-editor-foreground) !important;
          stroke: var(--vscode-editor-foreground) !important;
        }

        /* 選中的連接線 */
        .djs-connection.selected path.djs-visual {
          stroke: var(--vscode-focusBorder) !important;
          stroke-width: 2.5px !important;
        }

        /* 滑鼠懸停的連接線 */
        .djs-connection.hover path.djs-visual {
          stroke: var(--vscode-focusBorder) !important;
        }

        /* Shape 外框 (Task, Event, Gateway) */
        .djs-shape .djs-visual > rect,
        .djs-shape .djs-visual > circle,
        .djs-shape .djs-visual > polygon,
        .djs-shape .djs-visual > path {
          stroke: var(--vscode-editor-foreground) !important;
          stroke-width: 2px !important;
        }

        /* Task 背景填充 */
        .djs-shape .djs-visual > rect {
          fill: var(--vscode-editor-background) !important;
        }

        /* Gateway (閘道) 背景 */
        .djs-shape .djs-visual > polygon {
          fill: var(--vscode-editor-background) !important;
        }

        /* Event (事件) 背景 */
        .djs-shape .djs-visual > circle {
          fill: var(--vscode-editor-background) !important;
        }

        /* 選中的元素外框 */
        .djs-shape.selected .djs-visual > rect,
        .djs-shape.selected .djs-visual > circle,
        .djs-shape.selected .djs-visual > polygon {
          stroke: var(--vscode-focusBorder) !important;
          stroke-width: 2.5px !important;
        }

        /* 滑鼠懸停的元素外框 */
        .djs-shape.hover .djs-visual > rect,
        .djs-shape.hover .djs-visual > circle,
        .djs-shape.hover .djs-visual > polygon {
          stroke: var(--vscode-focusBorder) !important;
        }

        /* 文字標籤 */
        .djs-label text,
        .djs-shape text {
          fill: var(--vscode-editor-foreground) !important;
        }

        /* 連接線上的標籤 */
        .djs-connection text {
          fill: var(--vscode-editor-foreground) !important;
        }

        /* Start Event 綠色邊框 */
        .bpmn-icon-start-event-none ~ .djs-visual > circle,
        [data-element-id*="StartEvent"] .djs-visual > circle {
          stroke: var(--vscode-testing-iconPassed, #4caf50) !important;
        }

        /* End Event 紅色邊框 */
        .bpmn-icon-end-event-none ~ .djs-visual > circle,
        [data-element-id*="EndEvent"] .djs-visual > circle {
          stroke: var(--vscode-testing-iconFailed, #f44336) !important;
          stroke-width: 3px !important;
        }

        /* 選取框 */
        .djs-outline {
          stroke: var(--vscode-focusBorder) !important;
          stroke-width: 1px !important;
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

        /* 拖曳時的預覽 */
        .djs-dragging .djs-visual > * {
          opacity: 0.7;
        }

        /* Marker (箭頭) 定義 */
        marker path {
          fill: var(--vscode-editor-foreground) !important;
        }

        /* Message Flow 虛線 */
        .djs-connection[data-element-id*="MessageFlow"] path.djs-visual {
          stroke-dasharray: 8, 5 !important;
        }

        /* Association 點線 */
        .djs-connection[data-element-id*="Association"] path.djs-visual {
          stroke-dasharray: 3, 3 !important;
          stroke: var(--vscode-descriptionForeground) !important;
        }

        /* Resizer */
        .djs-resizer rect {
          fill: var(--vscode-focusBorder) !important;
        }

        /* Direct editing (inline text) */
        .djs-direct-editing-content {
          background: var(--vscode-input-background) !important;
          color: var(--vscode-input-foreground) !important;
          border: 1px solid var(--vscode-focusBorder) !important;
        }

        /* Minimap (if enabled) */
        .djs-minimap {
          background: var(--vscode-sideBar-background) !important;
          border-color: var(--vscode-panel-border) !important;
        }
      `}</style>
    </div>
  );
};

export default App;
