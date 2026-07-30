package com.antlr.plugin.configdialogs;

import com.antlr.plugin.parsing.CaseChangingStrategy;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.WildcardFileNameMatcher;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.xmlb.annotations.Property;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Stores settings related to code generation per grammar file.
 * <p>
 * One row per canonical {@code fileName} ({@code $PROJECT_DIR$} form when possible).
 * Save updates in place; load/persist dedupe keeps the <em>last</em> row for a key.
 */
public class ANTLRv4ToolGrammarPropertiesStore {

    private static final Logger logger = Logger.getInstance(ANTLRv4ToolGrammarPropertiesStore.class.getName());

    public static final ANTLRv4GrammarProperties DEFAULT_GRAMMAR_PROPERTIES = initDefaultGrammarProperties();

    @Property
    private final List<ANTLRv4GrammarProperties> perGrammarGenerationSettings = new ArrayList<>();

    public void add(ANTLRv4GrammarProperties properties) {
        perGrammarGenerationSettings.add(properties);
    }

    /** Visible for tests / diagnostics. */
    int size() {
        return perGrammarGenerationSettings.size();
    }

    public ANTLRv4GrammarProperties getGrammarProperties(String grammarFile) {
        ANTLRv4GrammarProperties grammarSettings = findSettingsForFile(grammarFile);

        if (grammarSettings == null) {
            ANTLRv4GrammarProperties projectSettings = findSettingsForFile("*");

            if (projectSettings == null) {
                return ANTLRv4ToolGrammarPropertiesStore.DEFAULT_GRAMMAR_PROPERTIES;
            }

            return projectSettings;
        }

        return grammarSettings;
    }

    /**
     * Reuse the existing row for this grammar (collapsed / absolute / expanded aliases),
     * drop sibling duplicates, and migrate {@code fileName} to the canonical key.
     * Miss → append exactly one new row.
     */
    private ANTLRv4GrammarProperties getOrCreateRow(@Nullable Project project, String grammarFile) {
        String canonical = collapseGrammarKey(project, grammarFile);
        ANTLRv4GrammarProperties existing = findRowForGrammar(project, grammarFile);
        if (existing != null) {
            existing.fileName = canonical;
            removeDuplicateRows(project, canonical, existing);
            return existing;
        }

        ANTLRv4GrammarProperties template = getGrammarProperties(canonical);
        ANTLRv4GrammarProperties newProperties = new ANTLRv4GrammarProperties(template);
        newProperties.fileName = canonical;
        add(newProperties);
        return newProperties;
    }

    /**
     * Resolve a stored row for the grammar using collapsed, raw, and expanded path aliases.
     * When several rows share the same canonical key, prefer the <em>last</em> (newest) one.
     */
    @Nullable
    private ANTLRv4GrammarProperties findRowForGrammar(@Nullable Project project, @Nullable String grammarFile) {
        if (grammarFile == null || grammarFile.isEmpty()) {
            return null;
        }
        String collapsed = collapseGrammarKey(project, grammarFile);
        ANTLRv4GrammarProperties match = findLastExactSettings(collapsed);
        if (match != null) {
            return match;
        }
        if (!Objects.equals(normalizeKey(collapsed), normalizeKey(grammarFile))) {
            match = findLastExactSettings(grammarFile);
            if (match != null) {
                return match;
            }
        }
        if (project != null && !project.isDisposed()) {
            String expanded = normalizeKey(PathMacroManager.getInstance(project).expandPath(collapsed));
            if (!Objects.equals(expanded, normalizeKey(collapsed))) {
                match = findLastExactSettings(expanded);
                if (match != null) {
                    return match;
                }
            }
            // Legacy absolute rows that collapse to the same canonical key
            for (int i = perGrammarGenerationSettings.size() - 1; i >= 0; i--) {
                ANTLRv4GrammarProperties settings = perGrammarGenerationSettings.get(i);
                if (settings.fileName == null || settings.fileName.isEmpty()) {
                    continue;
                }
                if ("*".equals(settings.fileName) || "**".equals(settings.fileName)) {
                    continue;
                }
                if (settings.fileName.contains("*") || settings.fileName.contains("?")) {
                    continue;
                }
                if (normalizeKey(collapseGrammarKey(project, settings.fileName)).equals(normalizeKey(collapsed))) {
                    return settings;
                }
            }
        }
        return null;
    }

