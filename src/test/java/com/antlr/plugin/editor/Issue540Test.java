package com.antlr.plugin.editor;

import com.antlr.plugin.TestUtils;
import com.antlr.plugin.configdialogs.ANTLRv4ToolGrammarPropertiesStore;
import com.antlr.plugin.parsing.ParsingUtils;
import com.antlr.plugin.parsing.RunANTLROnGrammarFile;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.antlr.v4.tool.LexerGrammar;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Regression for issue 540: lexer {@code .tokens} are written under the configured output dir.
 */
public class Issue540Test extends BasePlatformTestCase {

    public void test_shouldOnlyCreateTokensWhenModified() throws Exception {
        PsiFile lexerPsi = myFixture.addFileToProject(
                "TestLexer.g4",
                "lexer grammar TestLexer;\nTOKEN1: 'TOKEN1';");
        VirtualFile lexerFile = lexerPsi.getVirtualFile();
        assertNotNull(lexerFile);

        File tokensFile = resolveTokensFile(lexerFile);
        Files.deleteIfExists(tokensFile.toPath());
        Files.deleteIfExists(Path.of("TestLexer.tokens"));

        LexerGrammar lg = (LexerGrammar) ParsingUtils.loadGrammars(lexerFile, getProject())[0];
        RunANTLROnGrammarFile.writeTokensVocabFile(getProject(), lexerFile, lg);

        assertTrue("tokens should be under output dir: " + tokensFile, tokensFile.isFile());
        assertFalse("tokens must not spill into process cwd", new File("TestLexer.tokens").exists());
        String first = Files.readString(tokensFile.toPath(), StandardCharsets.UTF_8);
        assertTrue(first.contains("TOKEN1"));

        long lastModified1 = tokensFile.lastModified();
        Thread.sleep(120);

        WriteAction.runAndWait(() -> VfsUtil.saveText(lexerFile,
                "lexer grammar TestLexer;\nTOKEN1: 'TOKEN1';\nTOKEN2: 'TOKEN2';"));
        LexerGrammar lg2 = (LexerGrammar) ParsingUtils.loadGrammars(lexerFile, getProject())[0];
        RunANTLROnGrammarFile.writeTokensVocabFile(getProject(), lexerFile, lg2);

        assertTrue(tokensFile.isFile());
        String second = Files.readString(tokensFile.toPath(), StandardCharsets.UTF_8);
        assertTrue("updated tokens should include TOKEN2", second.contains("TOKEN2"));
        assertTrue(tokensFile.lastModified() >= lastModified1);
    }

    private File resolveTokensFile(VirtualFile lexerFile) {
        String tokensFileName = "TestLexer.tokens";
        VirtualFile contentRoot = ProjectRootManager.getInstance(getProject())
                .getFileIndex()
                .getContentRootForFile(lexerFile);
        assertNotNull(contentRoot);
        String outDir = ANTLRv4ToolGrammarPropertiesStore
                .getGrammarProperties(getProject(), lexerFile)
                .resolveOutputDirName(getProject(), contentRoot, null);
        return Path.of(outDir, tokensFileName).toFile();
    }

    @Override
    protected void tearDown() throws Exception {
        Files.deleteIfExists(Path.of("TestLexer.tokens"));
        TestUtils.tearDownIgnoringObjectNotDisposedException(super::tearDown);
    }
}
