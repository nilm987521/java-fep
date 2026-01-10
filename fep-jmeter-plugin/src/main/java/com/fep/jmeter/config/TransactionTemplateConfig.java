package com.fep.jmeter.config;

import com.fep.message.iso8583.Iso8583Message;
import org.apache.jmeter.config.ConfigTestElement;
import org.apache.jmeter.testbeans.TestBean;
import org.apache.jmeter.threads.JMeterContext;
import org.apache.jmeter.threads.JMeterContextService;
import org.apache.jmeter.threads.JMeterVariables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JMeter Config Element for managing Transaction Templates.
 *
 * <p>This element allows users to configure custom transaction templates
 * that can be used by samplers to generate ISO 8583 messages.
 *
 * <p>Features:
 * <ul>
 *   <li>Load templates from JSON file or inline JSON</li>
 *   <li>Use predefined common templates</li>
 *   <li>Variable substitution with JMeter variables</li>
 *   <li>Automatic STAN and timestamp generation</li>
 * </ul>
 *
 * <p>Usage in JMeter:
 * <ol>
 *   <li>Add TransactionTemplateConfig as a Config Element</li>
 *   <li>Configure templates via JSON file or inline</li>
 *   <li>Use TemplateSampler to send transactions from templates</li>
 * </ol>
 */
public class TransactionTemplateConfig extends ConfigTestElement implements TestBean {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(TransactionTemplateConfig.class);

    // Property names
    public static final String TEMPLATE_SOURCE = "templateSource";
    public static final String TEMPLATE_FILE = "templateFile";
    public static final String INLINE_TEMPLATES = "inlineTemplates";
    public static final String USE_COMMON_TEMPLATES = "useCommonTemplates";
    public static final String AUTO_GENERATE_STAN = "autoGenerateStan";
    public static final String AUTO_GENERATE_TIMESTAMP = "autoGenerateTimestamp";

    // Template source options
    public static final String SOURCE_FILE = "FILE";
    public static final String SOURCE_INLINE = "INLINE";
    public static final String SOURCE_COMMON = "COMMON";

    // Loaded templates cache
    private static final Map<String, List<TransactionTemplate>> templateCache = new ConcurrentHashMap<>();
    private static final AtomicInteger stanCounter = new AtomicInteger(0);

    public TransactionTemplateConfig() {
        super();
        setName("Transaction Template Config");
    }

    /**
     * Gets the loaded templates for this configuration.
     */
    public List<TransactionTemplate> getTemplates() {
        String cacheKey = getCacheKey();
        return templateCache.computeIfAbsent(cacheKey, k -> loadTemplates());
    }

    /**
     * Gets a specific template by name.
     */
    public Optional<TransactionTemplate> getTemplate(String name) {
        return getTemplates().stream()
            .filter(t -> name.equals(t.getName()))
            .findFirst();
    }

