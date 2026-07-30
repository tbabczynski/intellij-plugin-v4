package com.antlr.plugin.parsing;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.LexerInterpreter;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Vocabulary;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.tool.LexerGrammar;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * Preview lexer that tolerates mismatched {@code -> popMode} (empty mode stack).
 * Stock ANTLR throws {@link java.util.EmptyStackException}, which surfaces as an IDE plugin
 * error report (#163/#164/#181–#184/#209–#211).
 */
public final class SafeLexerInterpreter extends LexerInterpreter {

    private SafeLexerInterpreter(String grammarFileName,
                                 Vocabulary vocabulary,
                                 Collection<String> ruleNames,
                                 Collection<String> channelNames,
                                 Collection<String> modeNames,
                                 ATN atn,
                                 CharStream input) {
        super(grammarFileName, vocabulary, ruleNames, channelNames, modeNames, atn, input);
    }

    /**
     * Build from the same ATN/vocabulary {@link LexerGrammar#createLexerInterpreter} would use,
     * but with a safe {@link #popMode()}.
     */
    public static SafeLexerInterpreter create(LexerGrammar lg, CharStream input) {
        LexerInterpreter prototype = lg.createLexerInterpreter(input);
        return new SafeLexerInterpreter(
                prototype.getGrammarFileName(),
                prototype.getVocabulary(),
                asList(prototype.getRuleNames()),
                asList(prototype.getChannelNames()),
                asList(prototype.getModeNames()),
                prototype.getATN(),
                input
        );
    }

    private static Collection<String> asList(String[] names) {
        if (names == null || names.length == 0) {
            return Collections.emptyList();
        }
        return Arrays.asList(names);
    }

    @Override
    public int popMode() {
        if (_modeStack.isEmpty()) {
            // Stay in the current mode and report a syntax error instead of crashing the IDE
            getErrorListenerDispatch().syntaxError(
                    this,
                    null,
                    getLine(),
                    getCharPositionInLine(),
                    "popMode called with an empty mode stack (mismatched pushMode/popMode)",
                    null
            );
            return _mode;
        }
        return super.popMode();
    }

    @Override
    public void recover(RecognitionException re) {
        try {
            super.recover(re);
        } catch (RuntimeException ignored) {
            // Broken lexer ATNs can throw again during recovery; keep preview alive
            if (_input != null && _input.LA(1) != CharStream.EOF) {
                _input.consume();
            }
        }
    }
}
