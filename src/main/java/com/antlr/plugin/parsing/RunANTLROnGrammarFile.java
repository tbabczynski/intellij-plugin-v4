package com.antlr.plugin.parsing;

import com.antlr.plugin.ANTLRv4PluginController;
import com.antlr.plugin.ANTLRv4TokenTypes;
import com.antlr.plugin.PluginIgnoreMissingTokensFileErrorManager;
import com.antlr.plugin.configdialogs.ANTLRv4GrammarProperties;
import com.antlr.plugin.parser.ANTLRv4Parser;
import com.antlr.plugin.preview.PreviewState;
import com.antlr.plugin.psi.AtAction;
import com.antlr.plugin.psi.GrammarSpecNode;
import com.antlr.plugin.util.ConsoleUtils;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.antlr.intellij.adaptor.lexer.RuleIElementType;
import org.antlr.v4.Tool;
import org.antlr.v4.codegen.CodeGenerator;
import org.antlr.v4.runtime.misc.Utils;
import org.antlr.v4.tool.Grammar;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.stringtemplate.v4.misc.Misc;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static com.antlr.plugin.configdialogs.ANTLRv4ToolGrammarPropertiesStore.getGrammarProperties;
import static com.antlr.plugin.psi.MyPsiUtils.findChildrenOfType;
import static com.intellij.psi.util.PsiTreeUtil.getChildOfType;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

// learned how to do from Grammar-Kit by Gregory Shrago
public class RunANTLROnGrammarFile extends Task.Modal {
    public static final Logger LOG = Logger.getInstance("RunANTLROnGrammarFile");
    public static final String OUTPUT_DIR_NAME = "gen";
    public static final String groupDisplayId = "ANTLR 4 Parser Generation";

    private static final Pattern PACKAGE_DEFINITION_REGEX =
            Pattern.compile("package\\s+[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)*\\s*;");

    private final VirtualFile grammarFile;
    private final Project project;
    private final boolean forceGeneration;
    private boolean hadErrors;

    public RunANTLROnGrammarFile(VirtualFile grammarFile,
                                 @Nullable final Project project,
                                 @NotNull final String title,
                                 final boolean canBeCancelled,
                                 boolean forceGeneration) {
        super(project, title, canBeCancelled);
        this.grammarFile = grammarFile;
        this.project = project;
        this.forceGeneration = forceGeneration;
    }

    public boolean hadErrors() {
        return hadErrors;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
        indicator.setIndeterminate(true);
        ANTLRv4GrammarProperties grammarProperties = getGrammarProperties(project, grammarFile);
        boolean autogen = false;
        if (grammarProperties != null) {
            autogen = grammarProperties.shouldAutoGenerateParser();
        }
        if (forceGeneration || (autogen && isGrammarStale(grammarProperties))) {
            ReadAction.run(() -> antlr(grammarFile));
        } else {
            ANTLRv4PluginController controller = ANTLRv4PluginController.getInstance(project);
            if (controller == null) {
                return;
            }
            final PreviewState previewState = controller.getPreviewState(grammarFile);
            // is lexer file? gen .tokens file no matter what as tokens might have changed;
            // a parser that feeds off of that file will need to see the changes.
            if (previewState.g == null && previewState.lg != null) {
                writeTokensVocabFile(project, grammarFile, previewState.lg);
            }
        }
    }

    /**
     * Synchronously write the {@code .tokens} vocab file for a lexer grammar.
     * Used when a lexer save must refresh tokens before the associated parser is reloaded.
     */
    public static void writeTokensVocabFile(Project project, VirtualFile grammarFile, Grammar g) {
        if (project == null || grammarFile == null || g == null) {
            return;
        }
        String language = g.getOptionString(ANTLRv4GrammarProperties.PROP_LANGUAGE);
        if (language == null || language.isBlank()) {
            language = "Java";
        }
        ANTLRv4GrammarProperties props = getGrammarProperties(project, grammarFile);
        VirtualFile contentRoot = getContentRoot(project, grammarFile);
        Map<String, String> argMap = getANTLRArgs(project, grammarFile);
        String package_ = argMap.get("-package");
        if (package_ == null && props != null) {
            package_ = props.getPackage();
        }
        String outputDirName = props != null
                ? props.resolveOutputDirName(project, contentRoot, package_)
                : OUTPUT_DIR_NAME;
        //noinspection ResultOfMethodCallIgnored
        new File(outputDirName).mkdirs();

        // Construct Tool with -o so haveOutputDir is set (field is protected)
        String libDir = props != null
                ? props.resolveLibDir(project, getParentDir(grammarFile))
                : getParentDir(grammarFile);
        // Same as antlr(): Tool(args) can emit DIR_NOT_FOUND for -lib before listeners attach,
        // then silently fall back libDirectory to ".". Do not write .tokens with a wrong lib root.
        Tool tool = new Tool(new String[]{"-o", outputDirName, "-lib", libDir});
        if (tool.getNumErrors() > 0) {
            LOG.warn("Skipping .tokens write for " + grammarFile.getName()
                    + ": invalid ANTLR options (-lib=" + libDir + ", -o=" + outputDirName + ")");
            ConsoleUtils.consolePrint(project,
                    "error: skipped writing .tokens for " + grammarFile.getName()
                            + " due to invalid ANTLR options (-lib=" + libDir + ", -o=" + outputDirName + "). "
                            + "Check Configure ANTLR.\n",
                    ConsoleViewContentType.ERROR_OUTPUT);
            return;
        }
        tool.errMgr = new PluginIgnoreMissingTokensFileErrorManager(tool);
        tool.errMgr.setFormat("antlr");
        tool.removeListeners();
        // Target.genFile uses Grammar.tool for the output directory; force absolute -o there too.
        if (g.tool != null) {
            g.tool.outputDirectory = outputDirName;
            setHaveOutputDir(g.tool);
        }
        setHaveOutputDir(tool);
        CodeGenerator gen = CodeGenerator.create(tool, g, language);
        if (gen != null) {
            gen.writeVocabFile();
        }
    }

