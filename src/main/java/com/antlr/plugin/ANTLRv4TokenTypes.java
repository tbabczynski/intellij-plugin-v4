package com.antlr.plugin;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.antlr.intellij.adaptor.lexer.PSIElementTypeFactory;
import org.antlr.intellij.adaptor.lexer.RuleIElementType;
import org.antlr.intellij.adaptor.lexer.TokenIElementType;
import com.antlr.plugin.parser.ANTLRv4Lexer;
import com.antlr.plugin.parser.ANTLRv4Parser;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class ANTLRv4TokenTypes {
    public static IElementType BAD_TOKEN_TYPE = new IElementType("BAD_TOKEN", ANTLRv4Language.INSTANCE);

    static {
        // Ensure element types exist before any BraceMatcher / highlighter reads them (#207/#253)
        PSIElementTypeFactory.defineLanguageIElementTypes(
                ANTLRv4Language.INSTANCE,
                ANTLRv4Lexer.VOCABULARY,
                ANTLRv4Parser.ruleNames,
                true
        );
    }

    public static final List<TokenIElementType> TOKEN_ELEMENT_TYPES = tokenTypes();
    public static final List<RuleIElementType> RULE_ELEMENT_TYPES = ruleTypes();

    private static List<TokenIElementType> tokenTypes() {
        List<TokenIElementType> types = PSIElementTypeFactory.getTokenIElementTypes(ANTLRv4Language.INSTANCE);
        return types != null ? types : Collections.emptyList();
    }

    private static List<RuleIElementType> ruleTypes() {
        List<RuleIElementType> types = PSIElementTypeFactory.getRuleIElementTypes(ANTLRv4Language.INSTANCE);
        return types != null ? types : Collections.emptyList();
    }

    public static final TokenSet COMMENTS =
            PSIElementTypeFactory.createTokenSet(
                    ANTLRv4Language.INSTANCE,
                    ANTLRv4Lexer.DOC_COMMENT,
                    ANTLRv4Lexer.BLOCK_COMMENT,
                    ANTLRv4Lexer.LINE_COMMENT);

    public static final TokenSet WHITESPACES =
            PSIElementTypeFactory.createTokenSet(
                    ANTLRv4Language.INSTANCE,
                    ANTLRv4Lexer.WS);

    public static final TokenSet KEYWORDS =
            PSIElementTypeFactory.createTokenSet(
                    ANTLRv4Language.INSTANCE,
                    ANTLRv4Lexer.LEXER, ANTLRv4Lexer.PROTECTED, ANTLRv4Lexer.IMPORT, ANTLRv4Lexer.CATCH,
                    ANTLRv4Lexer.PRIVATE, ANTLRv4Lexer.FRAGMENT, ANTLRv4Lexer.PUBLIC, ANTLRv4Lexer.MODE,
                    ANTLRv4Lexer.FINALLY, ANTLRv4Lexer.RETURNS, ANTLRv4Lexer.THROWS, ANTLRv4Lexer.GRAMMAR,
                    ANTLRv4Lexer.LOCALS, ANTLRv4Lexer.PARSER,
                    ANTLRv4Lexer.OPTIONS, ANTLRv4Lexer.TOKENS, ANTLRv4Lexer.CHANNELS);

    public static RuleIElementType getRuleElementType(@MagicConstant(valuesFromClass = ANTLRv4Parser.class) int ruleIndex) {
        return RULE_ELEMENT_TYPES.get(ruleIndex);
    }

    @Nullable
    public static TokenIElementType getTokenElementType(@MagicConstant(valuesFromClass = ANTLRv4Lexer.class) int ruleIndex) {
        if (TOKEN_ELEMENT_TYPES.isEmpty() || ruleIndex < 0 || ruleIndex >= TOKEN_ELEMENT_TYPES.size()) {
            return null;
        }
        return TOKEN_ELEMENT_TYPES.get(ruleIndex);
    }
}
