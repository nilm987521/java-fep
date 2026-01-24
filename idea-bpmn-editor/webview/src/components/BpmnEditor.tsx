import React, { useEffect, useRef } from 'react';
import BpmnModeler from 'bpmn-js/lib/Modeler';
import camundaModdleDescriptor from 'camunda-bpmn-moddle/resources/camunda.json';
import { useEditorStore } from '../stores/editorStore';
import { sendToIde } from '../App';
import { SelectedElement } from '../types';
import coloredRendererModule from '../custom-renderer';

// Import bpmn-js styles
import 'bpmn-js/dist/assets/diagram-js.css';
import 'bpmn-js/dist/assets/bpmn-js.css';
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css';

const BpmnEditor: React.FC = () => {
    const containerRef = useRef<HTMLDivElement>(null);
    const modelerRef = useRef<BpmnModeler | null>(null);
    const { bpmnXml, isDarkTheme, setDirty, setSelectedElement } = useEditorStore();

    useEffect(() => {
        if (!containerRef.current) return;

        // Initialize modeler with custom renderer
        const modeler = new BpmnModeler({
            container: containerRef.current,
            moddleExtensions: {
                camunda: camundaModdleDescriptor
            },
            additionalModules: [
                coloredRendererModule
            ],
            keyboard: {
                bindTo: window
            }
        });

        modelerRef.current = modeler;

        // Setup event listeners
        const eventBus = modeler.get('eventBus') as any;

        // Track changes
        eventBus.on('commandStack.changed', () => {
            setDirty(true);
            saveCurrentXml();
        });

        // Track selection
        eventBus.on('selection.changed', (e: any) => {
            const selection = e.newSelection;
            if (selection && selection.length === 1) {
                const element = selection[0];
                const businessObject = element.businessObject;

                const selectedElement: SelectedElement = {
                    id: element.id,
                    type: element.type,
                    name: businessObject?.name || '',
                    delegateExpression: businessObject?.delegateExpression
                };
                setSelectedElement(selectedElement);
            } else {
                setSelectedElement(null);
            }
        });

        // Listen for commands from IDE
        const handleCommand = (e: CustomEvent<string>) => {
            executeCommand(e.detail);
        };
        window.addEventListener('bpmn-command', handleCommand as EventListener);

        // Listen for add-delegate events from ComponentPalette
        const handleAddDelegate = (e: CustomEvent<any>) => {
            addDelegateToCenter(e.detail);
        };
        window.addEventListener('add-delegate', handleAddDelegate as EventListener);

        return () => {
            window.removeEventListener('bpmn-command', handleCommand as EventListener);
            window.removeEventListener('add-delegate', handleAddDelegate as EventListener);
            modeler.destroy();
        };
    }, [setDirty, setSelectedElement]);

    // Load BPMN XML when it changes
    useEffect(() => {
        if (!modelerRef.current || !bpmnXml) return;

        modelerRef.current.importXML(bpmnXml).catch((err: Error) => {
            console.error('Failed to import BPMN:', err);
            sendToIde({ type: 'showError', message: 'Failed to load BPMN: ' + err.message });
        });
    }, [bpmnXml]);

    // Update connection colors when theme changes
    useEffect(() => {
        if (!containerRef.current) return;

        // Get the stroke color from CSS variable
        const strokeColor = getComputedStyle(document.body)
            .getPropertyValue('--bpmn-connection-stroke').trim() || '#ffffff';

        // Update all existing connection paths
        const connections = containerRef.current.querySelectorAll('.djs-connection path');
        connections.forEach((path) => {
            path.setAttribute('stroke', strokeColor);
        });

        // Update all markers (arrows)
        const markers = containerRef.current.querySelectorAll('marker path');
        markers.forEach((path) => {
            path.setAttribute('fill', strokeColor);
            path.setAttribute('stroke', strokeColor);
        });
    }, [isDarkTheme]);

    const saveCurrentXml = async () => {
        if (!modelerRef.current) return;

        try {
            const result = await modelerRef.current.saveXML({ format: true });
            sendToIde({ type: 'update', xml: result.xml });
        } catch (err) {
            console.error('Failed to save XML:', err);
        }
    };

    const executeCommand = (command: string) => {
        if (!modelerRef.current) return;

        const canvas = modelerRef.current.get('canvas') as any;
        const commandStack = modelerRef.current.get('commandStack') as any;

        switch (command) {
            case 'undo':
                commandStack.undo();
                break;
            case 'redo':
                commandStack.redo();
                break;
            case 'zoomIn':
                canvas.zoom(canvas.zoom() * 1.2);
                break;
            case 'zoomOut':
                canvas.zoom(canvas.zoom() / 1.2);
                break;
            case 'fitToScreen':
                canvas.zoom('fit-viewport');
                break;
            case 'exportSvg':
                exportSvg();
                break;
            case 'exportPng':
                exportPng();
                break;
            case 'save':
                sendToIde({ type: 'save' });
                break;
            default:
                console.log('Unknown command:', command);
        }
    };

    const exportSvg = async () => {
        if (!modelerRef.current) return;

        try {
            const result = await modelerRef.current.saveSVG();
            sendToIde({ type: 'exportSvg', svg: result.svg });
        } catch (err) {
            console.error('Failed to export SVG:', err);
            sendToIde({ type: 'showError', message: 'Failed to export SVG' });
        }
    };

    const exportPng = async () => {
        if (!modelerRef.current) return;

        try {
            const result = await modelerRef.current.saveSVG();

            // Convert SVG to PNG using canvas
            const canvas = document.createElement('canvas');
            const ctx = canvas.getContext('2d');
            const img = new Image();

            img.onload = () => {
                canvas.width = img.width * 2;  // 2x for retina
                canvas.height = img.height * 2;
                ctx?.scale(2, 2);
                ctx?.drawImage(img, 0, 0);

                const dataUrl = canvas.toDataURL('image/png');
                sendToIde({ type: 'exportPng', dataUrl });
            };

            img.src = 'data:image/svg+xml;base64,' + btoa(unescape(encodeURIComponent(result.svg)));
        } catch (err) {
            console.error('Failed to export PNG:', err);
            sendToIde({ type: 'showError', message: 'Failed to export PNG' });
        }
    };

    // Handle drag and drop from palette
    const handleDrop = (e: React.DragEvent) => {
        e.preventDefault();

        const delegateData = e.dataTransfer.getData('application/json');
        if (!delegateData) return;

        try {
            const delegate = JSON.parse(delegateData);
            addServiceTask(delegate, e.clientX, e.clientY);
        } catch (err) {
            console.error('Failed to parse delegate data:', err);
        }
    };

    const addServiceTask = (delegate: any, x: number, y: number) => {
        if (!modelerRef.current || !containerRef.current) return;

        const modeling = modelerRef.current.get('modeling') as any;
        const elementFactory = modelerRef.current.get('elementFactory') as any;
        const canvas = modelerRef.current.get('canvas') as any;

        // Get container bounds for coordinate conversion
        const containerRect = containerRef.current.getBoundingClientRect();

        // Convert screen coordinates to diagram coordinates
        const viewbox = canvas.viewbox();
        const relativeX = x - containerRect.left;
        const relativeY = y - containerRect.top;
        const diagramX = viewbox.x + (relativeX / viewbox.scale);
        const diagramY = viewbox.y + (relativeY / viewbox.scale);

        // Get root element
        const rootElement = canvas.getRootElement();

        // Create service task shape
        const shape = elementFactory.createShape({
            type: 'bpmn:ServiceTask'
        });

        // Add the shape
        modeling.createShape(shape, { x: diagramX, y: diagramY }, rootElement);

        // Update properties
        modeling.updateProperties(shape, {
            name: delegate.displayName,
            'camunda:delegateExpression': '${' + delegate.name + '}'
        });
    };

    const addDelegateToCenter = (delegate: any) => {
        if (!modelerRef.current || !containerRef.current) return;

        const modeling = modelerRef.current.get('modeling') as any;
        const elementFactory = modelerRef.current.get('elementFactory') as any;
        const canvas = modelerRef.current.get('canvas') as any;

        // Get viewbox center
        const viewbox = canvas.viewbox();
        const centerX = viewbox.x + (viewbox.width / 2);
        const centerY = viewbox.y + (viewbox.height / 2);

        // Get root element
        const rootElement = canvas.getRootElement();

        // Create service task shape
        const shape = elementFactory.createShape({
            type: 'bpmn:ServiceTask'
        });

        // Add the shape at center
        modeling.createShape(shape, { x: centerX, y: centerY }, rootElement);

        // Update properties
        modeling.updateProperties(shape, {
            name: delegate.displayName,
            'camunda:delegateExpression': '${' + delegate.name + '}'
        });

        // Select the new shape
        const selection = modelerRef.current.get('selection') as any;
        selection.select(shape);
    };

    const handleDragOver = (e: React.DragEvent) => {
        e.preventDefault();
        e.dataTransfer.dropEffect = 'copy';
    };

    return (
        <div
            ref={containerRef}
            className="bpmn-container"
            onDrop={handleDrop}
            onDragOver={handleDragOver}
        />
    );
};

export default BpmnEditor;
