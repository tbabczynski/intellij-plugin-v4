package com.antlr.plugin.psi;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.impl.PsiFileFactoryImpl;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiElementFilter;
import com.intellij.psi.util.PsiTreeUtil;
import com.antlr.plugin.ANTLRv4FileRoot;
import com.antlr.plugin.ANTLRv4Language;
import com.antlr.plugin.ANTLRv4TokenTypes;
import com.antlr.plugin.parser.ANTLRv4Parser;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

@SuppressWarnings("SimplifiableIfStatement")
public class MyPsiUtils {
    @Nullable
    public static PsiElement findFirstChildOfType(final PsiElement parent, IElementType type) {
        return findFirstChildOfType(parent, TokenSet.create(type));
    }

    /**
     * traverses the psi tree depth-first, returning the first it finds with the given types
     * @param parent the element whose children will be searched
     * @param types the types to search for
     * @return the first child, or null;
     */
    @Nullable
    public static PsiElement findFirstChildOfType(final PsiElement parent, final TokenSet types) {
        Iterator<PsiElement> iterator = findChildrenOfType(parent, types).iterator();
        if (iterator.hasNext()) return iterator.next();
        return null;
    }

    public static Iterable<PsiElement> findChildrenOfType(final PsiElement parent, IElementType type) {
        return findChildrenOfType(parent, TokenSet.create(type));
    }

    /**
     * Like PsiTreeUtil.findChildrenOfType, except no collection is created and it doesnt use recursion.
     * @param parent the element whose children will be searched
     * @param types the types to search for
     * @return an iterable that will traverse the psi tree depth-first, including only the elements
     * whose type is contained in the provided tokenset.
     */
    public static Iterable<PsiElement> findChildrenOfType(final PsiElement parent, final TokenSet types) {
        PsiElement[] psiElements = PsiTreeUtil.collectElements(parent, input -> {
            ASTNode node = input.getNode();
            if (node == null) return false;
            return types.contains(node.getElementType());
        });
        return Arrays.asList(psiElements);
    }

    /**
     * Finds the first {@link RuleSpecNode} or {@link ModeSpecNode} matching the {@code ruleName} defined in
     * the given {@code grammar}.
     *
     * Rule specs can be either children of the {@link RulesNode}, or under one of the {@code mode}s defined in
     * the grammar. This means we have to walk the whole grammar to find matching candidates.
     */
    public static PsiElement findSpecNode(GrammarSpecNode grammar, final String ruleName) {
        if (grammar == null || ruleName == null) {
            return null;
        }
        PsiElementFilter definitionFilter = element1 -> {
            if (!(element1 instanceof RuleSpecNode)) {
                return false;
            }

            GrammarElementRefNode id = ((RuleSpecNode) element1).getNameIdentifier();
            return id != null && id.getText().equals(ruleName);
        };

        PsiElement[] ruleSpec = PsiTreeUtil.collectElements(grammar, definitionFilter);
        if (ruleSpec.length > 0) {
            return ruleSpec[0];
        }
        return null;
    }

    public static PsiElement createLeafFromText(Project project, PsiElement context,
                                                String text, IElementType type) {
        PsiFileFactoryImpl factory = (PsiFileFactoryImpl) PsiFileFactory.getInstance(project);
        PsiElement el = factory.createElementFromText(text,
                ANTLRv4Language.INSTANCE,
                type,
                context);
        if (el == null) {
            return null;
        }
        return PsiTreeUtil.getDeepestFirst(el); // forces parsing of file!!
        // start rule depends on root passed in
    }

    public static void replacePsiFileFromText(final Project project, final PsiFile psiFile, String text) {
        final PsiFile newPsiFile = createFile(project, text);
        WriteCommandAction setTextAction = new WriteCommandAction(project) {
            @Override
            protected void run(final Result result) {
                psiFile.deleteChildRange(psiFile.getFirstChild(), psiFile.getLastChild());
                psiFile.addRange(newPsiFile.getFirstChild(), newPsiFile.getLastChild());
            }
        };
        setTextAction.execute();
    }

    public static PsiFile createFile(Project project, String text) {
        String fileName = "a.g4"; // random name but must be .g4
        PsiFileFactoryImpl factory = (PsiFileFactoryImpl) PsiFileFactory.getInstance(project);
        return factory.createFileFromText(fileName, ANTLRv4Language.INSTANCE,
                text, false, false);
    }

    public static PsiElement[] collectAtActions(PsiElement root, final String tokenText) {
        return PsiTreeUtil.collectElements(root, element -> {
            PsiElement p = element.getContext();
            if (p != null) p = p.getContext();
            return p instanceof AtAction &&
                    element instanceof ParserRuleRefNode &&
                    element.getText().equals(tokenText);
        });
    }

    /** Search all internal and leaf nodes looking for token or internal node
     *  with specific text.
     *  This saves having to create lots of java classes just to identify psi nodes.
     */
    public static PsiElement[] collectNodesWithName(PsiElement root, final String tokenText) {
        return PsiTreeUtil.collectElements(root, element -> {
            String tokenTypeName = element.getNode().getElementType().toString();
            return tokenTypeName.equals(tokenText);
        });
    }

    public static PsiElement[] collectNodesWithText(PsiElement root, final String text) {
        return PsiTreeUtil.collectElements(root, element -> element.getText().equals(text));
    }

    public static PsiElement[] collectChildrenOfType(PsiElement root, final IElementType tokenType) {
        List<PsiElement> elems = new ArrayList<>();
        for (PsiElement child : root.getChildren()) {
            if (child.getNode().getElementType() == tokenType) {
                elems.add(child);
            }
        }
        return elems.toArray(new PsiElement[0]);
    }

    public static PsiElement findChildOfType(PsiElement root, final IElementType tokenType) {
        for (PsiElement child : root.getChildren()) {
            if (child.getNode().getElementType() == tokenType) {
                return child;
            }
        }
        return null;
    }

    public static PsiElement[] collectChildrenWithText(PsiElement root, final String text) {
        List<PsiElement> elems = new ArrayList<>();
        for (PsiElement child : root.getChildren()) {
            if (child.getText().equals(text)) {
                elems.add(child);
            }
        }
        return elems.toArray(new PsiElement[0]);
    }

    // Look for stuff like: options { tokenVocab=ANTLRv4Lexer; superClass=Foo; }
    public static String findTokenVocabIfAny(ANTLRv4FileRoot file) {
        String vocabName = null;
        PsiElement[] options = collectNodesWithName(file, "option");
        for (PsiElement o : options) {
            PsiElement[] tokenVocab = collectChildrenWithText(o, "tokenVocab");
            if (tokenVocab.length > 0) {
                PsiElement optionNode = tokenVocab[0].getParent();// tokenVocab[0] is id node
                PsiElement[] ids = collectChildrenOfType(optionNode, ANTLRv4TokenTypes.RULE_ELEMENT_TYPES.get(ANTLRv4Parser.RULE_optionValue));
                if (ids.length > 0) {
                    // Strip quotes for tokenVocab='LexerName' form
                    vocabName = stripQuotes(ids[0].getText());
                }
            }
        }
        return vocabName;
    }

    public static String stripQuotes(String text) {
        if (text == null || text.length() < 2) {
            return text;
        }
        char first = text.charAt(0);
        char last = text.charAt(text.length() - 1);
        if ((first == '\'' && last == '\'') || (first == '"' && last == '"')) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

}
