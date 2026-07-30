package com.antlr.plugin.validation;

import com.antlr.plugin.TestUtils;
import com.intellij.openapi.util.TextRange;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class CreateRuleFixTest extends BasePlatformTestCase {

    @Override
    protected String getTestDataPath() {
        return "src/test/resources/quickfixes/CreateRuleFix";
    }

    public void testQuickFixDescriptionShouldShowRuleName() {
        myFixture.configureByFile("missingRule.g4");
        CreateRuleFix createRuleFix = new CreateRuleFix(TextRange.create(30, 37), myFixture.getFile());

        assertEquals("Create rule 'newRule'", createRuleFix.getText());
        assertTrue(createRuleFix.isAvailable(getProject(), myFixture.getEditor(), myFixture.getFile()));
    }

    @Override
    protected void tearDown() throws Exception {
        TestUtils.tearDownIgnoringObjectNotDisposedException(super::tearDown);
    }
}
