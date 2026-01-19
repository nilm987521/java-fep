import React, { useEffect, useRef, useCallback } from 'react';
import BpmnModeler from 'bpmn-js/lib/Modeler';
import 'bpmn-js/dist/assets/diagram-js.css';
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css';
import { useEditorStore, postMessage } from '../stores/editorStore';
import camundaModdleDescriptor from 'camunda-bpmn-moddle/resources/camunda';

interface BpmnEditorProps {
  onSelectionChange?: (element: any) => void;
  onContentChange?: (xml: string) => void;
}

export const BpmnEditor: React.FC<BpmnEditorProps> = ({
  onSelectionChange,
  onContentChange
}) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const modelerRef = useRef<BpmnModeler | null>(null);
  const { bpmnXml, setDirty, setSelectedElement } = useEditorStore();

  // Initialize modeler
  useEffect(() => {
    if (!containerRef.current) return;

    const modeler = new BpmnModeler({
      container: containerRef.current,
      keyboard: {
        bindTo: window
      },
      moddleExtensions: {
        camunda: camundaModdleDescriptor
      },
      additionalModules: [
        // Custom modules can be added here
      ]
    });

    modelerRef.current = modeler;

    // Handle selection changes
    const eventBus = modeler.get('eventBus') as any;

    eventBus.on('selection.changed', (e: any) => {
      const selection = e.newSelection;
      if (selection && selection.length > 0) {
        const element = selection[0];
        const businessObject = element.businessObject;

        setSelectedElement({
          id: element.id,
          type: element.type,
          name: businessObject?.name,
          delegateExpression: businessObject?.get('camunda:delegateExpression'),
          businessObject
        });

        onSelectionChange?.(element);
      } else {
        setSelectedElement(null);
        onSelectionChange?.(null);
      }
    });

    // Handle content changes
    eventBus.on('commandStack.changed', async () => {
      setDirty(true);

      try {
        const { xml } = await modeler.saveXML({ format: true });
        onContentChange?.(xml || '');
      } catch (err) {
        console.error('Error getting XML:', err);
      }
    });

    // Cleanup
    return () => {
      modeler.destroy();
      modelerRef.current = null;
    };
  }, []);

  // Load BPMN XML when it changes
  useEffect(() => {
    if (!modelerRef.current || !bpmnXml) return;

    const loadDiagram = async () => {
      try {
        await modelerRef.current!.importXML(bpmnXml);

        // Fit to viewport
        const canvas = modelerRef.current!.get('canvas') as any;
        canvas.zoom('fit-viewport');
      } catch (err) {
        console.error('Error loading BPMN:', err);
        postMessage({
          type: 'showError',
          message: `Failed to load BPMN: ${err}`
        });
      }
    };

    loadDiagram();
  }, [bpmnXml]);

  // Save BPMN
  const saveBpmn = useCallback(async () => {
    if (!modelerRef.current) return;

    try {
      const { xml } = await modelerRef.current.saveXML({ format: true });
      postMessage({ type: 'save', content: xml });
      setDirty(false);
    } catch (err) {
      console.error('Error saving BPMN:', err);
      postMessage({
        type: 'showError',
        message: `Failed to save: ${err}`
      });
    }
  }, []);

  // Export SVG
  const exportSvg = useCallback(async () => {
    if (!modelerRef.current) return;

    try {
      const { svg } = await modelerRef.current.saveSVG();
      postMessage({ type: 'exportSvg', content: svg });
    } catch (err) {
      console.error('Error exporting SVG:', err);
    }
  }, []);

  // Add element with delegate
  const addDelegateTask = useCallback((delegate: any, position?: { x: number; y: number }) => {
    if (!modelerRef.current) return;

    const modeling = modelerRef.current.get('modeling') as any;
    const elementFactory = modelerRef.current.get('elementFactory') as any;
    const canvas = modelerRef.current.get('canvas') as any;

    // Get current viewport center if no position specified
    const viewbox = canvas.viewbox();
    const x = position?.x ?? viewbox.x + viewbox.width / 2;
    const y = position?.y ?? viewbox.y + viewbox.height / 2;

    // Create service task with delegate expression
    const shape = elementFactory.createShape({
      type: 'bpmn:ServiceTask'
    });

    // Get root element
    const rootElement = canvas.getRootElement();

    // Create the element
    const created = modeling.createShape(shape, { x, y }, rootElement);

    // Update properties
    if (created) {
      const moddle = modelerRef.current.get('moddle') as any;
      const businessObject = created.businessObject;

      // Set name
      modeling.updateProperties(created, {
        name: delegate.displayName || delegate.name
      });

      // Set delegate expression using Camunda namespace
      businessObject.set('camunda:delegateExpression', `\${${delegate.name}}`);
    }

    return created;
  }, []);

  // Expose methods to parent
  useEffect(() => {
    (window as any).bpmnEditor = {
      save: saveBpmn,
      exportSvg,
      addDelegateTask,
      getModeler: () => modelerRef.current
    };
  }, [saveBpmn, exportSvg, addDelegateTask]);

  return (
    <div
      ref={containerRef}
      style={{
        width: '100%',
        height: '100%',
        background: 'var(--vscode-editor-background)'
      }}
    />
  );
};

export default BpmnEditor;
