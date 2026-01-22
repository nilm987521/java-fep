package com.fep.bpmn.toolwindow;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Factory for creating the BPMN Files Tool Window.
 * This tool window displays all BPMN files in the project.
 */
public class BpmnFilesToolWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        BpmnFilesPanel panel = new BpmnFilesPanel(project);
        Content content = ContentFactory.getInstance().createContent(panel, "", false);
        toolWindow.getContentManager().addContent(content);
    }

    /**
     * Panel showing all BPMN files in the project.
     */
    private static class BpmnFilesPanel extends JPanel {

        private final Project project;
        private final Tree fileTree;
        private final DefaultTreeModel treeModel;
        private final DefaultMutableTreeNode rootNode;

        public BpmnFilesPanel(Project project) {
            super(new BorderLayout());
            this.project = project;

            rootNode = new DefaultMutableTreeNode("BPMN Files");
            treeModel = new DefaultTreeModel(rootNode);
            fileTree = new Tree(treeModel);
            fileTree.setRootVisible(false);
            fileTree.setCellRenderer(new BpmnFileCellRenderer());

            // Double-click to open
            fileTree.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        openSelectedFile();
                    }
                }
            });

            // Create toolbar
            ActionToolbar toolbar = createToolbar();

            add(toolbar.getComponent(), BorderLayout.NORTH);
            add(new JBScrollPane(fileTree), BorderLayout.CENTER);

            // Watch for file changes
            setupFileWatcher();

            // Initial load
            refreshFiles();
        }

        private ActionToolbar createToolbar() {
            DefaultActionGroup group = new DefaultActionGroup();

            group.add(new AnAction("Refresh", "Refresh BPMN files list", AllIcons.Actions.Refresh) {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e) {
                    refreshFiles();
                }
            });

            group.add(new AnAction("New BPMN File", "Create a new BPMN file", AllIcons.General.Add) {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e) {
                    // Delegate to NewBpmnFileAction
                    AnAction action = ActionManager.getInstance().getAction("FepBpmn.NewBpmnFile");
                    if (action != null) {
                        action.actionPerformed(e);
                    }
                }
            });

            ActionToolbar toolbar = ActionManager.getInstance()
                    .createActionToolbar("BpmnFilesToolbar", group, true);
            toolbar.setTargetComponent(this);
            return toolbar;
        }

        private void setupFileWatcher() {
            VirtualFileManager.getInstance().addVirtualFileListener(new VirtualFileListener() {
                @Override
                public void fileCreated(@NotNull VirtualFileEvent event) {
                    if (event.getFileName().endsWith(".bpmn")) {
                        refreshFiles();
                    }
                }

                @Override
                public void fileDeleted(@NotNull VirtualFileEvent event) {
                    if (event.getFileName().endsWith(".bpmn")) {
                        refreshFiles();
                    }
                }

                @Override
                public void fileMoved(@NotNull VirtualFileMoveEvent event) {
                    if (event.getFileName().endsWith(".bpmn")) {
                        refreshFiles();
                    }
                }
            });
        }

        private void refreshFiles() {
            List<VirtualFile> bpmnFiles = new ArrayList<>();

            ProjectFileIndex.getInstance(project).iterateContent(file -> {
                if (!file.isDirectory() && "bpmn".equalsIgnoreCase(file.getExtension())) {
                    bpmnFiles.add(file);
                }
                return true;
            });

            // Sort by path
            bpmnFiles.sort(Comparator.comparing(VirtualFile::getPath));

            // Update tree
            rootNode.removeAllChildren();
            for (VirtualFile file : bpmnFiles) {
                rootNode.add(new DefaultMutableTreeNode(file));
            }
            treeModel.reload();

            // Expand root
            fileTree.expandRow(0);
        }

        private void openSelectedFile() {
            var path = fileTree.getSelectionPath();
            if (path == null) return;

            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            Object userObject = node.getUserObject();

            if (userObject instanceof VirtualFile file) {
                FileEditorManager.getInstance(project).openFile(file, true);
            }
        }

        /**
         * Custom cell renderer for BPMN files.
         */
        private class BpmnFileCellRenderer extends DefaultTreeCellRenderer {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                          boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

                DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
                Object userObject = node.getUserObject();

                if (userObject instanceof VirtualFile file) {
                    setText(file.getName());
                    setIcon(AllIcons.FileTypes.Xml);

                    // Show relative path as tooltip
                    String basePath = project.getBasePath();
                    if (basePath != null && file.getPath().startsWith(basePath)) {
                        setToolTipText(file.getPath().substring(basePath.length() + 1));
                    } else {
                        setToolTipText(file.getPath());
                    }
                }

                return this;
            }
        }
    }
}
