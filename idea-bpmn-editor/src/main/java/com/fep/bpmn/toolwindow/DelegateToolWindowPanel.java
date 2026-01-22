package com.fep.bpmn.toolwindow;

import com.fep.bpmn.scanner.JavaDelegateScanner;
import com.fep.bpmn.scanner.model.DelegateCategory;
import com.fep.bpmn.scanner.model.DelegateVariable;
import com.fep.bpmn.scanner.model.JavaDelegate;
import com.fep.bpmn.services.DelegateRegistryService;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;

/**
 * Panel for the Delegate Tool Window.
 * Shows a searchable tree of JavaDelegates organized by category.
 */
public class DelegateToolWindowPanel extends JPanel {

    private final Project project;
    private final Tree delegateTree;
    private final DefaultTreeModel treeModel;
    private final DefaultMutableTreeNode rootNode;
    private final SearchTextField searchField;
    private final JBLabel statusLabel;

    public DelegateToolWindowPanel(Project project) {
        super(new BorderLayout());
        this.project = project;

        // Create root node
        rootNode = new DefaultMutableTreeNode("Java Delegates");
        treeModel = new DefaultTreeModel(rootNode);
        delegateTree = new Tree(treeModel);
        delegateTree.setRootVisible(false);
        delegateTree.setShowsRootHandles(true);
        delegateTree.setCellRenderer(new DelegateTreeCellRenderer());

        // Setup double-click to open source
        delegateTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelectedDelegate();
                }
            }
        });

        // Create search field
        searchField = new SearchTextField();
        searchField.addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull javax.swing.event.DocumentEvent e) {
                filterDelegates(searchField.getText());
            }
        });

        // Create status label
        statusLabel = new JBLabel("0 delegates");
        statusLabel.setBorder(JBUI.Borders.empty(2, 5));

        // Create toolbar
        ActionToolbar toolbar = createToolbar();

        // Layout
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(toolbar.getComponent(), BorderLayout.NORTH);
        topPanel.add(searchField, BorderLayout.CENTER);

        add(topPanel, BorderLayout.NORTH);
        add(new JBScrollPane(delegateTree), BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);

        // Subscribe to delegate changes
        ApplicationManager.getApplication().getMessageBus()
                .connect()
                .subscribe(DelegateRegistryService.DELEGATES_CHANGED_TOPIC, this::onDelegatesChanged);

        // Initial load
        loadDelegates();
    }

    private ActionToolbar createToolbar() {
        DefaultActionGroup group = new DefaultActionGroup();

        // Refresh action
        group.add(new AnAction("Refresh", "Rescan Java delegates", AllIcons.Actions.Refresh) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                rescanDelegates();
            }
        });

        // Expand all
        group.add(new AnAction("Expand All", "Expand all categories", AllIcons.Actions.Expandall) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                expandAll();
            }
        });

        // Collapse all
        group.add(new AnAction("Collapse All", "Collapse all categories", AllIcons.Actions.Collapseall) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                collapseAll();
            }
        });

        ActionToolbar toolbar = ActionManager.getInstance()
                .createActionToolbar("DelegateToolbar", group, true);
        toolbar.setTargetComponent(this);
        return toolbar;
    }

    private void loadDelegates() {
        DelegateRegistryService registry = ApplicationManager.getApplication()
                .getService(DelegateRegistryService.class);
        updateTree(registry.getDelegates());
    }

    private void rescanDelegates() {
        JavaDelegateScanner scanner = project.getService(JavaDelegateScanner.class);
        scanner.scanAsync().thenAccept(delegates -> {
            DelegateRegistryService registry = ApplicationManager.getApplication()
                    .getService(DelegateRegistryService.class);
            registry.setDelegates(delegates);
        });
    }

    private void onDelegatesChanged(List<JavaDelegate> delegates) {
        ApplicationManager.getApplication().invokeLater(() -> updateTree(delegates));
    }

    private void updateTree(List<JavaDelegate> delegates) {
        rootNode.removeAllChildren();

        Map<DelegateCategory, List<JavaDelegate>> byCategory =
                ApplicationManager.getApplication()
                        .getService(DelegateRegistryService.class)
                        .getDelegatesByCategory();

        for (DelegateCategory category : DelegateCategory.values()) {
            List<JavaDelegate> categoryDelegates = byCategory.get(category);
            if (categoryDelegates != null && !categoryDelegates.isEmpty()) {
                DefaultMutableTreeNode categoryNode = new DefaultMutableTreeNode(category);
                for (JavaDelegate delegate : categoryDelegates) {
                    categoryNode.add(new DefaultMutableTreeNode(delegate));
                }
                rootNode.add(categoryNode);
            }
        }

        treeModel.reload();
        expandAll();

        statusLabel.setText(delegates.size() + " delegates");
    }

    private void filterDelegates(String query) {
        if (query == null || query.isEmpty()) {
            loadDelegates();
            return;
        }

        DelegateRegistryService registry = ApplicationManager.getApplication()
                .getService(DelegateRegistryService.class);
        List<JavaDelegate> filtered = registry.searchByName(query);

        rootNode.removeAllChildren();

        Map<DelegateCategory, List<JavaDelegate>> byCategory =
                filtered.stream().collect(java.util.stream.Collectors.groupingBy(JavaDelegate::getCategory));

        for (DelegateCategory category : DelegateCategory.values()) {
            List<JavaDelegate> categoryDelegates = byCategory.get(category);
            if (categoryDelegates != null && !categoryDelegates.isEmpty()) {
                DefaultMutableTreeNode categoryNode = new DefaultMutableTreeNode(category);
                for (JavaDelegate delegate : categoryDelegates) {
                    categoryNode.add(new DefaultMutableTreeNode(delegate));
                }
                rootNode.add(categoryNode);
            }
        }

        treeModel.reload();
        expandAll();

        statusLabel.setText(filtered.size() + " delegates (filtered)");
    }

    private void expandAll() {
        for (int i = 0; i < delegateTree.getRowCount(); i++) {
            delegateTree.expandRow(i);
        }
    }

    private void collapseAll() {
        for (int i = delegateTree.getRowCount() - 1; i >= 0; i--) {
            delegateTree.collapseRow(i);
        }
    }

    private void openSelectedDelegate() {
        TreePath path = delegateTree.getSelectionPath();
        if (path == null) return;

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        Object userObject = node.getUserObject();

        if (userObject instanceof JavaDelegate delegate) {
            VirtualFile file = LocalFileSystem.getInstance().findFileByPath(delegate.getFilePath());
            if (file != null) {
                OpenFileDescriptor descriptor = new OpenFileDescriptor(
                        project, file, delegate.getLineNumber() - 1, 0);
                descriptor.navigate(true);
            }
        }
    }

    /**
     * Custom cell renderer for the delegate tree.
     */
    private static class DelegateTreeCellRenderer extends DefaultTreeCellRenderer {

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                      boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            Object userObject = node.getUserObject();

            if (userObject instanceof DelegateCategory category) {
                setText(category.getDisplayName() + " (" + node.getChildCount() + ")");
                setIcon(AllIcons.Nodes.Folder);
                setToolTipText(null);
            } else if (userObject instanceof JavaDelegate delegate) {
                setText(delegate.getName());
                setIcon(AllIcons.Nodes.Class);

                // Build tooltip with delegate info
                StringBuilder tooltip = new StringBuilder("<html>");
                tooltip.append("<b>").append(delegate.getDisplayName()).append("</b><br>");
                tooltip.append("<code>${").append(delegate.getName()).append("}</code><br><br>");

                if (!delegate.getDescription().isEmpty()) {
                    tooltip.append(delegate.getDescription()).append("<br><br>");
                }

                if (!delegate.getInputVariables().isEmpty()) {
                    tooltip.append("<b>Input Variables:</b><ul>");
                    for (DelegateVariable var : delegate.getInputVariables()) {
                        tooltip.append("<li>").append(var.getName())
                                .append(": ").append(var.getType())
                                .append(var.isRequired() ? " (required)" : "")
                                .append("</li>");
                    }
                    tooltip.append("</ul>");
                }

                if (!delegate.getOutputVariables().isEmpty()) {
                    tooltip.append("<b>Output Variables:</b><ul>");
                    for (DelegateVariable var : delegate.getOutputVariables()) {
                        tooltip.append("<li>").append(var.getName())
                                .append(": ").append(var.getType())
                                .append("</li>");
                    }
                    tooltip.append("</ul>");
                }

                tooltip.append("</html>");
                setToolTipText(tooltip.toString());
            }

            return this;
        }
    }
}
