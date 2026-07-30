package com.antlr.plugin.parsing;

import com.antlr.plugin.PluginIgnoreMissingTokensFileErrorManager;
import com.antlr.plugin.configdialogs.ANTLRv4GrammarProperties;
import com.antlr.plugin.parser.ANTLRv4Lexer;
import com.antlr.plugin.parser.ANTLRv4Parser;
import com.antlr.plugin.preview.PreviewState;
import com.antlr.plugin.resolve.TokenVocabResolver;
import com.antlr.plugin.util.ConsoleUtils;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.antlr.intellij.adaptor.parser.SyntaxErrorListener;
import org.antlr.runtime.ANTLRStringStream;
import org.antlr.v4.Tool;
import org.antlr.v4.parse.ANTLRParser;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.PredictionMode;
import org.antlr.v4.runtime.misc.Pair;
import org.antlr.v4.runtime.misc.Utils;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.Tree;
import org.antlr.v4.runtime.tree.Trees;
import org.antlr.v4.tool.ErrorType;
import org.antlr.v4.tool.Grammar;
import org.antlr.v4.tool.LexerGrammar;
import org.antlr.v4.tool.Rule;
import org.antlr.v4.tool.ast.GrammarAST;
import org.antlr.v4.tool.ast.GrammarRootAST;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.antlr.plugin.configdialogs.ANTLRv4ToolGrammarPropertiesStore.getGrammarProperties;


public class ParsingUtils {
    private static final Logger LOG = Logger.getInstance(ParsingUtils.class);
    public static Grammar BAD_PARSER_GRAMMAR;
    public static LexerGrammar BAD_LEXER_GRAMMAR;

    static {
        try {
            ParsingUtils.BAD_PARSER_GRAMMAR = new Grammar("grammar BAD; a : 'bad' ;");
            ParsingUtils.BAD_PARSER_GRAMMAR.name = "BAD_PARSER_GRAMMAR";
            ParsingUtils.BAD_LEXER_GRAMMAR = new LexerGrammar("lexer grammar BADLEXER; A : 'bad' ;");
            ParsingUtils.BAD_LEXER_GRAMMAR.name = "BAD_LEXER_GRAMMAR";
        } catch (org.antlr.runtime.RecognitionException re) {
            LOG.error("can't init bad grammar markers");
        }
    }

    public static Token nextRealToken(CommonTokenStream tokens, int i) {
        int n = tokens.size();
        i++; // search after current i token
        if (i >= n || i < 0) return null;
        Token t = tokens.get(i);
        while (t.getChannel() != Token.DEFAULT_CHANNEL) {  // Parser must parse tokens on DEFAULT_CHANNEL
            if (t.getType() == Token.EOF) {
                TokenSource tokenSource = tokens.getTokenSource();
                if (tokenSource == null) {
                    return new CommonToken(Token.EOF, "EOF");
                }
                TokenFactory<?> tokenFactory = tokenSource.getTokenFactory();
                if (tokenFactory == null) {
                    return new CommonToken(Token.EOF, "EOF");
                }
                return tokenFactory.create(Token.EOF, "EOF");
            }
            i++;
            if (i >= n) return null; // just in case no EOF
            t = tokens.get(i);
        }
        return t;
    }

    public static Token previousRealToken(CommonTokenStream tokens, int i) {
        int size = tokens.size();
        i--; // search before current i token
        if (i >= size || i < 0) return null;
        Token t = tokens.get(i);
        while (t.getChannel() != Token.DEFAULT_CHANNEL) { // Parser must parse tokens on DEFAULT_CHANNEL
            i--;
            if (i < 0) return null;
            t = tokens.get(i);
        }
        return t;
    }

    public static Token getTokenUnderCursor(PreviewState previewState, int offset) {
        if (previewState == null || previewState.parsingResult == null) return null;

        CommonTokenStream tokenStream = previewState.parsingResult.getTokenStream();
        if (tokenStream == null) return null;
        return ParsingUtils.getTokenUnderCursor(tokenStream, offset);
    }

