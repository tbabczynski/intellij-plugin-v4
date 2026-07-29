package com.antlr.plugin.preview;

import com.antlr.plugin.ANTLRv4PluginController;
import com.antlr.plugin.parsing.ParsingResult;
import com.antlr.plugin.parsing.ParsingUtils;
import com.antlr.plugin.parsing.PreviewParser;
import com.antlr.plugin.profiler.ProfilerPanel;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.antlr.v4.misc.OrderedHashMap;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Pair;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.TerminalNodeImpl;
import org.antlr.v4.runtime.tree.Tree;
import org.antlr.v4.tool.Rule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.Collections;

import static com.antlr.plugin.toolwindow.PreViewToolWindow.WINDOW_ID;
import static com.intellij.icons.AllIcons.Actions.Find;
import static com.intellij.icons.AllIcons.General.AutoscrollFromSource;

/**
 * The top level contents of the preview tool window created by
 * intellij automatically. Since we need grammars to interpret,
 * this object creates and caches lexer/parser grammars for
 * each grammar file it gets notified about.
 */
public class PreviewPanel extends JPanel implements ParsingResultSelectionListener, Disposable {
    public static final Logger LOG = Logger.getInstance("ANTLR PreviewPanel");

    public Project project;

    public InputPanel inputPanel;

    private ParseTreeGraphView treeViewer;
    public HierarchyViewer hierarchyViewer;

    public ProfilerPanel profilerPanel;

    /**
     * Indicates if the preview should be automatically refreshed after grammar changes.
     */
    private boolean autoRefresh = true;

    private boolean scrollFromSource = false;
    private boolean highlightSource = false;
    private boolean buildTree = true;
    private boolean buildHierarchy = true;

    private ActionToolbar buttonBar;
    private final CancelParserAction cancelParserAction = new CancelParserAction();

    /**
     * Used to avoid reparsing and also updating the parse tree upon each keystroke.
     */
    private final MergingUpdateQueue updateQueue;

    public PreviewPanel(Project project) {
        this.project = project;
        createGUI();
        updateQueue =
                new MergingUpdateQueue("(Re-) Parse Queue",
                        500,
                        true,
                        treeViewer
                );
        // If someone is typing, keep resetting timer so parsing doesn't start
        updateQueue.setRestartTimerOnAdd(true);
    }

    @Override
    public void dispose() {
        updateQueue.cancelAllUpdates();
        Disposer.dispose(updateQueue);
    }

    /**
     * True when {@code grammarFile} is the grammar currently shown in preview,
     * or a lexer/import dependency of that grammar (so UI refresh stays on the active preview).
     */
    private boolean shouldRefreshPreviewFor(@Nullable VirtualFile grammarFile) {
        if (grammarFile == null || inputPanel == null || inputPanel.previewState == null) {
            return false;
        }
        VirtualFile active = inputPanel.previewState.grammarFile;
        if (active == null) {
            return false;
        }
        if (FileUtil.pathsEqual(active.getPath(), grammarFile.getPath())) {
            return true;
        }
        ANTLRv4PluginController controller = ANTLRv4PluginController.getInstance(project);
        if (controller == null) {
            return false;
        }
        for (PreviewState associated : controller.getAssociatedParsersIfLexer(grammarFile.getPath())) {
            if (associated.grammarFile != null
                    && FileUtil.pathsEqual(associated.grammarFile.getPath(), active.getPath())) {
                return true;
            }
        }
        return false;
    }

    private void createGUI() {
        this.setLayout(new BorderLayout());

        // Had to set min size / preferred size in InputPanel.form to get slider to allow left shift of divider
        Splitter splitPane = new Splitter();
        inputPanel = getEditorPanel();
        inputPanel.addCaretListener(new CaretListener() {
            @Override
            public void caretPositionChanged(@NotNull CaretEvent event) {
                Caret caret = event.getCaret();

                if (scrollFromSource && caret != null) {
                    hierarchyViewer.selectNodeAtOffset(caret.getOffset());
                }
            }
        });
        splitPane.setFirstComponent(inputPanel.getComponent());
        splitPane.setSecondComponent(createParseTreeAndProfileTabbedPanel());

        this.add(splitPane, BorderLayout.CENTER);
        this.buttonBar = createButtonBar();
        this.add(buttonBar.getComponent(), BorderLayout.WEST);
    }