    /** {@code Tool.haveOutputDir} is protected; -o constructor sets it, but Grammar.tool may be a bare Tool. */
    private static void setHaveOutputDir(Tool tool) {
        try {
            var field = Tool.class.getDeclaredField("haveOutputDir");
            field.setAccessible(true);
            field.setBoolean(tool, true);
        } catch (ReflectiveOperationException ignored) {
            // Best-effort; Tool constructed with -o already has the flag set.
        }
    }

    // TODO: lots of duplication with antlr() function.
    private boolean isGrammarStale(ANTLRv4GrammarProperties grammarProperties) {
        // #722: VFS events can fire for files without a parent (e.g. git-index virtual files)
        String parentDir = getParentDir(grammarFile);
        if (parentDir == null) {
            return false;
        }
        // Grammar file path is always next to the .g4 file; libDir is for imports/tokenVocab only
        String fullyQualifiedInputFileName = parentDir + File.separator + grammarFile.getName();

        ANTLRv4PluginController controller = ANTLRv4PluginController.getInstance(project);
        if (controller == null) {
            return false;
        }
        final PreviewState previewState = controller.getPreviewState(grammarFile);
        Grammar g = previewState.getMainGrammar();
        // Grammar should be updated in the preview state before calling this function
        if (g == null) {
            return false;
        }

        String language = g.getOptionString(ANTLRv4GrammarProperties.PROP_LANGUAGE);
        CodeGenerator generator = CodeGenerator.create(null, g, language);
        String recognizerFileName = generator.getRecognizerFileName();

        VirtualFile contentRoot = getContentRoot(project, grammarFile);
        // Match package used by getANTLRArgs (configured or inferred)
        Map<String, String> argMap = getANTLRArgs(project, grammarFile);
        String package_ = argMap.get("-package");
        if (package_ == null) {
            package_ = grammarProperties.getPackage();
        }
        String outputDirName = grammarProperties.resolveOutputDirName(project, contentRoot, package_);
        String fullyQualifiedOutputFileName = outputDirName + File.separator + recognizerFileName;

        File inF = new File(fullyQualifiedInputFileName);
        File outF = new File(fullyQualifiedOutputFileName);
        boolean stale = !outF.exists() || inF.lastModified() > outF.lastModified();
        if (!stale) {
            // Imports / tokenVocab / companion lexer can change without touching the main .g4 mtime
            stale = hasNewerDependency(g, outF.lastModified(), grammarProperties);
        }
        LOG.info((!stale ? "not" : "") + "stale: " + fullyQualifiedInputFileName + " -> " + fullyQualifiedOutputFileName);
        return stale;
    }