    public static Token getTokenUnderCursor(CommonTokenStream tokens, int offset) {
        Comparator<Token> cmp = (a, b) -> {
            if (a.getStopIndex() < b.getStartIndex()) return -1;
            if (a.getStartIndex() > b.getStopIndex()) return 1;
            return 0;
        };
        if (offset < 0 || offset >= tokens.getTokenSource().getInputStream().size()) return null;
        CommonToken key = new CommonToken(Token.INVALID_TYPE, "");
        key.setStartIndex(offset);
        key.setStopIndex(offset);
        List<Token> tokenList = tokens.getTokens();
        Token tokenUnderCursor = null;
        int i = Collections.binarySearch(tokenList, key, cmp);
        if (i >= 0) tokenUnderCursor = tokenList.get(i);
        return tokenUnderCursor;
    }

    /*
    [77] = {org.antlr.v4.runtime.CommonToken@16710}"[@77,263:268='import',<25>,9:0]"
    [78] = {org.antlr.v4.runtime.CommonToken@16709}"[@78,270:273='java',<100>,9:7]"
     */
    public static Token getSkippedTokenUnderCursor(CommonTokenStream tokens, int offset) {
        if (offset < 0 || offset >= tokens.getTokenSource().getInputStream().size()) return null;
        Token prevToken = null;
        Token tokenUnderCursor = null;
        for (Token t : tokens.getTokens()) {
            int begin = t.getStartIndex();
            int end = t.getStopIndex();
            if ((prevToken == null || offset > prevToken.getStopIndex()) && offset < begin) {
                // found in between
                TokenSource tokenSource = tokens.getTokenSource();
                CharStream inputStream = null;
                if (tokenSource != null) {
                    inputStream = tokenSource.getInputStream();
                }
                tokenUnderCursor = new org.antlr.v4.runtime.CommonToken(
                        new Pair<>(tokenSource, inputStream),
                        Token.INVALID_TYPE,
                        -1,
                        prevToken != null ? prevToken.getStopIndex() + 1 : 0,
                        begin - 1
                );
                break;
            }
            if (offset >= begin && offset <= end) {
                tokenUnderCursor = t;
                break;
            }
            prevToken = t;
        }
        return tokenUnderCursor;
    }

    public static CommonTokenStream tokenizeANTLRGrammar(String text) {
        CodePointCharStream input = CharStreams.fromString(text);
        ANTLRv4Lexer lexer = new ANTLRv4Lexer(input);
        CommonTokenStream tokens = new TokenStreamSubset(lexer);
        tokens.fill();
        return tokens;
    }

    public static ParseTree getParseTreeNodeWithToken(ParseTree tree, Token token) {
        if (tree == null || token == null) {
            return null;
        }

        Collection<ParseTree> tokenNodes = Trees.findAllTokenNodes(tree, token.getType());
        for (ParseTree t : tokenNodes) {
            TerminalNode tnode = (TerminalNode) t;
            if (tnode.getPayload() == token) {
                return tnode;
            }
        }
        return null;
    }

    /**
     * True when the grammar AST contains embedded actions or semantic predicates.
     * The live Preview interpreter does not execute these (#523 / #732).
     */
    public static boolean grammarHasActionsOrPredicates(@Nullable Grammar g) {
        if (g == null || g.ast == null) {
            return false;
        }
        List<? extends GrammarAST> actions = g.ast.getNodesWithType(ANTLRParser.ACTION);
        List<? extends GrammarAST> preds = g.ast.getNodesWithType(ANTLRParser.SEMPRED);
        return (actions != null && !actions.isEmpty()) || (preds != null && !preds.isEmpty());
    }

    public static ParsingResult parseANTLRGrammar(String text) {
        CodePointCharStream input = CharStreams.fromString(text);
        ANTLRv4Lexer lexer = new ANTLRv4Lexer(input);
        CommonTokenStream tokens = new TokenStreamSubset(lexer);
        ANTLRv4Parser parser = new ANTLRv4Parser(tokens);

        SyntaxErrorListener listener = new SyntaxErrorListener();
        parser.removeErrorListeners();
        parser.addErrorListener(listener);
        lexer.removeErrorListeners();
        lexer.addErrorListener(listener);

        ParseTree t = parser.grammarSpec();
        return new ParsingResult(parser, t, listener);
    }

