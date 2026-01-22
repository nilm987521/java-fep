# FEP BPMN Editor for IntelliJ IDEA

A visual BPMN 2.0 process editor plugin for IntelliJ IDEA, integrated with Camunda Java Delegate discovery.

## Features

### Visual BPMN Editor
- Full BPMN 2.0 editing powered by bpmn-js
- Drag-and-drop interface for creating process elements
- Canvas controls: pan, zoom, fit-to-viewport
- Real-time editing with undo/redo support
- Dark theme integration with IntelliJ

### Java Delegate Scanner
- Automatically discovers `JavaDelegate` implementations in your project
- Scans files matching configurable patterns (default: `**/delegate/**/*.java`, `**/bpmn/**/*.java`)
- Detects annotations: `@Component`, `@Service`, `@Named`
- Extracts input/output variables from `execution.getVariable()` and `execution.setVariable()` calls
- Parses JavaDoc for descriptions

### Delegate Categories
Auto-categorizes delegates based on naming patterns:

| Category | Display Name | Color | Pattern |
|----------|-------------|-------|---------|
| validate | 驗證類 | Green | validate/check/verify |
| account | 帳務類 | Blue | freeze/debit/credit/account |
| message | 電文類 | Orange | assemble/message/build |
| communication | 通訊類 | Purple | send/receive/fisc |
| log | 日誌類 | Brown | log/audit/trace |
| other | 其他 | Gray | (default) |

### Component Palette
- Search/filter delegates by name or description
- Grouped display by category with expand/collapse
- Drag-and-drop delegates onto canvas to create ServiceTasks
- Double-click to navigate to source code

### Properties Panel
- View element properties (ID, type, name)
- Configure delegate expressions for ServiceTasks
- View delegate documentation inline
- Display input/output variables with types
- Quick navigation to delegate source code

### Export Options
- Export as SVG (vector graphics)
- Export as PNG (raster image)

## Installation

### From Source

1. Clone the repository:
```bash
git clone https://github.com/fep/idea-bpmn-editor.git
cd idea-bpmn-editor
```

2. Build the webview:
```bash
cd webview
npm install
npm run build
cd ..
```

3. Build the plugin:
```bash
./gradlew build
```

4. Install the plugin:
   - Open IntelliJ IDEA
   - Go to Settings > Plugins > Install Plugin from Disk
   - Select `build/distributions/idea-bpmn-editor-1.0.0.zip`

## Usage

### Opening BPMN Files
- Double-click any `.bpmn` file in the project view
- The visual editor opens automatically

### Creating New BPMN Files
- Use the menu: BPMN > New BPMN File
- Or press `Ctrl+Alt+B`

### Scanning Delegates
- Delegates are auto-scanned when the project opens (configurable)
- Manual scan: BPMN > Scan Java Delegates
- Or press `Ctrl+Alt+D`

### Configuring Delegates
1. Add a ServiceTask to the diagram
2. Select the task
3. In the Properties panel, choose a delegate from the dropdown
4. The delegate expression is automatically set

### Export
- BPMN > Export as SVG
- BPMN > Export as PNG

## Configuration

Go to Settings > Tools > FEP BPMN Editor to configure:

### Delegate Scanning
- **Scan patterns**: Glob patterns for finding Java delegate files
- **Required annotations**: Annotations that delegates must have
- **Delegate interface**: Interface that delegates must implement
- **Auto-scan**: Automatically scan on project open

### Editor UI
- **Show minimap**: Display minimap in editor
- **Show palette**: Show component palette by default
- **Show properties**: Show properties panel by default
- **Theme**: Editor theme (auto/light/dark)

### Grid & Snapping
- **Enable grid snapping**: Snap elements to grid
- **Grid size**: Grid size in pixels

## Tool Windows

### BPMN Delegates (Right)
- Tree view of discovered delegates organized by category
- Search and filter
- Double-click to open source file

### BPMN Files (Left)
- List of all BPMN files in the project
- Quick access to open files

## Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| `Ctrl+S` | Save |
| `Ctrl+Z` | Undo |
| `Ctrl+Y` | Redo |
| `Ctrl+Alt+B` | New BPMN File |
| `Ctrl+Alt+D` | Scan Delegates |

## Requirements

- IntelliJ IDEA 2023.3 or later
- Java 17 or later
- Java plugin enabled

## Technology Stack

### Plugin (Backend)
- IntelliJ Platform SDK
- JCEF (Chromium Embedded Framework)
- Gson for JSON processing

### WebView (Frontend)
- React 18
- bpmn-js 17
- Zustand for state management
- TypeScript
- Webpack

## Project Structure

```
idea-bpmn-editor/
├── src/main/java/com/fep/bpmn/
│   ├── editor/           # Custom editor implementation
│   ├── scanner/          # Java delegate scanner
│   ├── services/         # Application services
│   ├── settings/         # Plugin settings
│   ├── toolwindow/       # Tool windows
│   └── actions/          # IDE actions
├── src/main/resources/
│   ├── META-INF/         # Plugin descriptor
│   ├── icons/            # Plugin icons
│   └── webview/          # Built webview resources
├── webview/              # React frontend
│   ├── src/
│   │   ├── components/   # React components
│   │   ├── stores/       # Zustand stores
│   │   └── styles/       # CSS styles
│   ├── package.json
│   └── webpack.config.js
├── build.gradle.kts
└── settings.gradle.kts
```

## Development

### Prerequisites
- JDK 17+
- Node.js 18+
- IntelliJ IDEA (for running/debugging)

### Building
```bash
# Build webview
cd webview && npm install && npm run build && cd ..

# Build plugin
./gradlew build
```

### Running
```bash
./gradlew runIde
```

### Debugging
1. Import the project into IntelliJ IDEA
2. Run the `Run Plugin` configuration

## License

MIT License

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Submit a pull request

## Acknowledgements

- [bpmn-js](https://bpmn.io/toolkit/bpmn-js/) - BPMN 2.0 rendering toolkit
- [Camunda](https://camunda.com/) - BPM platform
- [IntelliJ Platform SDK](https://plugins.jetbrains.com/docs/intellij/) - Plugin development framework
