package com.antlr.plugin.preview;

import org.antlr.v4.gui.TreeTextProvider;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.Tree;
import org.antlr.v4.tool.Grammar;

/**
 * Labels for lexer-only preview trees: root is {@code Tokens}, leaves use token display names.
 */
public class LexerTokenTreeTextProvider implements TreeTextProvider {
    private final Grammar lexerGrammar;

    public LexerTokenTreeTextProvider(Grammar lexerGrammar) {
        this.lexerGrammar = lexerGrammar;
    }

    @Override
    public String getText(Tree node) {
        if (node instanceof TerminalNode) {
            return labelForToken(((TerminalNode) node).getSymbol());
        }
        if (node instanceof RuleNode) {
            return "Tokens";
        }
        return String.valueOf(node);
    }

    private String labelForToken(Token token) {
        if (token == null) {
            return "";
        }
        String text = token.getText();
        if (text != null && text.equals("<EOF>")) {
            return text;
        }
        String display = lexerGrammar != null
                ? lexerGrammar.getTokenDisplayName(token.getType())
                : String.valueOf(token.getType());
        if (text == null || text.isEmpty() || text.equals(display)) {
            return display;
        }
        if (display != null && text.equalsIgnoreCase(display)) {
            return display;
        }
        return display + ":" + text;
    }
}
