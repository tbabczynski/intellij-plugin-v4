package com.antlr.plugin;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.annotation.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.antlr.plugin.actions.AnnotationIntentActionsFactory;
import com.antlr.plugin.toolwindow.PreViewToolWindow;
import com.antlr.plugin.validation.GrammarIssue;
import com.antlr.plugin.validation.GrammarIssuesCollector;
import org.antlr.runtime.ANTLRFileStream;
import org.antlr.runtime.CharStream;
import org.antlr.runtime.CommonToken;
import org.antlr.runtime.Token;
import org.antlr.v4.tool.ErrorSeverity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class ANTLRv4ExternalAnnotator extends ExternalAnnotator<PsiFile, List<GrammarIssue>> {

    /**
     * Called first; return file
     */
    @Override
    @Nullable
    public PsiFile collectInformation(@NotNull PsiFile file) {
        return file;
    }

    /**
     * Called 2nd; run antlr on file.
     * Only PSI reads are under a short read action; ANTLR analysis runs without holding the lock.
     */
    @Nullable
    @Override
    public List<GrammarIssue> doAnnotate(final PsiFile file) {
        VirtualFile vFile = file.getVirtualFile();
        if (vFile == null) {
            return Collections.emptyList();
        }
        return GrammarIssuesCollector.collectGrammarIssues(file);
    }

    /**
     * Called 3rd
     */
    @Override
    public void apply(@NotNull PsiFile file,
                      List<GrammarIssue> issues,
                      @NotNull AnnotationHolder holder) {
        for (GrammarIssue issue : issues) {
            if (issue.getOffendingTokens().isEmpty()) {
                annotateFileIssue(file, holder, issue);
            } else {
                annotateIssue(file, holder, issue);
            }
        }

        final ANTLRv4PluginController controller = ANTLRv4PluginController.getInstance(file.getProject());
        if (controller != null && !ApplicationManager.getApplication().isUnitTestMode()
                && !file.getProject().isDisposed()) {
            VirtualFile vFile = file.getVirtualFile();
            if (vFile != null) {
                // Defer preview refresh so annotation apply stays lightweight on the EDT
                ApplicationManager.getApplication().invokeLater(
                        () -> {
                            if (!file.getProject().isDisposed()) {
                                file.getProject().getMessageBus()
                                        .syncPublisher(PreViewToolWindow.TOPIC)
                                        .autoRefreshPreview(vFile);
                            }
                        },
                        file.getProject().getDisposed()
                );
            }
        }
    }

    private void annotateFileIssue(@NotNull PsiFile file, @NotNull AnnotationHolder holder, GrammarIssue issue) {
        holder.newAnnotation(HighlightSeverity.WARNING, issue.getAnnotation())
                .fileLevel()
                .create();
    }

    private void annotateIssue(@NotNull PsiFile file, @NotNull AnnotationHolder holder, GrammarIssue issue) {
        for (Token t : issue.getOffendingTokens()) {
            if (t instanceof CommonToken && tokenBelongsToFile(t, file)) {
                TextRange range = getTokenRange((CommonToken) t, file);
                ErrorSeverity severity = getIssueSeverity(issue);
                annotate(holder, issue, range, severity, file);
            }
        }
    }

    private ErrorSeverity getIssueSeverity(GrammarIssue issue) {
        if (issue.getMsg().getErrorType() != null) {
            return issue.getMsg().getErrorType().severity;
        }

        return ErrorSeverity.INFO;
    }

    @NotNull
    private TextRange getTokenRange(CommonToken ct, @NotNull PsiFile file) {
        int startIndex = ct.getStartIndex();
        int stopIndex = ct.getStopIndex();

        if (startIndex >= file.getTextLength()) {
            // can happen in case of a 'mismatched input EOF' error
            startIndex = stopIndex = file.getTextLength() - 1;
        }

        if (startIndex < 0) {
            // can happen on empty files, in that case we won't be able to show any error :/
            startIndex = 0;
        }

        return new TextRange(startIndex, stopIndex + 1);
    }

    private boolean tokenBelongsToFile(Token t, @NotNull PsiFile file) {
        CharStream inputStream = t.getInputStream();
        if (inputStream instanceof ANTLRFileStream) {
            VirtualFile vFile = file.getVirtualFile();
            if (vFile == null) {
                return false;
            }
            // Not equal if the token belongs to an imported grammar
            return inputStream.getSourceName().equals(vFile.getCanonicalPath());
        }

        return true;
    }

    private void annotate(@NotNull AnnotationHolder holder, GrammarIssue issue, TextRange range, ErrorSeverity severity, PsiFile file) {
        Optional<IntentionAction> intentionAction = AnnotationIntentActionsFactory.getFix(range, issue.getMsg().getErrorType(), file);
        switch (severity) {
            case ERROR:
            case ERROR_ONE_OFF:
            case FATAL: {
                AnnotationBuilder annotationBuilder = holder.newAnnotation(HighlightSeverity.ERROR, issue.getAnnotation()).range(range);
                if (intentionAction.isPresent()) {
                    annotationBuilder = annotationBuilder.newFix(intentionAction.get()).range(range).registerFix();
                }
                annotationBuilder.create();
                break;
            }
            case WARNING:
            case WARNING_ONE_OFF: {
                AnnotationBuilder warningBuilder = holder.newAnnotation(HighlightSeverity.WARNING, issue.getAnnotation()).range(range);
                if (intentionAction.isPresent()) {
                    warningBuilder = warningBuilder.newFix(intentionAction.get()).range(range).registerFix();
                }
                warningBuilder.create();
                break;
            }
            case INFO: {
                AnnotationBuilder infoBuilder = holder.newAnnotation(HighlightSeverity.INFORMATION, issue.getAnnotation()).range(range);
                if (intentionAction.isPresent()) {
                    infoBuilder = infoBuilder.newFix(intentionAction.get()).range(range).registerFix();
                }
                infoBuilder.create();
                break;
            }
            default:
                break;
        }
    }
}
