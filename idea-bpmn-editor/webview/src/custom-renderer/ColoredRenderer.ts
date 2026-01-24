import BaseRenderer from 'diagram-js/lib/draw/BaseRenderer';

const HIGH_PRIORITY = 1500;

// Get CSS variable value
const getCSSVariable = (name: string): string => {
    return getComputedStyle(document.body).getPropertyValue(name).trim();
};

export default class ColoredRenderer extends BaseRenderer {
    private bpmnRenderer: any;

    static $inject = ['eventBus', 'bpmnRenderer'];

    constructor(eventBus: any, bpmnRenderer: any) {
        super(eventBus, HIGH_PRIORITY);
        this.bpmnRenderer = bpmnRenderer;
    }

    canRender(element: any): boolean {
        // Only render connections (sequence flows)
        return element.type === 'bpmn:SequenceFlow' ||
               element.type === 'bpmn:MessageFlow' ||
               element.type === 'bpmn:Association';
    }

    drawConnection(parentGfx: SVGElement, element: any): SVGElement {
        // Let the default renderer draw first
        const connection = this.bpmnRenderer.drawConnection(parentGfx, element);

        // Get the stroke color from CSS variable
        const strokeColor = getCSSVariable('--bpmn-connection-stroke') || '#ffffff';

        // Find the path element and update its stroke
        const path = connection.querySelector('path') || connection;
        if (path) {
            path.setAttribute('stroke', strokeColor);
            path.setAttribute('stroke-width', '2');
        }

        return connection;
    }

    drawShape(parentGfx: SVGElement, element: any): SVGElement {
        // Delegate to default renderer for shapes
        return this.bpmnRenderer.drawShape(parentGfx, element);
    }
}
