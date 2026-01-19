import React, { useCallback, useMemo } from 'react';
import { useEditorStore, postMessage } from '../stores/editorStore';

/**
 * Properties Panel - displays and edits properties of selected BPMN element
 */
export const PropertiesPanel: React.FC = () => {
  const { selectedElement, delegates } = useEditorStore();

  // Find matching delegate for current element
  const matchingDelegate = useMemo(() => {
    if (!selectedElement?.delegateExpression) return null;

    const match = selectedElement.delegateExpression.match(/\$\{(\w+)\}/);
    if (!match) return null;

    return delegates.find((d) => d.name === match[1]);
  }, [selectedElement, delegates]);

  // Handle delegate selection change
  const handleDelegateChange = useCallback(
    (e: React.ChangeEvent<HTMLSelectElement>) => {
      const delegateName = e.target.value;
      const editor = (window as any).bpmnEditor;

      if (editor && selectedElement) {
        const modeler = editor.getModeler();
        if (modeler) {
          const modeling = modeler.get('modeling');
          const elementRegistry = modeler.get('elementRegistry');
          const element = elementRegistry.get(selectedElement.id);

          if (element) {
            const businessObject = element.businessObject;
            businessObject.set(
              'camunda:delegateExpression',
              delegateName ? `\${${delegateName}}` : undefined
            );

            // Find delegate info
            const delegate = delegates.find((d) => d.name === delegateName);
            if (delegate) {
              modeling.updateProperties(element, {
                name: delegate.displayName || delegate.name
              });
            }
          }
        }
      }
    },
    [selectedElement, delegates]
  );

  // Handle opening delegate file
  const handleOpenDelegate = useCallback(() => {
    if (matchingDelegate) {
      postMessage({
        type: 'openDelegate',
        filePath: matchingDelegate.filePath,
        lineNumber: matchingDelegate.lineNumber
      });
    }
  }, [matchingDelegate]);

  // Handle name change
  const handleNameChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const editor = (window as any).bpmnEditor;
      if (editor && selectedElement) {
        const modeler = editor.getModeler();
        if (modeler) {
          const modeling = modeler.get('modeling');
          const elementRegistry = modeler.get('elementRegistry');
          const element = elementRegistry.get(selectedElement.id);

          if (element) {
            modeling.updateProperties(element, {
              name: e.target.value
            });
          }
        }
      }
    },
    [selectedElement]
  );

  if (!selectedElement) {
    return (
      <div className="properties-panel empty">
        <div className="empty-message">Select an element to view properties</div>
        <style>{panelStyles}</style>
      </div>
    );
  }

  const isServiceTask = selectedElement.type === 'bpmn:ServiceTask';
  const isSendTask = selectedElement.type === 'bpmn:SendTask';
  const supportsDelegate = isServiceTask || isSendTask;

  return (
    <div className="properties-panel">
      {/* Element Info */}
      <div className="panel-section">
        <div className="section-header">Element</div>
        <div className="property-row">
          <label>ID</label>
          <input type="text" value={selectedElement.id} readOnly className="readonly" />
        </div>
        <div className="property-row">
          <label>Type</label>
          <input
            type="text"
            value={selectedElement.type.replace('bpmn:', '')}
            readOnly
            className="readonly"
          />
        </div>
        <div className="property-row">
          <label>Name</label>
          <input
            type="text"
            value={selectedElement.name || ''}
            onChange={handleNameChange}
            placeholder="Enter element name"
          />
        </div>
      </div>

      {/* Delegate Configuration (for Service/Send Tasks) */}
      {supportsDelegate && (
        <div className="panel-section">
          <div className="section-header">Java Delegate</div>
          <div className="property-row">
            <label>Delegate</label>
            <select
              value={
                matchingDelegate?.name ||
                selectedElement.delegateExpression?.match(/\$\{(\w+)\}/)?.[1] ||
                ''
              }
              onChange={handleDelegateChange}
            >
              <option value="">-- Select Delegate --</option>
              {delegates.map((d) => (
                <option key={d.name} value={d.name}>
                  {d.displayName} ({d.name})
                </option>
              ))}
            </select>
          </div>

          {selectedElement.delegateExpression && (
            <div className="property-row">
              <label>Expression</label>
              <input
                type="text"
                value={selectedElement.delegateExpression}
                readOnly
                className="readonly code"
              />
            </div>
          )}

          {matchingDelegate && (
            <div className="delegate-details">
              <div className="delegate-header">
                <span
                  className="delegate-category"
                  style={{ backgroundColor: matchingDelegate.category.color }}
                >
                  {matchingDelegate.category.name}
                </span>
                <button className="open-file-btn" onClick={handleOpenDelegate} title="Open source file">
                  Open File
                </button>
              </div>

              {matchingDelegate.description && (
                <div className="delegate-description">{matchingDelegate.description}</div>
              )}

              {matchingDelegate.inputVariables.length > 0 && (
                <div className="variables-section">
                  <div className="variables-header">Input Variables</div>
                  <ul className="variables-list">
                    {matchingDelegate.inputVariables.map((v) => (
                      <li key={v.name}>
                        <code>{v.name}</code>
                        <span className="var-type">{v.type}</span>
                        {v.required && <span className="var-required">*</span>}
                      </li>
                    ))}
                  </ul>
                </div>
              )}

              {matchingDelegate.outputVariables.length > 0 && (
                <div className="variables-section">
                  <div className="variables-header">Output Variables</div>
                  <ul className="variables-list">
                    {matchingDelegate.outputVariables.map((v) => (
                      <li key={v.name}>
                        <code>{v.name}</code>
                        <span className="var-type">{v.type}</span>
                        {v.possibleValues && (
                          <span className="var-values">
                            [{v.possibleValues.join(', ')}]
                          </span>
                        )}
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          )}
        </div>
      )}

      <style>{panelStyles}</style>
    </div>
  );
};

const panelStyles = `
  .properties-panel {
    display: flex;
    flex-direction: column;
    height: 100%;
    background: var(--vscode-sideBar-background);
    color: var(--vscode-sideBar-foreground);
    font-size: 12px;
    overflow-y: auto;
  }

  .properties-panel.empty {
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .empty-message {
    color: var(--vscode-descriptionForeground);
    text-align: center;
    padding: 20px;
  }

  .panel-section {
    padding: 12px;
    border-bottom: 1px solid var(--vscode-sideBar-border);
  }

  .section-header {
    font-weight: 600;
    margin-bottom: 12px;
    text-transform: uppercase;
    font-size: 11px;
    letter-spacing: 0.5px;
    color: var(--vscode-sideBarSectionHeader-foreground);
  }

  .property-row {
    margin-bottom: 8px;
  }

  .property-row label {
    display: block;
    margin-bottom: 4px;
    color: var(--vscode-descriptionForeground);
    font-size: 11px;
  }

  .property-row input,
  .property-row select {
    width: 100%;
    padding: 4px 8px;
    background: var(--vscode-input-background);
    color: var(--vscode-input-foreground);
    border: 1px solid var(--vscode-input-border);
    border-radius: 2px;
    font-size: 12px;
    outline: none;
  }

  .property-row input:focus,
  .property-row select:focus {
    border-color: var(--vscode-focusBorder);
  }

  .property-row input.readonly {
    background: var(--vscode-input-background);
    opacity: 0.7;
    cursor: default;
  }

  .property-row input.code {
    font-family: var(--vscode-editor-font-family);
  }

  .delegate-details {
    margin-top: 12px;
    padding: 8px;
    background: var(--vscode-editor-background);
    border-radius: 4px;
  }

  .delegate-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 8px;
  }

  .delegate-category {
    padding: 2px 8px;
    border-radius: 10px;
    font-size: 10px;
    color: white;
  }

  .open-file-btn {
    padding: 2px 8px;
    background: var(--vscode-button-secondaryBackground);
    color: var(--vscode-button-secondaryForeground);
    border: none;
    border-radius: 2px;
    font-size: 11px;
    cursor: pointer;
  }

  .open-file-btn:hover {
    background: var(--vscode-button-secondaryHoverBackground);
  }

  .delegate-description {
    margin-bottom: 12px;
    font-style: italic;
    color: var(--vscode-descriptionForeground);
    line-height: 1.4;
  }

  .variables-section {
    margin-top: 8px;
  }

  .variables-header {
    font-weight: 500;
    margin-bottom: 4px;
    font-size: 11px;
  }

  .variables-list {
    list-style: none;
    padding: 0;
    margin: 0;
  }

  .variables-list li {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 2px 0;
    font-size: 11px;
  }

  .variables-list code {
    font-family: var(--vscode-editor-font-family);
    background: var(--vscode-textCodeBlock-background);
    padding: 1px 4px;
    border-radius: 2px;
  }

  .var-type {
    color: var(--vscode-symbolIcon-classForeground);
    font-size: 10px;
  }

  .var-required {
    color: var(--vscode-errorForeground);
  }

  .var-values {
    color: var(--vscode-descriptionForeground);
    font-size: 10px;
  }
`;

export default PropertiesPanel;
