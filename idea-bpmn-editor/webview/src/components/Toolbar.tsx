import React from 'react';
import { useEditorStore } from '../stores/editorStore';
import { sendToIde } from '../App';

const Toolbar: React.FC = () => {
    const {
        fileName,
        isDirty,
        showPalette,
        showProperties,
        isDarkTheme,
        togglePalette,
        toggleProperties,
        toggleTheme
    } = useEditorStore();

    const handleCommand = (command: string) => {
        window.dispatchEvent(new CustomEvent('bpmn-command', { detail: command }));
    };

    const handleSave = () => {
        sendToIde({ type: 'save' });
    };

    const handleRequestDelegates = () => {
        sendToIde({ type: 'requestDelegates' });
    };

    return (
        <div className="toolbar">
            {/* File Operations */}
            <div className="toolbar-group">
                <button
                    className="toolbar-button"
                    onClick={handleSave}
                    title="Save (Ctrl+S)"
                >
                    <svg viewBox="0 0 24 24" fill="currentColor">
                        <path d="M17 3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V7l-4-4zm-5 16c-1.66 0-3-1.34-3-3s1.34-3 3-3 3 1.34 3 3-1.34 3-3 3zm3-10H5V5h10v4z"/>
                    </svg>
                </button>
            </div>

            <div className="toolbar-separator" />

            {/* Edit Operations */}
            <div className="toolbar-group">
                <button
                    className="toolbar-button"
                    onClick={() => handleCommand('undo')}
                    title="Undo (Ctrl+Z)"
                >
                    <svg viewBox="0 0 24 24" fill="currentColor">
                        <path d="M12.5 8c-2.65 0-5.05.99-6.9 2.6L2 7v9h9l-3.62-3.62c1.39-1.16 3.16-1.88 5.12-1.88 3.54 0 6.55 2.31 7.6 5.5l2.37-.78C21.08 11.03 17.15 8 12.5 8z"/>
                    </svg>
                </button>
                <button
                    className="toolbar-button"
                    onClick={() => handleCommand('redo')}
                    title="Redo (Ctrl+Y)"
                >
                    <svg viewBox="0 0 24 24" fill="currentColor">
                        <path d="M18.4 10.6C16.55 8.99 14.15 8 11.5 8c-4.65 0-8.58 3.03-9.96 7.22L3.9 16c1.05-3.19 4.05-5.5 7.6-5.5 1.95 0 3.73.72 5.12 1.88L13 16h9V7l-3.6 3.6z"/>
                    </svg>
                </button>
            </div>

            <div className="toolbar-separator" />

            {/* Zoom Controls */}
            <div className="toolbar-group">
                <button
                    className="toolbar-button"
                    onClick={() => handleCommand('zoomIn')}
                    title="Zoom In"
                >
                    <svg viewBox="0 0 24 24" fill="currentColor">
                        <path d="M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14zm2.5-4h-2v2H9v-2H7V9h2V7h1v2h2v1z"/>
                    </svg>
                </button>
                <button
                    className="toolbar-button"
                    onClick={() => handleCommand('zoomOut')}
                    title="Zoom Out"
                >
                    <svg viewBox="0 0 24 24" fill="currentColor">
                        <path d="M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14zM7 9h5v1H7V9z"/>
                    </svg>
                </button>
                <button
                    className="toolbar-button"
                    onClick={() => handleCommand('fitToScreen')}
                    title="Fit to Screen"
                >
                    <svg viewBox="0 0 24 24" fill="currentColor">
                        <path d="M3 5v4h2V5h4V3H5c-1.1 0-2 .9-2 2zm2 10H3v4c0 1.1.9 2 2 2h4v-2H5v-4zm14 4h-4v2h4c1.1 0 2-.9 2-2v-4h-2v4zm0-16h-4v2h4v4h2V5c0-1.1-.9-2-2-2z"/>
                    </svg>
                </button>
            </div>

            <div className="toolbar-separator" />

            {/* Delegate Operations */}
            <div className="toolbar-group">
                <button
                    className="toolbar-button"
                    onClick={handleRequestDelegates}
                    title="Rescan Delegates"
                >
                    <svg viewBox="0 0 24 24" fill="currentColor">
                        <path d="M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z"/>
                    </svg>
                </button>
            </div>

            <div className="toolbar-separator" />

            {/* Panel Toggles */}
            <div className="toolbar-group">
                <button
                    className={`toolbar-button ${showPalette ? 'active' : ''}`}
                    onClick={togglePalette}
                    title="Toggle Palette"
                >
                    <svg viewBox="0 0 24 24" fill="currentColor">
                        <path d="M3 13h2v-2H3v2zm0 4h2v-2H3v2zm0-8h2V7H3v2zm4 4h14v-2H7v2zm0 4h14v-2H7v2zM7 7v2h14V7H7z"/>
                    </svg>
                </button>
                <button
                    className={`toolbar-button ${showProperties ? 'active' : ''}`}
                    onClick={toggleProperties}
                    title="Toggle Properties"
                >
                    <svg viewBox="0 0 24 24" fill="currentColor">
                        <path d="M3 17v2h6v-2H3zM3 5v2h10V5H3zm10 16v-2h8v-2h-8v-2h-2v6h2zM7 9v2H3v2h4v2h2V9H7zm14 4v-2H11v2h10zm-6-4h2V7h4V5h-4V3h-2v6z"/>
                    </svg>
                </button>
            </div>

            <div className="toolbar-separator" />

            {/* Theme Toggle */}
            <div className="toolbar-group">
                <button
                    className="toolbar-button"
                    onClick={toggleTheme}
                    title={isDarkTheme ? 'Switch to Light Theme' : 'Switch to Dark Theme'}
                >
                    {isDarkTheme ? (
                        <svg viewBox="0 0 24 24" fill="currentColor">
                            <path d="M12 7c-2.76 0-5 2.24-5 5s2.24 5 5 5 5-2.24 5-5-2.24-5-5-5zM2 13h2c.55 0 1-.45 1-1s-.45-1-1-1H2c-.55 0-1 .45-1 1s.45 1 1 1zm18 0h2c.55 0 1-.45 1-1s-.45-1-1-1h-2c-.55 0-1 .45-1 1s.45 1 1 1zM11 2v2c0 .55.45 1 1 1s1-.45 1-1V2c0-.55-.45-1-1-1s-1 .45-1 1zm0 18v2c0 .55.45 1 1 1s1-.45 1-1v-2c0-.55-.45-1-1-1s-1 .45-1 1zM5.99 4.58c-.39-.39-1.03-.39-1.41 0-.39.39-.39 1.03 0 1.41l1.06 1.06c.39.39 1.03.39 1.41 0s.39-1.03 0-1.41L5.99 4.58zm12.37 12.37c-.39-.39-1.03-.39-1.41 0-.39.39-.39 1.03 0 1.41l1.06 1.06c.39.39 1.03.39 1.41 0 .39-.39.39-1.03 0-1.41l-1.06-1.06zm1.06-10.96c.39-.39.39-1.03 0-1.41-.39-.39-1.03-.39-1.41 0l-1.06 1.06c-.39.39-.39 1.03 0 1.41s1.03.39 1.41 0l1.06-1.06zM7.05 18.36c.39-.39.39-1.03 0-1.41-.39-.39-1.03-.39-1.41 0l-1.06 1.06c-.39.39-.39 1.03 0 1.41s1.03.39 1.41 0l1.06-1.06z"/>
                        </svg>
                    ) : (
                        <svg viewBox="0 0 24 24" fill="currentColor">
                            <path d="M9.37 5.51C9.19 6.15 9.1 6.82 9.1 7.5c0 4.08 3.32 7.4 7.4 7.4.68 0 1.35-.09 1.99-.27C17.45 17.19 14.93 19 12 19c-3.86 0-7-3.14-7-7 0-2.93 1.81-5.45 4.37-6.49zM12 3c-4.97 0-9 4.03-9 9s4.03 9 9 9 9-4.03 9-9c0-.46-.04-.92-.1-1.36-.98 1.37-2.58 2.26-4.4 2.26-2.98 0-5.4-2.42-5.4-5.4 0-1.81.89-3.42 2.26-4.4-.44-.06-.9-.1-1.36-.1z"/>
                        </svg>
                    )}
                </button>
            </div>

            {/* File Info */}
            <div className="file-info">
                {isDirty && <span className="dirty-indicator">●</span>}
                {fileName}
            </div>
        </div>
    );
};

export default Toolbar;
