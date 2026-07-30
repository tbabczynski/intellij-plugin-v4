package com.antlr.plugin.configdialogs;

import com.intellij.configurationStore.StoreUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Persists per-grammar ANTLR tool preferences.
 * Primary store is {@code ANTLRv4ToolGrammarProperties.xml}; {@code misc.xml} is read for migration (#568/#400).
 */
@State(name = "ANTLRv4ToolGrammarProperties", storages = {
        @Storage("ANTLRv4ToolGrammarProperties.xml"),
        @Storage(value = "misc.xml", deprecated = true)
})
public class ANTLRv4ToolGrammarPropertiesComponent implements PersistentStateComponent<ANTLRv4ToolGrammarPropertiesStore> {

    private static final Logger LOG = Logger.getInstance(ANTLRv4ToolGrammarPropertiesComponent.class);

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

    /** Force-write the current (deduped) store to {@code ANTLRv4ToolGrammarProperties.xml}. */
    public static void persistNow(@Nullable Project project) {
        ANTLRv4ToolGrammarPropertiesComponent component = getInstance(project);
        if (component == null) {
            return;
        }
        component.mySettings.dedupeAll(project);
        component.rewritePersistedState();
    }

    @NotNull
    @Override
    public ANTLRv4ToolGrammarPropertiesStore getState() {
        // Belt-and-suspenders: never serialize duplicate fileName rows
        mySettings.dedupeAll(project);
        return mySettings;
    }

    @Override
    public void loadState(@NotNull ANTLRv4ToolGrammarPropertiesStore state) {
        mySettings = state;
        // Migrate dirty XML: same fileName → keep last row, then rewrite the file
        if (mySettings.dedupeAll(project)) {
            scheduleRewritePersistedState();
        }
    }

    /**
     * {@link ANTLRv4ToolGrammarPropertiesStore#dedupeAll} only mutates memory; force a project
     * settings flush so {@code .idea/ANTLRv4ToolGrammarProperties.xml} shrinks to the cleaned list.
     */
    private void scheduleRewritePersistedState() {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) {
                return;
            }
            rewritePersistedState();
        }, ModalityState.NON_MODAL);
    }

    private void rewritePersistedState() {
        try {
            // forceSavingAllSettings: load-time mutation is not always marked dirty
            StoreUtil.saveSettings(project, true);
        } catch (Throwable t) {
            LOG.warn("Failed to rewrite ANTLRv4ToolGrammarProperties.xml after dedupe", t);
        }
    }
}
