package com.example.splint

import java.io.File

/**
 * Manages reading and modifying .splint.edn configuration files.
 * Uses regex-based parsing and targeted string manipulation for modifications.
 */
object EdnConfigManager {

    private const val CONFIG_FILE_NAME = ".splint.edn"

    /**
     * Adds a file exclusion to a specific rule in .splint.edn
     */
    fun addExclusion(projectRoot: File, ruleName: String, filePath: String): Boolean {
        return try {
            val configFile = File(projectRoot, CONFIG_FILE_NAME)
            val newContent = if (configFile.exists()) {
                modifyExistingConfig(configFile.readText(), ruleName, filePath)
            } else {
                createNewConfig(ruleName, filePath)
            }
            configFile.writeText(newContent)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if a rule already has a specific file exclusion
     */
    fun ruleHasExclusion(projectRoot: File, ruleName: String, filePath: String): Boolean {
        val configFile = File(projectRoot, CONFIG_FILE_NAME)
        if (!configFile.exists()) return false

        return try {
            val content = configFile.readText()
            val excludes = findExcludesForRule(content, ruleName)
            excludes.contains(filePath)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Finds the :excludes list for a specific rule using regex
     */
    private fun findExcludesForRule(content: String, ruleName: String): List<String> {
        // Pattern to find rule with :excludes vector
        // Matches: ruleName {...:excludes [...] ...}
        val escapedRuleName = Regex.escape(ruleName)
        val pattern = Regex(
            """$escapedRuleName\s*\{[^}]*:excludes\s*\[([^\]]*)\]""",
            RegexOption.DOT_MATCHES_ALL
        )

        val match = pattern.find(content) ?: return emptyList()
        val excludesContent = match.groupValues[1]

        // Extract all quoted strings from the excludes vector
        val stringPattern = Regex(""""([^"\\]*(?:\\.[^"\\]*)*)"""")
        return stringPattern.findAll(excludesContent)
            .map { it.groupValues[1].unescapeEdn() }
            .toList()
    }

    /**
     * Checks if a rule exists in the config
     */
    private fun ruleExists(content: String, ruleName: String): Boolean {
        val escapedRuleName = Regex.escape(ruleName)
        val pattern = Regex("""$escapedRuleName\s*\{""")
        return pattern.containsMatchIn(content)
    }

    /**
     * Checks if a rule has :excludes key
     */
    private fun ruleHasExcludesKey(content: String, ruleName: String): Boolean {
        val escapedRuleName = Regex.escape(ruleName)
        val pattern = Regex("""$escapedRuleName\s*\{[^}]*:excludes\s*\[""", RegexOption.DOT_MATCHES_ALL)
        return pattern.containsMatchIn(content)
    }

    /**
     * Creates a new .splint.edn file with the exclusion
     */
    private fun createNewConfig(ruleName: String, filePath: String): String {
        val escapedPath = filePath.escapeEdn()
        return "{$ruleName {:excludes [\"$escapedPath\"]}}\n"
    }

    /**
     * Modifies existing config to add exclusion
     */
    private fun modifyExistingConfig(
        content: String,
        ruleName: String,
        filePath: String
    ): String {
        return when {
            ruleHasExcludesKey(content, ruleName) -> {
                // Case 1: Rule exists with :excludes - append to vector
                appendToExistingExcludes(content, ruleName, filePath)
            }
            ruleExists(content, ruleName) -> {
                // Case 2: Rule exists without :excludes - add :excludes key
                addExcludesToExistingRule(content, ruleName, filePath)
            }
            else -> {
                // Case 3: Rule doesn't exist - add new rule entry
                addNewRuleWithExcludes(content, ruleName, filePath)
            }
        }
    }

    /**
     * Appends a path to an existing :excludes vector
     */
    private fun appendToExistingExcludes(
        content: String,
        ruleName: String,
        newPath: String
    ): String {
        val escapedPath = newPath.escapeEdn()
        val escapedRuleName = Regex.escape(ruleName)
        val rulePattern = Regex(
            """($escapedRuleName\s*\{[^}]*:excludes\s*\[)([^\]]*)(])""",
            RegexOption.DOT_MATCHES_ALL
        )

        return rulePattern.replace(content) { match ->
            val prefix = match.groupValues[1]
            val existingPaths = match.groupValues[2].trimEnd()
            val suffix = match.groupValues[3]

            val separator = if (existingPaths.isNotBlank()) " " else ""
            """$prefix$existingPaths$separator"$escapedPath"$suffix"""
        }
    }

    /**
     * Adds :excludes to an existing rule that doesn't have it
     */
    private fun addExcludesToExistingRule(
        content: String,
        ruleName: String,
        newPath: String
    ): String {
        val escapedPath = newPath.escapeEdn()
        val escapedRuleName = Regex.escape(ruleName)
        val rulePattern = Regex("""($escapedRuleName\s*\{[^}]*)(})""")

        return rulePattern.replace(content) { match ->
            val prefix = match.groupValues[1].trimEnd()
            """$prefix :excludes ["$escapedPath"]}"""
        }
    }

    /**
     * Adds a new rule entry before the final closing brace
     */
    private fun addNewRuleWithExcludes(
        content: String,
        ruleName: String,
        newPath: String
    ): String {
        val escapedPath = newPath.escapeEdn()
        val lastBrace = content.lastIndexOf('}')
        if (lastBrace == -1) {
            return createNewConfig(ruleName, newPath)
        }

        val before = content.substring(0, lastBrace).trimEnd()
        val newEntry = "\n $ruleName {:excludes [\"$escapedPath\"]}"
        return "$before$newEntry}\n"
    }

    /**
     * Escapes special characters for EDN strings
     */
    private fun String.escapeEdn(): String {
        return this
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    /**
     * Unescapes EDN string sequences
     */
    private fun String.unescapeEdn(): String {
        return this
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\\", "\\")
    }
}
