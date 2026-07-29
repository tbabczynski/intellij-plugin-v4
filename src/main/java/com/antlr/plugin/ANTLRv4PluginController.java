package com.antlr.plugin;

import com.antlr.ApplicationInfo;
import com.antlr.plugin.parsing.ParsingResult;
import com.antlr.plugin.parsing.ParsingUtils;
import com.antlr.plugin.parsing.RunANTLROnGrammarFile;
import com.antlr.plugin.preview.PreviewState;
import com.antlr.plugin.toolwindow.ConsoleToolWindow;
import com.antlr.plugin.toolwindow.PreViewToolWindow;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.antlr.plugin.configdialogs.ANTLRv4GrammarProperties;
import com.antlr.plugin.configdialogs.ANTLRv4ToolGrammarPropertiesStore;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.messages.MessageBusConnection;
import org.antlr.v4.parse.ANTLRParser;
import org.antlr.v4.tool.Grammar;
import org.antlr.v4.tool.LexerGrammar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This object is the controller for the ANTLR plug-in. It receives
 * events and can send them on to its contained components. For example,
 * saving the grammar editor or flipping to a new grammar sends an event
 * to this object, which forwards on update events to the preview tool window.
 * <p>
 * The main components are related to the console tool window forever output and
 * the main panel of the preview tool window.
 * <p>
 * This controller also manages the cache of grammar/editor combinations
 * needed for the preview window. Updates must be made atomically so that
 * the grammars and editors are consistently associated with the same window.
 */
public class ANTLRv4PluginController implements Disposable {
    public static final String PLUGIN_ID = ApplicationInfo.PLUGIN_ID;

    public static final Key<GrammarEditorMouseAdapter> EDITOR_MOUSE_LISTENER_KEY = Key.create("EDITOR_MOUSE_LISTENER_KEY");
    public static final Logger LOG = Logger.getInstance("ANTLRv4PluginController");


    public boolean projectIsClosed = false;

    public Project project;

    public final Map<String, PreviewState> grammarToPreviewState = new ConcurrentHashMap<>();

    public MyVirtualFileAdapter myVirtualFileAdapter = new MyVirtualFileAdapter();
    public MyFileEditorManagerAdapter myFileEditorManagerAdapter = new MyFileEditorManagerAdapter();

    private ProgressIndicator parsingProgressIndicator;
    private MessageBusConnection messageBusConnection;
    private final AtomicBoolean listenersInstalled = new AtomicBoolean(false);
    private final AtomicLong parseGeneration = new AtomicLong();

    private final Map<String, Long> grammarFileMods = new ConcurrentHashMap<>();

    public ANTLRv4PluginController(Project project) {
        this.project = project;
    }

    public static ANTLRv4PluginController getInstance(Project project) {
        if (project == null) {
            LOG.info("getInstance: project is null");
            return null;
        }
        if (project.isDisposed()) {
            LOG.info("getInstance: project is already disposed");
            return null;
        }
        ANTLRv4PluginController pc = project.getService(ANTLRv4PluginController.class);
        if (pc == null) {
            LOG.info("getInstance: getComponent() for " + project.getName() + " returns null");
        }
        return pc;
    }

    public void showPre(Runnable runnable) {
        ToolWindow toolWindow = ToolWindowManager.getInstance(this.project).getToolWindow(PreViewToolWindow.WINDOW_ID);
        if (toolWindow != null) {
            toolWindow.show(runnable);
        }
    }


