package com.antlr.plugin.parsing;

import org.antlr.intellij.adaptor.parser.SyntaxErrorListener;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.jetbrains.annotations.Nullable;

public class ParsingResult {
    /** Null for lexer-only tokenization results. */
    @Nullable
    public Parser parser;
    public ParseTree tree;
    public SyntaxErrorListener syntaxErrorListener;
    /** Token stream used for this result (parser input or lexer-only fill). */
    @Nullable
    public TokenStream tokens;

    public ParsingResult(Parser parser, ParseTree tree, SyntaxErrorListener syntaxErrorListener) {
        this(parser, tree, syntaxErrorListener, parser != null ? parser.getInputStream() : null);
    }

    public ParsingResult(@Nullable Parser parser,
                         ParseTree tree,
                         SyntaxErrorListener syntaxErrorListener,
                         @Nullable TokenStream tokens) {
        this.parser = parser;
        this.tree = tree;
        this.syntaxErrorListener = syntaxErrorListener;
        this.tokens = tokens;
    }

    @Nullable
    public CommonTokenStream getTokenStream() {
        if (tokens instanceof CommonTokenStream) {
            return (CommonTokenStream) tokens;
        }
        if (parser != null && parser.getInputStream() instanceof CommonTokenStream) {
            return (CommonTokenStream) parser.getInputStream();
        }
        return null;
    }
}
