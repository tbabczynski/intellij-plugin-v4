package com.antlr.plugin.actions;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.antlr.plugin.parsing.RunANTLROnGrammarFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/** Generate parser from ANTLR grammar;
 *  learned how to do from Grammar-Kit by Gregory Shrago.
 */
public class GenerateParserAction extends AnAction implements DumbAware {
    public static final Logger LOG = Logger.getInstance("ANTLR GenerateAction");

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(AnActionEvent e) {
        MyActionUtils.selectedFileIsGrammar(e);
    }

    @Override
    public void actionPerformed(final AnActionEvent e) {
        Project project = e.getData(PlatformDataKeys.PROJECT);
        if (project == null) {
            LOG.error("actionPerformed no project for " + e);
            return; // whoa!
        }
        VirtualFile grammarFile = MyActionUtils.getGrammarFileFromEvent(e);
        LOG.info("actionPerformed " + (grammarFile == null ? "NONE" : grammarFile));
        if (grammarFile == null) return;
        String title = "ANTLR Code Generation";
        boolean canBeCancelled = true;

        // commit changes to PSI and file system
        PsiDocumentManager psiMgr = PsiDocumentManager.getInstance(project);
        FileDocumentManager docMgr = FileDocumentManager.getInstance();
        Document doc = docMgr.getDocument(grammarFile);
        if (doc == null) return;

        boolean unsaved = !psiMgr.isCommitted(doc) || docMgr.isDocumentUnsaved(doc);
        if (unsaved) {
            psiMgr.commitDocument(doc);
            docMgr.saveDocument(doc);
        }
        // Explicit Generate always forces a run (do not rely on autogen/save side-effects)
        RunANTLROnGrammarFile gen =
                new RunANTLROnGrammarFile(grammarFile,
                        project,
                        title,
                        canBeCancelled,
                        true);

        ProgressManager.getInstance().run(gen);

        // refresh from disk to see new files
        Set<File> generatedFiles = new HashSet<>();
        generatedFiles.add(new File(gen.getOutputDirName()));
        LocalFileSystem.getInstance().refreshIoFiles(generatedFiles, true, true, null);
        Notification notification;
        if (gen.hadErrors()) {
            notification = new Notification(RunANTLROnGrammarFile.groupDisplayId,
                    "Failed to generate parser for " + grammarFile.getName(),
                    "See ANTLR Tool Console for details",
                    NotificationType.ERROR);
        } else {
            notification = new Notification(RunANTLROnGrammarFile.groupDisplayId,
                    "parser for " + grammarFile.getName() + " generated",
                    "to " + gen.getOutputDirName(),
                    NotificationType.INFORMATION);
        }
        Notifications.Bus.notify(notification, project);
    }
}