    private ActionToolbar createButtonBar() {
        final AnAction refreshAction = new ToggleAction("Refresh Preview Automatically",
                "Refresh preview automatically upon grammar changes", AllIcons.Actions.Refresh) {
            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.BGT;
            }

            @Override
            public boolean isSelected(@NotNull AnActionEvent e) {
                return autoRefresh;
            }

            @Override
            public void setSelected(@NotNull AnActionEvent e, boolean state) {
                autoRefresh = state;
            }
        };
        ToggleAction scrollFromSourceBtn = new ToggleAction("Scroll from Source", null, AutoscrollFromSource) {
            @Override
            public boolean isSelected(@NotNull AnActionEvent e) {
                return scrollFromSource;
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.BGT;
            }

            @Override
            public void setSelected(@NotNull AnActionEvent e, boolean state) {
                scrollFromSource = state;
            }
        };
        DefaultActionGroup actionGroup = getActionGroup(refreshAction, scrollFromSourceBtn);
        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(WINDOW_ID, actionGroup, false);
        toolbar.setTargetComponent(this.inputPanel.getComponent());
        return toolbar;
    }

    @NotNull
    private DefaultActionGroup getActionGroup(AnAction refreshAction, ToggleAction scrollFromSourceBtn) {
        ToggleAction scrollToSourceBtn = new ToggleAction("Highlight Source", null, Find) {
            @Override
            public boolean isSelected(@NotNull AnActionEvent e) {
                return highlightSource;
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.BGT;
            }

            @Override
            public void setSelected(@NotNull AnActionEvent e, boolean state) {
                highlightSource = state;
            }
        };
        ToggleAction autoBuildTree = new ToggleAction("Build Parse Tree After Parse", null, AllIcons.Toolwindows.ToolWindowHierarchy) {
            @Override
            public boolean isSelected(@NotNull AnActionEvent e) {
                return buildTree;
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.BGT;
            }

            @Override
            public void setSelected(@NotNull AnActionEvent e, boolean state) {
                buildTree = state;
            }
        };
        ToggleAction autoBuildHier = new ToggleAction("Build Hierarchy After Parse", null, AllIcons.Actions.ShowAsTree) {
            @Override
            public boolean isSelected(@NotNull AnActionEvent e) {
                return buildHierarchy;
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.BGT;
            }

            @Override
            public void setSelected(@NotNull AnActionEvent e, boolean state) {
                buildHierarchy = state;
            }
        };

        return new DefaultActionGroup(
                refreshAction,
                cancelParserAction,
                scrollFromSourceBtn,
                scrollToSourceBtn,
                autoBuildTree,
                autoBuildHier
        );
    }

    private InputPanel getEditorPanel() {
        LOG.info("createEditorPanel" + " " + project.getName());
        return new InputPanel(this);
    }

    public ProfilerPanel getProfilerPanel() {
        return profilerPanel;
    }

    private JTabbedPane createParseTreeAndProfileTabbedPanel() {
        JBTabbedPane tabbedPane = new JBTabbedPane();

        LOG.info("createParseTreePanel" + " " + project.getName());
        Pair<ParseTreeGraphView, JPanel> pair = createParseTreePanel();
        treeViewer = pair.a;
        setupParseTreeMouseHandlers(treeViewer);
        tabbedPane.addTab("Parse tree", pair.b);

        hierarchyViewer = new HierarchyViewer(null);
        hierarchyViewer.addParsingResultSelectionListener(this);
        tabbedPane.addTab("Hierarchy", hierarchyViewer);

        profilerPanel = new ProfilerPanel(project, this);
        tabbedPane.addTab("Profiler", profilerPanel.getComponent());

        return tabbedPane;
    }

    private void setupParseTreeMouseHandlers(final ParseTreeGraphView treeViewer) {
        treeViewer.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON3) {
                    ParseTreeContextualMenu.showPopupMenu(treeViewer, e);
                    return;
                }
                if (e.getButton() == MouseEvent.BUTTON1) {
                    Tree node = treeViewer.getTreeAt(e.getPoint());
                    if (node != null) {
                        treeViewer.setSelectedNode(node);
                        onParserRuleSelected(node);
                    }
                }
            }
        });
    }

    private static Pair<ParseTreeGraphView, JPanel> createParseTreePanel() {
        // wrap tree and slider in panel
        JPanel treePanel = new JPanel(new BorderLayout(0, 0));
        treePanel.setBackground(JBColor.white);

        final ParseTreeGraphView viewer = new ParseTreeGraphView(null, null, false);
        JSlider scaleSlider = createTreeViewSlider(viewer);

        // Wrap tree viewer component in scroll pane
        JScrollPane scrollPane = new JBScrollPane(viewer); // use Intellij's scroller

        treePanel.add(scrollPane, BorderLayout.CENTER);

        treePanel.add(scaleSlider, BorderLayout.SOUTH);

        return new Pair<>(viewer, treePanel);
    }

    @NotNull
    private static JSlider createTreeViewSlider(final ParseTreeGraphView viewer) {
        JSlider scaleSlider = new JSlider();
        scaleSlider.setModel(viewer.scaleModel);
        return scaleSlider;
    }

    /**
     * Notify the preview tool window contents that the grammar file has changed
     */
    public void grammarFileSaved(VirtualFile grammarFile) {
        // Avoid reparsing the active preview input against an unrelated grammar
        if (!shouldRefreshPreviewFor(grammarFile)) {
            return;
        }
        VirtualFile previewGrammar = inputPanel.previewState.grammarFile;
        String grammarFileName = grammarFile.getPath();
        LOG.info("grammarFileSaved " + grammarFileName + " (preview=" + previewGrammar.getPath() + ") " + project.getName());
        ANTLRv4PluginController controller = ANTLRv4PluginController.getInstance(project);
        if (controller == null) {
            return;
        }
        PreviewState previewState = controller.getPreviewState(previewGrammar);
        autoSetStartRule(previewState);
        ensureStartRuleExists(previewGrammar);
        inputPanel.grammarFileSaved();

        // if the preview grammar is not a pure lexer and there is a start rule, reparse
        if (previewState.g != null && previewState.startRuleName != null) {
            updateParseTreeFromDoc(previewGrammar);
        } else {
            clearTabs(null); // blank tree
        }

        profilerPanel.grammarFileSaved(previewState, previewGrammar);
    }

    private void ensureStartRuleExists(VirtualFile grammarFile) {
        ANTLRv4PluginController antlRv4PluginController = ANTLRv4PluginController.getInstance(project);
        if (antlRv4PluginController == null) {
            return;
        }
        PreviewState previewState = antlRv4PluginController.getPreviewState(grammarFile);
        // if start rule no longer exists, reset display/state.
        if (previewState.g != null &&
                previewState.g != ParsingUtils.BAD_PARSER_GRAMMAR &&
                previewState.startRuleName != null) {
            Rule rule = previewState.g.getRule(previewState.startRuleName);
            if (rule == null) {
                previewState.startRuleName = null;
                inputPanel.resetStartRuleLabel();
            }
        }
    }

    /**
     * Notify the preview tool window contents that the grammar file has changed
     */
    public void grammarFileChanged(VirtualFile newFile) {
        switchToGrammar(newFile);
    }

    /**
     * Load grammars and set editor component.
     */
    private void switchToGrammar(VirtualFile grammarFile) {
        String grammarFileName = grammarFile.getPath();
        LOG.info("switchToGrammar " + grammarFileName + " " + project.getName());
        ANTLRv4PluginController controller = ANTLRv4PluginController.getInstance(project);
        if (controller == null) {
            return;
        }
        PreviewState previewState = controller.getPreviewState(grammarFile);
        autoSetStartRule(previewState);
        inputPanel.switchToGrammar(previewState, grammarFile);
        profilerPanel.switchToGrammar(previewState);

        if (previewState.startRuleName != null) {
            updateParseTreeFromDoc(grammarFile); // regens tree and profile data
        } else {
            clearTabs(null); // blank tree
        }

        // Enable when either parser or lexer grammar loaded successfully
        setEnabled(previewState.g != null || previewState.lg != null);
    }

    /**
     * From 1.18, automatically set the start rule name to the first rule in the grammar
     * if none has been specified
     */
    protected void autoSetStartRule(PreviewState previewState) {
        if (previewState.g == null || previewState.g.rules.isEmpty()) {
            // If there is no grammar all of a sudden, we need to unset the previous rule name
            previewState.startRuleName = null;
        } else if (previewState.startRuleName == null) {
            OrderedHashMap<String, Rule> rules = previewState.g.rules;
            previewState.startRuleName = rules.getElement(0).name;
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        this.setEnabledRecursive(this, enabled);
    }

    private void setEnabledRecursive(Component component, boolean enabled) {
        if (component instanceof JTable) {
            // seems there's a special case
            ((JTable) component).getTableHeader().setEnabled(enabled);
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                child.setEnabled(enabled);
                setEnabledRecursive(child, enabled);
            }
        }
    }

    public void closeGrammar(VirtualFile grammarFile) {
        String grammarFileName = grammarFile.getPath();
        LOG.info("closeGrammar " + grammarFileName + " " + project.getName());
        inputPanel.resetStartRuleLabel();
        inputPanel.clearErrorConsole();
        clearParseTree(); // wipe tree
        ANTLRv4PluginController controller = ANTLRv4PluginController.getInstance(project);
        if (controller == null) {
            return;
        }
        // Do not create a zombie PreviewState during close
        PreviewState previewState = controller.getPreviewStateIfPresent(grammarFile);
        if (previewState != null) {
            inputPanel.releaseEditor(previewState);
        }
    }

    private void clearTabs(@Nullable ParseTree tree) {
        ApplicationManager.getApplication().invokeLater(() -> {
            treeViewer.setRuleNames(Collections.emptyList());
            treeViewer.setTree(tree);
            hierarchyViewer.setTree(null);
            hierarchyViewer.setRuleNames(Collections.emptyList());
        });
    }

    private void updateTreeViewer(final PreviewState preview, final ParsingResult result) {
        if (result.parser instanceof PreviewParser) {
            AltLabelTextProvider provider = new AltLabelTextProvider(result.parser, preview.g);
            if (buildTree) {
                treeViewer.setTreeTextProvider(provider);
                treeViewer.setTree(result.tree);
            }
            if (buildHierarchy) {
                hierarchyViewer.setTreeTextProvider(provider);
                hierarchyViewer.setTree(result.tree);
            }
        } else {
            if (buildTree) {
                treeViewer.setRuleNames(Arrays.asList(preview.g.getRuleNames()));
                treeViewer.setTree(result.tree);
            }
            if (buildHierarchy) {
                hierarchyViewer.setRuleNames(Arrays.asList(preview.g.getRuleNames()));
                hierarchyViewer.setTree(result.tree);
            }
        }
    }


    void clearParseTree() {
        clearTabs(null);
    }

    private void indicateInvalidGrammarInParseTreePane() {
        showError("Issues with parser and/or lexer grammar(s) prevent preview; see ANTLR 'Tool Output' pane");
    }

    private void showError(String message) {
        clearTabs(new TerminalNodeImpl(new CommonToken(Token.INVALID_TYPE, message)));
    }

    private void indicateNoStartRuleInParseTreePane() {
        showError("No start rule is selected");
    }

    public void updateParseTreeFromDoc(VirtualFile grammarFile) {
        ANTLRv4PluginController controller = ANTLRv4PluginController.getInstance(project);
        if (controller == null) return;
        PreviewState previewState = controller.getPreviewState(grammarFile);
        LOG.info("updateParseTreeFromDoc " + grammarFile + " rule " + previewState.startRuleName);
        if (previewState.g == null || previewState.lg == null) {
            // likely error in grammar prevents it from loading properly into previewState; bail
            indicateInvalidGrammarInParseTreePane();
            return;
        }

        Editor editor = inputPanel.getInputEditor();
        if (editor == null) return;
        final String inputText = editor.getDocument().getText();

        // The controller will call us back when it's done parsing
        // Wipes out the console and also any error annotations
        updateQueue.queue(new Update(this) {
            @Override
            public boolean canEat(@NotNull Update update) {
                return true; // kill any previous queued up parses; only last keystroke input text matters
            }

            @Override
            public void run() {
                inputPanel.clearParseErrors();
                controller.startParsing();
//				System.out.println("PARSE:\n"+inputText);
                controller.parseText(grammarFile, inputText);
            }
        });
    }

    public InputPanel getInputPanel() {
        return inputPanel;
    }

    public void autoRefreshPreview(VirtualFile virtualFile) {
        final ANTLRv4PluginController controller = ANTLRv4PluginController.getInstance(project);

        if (autoRefresh
                && controller != null
                && shouldRefreshPreviewFor(virtualFile)
                && inputPanel.previewState != null
                && inputPanel.previewState.startRuleName != null) {
            // Preview-only reload: must NOT go through grammarFileSavedEvent (that runs autogen)
            ApplicationManager.getApplication().invokeLater(
                    () -> controller.reloadGrammarForPreview(virtualFile),
                    project.getDisposed());
        }
    }

    public void onParsingCompleted(PreviewState previewState, long duration) {
        ApplicationManager.getApplication().invokeLater(() -> { // make sure we're on GUI thread for this block
            // Ignore completions for a grammar that is not currently shown
            if (inputPanel.previewState == null
                    || inputPanel.previewState.grammarFile == null
                    || previewState.grammarFile == null
                    || !FileUtil.pathsEqual(
                    inputPanel.previewState.grammarFile.getPath(), previewState.grammarFile.getPath())) {
                return;
            }
            cancelParserAction.setEnabled(false);
            buttonBar.updateActionsImmediately();

            if (previewState.parsingResult != null) {
                updateTreeViewer(previewState, previewState.parsingResult);
                profilerPanel.setProfilerData(previewState, duration);
                inputPanel.showParseErrors(previewState.parsingResult.syntaxErrorListener.getSyntaxErrors());
            } else if (previewState.startRuleName == null) {
                indicateNoStartRuleInParseTreePane();
            } else {
                indicateInvalidGrammarInParseTreePane();
            }
        }, project.getDisposed());
    }

    public void notifySlowParsing() {
        ApplicationManager.getApplication().invokeLater(() -> {
            cancelParserAction.setEnabled(true);
            buttonBar.updateActionsImmediately();
        }, project.getDisposed());
    }

    public void onParsingCancelled() {
        ApplicationManager.getApplication().invokeLater(() -> {
            cancelParserAction.setEnabled(false);
            buttonBar.updateActionsImmediately();
            showError("Parsing was aborted");
        }, project.getDisposed());
    }

    public void startParsing() {
        ApplicationManager.getApplication().invokeLater(() -> {
            cancelParserAction.setEnabled(false);
            buttonBar.updateActionsImmediately();
        }, project.getDisposed());
    }

    @Override
    public void onParserRuleSelected(Tree tree) {
        int startIndex;
        int stopIndex;

        if (tree instanceof ParserRuleContext) {
            Token start = ((ParserRuleContext) tree).getStart();
            Token stop = ((ParserRuleContext) tree).getStop();
            if (start == null || stop == null) { // stop can be null if start is EOF; nothing to show so return
                return;
            }
            startIndex = start.getStartIndex();
            stopIndex = stop.getStopIndex();
        } else if (tree instanceof TerminalNode) {
            startIndex = ((TerminalNode) tree).getSymbol().getStartIndex();
            stopIndex = ((TerminalNode) tree).getSymbol().getStopIndex();
        } else {
            return;
        }

        // ANTLRv4PluginController.parseText() lazily updates the parse tree so it's possible
        // that we have edited the input and something triggers a click on Hierarchy / Parse tree
        // before the tree is done and therefore the tree parameter to this method.
        // Avoid trying to select text outside of doc[0..stopindex] as a general rule too.
        // Text is selected in the input pane only from Hierarchy / Parse-tree mouse selection,
        // not when the caret moves in the input editor.
        Editor editor = inputPanel.getInputEditor();
        if (editor == null || editor.isDisposed()) {
            return;
        }
        if (startIndex >= 0 && stopIndex + 1 <= editor.getDocument().getTextLength()) {
            SelectionModel selectionModel = editor.getSelectionModel();
            selectionModel.removeSelection();
            selectionModel.setSelection(startIndex, stopIndex + 1);
        }
    }
}
