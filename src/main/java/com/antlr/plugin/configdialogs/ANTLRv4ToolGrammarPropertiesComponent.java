package com.antlr.plugin.configdialogs;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Persists per-grammar ANTLR tool preferences.
 * Primary store is {@code ANTLRv4ToolGrammarProperties.xml}; {@code misc.xml} is read for migration (#568/#400).
 * <p>
 * Persistence follows the normal {@link PersistentStateComponent} lifecycle: mutate in memory,
 * let the platform call {@link #getState()} on IDE save (no forced {@code StoreUtil} flush).
 */
@State(name = "ANTLRv4ToolGrammarProperties", storages = {
        @Storage("ANTLRv4ToolGrammarProperties.xml"),
        @Storage(value = "misc.xml", deprecated = true)
})
public class ANTLRv4ToolGrammarPropertiesComponent implements PersistentStateComponent<ANTLRv4ToolGrammarPropertiesStore> {

    private final Project project;
    private ANTLRv4ToolGrammarPropertiesStore mySettings = new ANTLRv4ToolGrammarPropertiesStore();

    public ANTLRv4ToolGrammarPropertiesComponent(@NotNull Project project) {
        this.project = project;
    }

    @Nullable
    public static ANTLRv4ToolGrammarPropertiesComponent getInstance(Project project) {
        if (project == null || project.isDisposed()) {
            return null;
        }
        try {
            return project.getService(ANTLRv4ToolGrammarPropertiesComponent.class);
        } catch (Throwable t) {
            // #137: project can dispose between isDisposed() and getService()
            return null;
        }
    }

    @NotNull
    @Override
    public ANTLRv4ToolGrammarPropertiesStore getState() {
        // Never serialize duplicate fileName rows
        mySettings.dedupeAll(project);
        return mySettings;
    }

    @Override
    public void loadState(@NotNull ANTLRv4ToolGrammarPropertiesStore state) {
        mySettings = state;
        // Clean duplicates in memory; disk is rewritten on the next normal IDE settings save
        mySettings.dedupeAll(project);
    }
}
