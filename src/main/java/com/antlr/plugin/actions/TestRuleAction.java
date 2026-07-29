package com.antlr.plugin.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.antlr.plugin.ANTLRv4PluginController;
import com.antlr.plugin.preview.PreviewState;
import com.antlr.plugin.psi.ParserRuleRefNode;
import com.antlr.plugin.toolwindow.PreViewToolWindow;
import org.jetbrains.annotations.NotNull;

public class TestRuleAction extends AnAction implements DumbAware {
    public static final Logger LOG = Logger.getInstance("ANTLR TestRuleAction");

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    /**
     * Only show if selection is a grammar and in a rule
     */
    @Override
    public void update(AnActionEvent e) {
        Presentation presentation = e.getPresentation();
        presentation.setText("Test ANTLR Rule"); // default text
        presentation.setIcon(AllIcons.Actions.Execute);

        VirtualFile grammarFile = MyActionUtils.getGrammarFileFromEvent(e);
        if (grammarFile == null) { // we clicked somewhere outside text or non grammar file
            presentation.setEnabled(false);
            presentation.setVisible(false);
            return;
        }

        ParserRuleRefNode r = MyActionUtils.getParserRuleSurroundingRef(e);
        if (r == null) {
            presentation.setEnabled(false);
            return;
        }

        presentation.setVisible(true);
        String ruleName = r.getText();
        if (Character.isLowerCase(ruleName.charAt(0))) {
            presentation.setEnabled(true);
            presentation.setText("Test Rule " + ruleName);
        } else {
            presentation.setEnabled(false);
        }
    }

    @Override
    public void actionPerformed(final AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            LOG.error("actionPerformed no project for " + e);
            return;
        }
        VirtualFile grammarFile = MyActionUtils.getGrammarFileFromEvent(e);
        if (grammarFile == null) return;

        ParserRuleRefNode r = MyActionUtils.getParserRuleSurroundingRef(e);
        if (r == null) {
            return;
        }
        String ruleName = r.getText();
        LOG.info("actionPerformed " + grammarFile + " rule " + ruleName);

        FileDocumentManager docMgr = FileDocumentManager.getInstance();
        Document doc = docMgr.getDocument(grammarFile);
        if (doc != null) {
            docMgr.saveDocument(doc);
        }

        ANTLRv4PluginController controller = ANTLRv4PluginController.getInstance(project);
        if (controller == null) {
            return;
        }

        // Set start rule before showing/loading so async grammar load picks it up
        PreviewState previewState = controller.getPreviewState(grammarFile);
        previewState.startRuleName = ruleName;

        // Wait until the tool window is shown (content created) before loading/parsing
        controller.showPre(() -> {
            if (project.isDisposed()) {
                return;
            }
            project.getMessageBus().syncPublisher(PreViewToolWindow.TOPIC)
                    .setStartRuleName(grammarFile, ruleName);
            controller.currentEditorFileChangedEvent(project, null, grammarFile, false);
            // If grammars are already loaded, parse immediately; otherwise the async
            // grammarFileChanged callback will parse using the startRuleName set above.
            if (previewState.g != null && previewState.lg != null) {
                project.getMessageBus().syncPublisher(PreViewToolWindow.TOPIC)
                        .updateParseTreeFromDoc(grammarFile);
            }
        });
    }
}
