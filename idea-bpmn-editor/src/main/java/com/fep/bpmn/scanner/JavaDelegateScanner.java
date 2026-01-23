package com.fep.bpmn.scanner;

import com.fep.bpmn.scanner.model.DelegateCategory;
import com.fep.bpmn.scanner.model.DelegateVariable;
import com.fep.bpmn.scanner.model.JavaDelegate;
import com.fep.bpmn.settings.BpmnSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for scanning project files to discover JavaDelegate implementations.
 * Uses IntelliJ's PSI (Program Structure Interface) for Java parsing.
 */
@Service(Service.Level.PROJECT)
public final class JavaDelegateScanner {

    private static final Logger LOG = Logger.getInstance(JavaDelegateScanner.class);

    private static final String JAVA_DELEGATE_INTERFACE = "org.camunda.bpm.engine.delegate.JavaDelegate";
    private static final Pattern GET_VARIABLE_PATTERN = Pattern.compile(
            "execution\\.getVariable\\([\"']([^\"']+)[\"']\\)"
    );
    private static final Pattern SET_VARIABLE_PATTERN = Pattern.compile(
            "execution\\.setVariable\\([\"']([^\"']+)[\"']\\s*,\\s*(.+?)\\)"
    );
    private static final Pattern CAST_PATTERN = Pattern.compile("\\(([A-Z][a-zA-Z0-9<>]*)\\)");

    private final Project project;
    private final BpmnSettings settings;

    public JavaDelegateScanner(Project project) {
        this.project = project;
        this.settings = ApplicationManager.getApplication().getService(BpmnSettings.class);
    }

