package com.antlr.plugin.actions;

import com.antlr.plugin.configdialogs.ANTLRv4GrammarProperties;
import com.antlr.plugin.configdialogs.ANTLRv4ToolGrammarPropertiesStore;
import com.antlr.plugin.configdialogs.ConfigANTLRPerGrammar;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import org.antlr.v4.Tool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.antlr.plugin.configdialogs.ANTLRv4ToolGrammarPropertiesStore.getGrammarProperties;

public class ConfigureANTLRAction extends AnAction implements DumbAware {
    public static final Logger LOG = Logger.getInstance("ConfigureANTLRAction");

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(AnActionEvent e) {
        MyActionUtils.selectedFileIsGrammar(e);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        if (e.getProject() == null) {
            LOG.error("actionPerformed no project for " + e);
            return; // whoa!
        }
        Project project = e.getProject();
        VirtualFile grammarFile = MyActionUtils.getGrammarFileFromEvent(e);
        if (grammarFile == null) return;
        LOG.info("actionPerformed " + grammarFile);

        String grammarKey = ANTLRv4ToolGrammarPropertiesStore.collapseGrammarKey(project, grammarFile.getPath());
        VirtualFile companion = findCompanionGrammar(grammarFile);
        String loadKey = resolveLoadKey(project, grammarFile, grammarKey, companion);

        ConfigANTLRPerGrammar configDialog = ConfigANTLRPerGrammar.getDialogForm(project, loadKey);
        String titleSuffix = companion != null
                ? grammarFile.getName() + " (+ " + companion.getName() + ")"
                : grammarFile.getName();
        configDialog.getPeer().setTitle("Configure ANTLR " + Tool.VERSION + " for " + titleSuffix);

        configDialog.show();

        if (configDialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
            configDialog.saveValues(project, grammarKey);
            // #548: keep separated parser/lexer generation settings in sync
            if (companion != null) {
                String companionKey = ANTLRv4ToolGrammarPropertiesStore.collapseGrammarKey(
                        project, companion.getPath());
                configDialog.saveValues(project, companionKey);
            }
        }
    }

    /**
     * If this file has no dedicated settings but its parser/lexer companion does, load those.
     */
    private static String resolveLoadKey(Project project,
                                         VirtualFile grammarFile,
                                         String grammarKey,
                                         @Nullable VirtualFile companion) {
        ANTLRv4GrammarProperties props = getGrammarProperties(project, grammarKey);
        if (props != null && isExactMatch(props.fileName, grammarKey, grammarFile.getPath())) {
            return grammarKey;
        }
        if (companion != null) {
            String companionKey = ANTLRv4ToolGrammarPropertiesStore.collapseGrammarKey(
                    project, companion.getPath());
            ANTLRv4GrammarProperties cProps = getGrammarProperties(project, companionKey);
            if (cProps != null && isExactMatch(cProps.fileName, companionKey, companion.getPath())) {
                return companionKey;
            }
        }
        return grammarKey;
    }

    private static boolean isExactMatch(String storedName, String collapsedKey, String absolutePath) {
        if (storedName == null) {
            return false;
        }
        String stored = ANTLRv4ToolGrammarPropertiesStore.normalizeKey(storedName);
        return stored.equals(ANTLRv4ToolGrammarPropertiesStore.normalizeKey(collapsedKey))
                || stored.equals(ANTLRv4ToolGrammarPropertiesStore.normalizeKey(absolutePath));
    }

    /** {@code XParser.g4} ↔ {@code XLexer.g4} beside each other. */
    @Nullable
    static VirtualFile findCompanionGrammar(@NotNull VirtualFile grammarFile) {
        VirtualFile parent = grammarFile.getParent();
        if (parent == null) {
            return null;
        }
        String name = grammarFile.getName();
        String companionName = null;
        if (name.endsWith("Parser.g4")) {
            companionName = name.substring(0, name.length() - "Parser.g4".length()) + "Lexer.g4";
        } else if (name.endsWith("Lexer.g4")) {
            companionName = name.substring(0, name.length() - "Lexer.g4".length()) + "Parser.g4";
        }
        if (companionName == null) {
            return null;
        }
        VirtualFile companion = parent.findChild(companionName);
        return companion != null && companion.exists() ? companion : null;
    }
}
