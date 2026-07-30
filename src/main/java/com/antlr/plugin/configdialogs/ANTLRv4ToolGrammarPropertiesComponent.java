package com.antlr.plugin.configdialogs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

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
            forceFlushProjectSettings(project);
        } catch (Throwable t) {
            LOG.warn("Failed to rewrite ANTLRv4ToolGrammarProperties.xml after dedupe", t);
        }
    }

    /**
     * Flush project settings to disk.
     * <p>
     * Do <em>not</em> call {@code StoreUtil.saveSettings(ComponentManager, boolean)} at compile time:
     * that overload was removed in IntelliJ 2025.3 (IU-253) and Plugin Verifier reports
     * binary incompatibility ({@code NoSuchMethodError} risk). Use reflection + public fallbacks.
     */
    private static void forceFlushProjectSettings(@NotNull Project project) {
        if (invokeStoreUtilSaveSettings(project)) {
            return;
        }
        // Public APIs present across supported ranges; may not force "post-loadState" dirty on all builds
        project.save();
        ApplicationManager.getApplication().saveSettings();
    }

    /**
     * @return {@code true} if a StoreUtil-style force-save was invoked successfully
     */
    private static boolean invokeStoreUtilSaveSettings(@NotNull Project project) {
        String[] classNames = {
                "com.intellij.configurationStore.StoreUtil",
                "com.intellij.configurationStore.StoreUtilKt"
        };
        for (String className : classNames) {
            try {
                Class<?> clazz = Class.forName(className);
                if (invokeSaveSettingsMethod(clazz, project)) {
                    return true;
                }
            } catch (ClassNotFoundException ignored) {
                // try next
            } catch (Throwable t) {
                LOG.warn("Reflective " + className + ".saveSettings failed", t);
            }
        }
        return false;
    }

    private static boolean invokeSaveSettingsMethod(@NotNull Class<?> clazz, @NotNull Project project)
            throws ReflectiveOperationException {
        // Historical Java signature (removed in 2025.3+)
        try {
            Method m = clazz.getMethod("saveSettings", ComponentManager.class, boolean.class);
            if (Modifier.isStatic(m.getModifiers())) {
                m.invoke(null, project, Boolean.TRUE);
                return true;
            }
        } catch (NoSuchMethodException ignored) {
            // fall through
        }

        // Newer / simplified overloads
        try {
            Method m = clazz.getMethod("saveSettings", ComponentManager.class);
            if (Modifier.isStatic(m.getModifiers())) {
                m.invoke(null, project);
                return true;
            }
        } catch (NoSuchMethodException ignored) {
            // fall through
        }

        // Kotlin named-arg / default-param variants: pick a non-suspending static saveSettings
        for (Method m : clazz.getMethods()) {
            if (!"saveSettings".equals(m.getName()) || !Modifier.isStatic(m.getModifiers())) {
                continue;
            }
            Class<?>[] pts = m.getParameterTypes();
            if (pts.length == 0 || !ComponentManager.class.isAssignableFrom(pts[0])) {
                continue;
            }
            // Skip coroutines
            if (pts[pts.length - 1].getName().contains("Continuation")) {
                continue;
            }
            Object[] args = new Object[pts.length];
            args[0] = project;
            boolean ok = true;
            for (int i = 1; i < pts.length; i++) {
                Class<?> pt = pts[i];
                if (pt == boolean.class || pt == Boolean.class) {
                    args[i] = Boolean.TRUE;
                } else if (pt.isPrimitive()) {
                    ok = false;
                    break;
                } else {
                    args[i] = null;
                }
            }
            if (!ok) {
                continue;
            }
            m.invoke(null, args);
            return true;
        }
        return false;
    }
}
