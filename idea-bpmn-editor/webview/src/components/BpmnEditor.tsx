import React, { useEffect, useRef, useCallback } from 'react';
import BpmnModeler from 'bpmn-js/lib/Modeler';
import camundaModdleDescriptor from 'camunda-bpmn-moddle/resources/camunda.json';
import { useEditorStore } from '../stores/editorStore';
import { sendToIde } from '../App';
import { SelectedElement } from '../types';

// Import bpmn-js styles
import 'bpmn-js/dist/assets/diagram-js.css';
import 'bpmn-js/dist/assets/bpmn-js.css';
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css';

// Theme colors configuration
const THEME_COLORS = {
    dark: {
        defaultFillColor: '#2d2d2d',
        defaultStrokeColor: '#d0d0d0',
        defaultLabelColor: '#e0e0e0'
    },
    light: {
        defaultFillColor: '#ffffff',
        defaultStrokeColor: '#333333',
        defaultLabelColor: '#333333'
    }
};

const BpmnEditor: React.FC = () => {
    const containerRef = useRef<HTMLDivElement>(null);
    const modelerRef = useRef<BpmnModeler | null>(null);
    const currentXmlRef = useRef<string>('');
    const viewboxRef = useRef<any>(null);
    const { bpmnXml, isDarkTheme, setDirty, setSelectedElement } = useEditorStore();

    // Create modeler with theme-specific colors
    const createModeler = useCallback((theme: 'dark' | 'light') => {
        if (!containerRef.current) return null;

        const colors = THEME_COLORS[theme];

        const modeler = new BpmnModeler({
            container: containerRef.current,
            moddleExtensions: {
                camunda: camundaModdleDescriptor
            },
            keyboard: {
                bindTo: window
            },
            // Apply theme colors via bpmnRenderer options
            bpmnRenderer: {
                defaultFillColor: colors.defaultFillColor,
                defaultStrokeColor: colors.defaultStrokeColor,
                defaultLabelColor: colors.defaultLabelColor
            }
        });

        return modeler;
    }, []);

    // Setup event listeners for a modeler instance
    const setupEventListeners = useCallback((modeler: BpmnModeler) => {
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
    }, [setDirty, setSelectedElement]);

    // Initialize modeler
    useEffect(() => {
        if (!containerRef.current) return;

        const theme = isDarkTheme ? 'dark' : 'light';
        const modeler = createModeler(theme);
        if (!modeler) return;

        modelerRef.current = modeler;
        setupEventListeners(modeler);

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
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []); // Only run once on mount

    // Load BPMN XML when it changes
    useEffect(() => {
        if (!modelerRef.current || !bpmnXml) return;

        currentXmlRef.current = bpmnXml;
        modelerRef.current.importXML(bpmnXml).catch((err: Error) => {
            console.error('Failed to import BPMN:', err);
            sendToIde({ type: 'showError', message: 'Failed to load BPMN: ' + err.message });
        });
    }, [bpmnXml]);

    // Recreate modeler when theme changes to apply new colors
    useEffect(() => {
        if (!containerRef.current || !modelerRef.current) return;

        const theme = isDarkTheme ? 'dark' : 'light';

        // Save current viewbox position
        try {
            const canvas = modelerRef.current.get('canvas') as any;
            viewboxRef.current = canvas.viewbox();
        } catch (e) {
            // Ignore if canvas not ready
        }

        // Save current XML
        modelerRef.current.saveXML({ format: true }).then((result) => {
            currentXmlRef.current = result.xml || '';

            // Destroy old modeler
            modelerRef.current?.destroy();

            // Create new modeler with updated theme colors
            const newModeler = createModeler(theme);
            if (!newModeler) return;

            modelerRef.current = newModeler;
            setupEventListeners(newModeler);

            // Re-import the diagram
            if (currentXmlRef.current) {
                newModeler.importXML(currentXmlRef.current).then(() => {
                    // Restore viewbox position
                    if (viewboxRef.current) {
                        try {
                            const canvas = newModeler.get('canvas') as any;
                            canvas.viewbox(viewboxRef.current);
                        } catch (e) {
                            // Ignore if viewbox restore fails
                        }
                    }
                }).catch((err: Error) => {
                    console.error('Failed to re-import after theme change:', err);
                });
            }
        }).catch((err) => {
            console.error('Failed to save XML before theme change:', err);
        });
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [isDarkTheme]); // Re-create modeler when theme changes

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