    public static ParsingResult parseText(Grammar g,
                                          LexerGrammar lg,
                                          String startRuleName,
                                          final VirtualFile grammarFile,
                                          String inputText,
                                          Project project) {
        if (lg == null || lg == BAD_LEXER_GRAMMAR) {
            LOG.info("parseText can't parse: missing lexer Grammar object for " +
                    (grammarFile != null ? grammarFile.getName() : "<unknown file>"));
            return null;
        }
        if (g == BAD_PARSER_GRAMMAR) {
            return null;
        }

        ANTLRv4GrammarProperties grammarProperties = getGrammarProperties(project, grammarFile);
        CaseChangingStrategy strategy = grammarProperties != null
                ? grammarProperties.getCaseChangingStrategy()
                : CaseChangingStrategy.LEAVE_AS_IS;
        String sourceName = grammarFile != null ? grammarFile.getPath() : "<input>";
        CharStream input = strategy.applyTo(CharStreams.fromString(inputText, sourceName));
        // #163 family: SafeLexerInterpreter avoids EmptyStackException on mismatched popMode
        LexerInterpreter lexEngine = SafeLexerInterpreter.create(lg, input);
        SyntaxErrorListener syntaxErrorListener = new SyntaxErrorListener();
        lexEngine.removeErrorListeners();
        lexEngine.addErrorListener(syntaxErrorListener);
        CommonTokenStream tokens = new TokenStreamSubset(lexEngine);

        try {
            // Pure lexer grammar: show a synthetic Tokens tree (no parser)
            if (g == null) {
                return tokenizeOnly(syntaxErrorListener, tokens);
            }
            return parseText(g, lg, startRuleName, syntaxErrorListener, tokens, 0);
        } catch (Throwable t) {
            // Last-resort: never let preview lexer/parser crashes escape as plugin errors
            LOG.warn("Preview parse failed for " + sourceName, t);
            syntaxErrorListener.syntaxError(
                    null, null, 1, 0,
                    "Preview aborted: " + t.getClass().getSimpleName()
                            + (t.getMessage() != null ? (": " + t.getMessage()) : ""),
                    null
            );
            return new ParsingResult(null, null, syntaxErrorListener, tokens);
        }
    }

    /**
     * Build a root {@code Tokens} node whose children are the lexed terminals (excluding EOF).
     */
    private static ParsingResult tokenizeOnly(SyntaxErrorListener syntaxErrorListener,
                                              CommonTokenStream tokens) {
        tokens.fill();
        ParserRuleContext root = new ParserRuleContext();
        Token first = null;
        Token last = null;
        for (Token t : tokens.getTokens()) {
            if (t.getType() == Token.EOF) {
                continue;
            }
            root.addChild(new org.antlr.v4.runtime.tree.TerminalNodeImpl(t));
            if (first == null) {
                first = t;
            }
            last = t;
        }
        if (first != null) {
            root.start = first;
            root.stop = last != null ? last : first;
        }
        return new ParsingResult(null, root, syntaxErrorListener, tokens);
    }

    private static ParsingResult parseText(Grammar g,
                                           LexerGrammar lg,
                                           String startRuleName,
                                           SyntaxErrorListener syntaxErrorListener,
                                           TokenStream tokens,
                                           int startIndex) {
        String grammarFileName = g.fileName;
        if (!new File(grammarFileName).exists()) {
            LOG.info("parseText grammar doesn't exist " + grammarFileName);
            return null;
        }

        if (g == BAD_PARSER_GRAMMAR || lg == BAD_LEXER_GRAMMAR) {
            return null;
        }

        tokens.seek(startIndex);

        PreviewParser parser = new PreviewParser(g, tokens);
        parser.getInterpreter().setPredictionMode(PredictionMode.LL_EXACT_AMBIG_DETECTION);
        parser.setProfile(true);

        parser.removeErrorListeners();
        parser.addErrorListener(syntaxErrorListener);

        Rule start = g.getRule(startRuleName);
        if (start == null) {
            return null; // can't find start rule
        }
        ParseTree t = parser.parse(start.index);

        if (t != null) {
            return new ParsingResult(parser, t, syntaxErrorListener, tokens);
        }
        return null;
    }