    private boolean hasNewerDependency(Grammar g, long outLastModified,
                                       ANTLRv4GrammarProperties grammarProperties) {
        if (g.importedGrammars != null) {
            for (Grammar imported : g.importedGrammars) {
                if (imported == null || imported.fileName == null) {
                    continue;
                }
                File dep = new File(imported.fileName);
                if (dep.isFile() && dep.lastModified() > outLastModified) {
                    return true;
                }
            }
        }

        String tokenVocab = g.getOptionString("tokenVocab");
        if (tokenVocab != null && !tokenVocab.isBlank()) {
            String sourcePath = getParentDir(grammarFile);
            String libDir = grammarProperties.resolveLibDir(project, sourcePath);
            File vocab = new File(tokenVocab.endsWith(".g4") ? tokenVocab : tokenVocab + ".g4");
            if (!vocab.isAbsolute()) {
                File beside = new File(sourcePath, vocab.getPath());
                File inLib = libDir != null ? new File(libDir, vocab.getPath()) : null;
                if (beside.isFile() && beside.lastModified() > outLastModified) {
                    return true;
                }
                if (inLib != null && inLib.isFile() && inLib.lastModified() > outLastModified) {
                    return true;
                }
            } else if (vocab.isFile() && vocab.lastModified() > outLastModified) {
                return true;
            }
        }

        String companionLexer = ParsingUtils.getLexerNameFromParserFileName(
                getParentDir(grammarFile) + File.separator + grammarFile.getName());
        File lexerFile = new File(companionLexer);
        return lexerFile.isFile() && lexerFile.lastModified() > outLastModified;
    }

    /**
     * Run ANTLR tool on file according to preferences in intellij for this file.
     * Returns set of generated files or empty set if error.
     */
    private void antlr(VirtualFile vfile) {
        if (vfile == null) return;

        LOG.info("antlr(\"" + vfile.getPath() + "\")");
        List<String> args = getANTLRArgsAsList(project, vfile);

        String sourcePath = getParentDir(vfile);
        String fullyQualifiedInputFileName = sourcePath + File.separator + vfile.getName();
        args.add(fullyQualifiedInputFileName); // add grammar file last

        String lexerGrammarFileName = ParsingUtils.getLexerNameFromParserFileName(fullyQualifiedInputFileName);
        if (new File(lexerGrammarFileName).exists()) {
            // build the lexer too as the grammar surely uses it if it exists
            args.add(lexerGrammarFileName);
        }

        LOG.info("args: " + Utils.join(args.iterator(), " "));

        String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Calendar.getInstance().getTime());
        ConsoleUtils.consolePrint(project, timeStamp + ": antlr4 " + Misc.join(args.iterator(), " ") + "\n", ConsoleViewContentType.SYSTEM_OUTPUT);

        // Tool(args) runs handleArgs() in the constructor. Errors like DIR_NOT_FOUND for -lib
        // are emitted via DefaultToolListener (stderr) while listeners is still empty — before we
        // can attach RunANTLRListener. Capture errMgr count so we don't report a false success.
        Tool antlr = new Tool(args.toArray(new String[0]));
        int errorsFromArgs = antlr.getNumErrors();

        antlr.removeListeners();
        RunANTLRListener listener = new RunANTLRListener(antlr, project);
        antlr.addListener(listener);
        if (errorsFromArgs > 0) {
            listener.hasErrors = true;
            listener.hasOutput = true;
            // Original messages went to stderr; surface actionable paths in ANTLR Console.
            String lib = antlr.libDirectory;
            String out = antlr.outputDirectory;
            ConsoleUtils.consolePrint(project,
                    "error: " + errorsFromArgs + " problem(s) in ANTLR options"
                            + " (-lib=" + lib + ", -o=" + out + ")."
                            + " Invalid directories are rejected; check Configure ANTLR.\n",
                    ConsoleViewContentType.ERROR_OUTPUT);
            // After DIR_NOT_FOUND, Tool silently falls back libDirectory to "."; do not continue
            // and pretend generation succeeded with a wrong lib root.
            hadErrors = true;
            ANTLRv4PluginController.showLaterConsoleWindow(project);
            return;
        }

        try {
            antlr.processGrammarsOnCommandLine();
            hadErrors = listener.hasErrors || antlr.getNumErrors() > 0;
        } catch (Throwable e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            String msg = sw.toString();
            Notification notification =
                    new Notification(groupDisplayId,
                            "can't generate parser for " + vfile.getName(),
                            e.toString(),
                            NotificationType.ERROR);
            Notifications.Bus.notify(notification, project);
            ConsoleUtils.consolePrint(project, timeStamp + ": antlr4 " + msg + "\n", ConsoleViewContentType.SYSTEM_OUTPUT);
            listener.hasOutput = true; // show console below
            hadErrors = true;
        }

