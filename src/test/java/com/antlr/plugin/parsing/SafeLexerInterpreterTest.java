package com.antlr.plugin.parsing;

import junit.framework.TestCase;
import org.antlr.intellij.adaptor.parser.SyntaxErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.LexerInterpreter;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.tool.LexerGrammar;

import java.util.EmptyStackException;

/**
 * Regression for fork issues #163/#164/#181–#184/#209–#211:
 * mismatched {@code -> popMode} must not crash the IDE via {@link EmptyStackException}.
 */
public class SafeLexerInterpreterTest extends TestCase {

    public void testPopModeWithEmptyStackDoesNotThrow() throws Exception {
        LexerGrammar lg = loadLexerGrammar(
                "lexer grammar BadModes;\n" +
                        "X : 'x' -> popMode ;\n" +
                        "WS : [ \\t\\r\\n]+ -> skip ;\n"
        );

        SafeLexerInterpreter lexer = SafeLexerInterpreter.create(lg, CharStreams.fromString("x"));
        SyntaxErrorListener errors = new SyntaxErrorListener();
        lexer.removeErrorListeners();
        lexer.addErrorListener(errors);

        CommonTokenStream tokens = new CommonTokenStream(lexer);
        tokens.fill();

        assertFalse("should produce at least EOF", tokens.getTokens().isEmpty());
        assertEquals(Token.EOF, tokens.getTokens().get(tokens.getTokens().size() - 1).getType());
        assertFalse("mismatched popMode should be reported as a syntax error",
                errors.getSyntaxErrors().isEmpty());
    }

    public void testStockLexerInterpreterThrowsOnEmptyPopMode() throws Exception {
        LexerGrammar lg = loadLexerGrammar(
                "lexer grammar BadModes;\n" +
                        "X : 'x' -> popMode ;\n" +
                        "WS : [ \\t\\r\\n]+ -> skip ;\n"
        );

        LexerInterpreter lexer = lg.createLexerInterpreter(CharStreams.fromString("x"));
        try {
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            tokens.fill();
            fail("expected EmptyStackException from stock LexerInterpreter");
        } catch (EmptyStackException expected) {
            // expected — documents why SafeLexerInterpreter exists
        }
    }

    private static LexerGrammar loadLexerGrammar(String text) throws Exception {
        // Constructor parses + builds ATN for a self-contained lexer grammar
        return new LexerGrammar(text);
    }
}
