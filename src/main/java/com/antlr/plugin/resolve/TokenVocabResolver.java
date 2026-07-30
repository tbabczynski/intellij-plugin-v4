package com.antlr.plugin.resolve;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.antlr.plugin.ANTLRv4FileRoot;
import com.antlr.plugin.configdialogs.ANTLRv4GrammarProperties;
import com.antlr.plugin.configdialogs.ANTLRv4ToolGrammarPropertiesStore;
import com.antlr.plugin.parser.ANTLRv4Parser;
import com.antlr.plugin.psi.*;
import org.jetbrains.annotations.Nullable;

import java.io.File;

import static com.antlr.plugin.ANTLRv4TokenTypes.RULE_ELEMENT_TYPES;


public class TokenVocabResolver {

    /**
     * If this reference is the value of a {@code tokenVocab} option, returns the corresponding
     * grammar file.
     */
    @Nullable
    public static PsiFile resolveTokenVocabFile(PsiElement reference) {
        PsiElement optionValue = PsiTreeUtil.findFirstParent(reference, TokenVocabResolver::isOptionValue);

        if (optionValue != null) {
            PsiElement option = optionValue.getParent();

            if (option != null) {
                PsiElement optionName = PsiTreeUtil.getDeepestFirst(option);

                if (optionName != null && "tokenVocab".equals(optionName.getText())) {
                    String text = MyPsiUtils.stripQuotes(reference.getText());
                    return findRelativeFile(text, reference.getContainingFile());
                }
            }
        }

        return null;
    }

    /**
     * Tries to find a declaration named {@code ruleName} in the {@code tokenVocab} file if it exists.
     */
    @Nullable
    public static PsiElement resolveInTokenVocab(GrammarElementRefNode reference, String ruleName) {
        PsiFile containingFile = reference.getContainingFile();
        if (!(containingFile instanceof ANTLRv4FileRoot)) {
            return null;
        }
        String tokenVocab = MyPsiUtils.findTokenVocabIfAny((ANTLRv4FileRoot) containingFile);

        if (tokenVocab != null) {
            PsiFile tokenVocabFile = findRelativeFile(tokenVocab, containingFile);

            if (tokenVocabFile != null) {
                GrammarSpecNode lexerGrammar = PsiTreeUtil.findChildOfType(tokenVocabFile, GrammarSpecNode.class);
                PsiElement node = MyPsiUtils.findSpecNode(lexerGrammar, ruleName);

                if (node instanceof LexerRuleSpecNode) {
                    // fragments are not visible to the parser
                    if (!((LexerRuleSpecNode) node).isFragment()) {
                        return node;
                    }
                }
                if (node instanceof TokenSpecNode) {
                    return node;
                }
            }
        }

        return null;
    }

    private static boolean isOptionValue(PsiElement el) {
        ASTNode node = el.getNode();
        return node != null && node.getElementType() == RULE_ELEMENT_TYPES.get(ANTLRv4Parser.RULE_optionValue);
    }

    /**
     * Looks for an ANTLR grammar file named {@code <baseName>}.g4 next to the given {@code sibling}
     * file, then in the configured {@code -lib} directory.
     */
    @Nullable
    public static PsiFile findRelativeFile(String baseName, PsiFile sibling) {
        if (sibling == null) {
            return null;
        }
        VirtualFile found = findGrammarFile(baseName, sibling.getVirtualFile(), sibling.getProject());
        return asGrammarFile(sibling.getProject(), found);
    }

    /**
     * Resolve {@code baseName} (with or without {@code .g4}) beside {@code siblingFile}, under a relative
     * subdirectory, or in the grammar's configured {@code -lib} directory.
     */
    @Nullable
    public static VirtualFile findGrammarFile(String baseName, @Nullable VirtualFile siblingFile, Project project) {
        if (baseName == null || baseName.isBlank()) {
            return null;
        }

        String fileName = baseName.endsWith(".g4") ? baseName : baseName + ".g4";
        String normalized = fileName.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String simpleName = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        String relativeDir = slash >= 0 ? normalized.substring(0, slash) : null;

        if (siblingFile != null) {
            VirtualFile parent = siblingFile.isDirectory() ? siblingFile : siblingFile.getParent();
            if (parent != null) {
                if (relativeDir != null) {
                    VirtualFile dir = parent.findFileByRelativePath(relativeDir);
                    if (dir != null && dir.isDirectory()) {
                        VirtualFile vf = dir.findChild(simpleName);
                        if (vf != null && vf.exists()) {
                            return vf;
                        }
                    }
                } else {
                    VirtualFile vf = parent.findChild(simpleName);
                    if (vf != null && vf.exists()) {
                        return vf;
                    }
                }
            }
        }

        if (project != null && siblingFile != null) {
            VirtualFile propsFile = siblingFile.isDirectory() ? siblingFile : siblingFile;
            ANTLRv4GrammarProperties props =
                    ANTLRv4ToolGrammarPropertiesStore.getGrammarProperties(project, propsFile);
            String defaultLib = siblingFile.getParent() != null
                    ? siblingFile.getParent().getPath()
                    : siblingFile.getPath();
            String libDir = props.resolveLibDir(project, defaultLib);
            if (libDir != null && !libDir.isBlank()) {
                File candidateFile = relativeDir != null
                        ? new File(new File(libDir, relativeDir), simpleName)
                        : new File(libDir, simpleName);
                VirtualFile vf = LocalFileSystem.getInstance().findFileByIoFile(candidateFile);
                if (vf != null && vf.exists()) {
                    return vf;
                }
            }
        }

        return null;
    }

    @Nullable
    private static PsiFile asGrammarFile(Project project, @Nullable VirtualFile vf) {
        if (project == null || vf == null || !vf.isValid()) {
            return null;
        }
        PsiFile psi = PsiManager.getInstance(project).findFile(vf);
        return psi instanceof ANTLRv4FileRoot ? psi : null;
    }
}
