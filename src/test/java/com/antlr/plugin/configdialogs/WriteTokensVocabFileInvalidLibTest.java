package com.antlr.plugin.configdialogs;

import com.antlr.plugin.TestUtils;
import com.antlr.plugin.parsing.ParsingUtils;
import com.antlr.plugin.parsing.RunANTLROnGrammarFile;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.antlr.v4.tool.LexerGrammar;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.antlr.plugin.configdialogs.ANTLRv4ToolGrammarPropertiesStore.getOrCreateGrammarProperties;

/**
 * Invalid Configure {@code -lib} must not write {@code .tokens} after Tool falls back to {@code "."}.
 */
public class WriteTokensVocabFileInvalidLibTest extends BasePlatformTestCase {

    public void test_shouldSkipTokensWriteWhenLibDirInvalid() throws Exception {
        PsiFile lexerPsi = myFixture.addFileToProject(
                "BadLibLexer.g4",
                "lexer grammar BadLibLexer;\nTOKEN1: 'TOKEN1';");
        VirtualFile lexerFile = lexerPsi.getVirtualFile();
        assertNotNull(lexerFile);

        ANTLRv4GrammarProperties props = getOrCreateGrammarProperties(getProject(), lexerFile.getPath());
        assertNotNull(props);
        props.libDir = "definitely-not-a-real-lib-dir-xyz";

        VirtualFile contentRoot = ProjectRootManager.getInstance(getProject())
                .getFileIndex()
                .getContentRootForFile(lexerFile);
        assertNotNull(contentRoot);
        File tokensFile = Path.of(
                props.resolveOutputDirName(getProject(), contentRoot, null),
                "BadLibLexer.tokens").toFile();
        Files.deleteIfExists(tokensFile.toPath());
        Files.deleteIfExists(Path.of("BadLibLexer.tokens"));

        LexerGrammar lg = (LexerGrammar) ParsingUtils.loadGrammars(lexerFile, getProject())[0];
        RunANTLROnGrammarFile.writeTokensVocabFile(getProject(), lexerFile, lg);

        assertFalse("must not write .tokens when -lib is invalid", tokensFile.exists());
        assertFalse("must not spill .tokens into process cwd", new File("BadLibLexer.tokens").exists());
    }

    @Override
    protected void tearDown() throws Exception {
        Files.deleteIfExists(Path.of("BadLibLexer.tokens"));
        TestUtils.tearDownIgnoringObjectNotDisposedException(super::tearDown);
    }
}