    /**
     * Creates a message from a named template with variable substitution.
     */
    public Iso8583Message createMessage(String templateName) {
        return getTemplate(templateName)
            .map(this::createMessageFromTemplate)
            .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateName));
    }

    /**
     * Creates a message from a template with automatic field generation and variable substitution.
     */
    public Iso8583Message createMessageFromTemplate(TransactionTemplate template) {
        Map<String, String> variables = buildVariables();
        return template.createMessage(variables);
    }

    /**
     * Builds the variable map for substitution.
     */
    private Map<String, String> buildVariables() {
        Map<String, String> variables = new HashMap<>();

        // Auto-generate STAN if enabled
        if (isAutoGenerateStan()) {
            String stan = String.format("%06d", stanCounter.incrementAndGet() % 1000000);
            variables.put("stan", stan);
        }

        // Auto-generate timestamp if enabled
        if (isAutoGenerateTimestamp()) {
            LocalDateTime now = LocalDateTime.now();
            variables.put("time", now.format(DateTimeFormatter.ofPattern("HHmmss")));
            variables.put("date", now.format(DateTimeFormatter.ofPattern("MMdd")));
            variables.put("datetime", now.format(DateTimeFormatter.ofPattern("MMddHHmmss")));
        }

        // Add JMeter variables
        JMeterContext context = JMeterContextService.getContext();
        if (context != null) {
            JMeterVariables jmeterVars = context.getVariables();
            if (jmeterVars != null) {
                for (String name : getJMeterVariableNames(jmeterVars)) {
                    String value = jmeterVars.get(name);
                    if (value != null) {
                        variables.put(name, value);
                    }
                }
            }
        }

        return variables;
    }

    /**
     * Gets JMeter variable names from the variables object.
     */
    private Set<String> getJMeterVariableNames(JMeterVariables vars) {
        Set<String> names = new HashSet<>();
        // Common variable names used in templates
        String[] commonNames = {
            "amount", "bankCode", "terminalId", "cardNumber", "pan",
            "sourceAccount", "destAccount", "billerCode", "billReference",
            "originalRrn", "originalProcessingCode", "originalDataElements"
        };
        for (String name : commonNames) {
            if (vars.get(name) != null) {
                names.add(name);
            }
        }
        return names;
    }

    /**
     * Loads templates based on the configured source.
     */
    private List<TransactionTemplate> loadTemplates() {
        List<TransactionTemplate> templates = new ArrayList<>();

        String source = getTemplateSource();

        try {
            switch (source) {
                case SOURCE_FILE -> {
                    String filePath = getTemplateFile();
                    if (filePath != null && !filePath.isEmpty()) {
                        Path path = Paths.get(filePath);
                        templates.addAll(TransactionTemplate.listFromFile(path));
                        log.info("Loaded {} templates from file: {}", templates.size(), filePath);
                    }
                }
                case SOURCE_INLINE -> {
                    String json = getInlineTemplates();
                    if (json != null && !json.isEmpty()) {
                        // Check if it's a single template or array
                        json = json.trim();
                        if (json.startsWith("[")) {
                            templates.addAll(TransactionTemplate.listFromJson(json));
                        } else {
                            templates.add(TransactionTemplate.fromJson(json));
                        }
                        log.info("Loaded {} inline templates", templates.size());
                    }
                }
                case SOURCE_COMMON -> {
                    templates.addAll(TransactionTemplate.CommonTemplates.all());
                    log.info("Loaded {} common templates", templates.size());
                }
            }

            // Optionally add common templates
            if (isUseCommonTemplates() && !SOURCE_COMMON.equals(source)) {
                // Only add common templates that don't already exist
                Set<String> existingNames = new HashSet<>();
                templates.forEach(t -> existingNames.add(t.getName()));

                for (TransactionTemplate common : TransactionTemplate.CommonTemplates.all()) {
                    if (!existingNames.contains(common.getName())) {
                        templates.add(common);
                    }
                }
            }

        } catch (Exception e) {
            log.error("Error loading transaction templates", e);
        }

        return templates;
    }

    /**
     * Gets cache key for this configuration.
     */
    private String getCacheKey() {
        return getTemplateSource() + "_" + getTemplateFile() + "_" + getInlineTemplates().hashCode();
    }

    /**
     * Clears the template cache.
     */
    public static void clearCache() {
        templateCache.clear();
    }

    /**
     * Gets the list of available template names.
     */
    public List<String> getTemplateNames() {
        return getTemplates().stream()
            .map(TransactionTemplate::getName)
            .toList();
    }

    // Getters and Setters for TestBean properties
    public String getTemplateSource() {
        return getPropertyAsString(TEMPLATE_SOURCE, SOURCE_COMMON);
    }

    public void setTemplateSource(String source) {
        setProperty(TEMPLATE_SOURCE, source);
    }

    public String getTemplateFile() {
        return getPropertyAsString(TEMPLATE_FILE, "");
    }

    public void setTemplateFile(String file) {
        setProperty(TEMPLATE_FILE, file);
    }

    public String getInlineTemplates() {
        return getPropertyAsString(INLINE_TEMPLATES, "");
    }

    public void setInlineTemplates(String templates) {
        setProperty(INLINE_TEMPLATES, templates);
    }

    public boolean isUseCommonTemplates() {
        return getPropertyAsBoolean(USE_COMMON_TEMPLATES, true);
    }

    public void setUseCommonTemplates(boolean use) {
        setProperty(USE_COMMON_TEMPLATES, use);
    }

    public boolean isAutoGenerateStan() {
        return getPropertyAsBoolean(AUTO_GENERATE_STAN, true);
    }

    public void setAutoGenerateStan(boolean autoGenerate) {
        setProperty(AUTO_GENERATE_STAN, autoGenerate);
    }

    public boolean isAutoGenerateTimestamp() {
        return getPropertyAsBoolean(AUTO_GENERATE_TIMESTAMP, true);
    }

    public void setAutoGenerateTimestamp(boolean autoGenerate) {
        setProperty(AUTO_GENERATE_TIMESTAMP, autoGenerate);
    }
}
