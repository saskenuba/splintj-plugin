package com.example.splint

import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EdnConfigManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var projectRoot: File

    @Before
    fun setUp() {
        projectRoot = tempFolder.root
    }

    // ============ Creating new config ============

    @Test
    fun `creates new config file when none exists`() {
        val success = EdnConfigManager.addExclusion(projectRoot, "lint/eq-nil", "glob:**/foo.clj")

        assertTrue("Should succeed", success)

        val configFile = File(projectRoot, ".splint.edn")
        assertTrue("Config file should exist", configFile.exists())

        val content = configFile.readText()
        assertTrue("Should contain rule name", content.contains("lint/eq-nil"))
        assertTrue("Should contain excludes", content.contains(":excludes"))
        assertTrue("Should contain file path", content.contains("glob:**/foo.clj"))
    }

    @Test
    fun `new config has correct EDN format`() {
        EdnConfigManager.addExclusion(projectRoot, "lint/plus-one", "glob:**/test.clj")

        val content = File(projectRoot, ".splint.edn").readText()
        assertEquals("{lint/plus-one {:excludes [\"glob:**/test.clj\"]}}\n", content)
    }

    // ============ Appending to existing excludes ============

    @Test
    fun `appends to existing excludes vector`() {
        val configFile = File(projectRoot, ".splint.edn")
        configFile.writeText("""{lint/eq-nil {:excludes ["glob:**/existing.clj"]}}""")

        val success = EdnConfigManager.addExclusion(projectRoot, "lint/eq-nil", "glob:**/new.clj")

        assertTrue("Should succeed", success)

        val content = configFile.readText()
        assertTrue("Should contain existing path", content.contains("glob:**/existing.clj"))
        assertTrue("Should contain new path", content.contains("glob:**/new.clj"))
    }

    @Test
    fun `appends with correct spacing`() {
        val configFile = File(projectRoot, ".splint.edn")
        configFile.writeText("""{lint/eq-nil {:excludes ["a"]}}""")

        EdnConfigManager.addExclusion(projectRoot, "lint/eq-nil", "b")

        val content = configFile.readText()
        assertTrue("Should have both entries with space", content.contains(""""a" "b""""))
    }

    @Test
    fun `appends to empty excludes vector`() {
        val configFile = File(projectRoot, ".splint.edn")
        configFile.writeText("""{lint/eq-nil {:excludes []}}""")

        EdnConfigManager.addExclusion(projectRoot, "lint/eq-nil", "glob:**/foo.clj")

        val content = configFile.readText()
        assertTrue("Should contain the path", content.contains("glob:**/foo.clj"))
    }

    // ============ Adding excludes to rule without them ============

    @Test
    fun `adds excludes to rule that has enabled flag`() {
        val configFile = File(projectRoot, ".splint.edn")
        configFile.writeText("""{lint/eq-nil {:enabled false}}""")

        val success = EdnConfigManager.addExclusion(projectRoot, "lint/eq-nil", "glob:**/foo.clj")

        assertTrue("Should succeed", success)

        val content = configFile.readText()
        assertTrue("Should still have enabled", content.contains(":enabled"))
        assertTrue("Should have excludes", content.contains(":excludes"))
        assertTrue("Should have path", content.contains("glob:**/foo.clj"))
    }

    @Test
    fun `adds excludes to rule with multiple options`() {
        val configFile = File(projectRoot, ".splint.edn")
        configFile.writeText("""{lint/eq-nil {:enabled true :chosen-style :foo}}""")

        EdnConfigManager.addExclusion(projectRoot, "lint/eq-nil", "test.clj")

        val content = configFile.readText()
        assertTrue("Should contain excludes", content.contains(":excludes [\"test.clj\"]"))
    }

    // ============ Adding new rule to existing config ============

    @Test
    fun `adds new rule to existing config`() {
        val configFile = File(projectRoot, ".splint.edn")
        configFile.writeText("""{global {:excludes ["foo"]}}""")

        val success = EdnConfigManager.addExclusion(projectRoot, "lint/eq-nil", "glob:**/bar.clj")

        assertTrue("Should succeed", success)

        val content = configFile.readText()
        assertTrue("Should contain global", content.contains("global"))
        assertTrue("Should contain new rule", content.contains("lint/eq-nil"))
        assertTrue("Should contain new path", content.contains("glob:**/bar.clj"))
    }

    @Test
    fun `adds new rule preserving existing rules`() {
        val configFile = File(projectRoot, ".splint.edn")
        configFile.writeText("""{
 performance {:enabled false}
 style/eq-zero {:excludes ["a.clj"]}
}""")

        EdnConfigManager.addExclusion(projectRoot, "lint/plus-one", "b.clj")

        val content = configFile.readText()
        assertTrue("Should contain performance", content.contains("performance"))
        assertTrue("Should contain style/eq-zero", content.contains("style/eq-zero"))
        assertTrue("Should contain new rule", content.contains("lint/plus-one"))
    }

    // ============ Checking for existing exclusions ============

    @Test
    fun `ruleHasExclusion returns true when file is excluded`() {
        val configFile = File(projectRoot, ".splint.edn")
        configFile.writeText("""{lint/eq-nil {:excludes ["glob:**/foo.clj"]}}""")

        val hasExclusion = EdnConfigManager.ruleHasExclusion(
            projectRoot, "lint/eq-nil", "glob:**/foo.clj"
        )

        assertTrue("Should find the exclusion", hasExclusion)
    }

    @Test
    fun `ruleHasExclusion returns false when file is not excluded`() {
        val configFile = File(projectRoot, ".splint.edn")
        configFile.writeText("""{lint/eq-nil {:excludes ["glob:**/foo.clj"]}}""")

        val hasExclusion = EdnConfigManager.ruleHasExclusion(
            projectRoot, "lint/eq-nil", "glob:**/other.clj"
        )

        assertFalse("Should not find non-existent exclusion", hasExclusion)
    }

    @Test
    fun `ruleHasExclusion returns false when rule has no excludes`() {
        val configFile = File(projectRoot, ".splint.edn")
        configFile.writeText("""{lint/eq-nil {:enabled false}}""")

        val hasExclusion = EdnConfigManager.ruleHasExclusion(
            projectRoot, "lint/eq-nil", "glob:**/foo.clj"
        )

        assertFalse("Should not find exclusion", hasExclusion)
    }

    @Test
    fun `ruleHasExclusion returns false when rule does not exist`() {
        val configFile = File(projectRoot, ".splint.edn")
        configFile.writeText("""{global {:excludes ["foo"]}}""")

        val hasExclusion = EdnConfigManager.ruleHasExclusion(
            projectRoot, "lint/eq-nil", "glob:**/foo.clj"
        )

        assertFalse("Should not find exclusion for non-existent rule", hasExclusion)
    }

    @Test
    fun `ruleHasExclusion returns false when config file does not exist`() {
        val hasExclusion = EdnConfigManager.ruleHasExclusion(
            projectRoot, "lint/eq-nil", "glob:**/foo.clj"
        )

        assertFalse("Should return false when no config exists", hasExclusion)
    }

    // ============ Edge cases ============

    @Test
    fun `handles rule names with slashes`() {
        EdnConfigManager.addExclusion(projectRoot, "lint/redundant-str-call", "test.clj")

        val content = File(projectRoot, ".splint.edn").readText()
        assertTrue("Should contain full rule name", content.contains("lint/redundant-str-call"))
    }

    @Test
    fun `escapes quotes in file paths`() {
        EdnConfigManager.addExclusion(projectRoot, "lint/eq-nil", """path/with"quote.clj""")

        val content = File(projectRoot, ".splint.edn").readText()
        assertTrue("Should contain escaped quote", content.contains("""\"quote"""))
    }

    @Test
    fun `handles multiple excludes in original config`() {
        val configFile = File(projectRoot, ".splint.edn")
        configFile.writeText("""{lint/eq-nil {:excludes ["a" "b" "c"]}}""")

        EdnConfigManager.addExclusion(projectRoot, "lint/eq-nil", "d")

        val content = configFile.readText()
        assertTrue("Should contain all paths",
            content.contains("a") && content.contains("b") &&
            content.contains("c") && content.contains("d"))
    }

    @Test
    fun `handles genre-level rules`() {
        EdnConfigManager.addExclusion(projectRoot, "performance", "glob:**/slow.clj")

        val content = File(projectRoot, ".splint.edn").readText()
        assertTrue("Should contain genre rule", content.contains("performance {:excludes"))
    }
}
