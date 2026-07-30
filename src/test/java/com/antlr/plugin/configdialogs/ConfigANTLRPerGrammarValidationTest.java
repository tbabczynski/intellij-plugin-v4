package com.antlr.plugin.configdialogs;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

public class ConfigANTLRPerGrammarValidationTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void resolveMaybeRelativeDirFindsAbsoluteDirectory() throws Exception {
        File dir = temporaryFolder.newFolder("lib");
        File resolved = ConfigANTLRPerGrammar.resolveMaybeRelativeDir(null, dir.getAbsolutePath());
        Assert.assertNotNull(resolved);
        Assert.assertTrue(resolved.isDirectory());
    }

    @Test
    public void resolveMaybeRelativeDirRejectsMissingPath() {
        File resolved = ConfigANTLRPerGrammar.resolveMaybeRelativeDir(null, "definitely-not-a-dir-12345");
        Assert.assertNotNull(resolved);
        Assert.assertFalse(resolved.isDirectory());
    }

    @Test
    public void resolveMaybeRelativeDirBlankReturnsNull() {
        Assert.assertNull(ConfigANTLRPerGrammar.resolveMaybeRelativeDir(null, ""));
        Assert.assertNull(ConfigANTLRPerGrammar.resolveMaybeRelativeDir(null, "   "));
    }
}