    public static Tool createANTLRToolForLoadingGrammars(ANTLRv4GrammarProperties grammarProperties,
                                                         Project project,
                                                         VirtualFile grammarFile) {
        Tool antlr = new Tool();
        antlr.errMgr = new PluginIgnoreMissingTokensFileErrorManager(antlr);
        antlr.errMgr.setFormat("antlr");
        LoadGrammarsToolListener listener = new LoadGrammarsToolListener(antlr);
        antlr.removeListeners();
        antlr.addListener(listener);
        if (grammarProperties != null) {
            String defaultLib = ".";
            if (grammarFile != null && grammarFile.getParent() != null) {
                defaultLib = grammarFile.getParent().getPath();
            }
            // Resolve macros / relative paths the same way codegen does
            if (project != null) {
                antlr.libDirectory = grammarProperties.resolveLibDir(project, defaultLib);
            } else {
                String lib = grammarProperties.getLibDir();
                antlr.libDirectory = (lib == null || lib.isEmpty()) ? defaultLib : lib;
            }
        }
        return antlr;
    }

    /**
     * Get lexer and parser grammars
     */
    public static Grammar[] loadGrammars(VirtualFile grammarFile, Project project) {
        if (project.isDisposed()) {
            return null;
        }
        LOG.info("loadGrammars " + grammarFile.getPath() + " " + project.getName());
        Tool antlr = createANTLRToolForLoadingGrammars(getGrammarProperties(project, grammarFile), project, grammarFile);
        LoadGrammarsToolListener listener = (LoadGrammarsToolListener) antlr.getListeners().get(0);

        Grammar g = loadGrammar(grammarFile, antlr);
        if (g == null) {
            reportBadGrammar(grammarFile, project);
            return null;
        }

        // see if a lexer is hanging around somewhere; don't want implicit token defs to make us bail
        LexerGrammar lg = null;
        if (g.getType() == ANTLRParser.PARSER) {
            lg = loadLexerGrammarFor(g, project);
            if (lg != null) {
                try {
                    g.importVocab(lg);
                } catch (Throwable t) {
                    // #177: broken/mismatched .tokens can throw inside Grammar.defineStringLiteral
                    LOG.warn("importVocab failed for " + grammarFile.getPath(), t);
                    ConsoleUtils.consolePrint(project,
                            "Failed to import lexer vocab for " + grammarFile.getName() + ": " + t.getMessage() + "\n",
                            ConsoleViewContentType.ERROR_OUTPUT);
                    return null;
                }
            } else {
                lg = BAD_LEXER_GRAMMAR;
            }
        }

        try {
            antlr.process(g, false);
        } catch (Throwable t) {
            // ANTLR tool can NPE / AIOOBE / EmptyStack on invalid grammars (#204/#215/#247)
            LOG.warn("ANTLR tool process failed for " + grammarFile.getPath(), t);
            ConsoleUtils.consolePrint(project,
                    "ANTLR failed to process " + grammarFile.getName() + ": " + t.getMessage() + "\n",
                    ConsoleViewContentType.ERROR_OUTPUT);
            return null;
        }
        if (!listener.grammarErrorMessages.isEmpty()) {
            String msg = Utils.join(listener.grammarErrorMessages.iterator(), "\n");
            ConsoleUtils.consolePrint(project, msg + "\n", ConsoleViewContentType.ERROR_OUTPUT);
            return null; // upon error, bail
        }

        // Examine's Grammar AST constructed by v3 for a v4 grammar.
        // Use ANTLR v3's ANTLRParser not ANTLRv4Parser from this plugin
        switch (g.getType()) {
            case ANTLRParser.PARSER -> {
                LOG.info("loadGrammars parser " + g.name);
                return new Grammar[]{lg, g};
            }
            case ANTLRParser.LEXER -> {
                LOG.info("loadGrammars lexer " + g.name);
                lg = (LexerGrammar) g;
                return new Grammar[]{lg, null};
            }
            case ANTLRParser.COMBINED -> {
                lg = g.getImplicitLexer();
                if (lg == null) {
                    lg = BAD_LEXER_GRAMMAR;
                }
                LOG.info("loadGrammars combined: " + lg.name + ", " + g.name);
                return new Grammar[]{lg, g};
            }
        }
        LOG.info("loadGrammars invalid grammar type " + g.getTypeString() + " for " + g.name);
        return null;
    }

