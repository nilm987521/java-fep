import React, { useEffect } from 'react';
import BpmnEditor from './components/BpmnEditor';
import Toolbar from './components/Toolbar';
import ComponentPalette from './components/ComponentPalette';
import PropertiesPanel from './components/PropertiesPanel';
import { useEditorStore } from './stores/editorStore';
import { JavaDelegate } from './types';

// Detect and apply theme
const applyTheme = (isDark: boolean) => {
    if (isDark) {
        document.body.classList.remove('light-theme');
    } else {
        document.body.classList.add('light-theme');
    }
};

const App: React.FC = () => {
    const { showPalette, showProperties, setDelegates, setBpmnXml } = useEditorStore();

    useEffect(() => {
        // Setup global functions for IDE communication
        window.loadBpmn = (xml: string) => {
            setBpmnXml(xml);
        };

        window.setDelegates = (delegates: JavaDelegate[]) => {
            setDelegates(delegates);
        };

        window.executeCommand = (command: string) => {
            // Dispatch command to the BpmnEditor component
            const event = new CustomEvent('bpmn-command', { detail: command });
            window.dispatchEvent(event);
        };

        // Theme detection from IDE
        window.setTheme = (theme: string) => {
            applyTheme(theme === 'dark');
        };

        // Initial theme detection - check if IDE provided a theme
        // If not, use system preference
        const prefersDark = window.matchMedia('(prefers-color-scheme: dark)');
        applyTheme(prefersDark.matches);

        // Listen for system theme changes
        const handleThemeChange = (e: MediaQueryListEvent) => {
            applyTheme(e.matches);
        };
        prefersDark.addEventListener('change', handleThemeChange);

        // Notify IDE that webview is ready
        sendToIde({ type: 'ready' });

        return () => {
            window.loadBpmn = undefined;
            window.setDelegates = undefined;
            window.executeCommand = undefined;
            window.setTheme = undefined;
            prefersDark.removeEventListener('change', handleThemeChange);
        };
    }, [setBpmnXml, setDelegates]);

    return (
        <div className="app-container">
            <Toolbar />
            <div className="main-content">
                {showPalette && <ComponentPalette />}
                <div className="editor-canvas">
                    <BpmnEditor />
                </div>
                {showProperties && <PropertiesPanel />}
            </div>
        </div>
    );
};

// Helper function to send messages to the IDE
export const sendToIde = (message: object) => {
    if (window.ideCallback) {
        window.ideCallback(JSON.stringify(message));
    } else {
        console.log('IDE callback not available:', message);
    }
};

export default App;
