# FEP BPMN Editor

Visual BPMN editor for VSCode with Java Delegate integration, designed for FEP (Front-End Processor) workflow design.

## Features

### Visual BPMN Editing
- Full-featured BPMN 2.0 diagram editor powered by [bpmn.js](https://bpmn.io)
- Drag-and-drop interface for creating workflow diagrams
- Support for all standard BPMN elements
- Camunda extensions support (delegateExpression, asyncBefore, etc.)

### Java Delegate Integration
- **Auto-scan**: Automatically discovers Java classes implementing `JavaDelegate`
- **Component Palette**: Displays all delegates grouped by category
- **Variable Detection**: Extracts input/output variables from source code
- **Quick Navigation**: Double-click to open delegate source file

### Smart Properties Panel
- Real-time property editing
- Delegate selector dropdown
- Variable documentation display
- Direct link to source files

## Installation

### From Source
```bash
cd vscode-bpmn-editor
npm install
npm run compile
```

Then press F5 in VSCode to launch the extension in development mode.

### Package for Distribution
```bash
npm install -g vsce
vsce package
```

## Usage

### Opening BPMN Files
1. Open any `.bpmn` file - the editor will activate automatically
2. Or use command palette: `FEP BPMN: Open BPMN Editor`

### Scanning Java Delegates
1. Run command: `FEP BPMN: Scan Java Delegates`
2. Delegates will appear in the left panel grouped by category
3. Click on a delegate to add it to the diagram
4. Double-click to open the source file

### Adding Delegates to Diagram
1. **Click**: Click a delegate in the palette to add at viewport center
2. **Drag**: Drag a delegate from palette to specific position
3. **Properties**: Select a Service Task and choose delegate from dropdown

### Keyboard Shortcuts
| Shortcut | Action |
|----------|--------|
| Ctrl+S | Save |
| Ctrl+Z | Undo |
| Ctrl+Y | Redo |
| Delete | Remove selected element |

## Configuration

```json
{
  // Patterns for scanning Java delegates
  "fep-bpmn.scanPaths": [
    "**/delegate/**/*.java",
    "**/bpmn/**/*.java"
  ],

  // Annotations that mark delegate classes
  "fep-bpmn.delegateAnnotations": [
    "@Component",
    "@Service",
    "@Named"
  ],

  // Auto-scan on startup
  "fep-bpmn.autoScan": true,

  // Category definitions
  "fep-bpmn.categories": {
    "Validate": {
      "name": "驗證類",
      "color": "#4CAF50",
      "icon": "check-circle"
    },
    "Account": {
      "name": "帳務類",
      "color": "#2196F3",
      "icon": "credit-card"
    }
  }
}
```

## Project Structure

```
vscode-bpmn-editor/
├── src/
│   ├── extension/           # Extension host (Node.js)
│   │   ├── extension.ts     # Entry point
│   │   ├── scanner/         # Java delegate scanner
│   │   ├── providers/       # Editor & tree providers
│   │   ├── commands/        # Command handlers
│   │   └── webview/         # Webview manager
│   │
│   └── webview/             # Webview UI (React)
│       ├── components/      # React components
│       ├── stores/          # State management
│       └── bpmn/            # bpmn.js integration
│
├── resources/               # Static resources
├── syntaxes/               # Syntax highlighting
└── dist/                   # Compiled output
```

## Supported Delegate Annotations

The scanner recognizes classes with these annotations:
- `@Component("delegateName")`
- `@Service("delegateName")`
- `@Named("delegateName")`

And implementing `JavaDelegate` interface.

### Example Delegate

```java
@Component("checkLimitDelegate")
public class CheckLimitDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        // Input variables
        String sourceAccount = (String) execution.getVariable("sourceAccount");
        Long amount = (Long) execution.getVariable("amount");

        // Business logic...

        // Output variables
        execution.setVariable("limitCheck", "OK");
        execution.setVariable("limitMessage", "Limit check passed");
    }
}
```

## Category Auto-Detection

Delegates are automatically categorized based on name patterns:

| Pattern | Category |
|---------|----------|
| validate, check, verify | 驗證類 |
| freeze, debit, credit, account | 帳務類 |
| assemble, message, build | 電文類 |
| send, receive, fisc | 通訊類 |
| (other) | 其他 |

## Development

### Prerequisites
- Node.js 18+
- npm 9+
- VSCode 1.85+

### Build
```bash
npm run compile    # Build once
npm run watch      # Watch mode
```

### Test
```bash
npm run test
```

### Debug
1. Open project in VSCode
2. Press F5 to launch Extension Development Host
3. Open a .bpmn file to test

## License

MIT

## Credits

- [bpmn.js](https://bpmn.io) - BPMN 2.0 rendering toolkit
- [Camunda](https://camunda.com) - BPM platform
- [Zustand](https://zustand-demo.pmnd.rs/) - State management
