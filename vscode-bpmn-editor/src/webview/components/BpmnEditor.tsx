import React, { useEffect, useRef, useCallback, useState } from 'react';
import BpmnModeler from 'bpmn-js/lib/Modeler';
import 'bpmn-js/dist/assets/diagram-js.css';
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css';
import { useEditorStore, postMessage } from '../stores/editorStore';
import camundaModdleDescriptor from 'camunda-bpmn-moddle/resources/camunda.json';

// Default empty BPMN diagram
const DEFAULT_BPMN = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
                  xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                  xmlns:modeler="http://camunda.org/schema/modeler/1.0"
                  id="Definitions_1"
                  targetNamespace="http://bpmn.io/schema/bpmn"
                  exporter="FEP BPMN Editor"
                  exporterVersion="1.0.0">
  <bpmn:process id="Process_1" name="New Process" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1" name="Start">
      <bpmn:outgoing>Flow_1</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:endEvent id="EndEvent_1" name="End">
      <bpmn:incoming>Flow_1</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="EndEvent_1" />
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_1">
      <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1">
        <dc:Bounds x="179" y="99" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="185" y="142" width="25" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EndEvent_1_di" bpmnElement="EndEvent_1">
        <dc:Bounds x="432" y="99" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="440" y="142" width="20" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Flow_1_di" bpmnElement="Flow_1">
        <dc:Bounds x="215" y="117" width="217" height="0" />
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`;

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
  const [isReady, setIsReady] = useState(false);
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

    // Load default diagram on init
    const initDiagram = async () => {
      try {
        await modeler.importXML(DEFAULT_BPMN);
        const canvas = modeler.get('canvas') as any;
        canvas.zoom('fit-viewport');
        setIsReady(true);
      } catch (err) {
        console.error('Error loading default BPMN:', err);
      }
    };

    initDiagram();

    // Cleanup
    return () => {
      modeler.destroy();
      modelerRef.current = null;
    };
  }, []);

  // Load BPMN XML when it changes (from extension)
  useEffect(() => {
    if (!modelerRef.current || !isReady) return;

    // If no bpmnXml provided, don't try to load (keep default diagram)
    if (!bpmnXml) {
      console.log('No BPMN XML provided, keeping default diagram');
      return;
    }

    const loadDiagram = async () => {
      try {
        console.log('Loading BPMN XML:', bpmnXml.substring(0, 100) + '...');
        await modelerRef.current!.importXML(bpmnXml);

        // Fit to viewport
        const canvas = modelerRef.current!.get('canvas') as any;
        canvas.zoom('fit-viewport');

        console.log('BPMN loaded successfully');
      } catch (err) {
        console.error('Error loading BPMN:', err);
        postMessage({
          type: 'showError',
          message: `Failed to load BPMN: ${err}`
        });
      }
    };

    loadDiagram();
  }, [bpmnXml, isReady]);

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