        if (listener.hasOutput || hadErrors) {
            ANTLRv4PluginController.showLaterConsoleWindow(project);
        }
    }

    public static List<String> getANTLRArgsAsList(Project project, VirtualFile vfile) {
        Map<String, String> argMap = getANTLRArgs(project, vfile);
        List<String> args = new ArrayList<>();
        for (String option : argMap.keySet()) {
            args.add(option);
            String value = argMap.get(option);
            if (StringUtils.isNotBlank(value) &&!value.isEmpty()) {
                args.add(value);
            }
        }
        return args;
    }

    private static Map<String, String> getANTLRArgs(Project project, VirtualFile vfile) {
        Map<String, String> args = new HashMap<>();
        ANTLRv4GrammarProperties grammarProperties = getGrammarProperties(project, vfile);
        String sourcePath = getParentDir(vfile);
        String package_ = null;
        if (grammarProperties != null) {
            package_ = grammarProperties.getPackage();
        }
        if (isBlank(package_) && !hasPackageDeclarationInHeader(project, vfile) && vfile.getParent() != null) {
            // ProjectFileIndex requires a read action; this may run on a background Progress thread
            final VirtualFile parentDir = vfile.getParent();
            package_ = ReadAction.compute(() ->
                    ProjectRootManager.getInstance(project).getFileIndex().getPackageNameByDirectory(parentDir));
        }
        if (isNotBlank(package_)) {
            args.put("-package", package_);
        }

        String language = grammarProperties != null ? grammarProperties.getLanguage() : null;
        if (isNotBlank(language)) {
            args.put("-Dlanguage=" + language, "");
        }

        // create gen dir at root of project by default, but add in package if any
        VirtualFile contentRoot = getContentRoot(project, vfile);
        String outputDirName = grammarProperties != null ? grammarProperties.resolveOutputDirName(project, contentRoot, package_) : null;
        if (isNotBlank(outputDirName)) {
            args.put("-o", outputDirName);
        }

        String libDir = grammarProperties != null ? grammarProperties.resolveLibDir(project, sourcePath) : null;
        if (libDir != null) {
            File f = new File(libDir);
            if (!f.isAbsolute() && contentRoot != null) { // if not absolute file spec, it's relative to project root
                libDir = contentRoot.getPath() + File.separator + libDir;
            }
        }
        if (isNotBlank(libDir)) {
            args.put("-lib", libDir);
        }

        String encoding = grammarProperties != null ? grammarProperties.getEncoding() : null;
        // #395: fall back to VirtualFile charset / platform default when unset
        if (isBlank(encoding) && vfile != null) {
            encoding = vfile.getCharset() != null ? vfile.getCharset().name() : null;
        }
        if (isNotBlank(encoding)) {
            args.put("-encoding", encoding);
        }

        if (grammarProperties != null && grammarProperties.shouldGenerateParseTreeListener()) {
            args.put("-listener", "");
        } else {
            args.put("-no-listener", "");
        }
        if (grammarProperties != null && grammarProperties.shouldGenerateParseTreeVisitor()) {
            args.put("-visitor", "");
        } else {
            args.put("-no-visitor", "");
        }

        return args;
    }

    private static boolean hasPackageDeclarationInHeader(Project project, VirtualFile grammarFile) {
        return ApplicationManager.getApplication().runReadAction((Computable<Boolean>) () -> {
            PsiFile file = PsiManager.getInstance(project).findFile(grammarFile);
            GrammarSpecNode grammarSpecNode = getChildOfType(file, GrammarSpecNode.class);

            if (grammarSpecNode != null) {
                RuleIElementType prequelElementType = ANTLRv4TokenTypes.getRuleElementType(ANTLRv4Parser.RULE_prequelConstruct);

                for (PsiElement prequelConstruct : findChildrenOfType(grammarSpecNode, prequelElementType)) {
                    AtAction atAction = getChildOfType(prequelConstruct, AtAction.class);

                    if (atAction != null && atAction.getIdText().equals("header")) {
                        return PACKAGE_DEFINITION_REGEX.matcher(atAction.getActionBlockText()).find();
                    }
                }
            }

            return false;
        });
    }

    private static String getParentDir(VirtualFile vfile) {
        if (vfile == null || vfile.getParent() == null) {
            return null;
        }
        return vfile.getParent().getPath();
    }

    private static VirtualFile getContentRoot(Project project, VirtualFile vfile) {
        AtomicReference<VirtualFile> virtualFileAtomicReference = new AtomicReference<>(null);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        ApplicationManager.getApplication().runReadAction(() -> {
            try {
                VirtualFile root = ProjectRootManager.getInstance(project).getFileIndex().getContentRootForFile(vfile);
                if (root != null) {
                    virtualFileAtomicReference.set(root);
                } else {
                    virtualFileAtomicReference.set(vfile.getParent());
                }
            } finally {
                countDownLatch.countDown();
            }
        });
        try {
            countDownLatch.await(5L, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            return null;
        }
        return virtualFileAtomicReference.get();
    }

    public String getOutputDirName() {
        VirtualFile contentRoot = getContentRoot(project, grammarFile);
        Map<String, String> argMap = getANTLRArgs(project, grammarFile);
        String package_ = argMap.get("-package");

        ANTLRv4GrammarProperties props = getGrammarProperties(project, grammarFile);
        if (props == null) {
            return OUTPUT_DIR_NAME;
        }
        return props.resolveOutputDirName(project, contentRoot, package_);
    }
}