    public void projectOpened() {
        IdeaPluginDescriptor plugin = PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID));
        String version = "unknown";
        if (plugin != null) {
            version = plugin.getVersion();
        }
        LOG.info("ANTLR 4 Plugin version " + version + ", Java version " + SystemInfo.JAVA_VERSION);
        // make sure the tool windows are created early
        installListeners();
    }


    public void projectClosed() {
        dispose();
    }

    @Override
    public void dispose() {
        if (projectIsClosed) {
            return;
        }
        LOG.info("dispose " + project.getName());
        projectIsClosed = true;
        abortCurrentParsing();
        uninstallListeners();
        for (PreviewState previewState : grammarToPreviewState.values()) {
            previewState.releaseEditor();
            if (!project.isDisposed()) {
                project.getMessageBus().syncPublisher(PreViewToolWindow.TOPIC).releaseEditor(previewState);
            }
        }
        grammarToPreviewState.clear();
        grammarFileMods.clear();
    }

    public void uninstallListeners() {
        if (!listenersInstalled.compareAndSet(true, false)) {
            return;
        }
        VirtualFileManager.getInstance().removeVirtualFileListener(myVirtualFileAdapter);
        if (messageBusConnection != null) {
            messageBusConnection.disconnect();
            messageBusConnection = null;
        }
    }

    // ------------------------------

    public void installListeners() {
        if (projectIsClosed || project.isDisposed() || !listenersInstalled.compareAndSet(false, true)) {
            return;
        }
        LOG.info("installListeners " + project.getName());
        // Listen for .g4 file saves (application-wide; filtered to this project below)
        VirtualFileManager.getInstance().addVirtualFileListener(myVirtualFileAdapter);

        // Listen for editor window changes; parented to this Disposable for auto-cleanup
        messageBusConnection = project.getMessageBus().connect(this);
        messageBusConnection.subscribe(
                FileEditorManagerListener.FILE_EDITOR_MANAGER,
                myFileEditorManagerAdapter
        );

        EditorFactory factory = EditorFactory.getInstance();
        factory.addEditorFactoryListener(
                new EditorFactoryListener() {
                    @Override
                    public void editorCreated(@NotNull EditorFactoryEvent event) {
                        final Editor editor = event.getEditor();
                        // Require a matching project; null-project editors are not ours
                        if (editor.getProject() != project) {
                            return;
                        }
                        final Document doc = editor.getDocument();
                        VirtualFile file = FileDocumentManager.getInstance().getFile(doc);
                        if (file != null && file.getName().endsWith(".g4")) {
                            GrammarEditorMouseAdapter listener = new GrammarEditorMouseAdapter();
                            editor.putUserData(EDITOR_MOUSE_LISTENER_KEY, listener);
                            editor.addEditorMouseListener(listener);
                        }
                    }

                    @Override
                    public void editorReleased(@NotNull EditorFactoryEvent event) {
                        Editor editor = event.getEditor();
                        if (editor.getProject() != project) {
                            return;
                        }
                        GrammarEditorMouseAdapter listener = editor.getUserData(EDITOR_MOUSE_LISTENER_KEY);
                        if (listener != null) {
                            editor.removeEditorMouseListener(listener);
                            editor.putUserData(EDITOR_MOUSE_LISTENER_KEY, null);
                        }
                    }
                }
                , this);
    }

    /**
     * The test ANTLR rule action triggers this event. This can occur
     * only occur when the current editor is showing a grammar, because
     * that is the only time that the action is enabled. We will see
     * a file changed event when the project loads the first grammar file.
     */
    public void setStartRuleNameEvent(VirtualFile grammarFile, String startRuleName) {
        LOG.info("setStartRuleNameEvent " + startRuleName + " " + project.getName());
        PreviewState previewState = getPreviewState(grammarFile);
        previewState.startRuleName = startRuleName;
        if (this.project != null && !this.project.isDisposed()) {
            this.project.getMessageBus().syncPublisher(PreViewToolWindow.TOPIC).setStartRuleName(grammarFile, startRuleName);
            this.project.getMessageBus().syncPublisher(PreViewToolWindow.TOPIC).updateParseTreeFromDoc(grammarFile);
        } else {
            LOG.error("setStartRuleNameEvent called before preview panel created");
        }
    }

    public void grammarFileSavedEvent(Project project, VirtualFile grammarFile) {
        if (projectIsClosed || project.isDisposed()) {
            return;
        }

        Long modCount = grammarFile.getModificationCount();
        String grammarFilePath = grammarFile.getPath();

        if (grammarFileMods.containsKey(grammarFilePath) && grammarFileMods.get(grammarFilePath).equals(modCount)) {
            return;
        }

        grammarFileMods.put(grammarFilePath, modCount);

        LOG.info("grammarFileSavedEvent " + grammarFilePath + " " + project.getName());
        // True disk/save path: may regenerate .tokens / run Autogen when enabled
        updateGrammarObjectsFromFile(project, grammarFile, true, () -> {
            if (projectIsClosed || this.project.isDisposed()) {
                return;
            }
            this.project.getMessageBus().syncPublisher(PreViewToolWindow.TOPIC).grammarFileSaved(grammarFile);
        });
    }

    /**
     * Reload interpreter grammars and refresh the preview UI only.
     * Does not write {@code .tokens} or run ANTLR code generation — used by annotator-driven auto-refresh.
     */
    public void reloadGrammarForPreview(VirtualFile grammarFile) {
        if (projectIsClosed || project.isDisposed() || grammarFile == null) {
            return;
        }
        LOG.info("reloadGrammarForPreview " + grammarFile.getPath() + " " + project.getName());
        updateGrammarObjectsFromFile(project, grammarFile, false, () -> {
            if (projectIsClosed || this.project.isDisposed()) {
                return;
            }
            this.project.getMessageBus().syncPublisher(PreViewToolWindow.TOPIC).grammarFileSaved(grammarFile);
        });
    }

    public void currentEditorFileChangedEvent(Project project, VirtualFile oldFile, VirtualFile newFile, boolean modified) {
        LOG.info("currentEditorFileChangedEvent " + (oldFile != null ? oldFile.getPath() : "none") +
                " -> " + (newFile != null ? newFile.getPath() : "none") + " " + project.getName());
        if (newFile == null) { // all files must be closed I guess
            return;
        }

        String newFileExt = newFile.getExtension();

        if (newFileExt == null) {
            return;
        }

        if (newFileExt.equals("g")) {
            LOG.info("currentEditorFileChangedEvent ANTLR 4 cannot handle .g files, only .g4");
            hidePreview();
            return;
        }

        if (!newFileExt.equals("g4")) {
            return;
        }

        // When switching from a lexer grammar, update its objects in case the grammar was modified.
        // The updated objects might be needed later by another dependant grammar.
        if (oldFile != null && "g4".equals(oldFile.getExtension()) && modified) {
            updateGrammarObjectsFromFile(project, oldFile, true, null);
        }

        PreviewState previewState = getPreviewState(newFile);
        if (previewState.g == null && previewState.lg == null) { // only load grammars if none is there
            updateGrammarObjectsFromFile(project, newFile, false, () -> {
                if (projectIsClosed || this.project.isDisposed()) {
                    return;
                }
                this.project.getMessageBus().syncPublisher(PreViewToolWindow.TOPIC).grammarFileChanged(newFile);
            });
        } else if (!this.project.isDisposed()) {
            this.project.getMessageBus().syncPublisher(PreViewToolWindow.TOPIC).grammarFileChanged(newFile);
        }

    }

    public void mouseEnteredGrammarEditorEvent(VirtualFile vfile, EditorMouseEvent e) {
        if (this.project != null && !this.project.isDisposed()) {
            this.project.getMessageBus().syncPublisher(PreViewToolWindow.TOPIC).mouseEnteredGrammarEditorEvent(vfile, e);
        }
    }

    public void editorFileClosedEvent(VirtualFile vfile) {
        // hopefully called only from swing EDT
        String grammarFileName = vfile.getPath();
        LOG.info("editorFileClosedEvent " + grammarFileName + " " + project.getName());
        if (!vfile.getName().endsWith(".g4")) {
            hidePreview();
            return;
        }

        // Dispose of state, editor, and such for this file
        PreviewState previewState = grammarToPreviewState.remove(grammarFileName);
        grammarFileMods.remove(grammarFileName);
        if (previewState == null) { // project closing must have done already
            return;
        }

        previewState.g = null; // wack old ref to the Grammar for text in editor
        previewState.lg = null;
        previewState.releaseEditor();
        if (!project.isDisposed()) {
            project.getMessageBus().syncPublisher(PreViewToolWindow.TOPIC).closeGrammar(vfile);
        }

        // Only hide preview when no other grammar preview state remains
        if (grammarToPreviewState.isEmpty()) {
            hidePreview();
        }
    }

    private void hidePreview() {
        if (this.project != null && !this.project.isDisposed()) {
            this.project.getMessageBus().syncPublisher(PreViewToolWindow.TOPIC).setEnabled(false);
        }
        if (this.project != null && !this.project.isDisposed()) {
            this.project.getMessageBus().syncPublisher(PreViewToolWindow.TOPIC).toolWindowHide(null);
        }

    }

    /**
     * Make sure to run after updating grammars in previewState
     */
    public void runANTLRTool(final VirtualFile grammarFile) {
        String title = "ANTLR Code Generation";
        boolean canBeCancelled = true;
        boolean forceGeneration = false;
        Task gen =
                new RunANTLROnGrammarFile(grammarFile,
                        project,
                        title,
                        canBeCancelled,
                        forceGeneration);
        ProgressManager.getInstance().run(gen);
    }

    /**
     * Look for state information concerning this grammar file and update
     * the Grammar objects.  This does not necessarily update the grammar file
     * in the current editor window.  Either we are already looking at
     * this grammar or we will have seen a grammar file changed event.
     * (I hope!)
     */
    private void updateGrammarObjectsFromFile(Project project, VirtualFile grammarFile, boolean generateTokensFile,
                                              @Nullable Runnable afterReload) {
        if (project.isDisposed() || projectIsClosed) {
            return;
        }
        updateGrammarObjectsFromFile_(project, grammarFile, () -> {
            if (project.isDisposed() || projectIsClosed) {
                return;
            }

            // if grammarFileName is a separate lexer, reload every open parser that depends on it
            List<PreviewState> associated = getAssociatedParsersIfLexer(grammarFile.getPath());
            if (!associated.isEmpty()) {
                Runnable reloadAssociated = () -> {
                    if (projectIsClosed || project.isDisposed()) {
                        return;
                    }
                    AtomicInteger remaining = new AtomicInteger(associated.size());
                    Runnable oneDone = () -> {
                        if (remaining.decrementAndGet() == 0 && afterReload != null) {
                            ApplicationManager.getApplication().invokeLater(afterReload, project.getDisposed());
                        }
                    };
                    for (PreviewState s : associated) {
                        if (s.grammarFile != null) {
                            updateGrammarObjectsFromFile_(project, s.grammarFile, oneDone);
                        } else if (remaining.decrementAndGet() == 0 && afterReload != null) {
                            ApplicationManager.getApplication().invokeLater(afterReload, project.getDisposed());
                        }
                    }
                };

                if (generateTokensFile) {
                    // Write .tokens synchronously, then reload parsers so they see fresh vocab
                    PreviewState lexerState = getPreviewState(grammarFile);
                    LexerGrammar lg = lexerState.lg;
                    if (lg != null && lg != ParsingUtils.BAD_LEXER_GRAMMAR) {
                        ApplicationManager.getApplication().executeOnPooledThread(() -> {
                            try {
                                RunANTLROnGrammarFile.writeTokensVocabFile(project, grammarFile, lg);
                            } catch (Exception ex) {
                                LOG.warn("Failed to write .tokens file for " + grammarFile.getName(), ex);
                            }
                            if (!projectIsClosed && !project.isDisposed()) {
                                reloadAssociated.run();
                            }
                        });
                        return;
                    }
                    // Fallback: full codegen if lexer object is unavailable
                    runANTLRTool(grammarFile);
                }

                reloadAssociated.run();
                return;
            }

            // Autogen for parser / combined grammars on save
            if (generateTokensFile) {
                ANTLRv4GrammarProperties props = ANTLRv4ToolGrammarPropertiesStore.getGrammarProperties(project, grammarFile);
                if (props != null && props.shouldAutoGenerateParser()) {
                    runANTLRTool(grammarFile);
                }
            }

            if (afterReload != null) {
                ApplicationManager.getApplication().invokeLater(afterReload, project.getDisposed());
            }
        });
    }

    private void updateGrammarObjectsFromFile_(Project project, VirtualFile grammarFile, @Nullable Runnable afterReload) {
        Task.Backgroundable task = new Task.Backgroundable(project, "Update grammar object from file") {
            @Override
            public void run(@NotNull ProgressIndicator progressIndicator) {
                if (projectIsClosed || project.isDisposed()) {
                    return;
                }
                PreviewState previewState = getPreviewState(grammarFile);
                CountDownLatch countDownLatch = new CountDownLatch(1);
                AtomicReference<Grammar[]> atomicReference = new AtomicReference<>(null);
                ApplicationManager.getApplication().executeOnPooledThread(() -> {
                    try {
                        if (!projectIsClosed && !project.isDisposed()) {
                            Grammar[] grammars = ParsingUtils.loadGrammars(grammarFile, project);
                            atomicReference.set(grammars);
                        }
                    } finally {
                        countDownLatch.countDown();
                    }
                });
                try {
                    countDownLatch.await(5L, TimeUnit.MINUTES);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOG.warn("Interrupted while loading grammar objects", e);
                }
                if (projectIsClosed || project.isDisposed()) {
                    return;
                }
                Grammar[] grammars = atomicReference.get();
                if (grammars != null) {
                    synchronized (previewState) { // build atomically
                        previewState.lg = (LexerGrammar) grammars[0];
                        previewState.g = grammars[1];
                    }
                } else {
                    synchronized (previewState) { // build atomically
                        previewState.lg = null;
                        previewState.g = null;
                    }
                }
                // Invalidate in-flight parses that may have read stale grammar objects
                parseGeneration.incrementAndGet();
                if (afterReload != null) {
                    ApplicationManager.getApplication().invokeLater(afterReload, project.getDisposed());
                }
            }
        };
        task.queue();
    }

    @Nullable
    public PreviewState getAssociatedParserIfLexer(String grammarFileName) {
        List<PreviewState> all = getAssociatedParsersIfLexer(grammarFileName);
        return all.isEmpty() ? null : all.get(0);
    }

    /**
     * All open parser preview states that depend on the given lexer grammar
     * (shared lg file, naming convention, or imported lexer).
     */
    @NotNull
    public List<PreviewState> getAssociatedParsersIfLexer(String grammarFileName) {
        List<PreviewState> matches = new ArrayList<>();
        for (Map.Entry<String, PreviewState> entry : grammarToPreviewState.entrySet()) {
            PreviewState s = entry.getValue();
            if (s == null) {
                continue;
            }
            // Only associate when lexer filename actually matches; never treat BAD_LEXER_GRAMMAR
            // as a wildcard match for every lexer save.
            if (s.lg != null && s.lg != ParsingUtils.BAD_LEXER_GRAMMAR
                    && sameFile(grammarFileName, s.lg.fileName)) {
                // s has a lexer with same filename, see if there is a parser grammar
                // (not a combined grammar)
                if (s.g != null && s.g.getType() == ANTLRParser.PARSER) {
                    matches.add(s);
                    continue;
                }
            }

            // Fallback: ParserFoo.g4 often pairs with LexerFoo.g4 / FooLexer.g4 by naming convention
            if (s.g != null && s.g.getType() == ANTLRParser.PARSER && s.grammarFile != null) {
                String parserPath = s.grammarFile.getPath();
                String expectedLexer = ParsingUtils.getLexerNameFromParserFileName(parserPath);
                if (expectedLexer != null && sameFile(grammarFileName, expectedLexer)) {
                    if (!matches.contains(s)) {
                        matches.add(s);
                    }
                    continue;
                }
            }

            if (s.g != null && s.g.importedGrammars != null) {
                for (Grammar importedGrammar : s.g.importedGrammars) {
                    // Only associate when the imported grammar is a lexer (token dependency)
                    if (sameFile(grammarFileName, importedGrammar.fileName)
                            && importedGrammar instanceof LexerGrammar) {
                        if (!matches.contains(s)) {
                            matches.add(s);
                        }
                        break;
                    }
                }
            }
        }
        return matches;
    }

    private boolean sameFile(String pathOne, String pathTwo) {
        // use new File() to support both / and \ in paths
        return FileUtil.comparePaths(pathOne, pathTwo) == 0;
//        return new File(pathOne).equals(new File(pathTwo));
    }

    public void parseText(final VirtualFile grammarFile, String inputText) {
        if (projectIsClosed || project.isDisposed()) {
            return;
        }
        final PreviewState previewState = getPreviewState(grammarFile);
        // No need to parse empty text during unit tests, yet...
        if (inputText.isEmpty() && ApplicationManager.getApplication().isUnitTestMode()) return;
        // Cancel any in-flight parse so out-of-order completions cannot overwrite newer results
        if (parsingProgressIndicator != null) {
            parsingProgressIndicator.cancel();
            parsingProgressIndicator = null;
        }
        final long generation = parseGeneration.incrementAndGet();
        // Snapshot grammar objects under the same lock used by reload
        final Grammar g;
        final LexerGrammar lg;
        final String startRuleName;
        synchronized (previewState) {
            g = previewState.g;
            lg = previewState.lg;
            startRuleName = previewState.startRuleName;
        }
        // Parse text in a background thread to avoid freezing the UI if the grammar is badly written
        // and takes forever to interpret the input.
        parsingProgressIndicator = BackgroundTaskUtil.executeAndTryWait(
                (indicator) -> {
                    long start = System.nanoTime();

                    ParsingResult result = ParsingUtils.parseText(
                            g, lg, startRuleName,
                            grammarFile, inputText, project
                    );
                    return () -> {
                        // Only commit if this is still the latest requested parse
                        if (generation != parseGeneration.get() || indicator.isCanceled()
                                || projectIsClosed || project.isDisposed()) {
                            return;
                        }
                        previewState.parsingResult = result;
                        project.getMessageBus().syncPublisher(PreViewToolWindow.TOPIC)
                                .onParsingCompleted(previewState, System.nanoTime() - start);
                    };
                },
                () -> {
                    if (generation == parseGeneration.get() && !projectIsClosed && !project.isDisposed()) {
                        project.getMessageBus().syncPublisher(PreViewToolWindow.TOPIC).notifySlowParsing();
                    }
                },
                ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS,
                false
        );
    }

    public void abortCurrentParsing() {
        parseGeneration.incrementAndGet();
        if (parsingProgressIndicator != null) {
            parsingProgressIndicator.cancel();
            parsingProgressIndicator = null;
            if (!projectIsClosed && !project.isDisposed()) {
                project.getMessageBus().syncPublisher(PreViewToolWindow.TOPIC).onParsingCancelled();
            }
        }
    }

    public void startParsing() {
        parseGeneration.incrementAndGet();
        if (parsingProgressIndicator != null) {
            parsingProgressIndicator.cancel();
            parsingProgressIndicator = null;
        }
        if (!projectIsClosed && !project.isDisposed()) {
            project.getMessageBus().syncPublisher(PreViewToolWindow.TOPIC).clearParseErrors();
            project.getMessageBus().syncPublisher(PreViewToolWindow.TOPIC).startParsing();
        }
    }


    public static void showLaterConsoleWindow(final Project project) {
        showLaterConsoleWindow(project, null);
    }

    public static void showLaterConsoleWindow(final Project project, Runnable runnable) {
        if (project.isDisposed()) {
            return;
        }
        ApplicationManager.getApplication().invokeLater(
                () -> {
                    if (project.isDisposed()) {
                        return;
                    }
                    ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ConsoleToolWindow.WINDOW_ID);
                    if (toolWindow != null) {
                        // ensure content is created so buffered messages can flush
                        toolWindow.getContentManager();
                        if (!toolWindow.isVisible()) {
                            toolWindow.show(runnable);
                        } else if (runnable != null) {
                            runnable.run();
                        }
                    } else if (runnable != null) {
                        // Still publish so ConsoleToolWindow can buffer until content exists
                        runnable.run();
                    }
                },
                project.getDisposed()
        );
    }

    public @NotNull PreviewState getPreviewState(VirtualFile grammarFile) {
        String grammarFileName = grammarFile.getPath();
        return grammarToPreviewState.computeIfAbsent(grammarFileName, path -> new PreviewState(project, grammarFile));
    }

    @Nullable
    public PreviewState getPreviewStateIfPresent(VirtualFile grammarFile) {
        return grammarToPreviewState.get(grammarFile.getPath());
    }

    public Editor getEditor(VirtualFile file) {
        final FileDocumentManager fdm = FileDocumentManager.getInstance();
        final Document doc = fdm.getDocument(file);
        if (doc == null) return null;

        EditorFactory factory = EditorFactory.getInstance();
        final Editor[] editors = factory.getEditors(doc, this.project);
        if (editors.length == 0) {
            // no editor found for this file. likely an out-of-sequence issue
            // where Intellij is opening a project and doesn't fire events
            // in order we'd expect.
            return null;
        }
        return editors[0]; // hope just one
    }


    /**
     * Get the state information associated with the grammar in the current
     * editor window. If there is no grammar in the editor window, return null.
     * If there is a grammar, return any existing preview state else
     * create a new one in store in the map.
     * <p>
     * Too dangerous; turning off but might be useful later.
     * public @org.jetbrains.annotations.Nullable PreviewState getPreviewState() {
     * VirtualFile currentGrammarFile = getCurrentGrammarFile();
     * if ( currentGrammarFile==null ) {
     * return null;
     * }
     * String currentGrammarFileName = currentGrammarFile.getPath();
     * if ( currentGrammarFileName==null ) {
     * return null; // we are not looking at a grammar file
     * }
     * return getPreviewState(currentGrammarFile);
     * }
     */

    // These "get current editor file" routines should only be used
    // when you are sure the user is in control and is viewing the
    // right file (i.e., don't use these during project loading etc...)
    public static VirtualFile getCurrentEditorFile(Project project) {
        FileEditorManager mgr = FileEditorManager.getInstance(project);
        // "If more than one file is selected (split), the file with most recent focused editor is returned first." from IDE doc on method
        VirtualFile[] files = mgr.getSelectedFiles();
        if (files.length == 0) {
            return null;
        }
        return files[0];
    }

    public VirtualFile getCurrentGrammarFile() {
        return getCurrentGrammarFile(project);
    }

    public static VirtualFile getCurrentGrammarFile(Project project) {
        VirtualFile f = getCurrentEditorFile(project);
        if (f == null) {
            return null;
        }
        if (f.getName().endsWith(".g4")) return f;
        return null;
    }

    private class GrammarEditorMouseAdapter implements EditorMouseListener {
        @Override
        public void mouseClicked(EditorMouseEvent e) {
            if (e.getEditor().getProject() != project) {
                return;
            }
            Document doc = e.getEditor().getDocument();
            VirtualFile file = FileDocumentManager.getInstance().getFile(doc);
            if (file != null && file.getName().endsWith(".g4")) {
                mouseEnteredGrammarEditorEvent(file, e);
            }
        }
    }

    private class MyVirtualFileAdapter implements VirtualFileListener {
        @Override
        public void contentsChanged(VirtualFileEvent event) {
            final VirtualFile file = event.getFile();
            if (!file.getName().endsWith(".g4")) return;
            if (projectIsClosed || project.isDisposed() || ApplicationManager.getApplication().isUnitTestMode()) {
                return;
            }
            // VirtualFileListener is application-wide; only handle files in this project
            if (!ProjectFileIndex.getInstance(project).isInContent(file)) {
                return;
            }
            grammarFileSavedEvent(project, file);
        }
    }

    public class MyFileEditorManagerAdapter implements FileEditorManagerListener {
        @Override
        public void fileOpenedSync(@NotNull FileEditorManager source, @NotNull VirtualFile file, @NotNull Pair<FileEditor[], FileEditorProvider[]> editors) {
            if (projectIsClosed || project.isDisposed()) {
                return;
            }
            currentEditorFileChangedEvent(project, null, file, false);
        }

        @Override
        public void selectionChanged(@NotNull FileEditorManagerEvent event) {
            if (!projectIsClosed) {
                boolean modified = false;

                if (event.getOldEditor() != null) {
                    if (event.getOldEditor().isModified()) {
                        modified = true;
                    } else {
                        VirtualFile oldFile = event.getOldEditor().getFile();
                        String oldFilePath = oldFile.getPath();
                        Long modCount = oldFile.getModificationCount();
                        modified = grammarFileMods.containsKey(oldFilePath) &&
                                !grammarFileMods.get(oldFilePath).equals(modCount);
                    }

                }

                if (modified) {
                    new Task.Backgroundable(project, "Commit document") {
                        @Override
                        public void run(@NotNull ProgressIndicator progressIndicator) {
                            ApplicationManager.getApplication().invokeLater(() -> {
                                try {
                                    if (getProject() == null || getProject().isDisposed()) return;
                                    PsiDocumentManager psiMgr = PsiDocumentManager.getInstance(project);
                                    FileDocumentManager docMgr = FileDocumentManager.getInstance();
                                    if (event.getOldFile() != null && event.getOldFile().exists()) {
                                        Document doc = docMgr.getDocument(event.getOldFile());
                                        if (doc != null) {
                                            if ((!psiMgr.isCommitted(doc) || docMgr.isDocumentUnsaved(doc))
                                                    && !getProject().isDisposed()
                                                    && !project.isDisposed()
                                            ) {
                                                psiMgr.commitDocument(doc);
                                                docMgr.saveDocument(doc);
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    LOG.info("Commit document error", e);
                                }

                            });
                        }
                    }.queue();


                }
                currentEditorFileChangedEvent(ANTLRv4PluginController.this.project, event.getOldFile(), event.getNewFile(), modified);
            }
        }

        @Override
        public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
            if (projectIsClosed || project.isDisposed()) {
                return;
            }
            // Clean up whenever the file is no longer open (not only when it was the selected tab)
            if (!source.isFileOpen(file)) {
                editorFileClosedEvent(file);
            }
        }
    }

}