    /**
     * Scan the project asynchronously for JavaDelegate implementations.
     * Waits for indexing to complete before scanning.
     *
     * @return CompletableFuture containing the list of discovered delegates
     */
    public CompletableFuture<List<JavaDelegate>> scanAsync() {
        CompletableFuture<List<JavaDelegate>> future = new CompletableFuture<>();

        // Wait for indexing to complete before scanning
        DumbService.getInstance(project).runWhenSmart(() -> {
            ProgressManager.getInstance().run(new Task.Backgroundable(project, "Scanning Java Delegates", true) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    try {
                        List<JavaDelegate> delegates = scan(indicator);
                        future.complete(delegates);
                    } catch (Exception e) {
                        LOG.error("Error scanning delegates", e);
                        future.completeExceptionally(e);
                    }
                }
            });
        });

        return future;
    }

    /**
     * Scan the project synchronously for JavaDelegate implementations.
     * This method should be called when the index is ready (not in dumb mode).
     *
     * @param indicator progress indicator (can be null)
     * @return list of discovered delegates
     */
    public List<JavaDelegate> scan(ProgressIndicator indicator) {
        List<JavaDelegate> delegates = new ArrayList<>();

        // Check if we're in dumb mode - if so, return empty list
        if (DumbService.isDumb(project)) {
            LOG.info("Project indexing in progress, skipping delegate scan");
            return delegates;
        }

        try {
            ReadAction.run(() -> {
                // Find all Java files in the project
                List<VirtualFile> javaFiles = findJavaFiles(indicator);

                if (indicator != null) {
                    indicator.setText("Analyzing Java files...");
                }

                int total = javaFiles.size();
                int current = 0;

                for (VirtualFile file : javaFiles) {
                    if (indicator != null) {
                        indicator.setFraction((double) current / total);
                        indicator.setText2(file.getName());
                        if (indicator.isCanceled()) {
                            break;
                        }
                    }
                    current++;

                    // Check dumb mode again for each file to handle mode changes during scan
                    if (DumbService.isDumb(project)) {
                        LOG.info("Index became unavailable during scan, stopping");
                        break;
                    }

                    try {
                        JavaDelegate delegate = analyzeFile(file);
                        if (delegate != null) {
                            delegates.add(delegate);
                        }
                    } catch (com.intellij.openapi.project.IndexNotReadyException e) {
                        LOG.warn("Index not ready while analyzing " + file.getName() + ", skipping");
                    }
                }
            });
        } catch (com.intellij.openapi.project.IndexNotReadyException e) {
            LOG.warn("Index not ready during scan, returning partial results");
        }

        LOG.info("Found " + delegates.size() + " Java delegates");
        return delegates;
    }

    private List<VirtualFile> findJavaFiles(ProgressIndicator indicator) {
        List<VirtualFile> files = new ArrayList<>();

        if (indicator != null) {
            indicator.setText("Searching for Java files...");
        }

        ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);

        // Get scan patterns from settings
        List<String> scanPatterns = settings.getScanPatterns();

        fileIndex.iterateContent(file -> {
            if (file.isDirectory()) {
                return true;
            }
            if (!file.getName().endsWith(".java")) {
                return true;
            }

            String path = file.getPath();
            boolean matches = scanPatterns.stream().anyMatch(pattern -> matchesGlob(path, pattern));

            if (matches) {
                files.add(file);
            }
            return true;
        });

        return files;
    }

    private boolean matchesGlob(String path, String pattern) {
        // Simple glob matching for common patterns
        String regex = pattern
                .replace(".", "\\.")
                .replace("**", ".*")
                .replace("*", "[^/]*");
        return path.matches(".*" + regex);
    }

    private JavaDelegate analyzeFile(VirtualFile file) {
        PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
        if (!(psiFile instanceof PsiJavaFile javaFile)) {
            return null;
        }

        for (PsiClass psiClass : javaFile.getClasses()) {
            if (isJavaDelegate(psiClass)) {
                return createJavaDelegate(psiClass, file);
            }
        }

        return null;
    }

    private boolean isJavaDelegate(PsiClass psiClass) {
        if (psiClass.isInterface() || psiClass.isAnnotationType() || psiClass.isEnum()) {
            return false;
        }

        // Check if the class has the required annotations
        if (!hasRequiredAnnotation(psiClass)) {
            return false;
        }

        // Check if the class implements JavaDelegate
        return implementsJavaDelegate(psiClass);
    }

    private boolean hasRequiredAnnotation(PsiClass psiClass) {
        List<String> annotations = settings.getDelegateAnnotations();
        for (PsiAnnotation annotation : psiClass.getAnnotations()) {
            String qualifiedName = annotation.getQualifiedName();
            if (qualifiedName != null) {
                for (String required : annotations) {
                    // Match by simple name or fully qualified name
                    if (qualifiedName.endsWith(required.replace("@", ""))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean implementsJavaDelegate(PsiClass psiClass) {
        // Check direct implementation
        for (PsiClassType type : psiClass.getImplementsListTypes()) {
            PsiClass resolved = type.resolve();
            if (resolved != null && JAVA_DELEGATE_INTERFACE.equals(resolved.getQualifiedName())) {
                return true;
            }
        }

        // Check superclass hierarchy
        PsiClass superClass = psiClass.getSuperClass();
        while (superClass != null) {
            for (PsiClassType type : superClass.getImplementsListTypes()) {
                PsiClass resolved = type.resolve();
                if (resolved != null && JAVA_DELEGATE_INTERFACE.equals(resolved.getQualifiedName())) {
                    return true;
                }
            }
            superClass = superClass.getSuperClass();
        }

        return false;
    }

    private JavaDelegate createJavaDelegate(PsiClass psiClass, VirtualFile file) {
        String className = psiClass.getName();
        String packageName = ((PsiJavaFile) psiClass.getContainingFile()).getPackageName();

        JavaDelegate.Builder builder = JavaDelegate.builder()
                .className(className)
                .packageName(packageName)
                .filePath(file.getPath())
                .lineNumber(getLineNumber(psiClass))
                .lastModified(file.getModificationStamp());

        // Extract bean name from annotation if available
        String beanName = extractBeanName(psiClass);
        if (beanName != null) {
            builder.name(beanName);
        }

        // Extract JavaDoc description
        PsiDocComment docComment = psiClass.getDocComment();
        if (docComment != null) {
            String description = extractDescription(docComment);
            builder.description(description);

            // Try to extract display name from @DisplayName tag or first line
            String displayName = extractDisplayName(docComment);
            if (displayName != null) {
                builder.displayName(displayName);
            }
        }

        // Find the execute method and extract variables
        PsiMethod executeMethod = findExecuteMethod(psiClass);
        if (executeMethod != null) {
            extractVariables(executeMethod, builder);
        }

        // Auto-detect category
        builder.category(DelegateCategory.detect(className));

        return builder.build();
    }

    private int getLineNumber(PsiElement element) {
        PsiFile file = element.getContainingFile();
        if (file == null) return 1;
        int offset = element.getTextOffset();
        return file.getViewProvider().getDocument().getLineNumber(offset) + 1;
    }

    private String extractBeanName(PsiClass psiClass) {
        // Try @Component("name") or @Service("name") or @Named("name")
        for (PsiAnnotation annotation : psiClass.getAnnotations()) {
            PsiAnnotationMemberValue value = annotation.findAttributeValue("value");
            if (value != null) {
                String text = value.getText();
                if (text.startsWith("\"") && text.endsWith("\"")) {
                    return text.substring(1, text.length() - 1);
                }
            }
        }
        return null;
    }

    private String extractDescription(PsiDocComment docComment) {
        StringBuilder description = new StringBuilder();
        for (PsiElement element : docComment.getDescriptionElements()) {
            String text = element.getText().trim();
            if (!text.isEmpty()) {
                if (description.length() > 0) {
                    description.append(" ");
                }
                description.append(text);
            }
        }
        return description.toString().trim();
    }

    private String extractDisplayName(PsiDocComment docComment) {
        // Look for @DisplayName or @name tag
        for (var tag : docComment.getTags()) {
            if ("DisplayName".equals(tag.getName()) || "name".equals(tag.getName())) {
                PsiElement value = tag.getDataElements().length > 0 ? tag.getDataElements()[0] : null;
                if (value != null) {
                    return value.getText().trim();
                }
            }
        }
        // Use first sentence of description
        String desc = extractDescription(docComment);
        int dotIndex = desc.indexOf('.');
        if (dotIndex > 0 && dotIndex < 50) {
            return desc.substring(0, dotIndex).trim();
        }
        return null;
    }

    private PsiMethod findExecuteMethod(PsiClass psiClass) {
        for (PsiMethod method : psiClass.getMethods()) {
            if ("execute".equals(method.getName())) {
                PsiParameter[] params = method.getParameterList().getParameters();
                if (params.length == 1) {
                    PsiType paramType = params[0].getType();
                    if (paramType.getCanonicalText().contains("DelegateExecution")) {
                        return method;
                    }
                }
            }
        }
        return null;
    }

    private void extractVariables(PsiMethod method, JavaDelegate.Builder builder) {
        String methodText = method.getText();

        // Extract input variables (getVariable)
        Matcher getMatcher = GET_VARIABLE_PATTERN.matcher(methodText);
        while (getMatcher.find()) {
            String varName = getMatcher.group(1);
            String varType = inferVariableType(methodText, varName, true);
            builder.addInputVariable(new DelegateVariable(varName, varType, true));
        }

        // Extract output variables (setVariable)
        Matcher setMatcher = SET_VARIABLE_PATTERN.matcher(methodText);
        while (setMatcher.find()) {
            String varName = setMatcher.group(1);
            String valueExpr = setMatcher.group(2);
            String varType = inferVariableType(methodText, varName, false);
            if (varType.equals("Object")) {
                varType = inferTypeFromValue(valueExpr);
            }
            builder.addOutputVariable(new DelegateVariable(varName, varType));
        }
    }

    private String inferVariableType(String methodText, String varName, boolean isInput) {
        if (isInput) {
            // Look for casting pattern: (Type) execution.getVariable("varName")
            String castPatternStr = String.format("\\(([A-Z][a-zA-Z0-9<>]*)\\)\\s*execution\\.getVariable\\([\"']%s[\"']\\)",
                    Pattern.quote(varName));
            Pattern castPattern = Pattern.compile(castPatternStr);
            Matcher matcher = castPattern.matcher(methodText);
            if (matcher.find() && matcher.groupCount() >= 1) {
                return matcher.group(1);
            }

            // Look for variable declaration: Type var = ... getVariable("varName")
            String declPatternStr = String.format("([A-Z][a-zA-Z0-9<>]*)\\s+\\w+\\s*=.*getVariable\\([\"']%s[\"']\\)",
                    Pattern.quote(varName));
            Pattern declPattern = Pattern.compile(declPatternStr);
            matcher = declPattern.matcher(methodText);
            if (matcher.find() && matcher.groupCount() >= 1) {
                return matcher.group(1);
            }
        }
        // For output variables, we rely on inferTypeFromValue instead
        return "Object";
    }

    private String inferTypeFromValue(String valueExpr) {
        valueExpr = valueExpr.trim();

        if (valueExpr.startsWith("\"")) return "String";
        if (valueExpr.matches("-?\\d+L?")) return "Long";
        if (valueExpr.matches("-?\\d+\\.\\d+[fF]?")) return "Double";
        if (valueExpr.equals("true") || valueExpr.equals("false")) return "Boolean";
        if (valueExpr.startsWith("new ")) {
            Matcher m = Pattern.compile("new\\s+([A-Z][a-zA-Z0-9<>]*)").matcher(valueExpr);
            if (m.find()) return m.group(1);
        }

        return "Object";
    }
}