    /** Last exact {@code fileName} match (normalized {@code /}). */
    @Nullable
    private ANTLRv4GrammarProperties findLastExactSettings(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }
        String needle = normalizeKey(fileName);
        ANTLRv4GrammarProperties last = null;
        for (ANTLRv4GrammarProperties settings : perGrammarGenerationSettings) {
            if (settings.fileName != null && normalizeKey(settings.fileName).equals(needle)) {
                last = settings;
            }
        }
        return last;
    }

    /** First exact {@code fileName} match (normalized {@code /}). Kept for wildcard fallback paths. */
    @Nullable
    private ANTLRv4GrammarProperties findExactSettings(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }
        String needle = normalizeKey(fileName);
        for (ANTLRv4GrammarProperties settings : perGrammarGenerationSettings) {
            if (settings.fileName != null && normalizeKey(settings.fileName).equals(needle)) {
                return settings;
            }
        }
        return null;
    }

    private void removeDuplicateRows(@Nullable Project project,
                                     @NotNull String canonical,
                                     @NotNull ANTLRv4GrammarProperties keep) {
        String canonicalNorm = normalizeKey(canonical);
        perGrammarGenerationSettings.removeIf(settings -> {
            if (settings == keep || settings.fileName == null || settings.fileName.isEmpty()) {
                return false;
            }
            if (normalizeKey(settings.fileName).equals(canonicalNorm)) {
                return true;
            }
            if (project != null && !project.isDisposed()
                    && !"*".equals(settings.fileName) && !"**".equals(settings.fileName)
                    && !settings.fileName.contains("*") && !settings.fileName.contains("?")) {
                return normalizeKey(collapseGrammarKey(project, settings.fileName)).equals(canonicalNorm);
            }
            return false;
        });
    }

    @Nullable
    private ANTLRv4GrammarProperties findSettingsForFile(String fileName) {
        ANTLRv4GrammarProperties exact = findLastExactSettings(fileName);
        if (exact != null) {
            return exact;
        }

        for (ANTLRv4GrammarProperties settings : perGrammarGenerationSettings) {
            if (matchesWildcardPattern(fileName, settings)) {
                return settings;
            }
        }

        return null;
    }

    private boolean matchesWildcardPattern(String fileName, ANTLRv4GrammarProperties settings) {
        if (settings.fileName == null) {
            return false;
        }
        // Exact keys are handled separately; only real wildcards here
        if (!settings.fileName.contains("*") && !settings.fileName.contains("?")) {
            return false;
        }
        try {
            WildcardFileNameMatcher wildcardFileNameMatcher = new WildcardFileNameMatcher(settings.fileName);
            if (wildcardFileNameMatcher.acceptsCharSequence(fileName)
                    || wildcardFileNameMatcher.acceptsCharSequence(normalizeKey(fileName))) {
                return true;
            }
        } catch (Exception e) {
            logger.warn("Unable to check if wildcard matches file name: " + fileName, e);
        }
        return false;
    }

    /**
     * Same logical {@code fileName} → keep the <em>last</em> row (newest save), drop the rest.
     * Optionally collapses keys with {@code project} so absolute and {@code $PROJECT_DIR$} merge.
     *
     * @return {@code true} if the list was modified (caller should rewrite the persisted XML)
     */
    public boolean dedupeAll() {
        return dedupeAll(null);
    }

    public boolean dedupeAll(@Nullable Project project) {
        Map<String, ANTLRv4GrammarProperties> lastByKey = new LinkedHashMap<>();
        for (ANTLRv4GrammarProperties settings : perGrammarGenerationSettings) {
            if (settings.fileName == null || settings.fileName.isEmpty()) {
                continue;
            }
            String key = collapseGrammarKey(project, settings.fileName);
            settings.fileName = key;
            lastByKey.put(key, settings); // last wins
        }
        List<ANTLRv4GrammarProperties> deduped = new ArrayList<>(lastByKey.values());
        if (deduped.equals(perGrammarGenerationSettings)) {
            return false;
        }
        int before = perGrammarGenerationSettings.size();
        perGrammarGenerationSettings.clear();
        perGrammarGenerationSettings.addAll(deduped);
        logger.info("Deduped grammar settings: " + before + " -> " + perGrammarGenerationSettings.size());
        return true;
    }

    public static ANTLRv4GrammarProperties getGrammarProperties(Project project, VirtualFile grammarFile) {
        return getGrammarProperties(project, grammarFile.getPath());
    }

    /**
     * Defaults to settings defined in the project if they exist, or to empty settings.
     * Lookup tries {@code $PROJECT_DIR$} keys, legacy absolute paths, then wildcards (#420).
     */
    public static ANTLRv4GrammarProperties getGrammarProperties(Project project, String grammarFile) {
        ANTLRv4ToolGrammarPropertiesComponent antlRv4ToolGrammarPropertiesComponent =
                ANTLRv4ToolGrammarPropertiesComponent.getInstance(project);
        if (antlRv4ToolGrammarPropertiesComponent == null) {
            return null;
        }
        ANTLRv4ToolGrammarPropertiesStore store = antlRv4ToolGrammarPropertiesComponent.getState();
        ANTLRv4GrammarProperties exact = store.findRowForGrammar(project, grammarFile);
        if (exact != null) {
            return exact;
        }
        String collapsed = collapseGrammarKey(project, grammarFile);
        return store.getGrammarProperties(collapsed != null ? collapsed : grammarFile);
    }

    /**
     * Get the properties for this grammar, or create a new properties object derived from the project settings if
     * they exist, or from the default empty settings otherwise.
     * New entries are keyed with collapsed {@code $PROJECT_DIR$} paths (#420).
     */
    @Nullable
    public static ANTLRv4GrammarProperties getOrCreateGrammarProperties(Project project, String grammarFile) {
        ANTLRv4ToolGrammarPropertiesComponent antlRv4ToolGrammarPropertiesComponent =
                ANTLRv4ToolGrammarPropertiesComponent.getInstance(project);
        if (antlRv4ToolGrammarPropertiesComponent == null) {
            return null;
        }
        ANTLRv4ToolGrammarPropertiesStore store = antlRv4ToolGrammarPropertiesComponent.getState();
        return store.getOrCreateRow(project, grammarFile);
    }

    /** Collapse absolute paths to {@code $PROJECT_DIR$} form for portable storage. */
    @NotNull
    public static String collapseGrammarKey(@Nullable Project project, @Nullable String grammarFile) {
        if (grammarFile == null || grammarFile.isEmpty()
                || "*".equals(grammarFile) || "**".equals(grammarFile)) {
            return grammarFile == null ? "" : grammarFile;
        }
        if (project == null || project.isDisposed()) {
            return normalizeKey(grammarFile);
        }
        return normalizeKey(PathMacroManager.getInstance(project).collapsePath(grammarFile));
    }

    @NotNull
    public static String collapsePath(@Nullable Project project, @Nullable String path) {
        if (path == null || path.isEmpty() || project == null || project.isDisposed()) {
            return path == null ? "" : path;
        }
        return normalizeKey(PathMacroManager.getInstance(project).collapsePath(path));
    }

    /** Normalize separators so Windows {@code \} and {@code /} keys compare equal. */
    @NotNull
    public static String normalizeKey(@Nullable String path) {
        if (path == null || path.isEmpty()) {
            return path == null ? "" : path;
        }
        return path.replace('\\', '/');
    }

    private static ANTLRv4GrammarProperties initDefaultGrammarProperties() {
        ANTLRv4GrammarProperties defaultSettings = new ANTLRv4GrammarProperties();

        defaultSettings.fileName = "**";
        defaultSettings.autoGen = false;
        // Match RunANTLROnGrammarFile.OUTPUT_DIR_NAME; portable and creates under content root
        defaultSettings.outputDir = "gen";
        defaultSettings.libDir = "";
        defaultSettings.encoding = "";
        defaultSettings.pkg = "";
        defaultSettings.language = "Java";
        defaultSettings.generateListener = true;
        defaultSettings.generateVisitor = true;
        defaultSettings.caseChangingStrategy = CaseChangingStrategy.LEAVE_AS_IS;

        return defaultSettings;
    }
}
