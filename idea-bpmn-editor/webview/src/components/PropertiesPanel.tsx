import React from 'react';
import { useEditorStore } from '../stores/editorStore';
import { sendToIde } from '../App';

const PropertiesPanel: React.FC = () => {
    const { selectedElement, delegates, getDelegateByName } = useEditorStore();

    if (!selectedElement) {
        return (
            <div className="properties-panel">
                <div className="properties-header">Properties</div>
                <div className="empty-state">
                    <div className="empty-state-icon">📋</div>
                    <div>No element selected</div>
                </div>
            </div>
        );
    }

    // Extract delegate name from expression like ${delegateName}
    const getDelegateName = (expression?: string): string | null => {
        if (!expression) return null;
        const match = expression.match(/\$\{([^}]+)\}/);
        return match ? match[1] : null;
    };

    const delegateName = getDelegateName(selectedElement.delegateExpression);
    const delegate = delegateName ? getDelegateByName(delegateName) : null;

    const handleOpenSource = () => {
        if (delegate) {
            sendToIde({
                type: 'openDelegate',
                filePath: delegate.filePath,
                lineNumber: delegate.lineNumber
            });
        }
    };

    const isServiceTask = selectedElement.type === 'bpmn:ServiceTask' ||
                          selectedElement.type === 'bpmn:SendTask';

    return (
        <div className="properties-panel">
            <div className="properties-header">Properties</div>
            <div className="properties-content">
                {/* Basic Properties */}
                <div className="property-group">
                    <div className="property-group-title">General</div>

                    <div className="property-row">
                        <div className="property-label">ID</div>
                        <input
                            type="text"
                            className="property-input"
                            value={selectedElement.id}
                            readOnly
                        />
                    </div>

                    <div className="property-row">
                        <div className="property-label">Type</div>
                        <input
                            type="text"
                            className="property-input"
                            value={selectedElement.type.replace('bpmn:', '')}
                            readOnly
                        />
                    </div>

                    <div className="property-row">
                        <div className="property-label">Name</div>
                        <input
                            type="text"
                            className="property-input"
                            value={selectedElement.name || ''}
                            readOnly
                        />
                    </div>
                </div>

                {/* Delegate Properties for ServiceTask */}
                {isServiceTask && (
                    <div className="property-group">
                        <div className="property-group-title">Java Delegate</div>

                        <div className="property-row">
                            <div className="property-label">Delegate</div>
                            <select className="delegate-select" value={delegateName || ''}>
                                <option value="">-- Select Delegate --</option>
                                {delegates.map((d) => (
                                    <option key={d.name} value={d.name}>
                                        {d.displayName} (${'{'}${d.name}{'}'})
                                    </option>
                                ))}
                            </select>
                        </div>

                        {delegate && (
                            <div className="delegate-info">
                                <div className="delegate-info-row">
                                    <span className="delegate-info-label">Expression:</span>
                                    <code style={{ fontFamily: 'monospace' }}>
                                        ${'{'}${delegate.name}{'}'}
                                    </code>
                                </div>

                                <div className="delegate-info-row">
                                    <span className="delegate-info-label">Category:</span>
                                    <span
                                        className="category-badge"
                                        style={{
                                            background: delegate.category?.color || '#607D8B',
                                            color: 'white'
                                        }}
                                    >
                                        {delegate.category?.displayName || 'Other'}
                                    </span>
                                </div>

                                {delegate.description && (
                                    <div className="delegate-info-row" style={{ marginTop: '8px' }}>
                                        <span style={{ fontSize: '11px', color: '#888' }}>
                                            {delegate.description}
                                        </span>
                                    </div>
                                )}

                                <button
                                    className="open-source-btn"
                                    onClick={handleOpenSource}
                                >
                                    Open Source
                                </button>

                                {/* Input Variables */}
                                {delegate.inputVariables && delegate.inputVariables.length > 0 && (
                                    <div className="variables-section">
                                        <div className="variables-title">Input Variables</div>
                                        {delegate.inputVariables.map((v) => (
                                            <div className="variable-item" key={v.name}>
                                                <span className="variable-name">{v.name}</span>
                                                <span className="variable-type">{v.type}</span>
                                                {v.required && (
                                                    <span className="variable-required">*</span>
                                                )}
                                            </div>
                                        ))}
                                    </div>
                                )}

                                {/* Output Variables */}
                                {delegate.outputVariables && delegate.outputVariables.length > 0 && (
                                    <div className="variables-section">
                                        <div className="variables-title">Output Variables</div>
                                        {delegate.outputVariables.map((v) => (
                                            <div className="variable-item" key={v.name}>
                                                <span className="variable-name">{v.name}</span>
                                                <span className="variable-type">{v.type}</span>
                                            </div>
                                        ))}
                                    </div>
                                )}
                            </div>
                        )}

                        {!delegate && selectedElement.delegateExpression && (
                            <div className="delegate-info" style={{ color: '#f44336' }}>
                                <small>
                                    Delegate not found: {selectedElement.delegateExpression}
                                </small>
                            </div>
                        )}
                    </div>
                )}
            </div>
        </div>
    );
};

export default PropertiesPanel;