    private static void reportBadGrammar(VirtualFile grammarFile, Project project) {
        String msg = "Empty or bad grammar in file " + grammarFile.getName();
        ConsoleUtils.consolePrint(project, msg, ConsoleViewContentType.ERROR_OUTPUT);
    }


    @Nullable
    private static Grammar loadGrammar(VirtualFile grammarFile, Tool antlr) {
        // basically here I am mimicking the loadGrammar() method from Tool
        // so that I can check for an empty AST coming back.
        GrammarRootAST grammarRootAST = parseGrammar(antlr, grammarFile);
        if (grammarRootAST == null) {
            return null;
        }

        // Create a grammar from the AST so we can figure out what type it is
        Grammar g = antlr.createGrammar(grammarRootAST);
        g.fileName = grammarFile.getPath();

        return g;
    }

    public static GrammarRootAST parseGrammar(Tool antlr, VirtualFile grammarFile) {
        AtomicReference<GrammarRootAST> atomicReference = new AtomicReference<>(null);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        AtomicReference<Document> documentAtomicReference = new AtomicReference<>();
        ApplicationManager.getApplication().runReadAction(() -> {
            try {
                Document document = FileDocumentManager.getInstance().getDocument(grammarFile);
                documentAtomicReference.set(document);
            } catch (Exception e) {
                documentAtomicReference.set(null);
            }
        });

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                Document document = documentAtomicReference.get();
                String grammarText = document != null
                        ? document.getText()
                        : new String(grammarFile.contentsToByteArray());
                ANTLRStringStream in = new ANTLRStringStream(grammarText);
                in.name = grammarFile.getPath();
                atomicReference.set(antlr.parse(grammarFile.getPath(), in));
            } catch (Exception e) {
                antlr.errMgr.toolError(ErrorType.CANNOT_OPEN_FILE, e, grammarFile);
            } finally {
                countDownLatch.countDown();
            }
        });
        try {
            // Bounded wait: unbounded await can freeze IDE/tests if parse stalls (#259-related)
            if (!countDownLatch.await(5L, TimeUnit.MINUTES)) {
                LOG.warn("Timed out parsing grammar " + grammarFile.getPath());
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        return atomicReference.get();
    }

    /**
     * Try to load a LexerGrammar given a parser grammar g. Derive lexer name
     * as:
     * V given tokenVocab=V in grammar or
     * XLexer given XParser.g4 filename or
     * XLexer given grammar name X
     */
    public static LexerGrammar loadLexerGrammarFor(Grammar g, Project project) {
        VirtualFile parserGrammarFile = LocalFileSystem.getInstance().findFileByIoFile(new File(g.fileName));
        Tool antlr = createANTLRToolForLoadingGrammars(
                getGrammarProperties(project, g.fileName), project, parserGrammarFile);
        LoadGrammarsToolListener listener = (LoadGrammarsToolListener) antlr.getListeners().get(0);
        LexerGrammar lg = null;
        VirtualFile lexerGrammarFile = null;

        String vocabName = g.getOptionString("tokenVocab");
        if (vocabName != null) {
            // Same -lib / relative-path rules as PSI TokenVocabResolver
            lexerGrammarFile = TokenVocabResolver.findGrammarFile(vocabName, parserGrammarFile, project);
        }
        if (lexerGrammarFile == null) {
            File companion = new File(getLexerNameFromParserFileName(g.fileName));
            lexerGrammarFile = LocalFileSystem.getInstance().findFileByIoFile(companion);
            if (lexerGrammarFile == null || !lexerGrammarFile.exists()) {
                String base = companion.getName();
                if (base.endsWith(".g4")) {
                    base = base.substring(0, base.length() - 3);
                }
                lexerGrammarFile = TokenVocabResolver.findGrammarFile(base, parserGrammarFile, project);
            }
        }

        if (lexerGrammarFile != null && lexerGrammarFile.exists()) {
            try {
                Grammar grammar = loadGrammar(lexerGrammarFile, antlr);
                if(grammar instanceof LexerGrammar lexerGrammar){
                    lg=lexerGrammar;
                }
                if (lg != null) {
                    try {
                        antlr.process(lg, false);
                    } catch (Throwable t) {
                        LOG.warn("ANTLR failed processing lexer " + lexerGrammarFile.getPath(), t);
                        lg = null;
                    }
                } else {
                    reportBadGrammar(lexerGrammarFile, project);
                }
            } catch (ClassCastException cce) {
                LOG.warn("File " + lexerGrammarFile + " isn't a lexer grammar", cce);
            } catch (Exception e) {
                String msg = null;
                if (!listener.grammarErrorMessages.isEmpty()) {
                    msg = ": " + listener.grammarErrorMessages;
                }
                LOG.warn("File " + lexerGrammarFile + " couldn't be parsed as a lexer grammar" + msg, e);
            }
            if (!listener.grammarErrorMessages.isEmpty()) {
                lg = null;
                String msg = Utils.join(listener.grammarErrorMessages.iterator(), "\n");
                ConsoleUtils.consolePrint(project, msg + "\n", ConsoleViewContentType.ERROR_OUTPUT);
            }
        }
        return lg;
    }

    @NotNull
    public static String getLexerNameFromParserFileName(String parserFileName) {
        File f = new File(parserFileName);
        String name = f.getName();
        // Match on filename only so path segments like ".../Parser.g4/..." cannot false-match
        int i = name.indexOf("Parser.g4");
        File parentDir = f.getParentFile();
        if (i >= 0) { // is filename XParser.g4?
            return new File(parentDir, name.substring(0, i) + "Lexer.g4").getAbsolutePath();
        }
        int dot = name.lastIndexOf(".g4");
        String parserName = dot >= 0 ? name.substring(0, dot) : name;
        return new File(parentDir, parserName + "Lexer.g4").getAbsolutePath();
    }

    public static Tree findOverriddenDecisionRoot(Tree ctx) {
        return Trees.findNodeSuchThat(
                ctx,
                t -> t instanceof PreviewInterpreterRuleContext && ((PreviewInterpreterRuleContext) t).isDecisionOverrideRoot()
        );
    }

    public static List<TerminalNode> getAllLeaves(Tree t) {
        List<TerminalNode> leaves = new ArrayList<>();
        _getAllLeaves(t, leaves);
        return leaves;
    }

    private static void _getAllLeaves(Tree t, List<TerminalNode> leaves) {
        int n = t.getChildCount();
        if (t instanceof TerminalNode) {
            Token tok = ((TerminalNode) t).getSymbol();
            if (tok.getType() != Token.INVALID_TYPE) {
                leaves.add((TerminalNode) t);
            }
            return;
        }
        for (int i = 0; i < n; i++) {
            _getAllLeaves(t.getChild(i), leaves);
        }
    }

    /**
     * Get ancestors where the first element of the list is the parent of t
     */
    public static List<? extends Tree> getAncestors(Tree t) {
        if (t.getParent() == null) return Collections.emptyList();
        List<Tree> ancestors = new ArrayList<>();
        t = t.getParent();
        while (t != null) {
            ancestors.add(t); // insert at start
            t = t.getParent();
        }
        return ancestors;
    }

}
