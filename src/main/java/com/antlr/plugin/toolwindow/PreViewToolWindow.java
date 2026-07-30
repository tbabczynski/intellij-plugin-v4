package com.antlr.plugin.toolwindow;

import com.antlr.plugin.ANTLRv4PluginController;
import com.antlr.plugin.Icons;
import com.antlr.plugin.listeners.PreViewListener;
import com.antlr.plugin.preview.PreviewPanel;
import com.antlr.plugin.preview.PreviewState;
import com.antlr.plugin.profiler.ProfilerPanel;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Per-project preview panel is stored in {@link Project} user data so the
 * shared ToolWindowFactory instance cannot cross-wire multi-project events.
 */
public class PreViewToolWindow implements ToolWindowFactory, DumbAware {
    public static final String WINDOW_ID = "ANTLR Preview";
    public static final Topic<PreViewListener> TOPIC = new Topic<>(PreViewListener.class);
    private static final Key<PreviewPanel> PREVIEW_PANEL_KEY = Key.create("antlr.tool.preview.panel");

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        PreviewPanel panel = new PreviewPanel(project);
        project.putUserData(PREVIEW_PANEL_KEY, panel);
        Content content = ContentFactory.getInstance().createContent(panel, "", false);
        content.setHelpId("antlr.new.pre.helper");
        content.setDisposer(() -> {
            Disposer.dispose(panel);
            if (project.getUserData(PREVIEW_PANEL_KEY) == panel) {
                project.putUserData(PREVIEW_PANEL_KEY, null);
            }
        });
        toolWindow.getContentManager().addContent(content);

        // Sync current grammar now that the panel exists (events before this were no-ops)
        VirtualFile current = ANTLRv4PluginController.getCurrentGrammarFile(project);
        if (current != null) {
            panel.grammarFileChanged(current);
        }
    }

    @Override
    public void init(@NotNull ToolWindow toolWindow) {
        toolWindow.setIcon(Icons.getToolWindow());
        Project project = toolWindow.getProject();
        if (project.isDisposed()) {
            return;
        }
        // Listener install is owned by AntlrStartupActivity.projectOpened()
        project.getMessageBus().connect(toolWindow.getDisposable()).subscribe(TOPIC, new PreViewListener() {
            private PreviewPanel panel() {
                return project.getUserData(PREVIEW_PANEL_KEY);
            }

            @Override
            public void releaseEditor(PreviewState previewState) {
                PreviewPanel p = panel();
                if (p != null) {
                    p.getInputPanel().releaseEditor(previewState);
                }
            }

            @Override
            public void setStartRuleName(VirtualFile grammarFile, String startRuleName) {
                PreviewPanel p = panel();
                if (p != null) {
                    p.getInputPanel().setStartRuleName(grammarFile, startRuleName);
                }
            }

            @Override
            public void updateParseTreeFromDoc(VirtualFile grammarFile) {
                PreviewPanel p = panel();
                if (p != null) {
                    p.updateParseTreeFromDoc(grammarFile);
                }
            }

            @Override
            public void grammarFileSaved(VirtualFile grammarFile) {
                PreviewPanel p = panel();
                if (p != null) {
                    p.grammarFileSaved(grammarFile);
                }
            }

            @Override
            public void grammarFileChanged(VirtualFile grammarFile) {
                PreviewPanel p = panel();
                if (p != null) {
                    p.grammarFileChanged(grammarFile);
                }
            }

            @Override
            public void mouseEnteredGrammarEditorEvent(VirtualFile file, EditorMouseEvent event) {
                PreviewPanel p = panel();
                if (p != null) {
                    ProfilerPanel profilerPanel = p.getProfilerPanel();
                    if (profilerPanel != null) {
                        profilerPanel.mouseEnteredGrammarEditorEvent(event);
                    }
                }
            }

            @Override
            public void closeGrammar(VirtualFile file) {
                PreviewPanel p = panel();
                if (p != null) {
                    p.closeGrammar(file);
                }
            }

            @Override
            public void setEnabled(boolean enabled) {
                PreviewPanel p = panel();
                if (p != null) {
                    p.setEnabled(enabled);
                }
            }

            @Override
            public void toolWindowHide(@Nullable Runnable runnable) {
                if (panel() != null) {
                    toolWindow.hide(runnable);
                }
            }

            @Override
            public void onParsingCompleted(PreviewState previewState, long duration) {
                PreviewPanel p = panel();
                if (p != null) {
                    p.onParsingCompleted(previewState, duration);
                }
            }

            @Override
            public void notifySlowParsing() {
                PreviewPanel p = panel();
                if (p != null) {
                    p.notifySlowParsing();
                }
            }

            @Override
            public void onParsingCancelled() {
                PreviewPanel p = panel();
                if (p != null) {
                    p.onParsingCancelled();
                }
            }

            @Override
            public void clearParseErrors() {
                PreviewPanel p = panel();
                if (p != null) {
                    p.getInputPanel().clearParseErrors();
                }
            }

            @Override
            public void startParsing() {
                PreviewPanel p = panel();
                if (p != null) {
                    p.startParsing();
                }
            }

            @Override
            public void autoRefreshPreview(VirtualFile virtualFile) {
                PreviewPanel p = panel();
                if (p != null) {
                    p.autoRefreshPreview(virtualFile);
                }
            }
        });
    }
}
