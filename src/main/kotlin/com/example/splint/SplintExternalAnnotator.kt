package com.example.splint

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile

// MARK: - Data Models

data class SplintIssue(
    val line: Int,
    val column: Int,
    val endLine: Int?,
    val endColumn: Int?,
    val message: String,
    val level: String,
    val rule: String?,
    val alt: String?
)

data class AnnotationContext(
    val file: PsiFile,
    val issues: List<SplintIssue>
)

// MARK: - Main Annotator

class SplintExternalAnnotator : ExternalAnnotator<PsiFile, AnnotationContext>() {

    private val logger = Logger.getInstance(SplintExternalAnnotator::class.java)

    override fun collectInformation(file: PsiFile): PsiFile? {
        val fileName = file.name
        val isClojure = fileName.endsWith(".clj") ||
                fileName.endsWith(".cljs") ||
                fileName.endsWith(".cljc")
        return if (isClojure) file else null
    }

    override fun doAnnotate(file: PsiFile): AnnotationContext? {
        val virtualFile = file.virtualFile ?: return null
        val filePath = virtualFile.path
        val project = file.project
        val settings = SplintSettings.getInstance()

        // Resolve executable strategy
        val splintPath = resolveSplintPath(settings.splintExecutablePath)

        try {
            val commandLine = GeneralCommandLine()
                .withExePath(splintPath)
                .withParameters(filePath, "--output", "json")
                .withWorkDirectory(project.basePath)

            if (settings.additionalArgs.isNotBlank()) {
                commandLine.addParameters(settings.additionalArgs.split(" "))
            }

            val timeoutMs = settings.analysisTimeoutSeconds * 1000
            val output = ExecUtil.execAndGetOutput(commandLine, timeoutMs)

            // Critical Failure Check: Non-zero exit + No JSON stdout + Stderr present
            if (output.exitCode != 0 && output.stdout.isBlank() && output.stderr.isNotBlank()) {
                notifyError(project, output.stderr)
                return null
            }

            // Pure function call for parsing
            val issues = parseSplintJsonOutput(output.stdout)
            return AnnotationContext(file, issues)

        } catch (e: Exception) {
            logger.warn("Splint execution failed", e)
            return null
        }
    }

    override fun apply(file: PsiFile, context: AnnotationContext, holder: AnnotationHolder) {
        val document = file.viewProvider.document ?: return

        context.issues.forEach { issue ->
            try {
                val range = calculateTextRange(document, issue)

                // Map severity
                val severity = when (issue.level.lowercase()) {
                    "error" -> HighlightSeverity.ERROR
                    "info" -> HighlightSeverity.INFORMATION
                    else -> HighlightSeverity.WARNING
                }

                val message = if (issue.rule != null) "${issue.message} [${issue.rule}]" else issue.message

                val builder = holder.newAnnotation(severity, message)
                    .range(range)

                // Attach QuickFix if 'alt' is available
                if (!issue.alt.isNullOrBlank()) {
                    builder.withFix(SplintQuickFix(range, issue.alt))
                }

                builder.create()

            } catch (e: Exception) {
                logger.warn("Failed to apply annotation for issue at line ${issue.line}", e)
            }
        }
    }

    private fun calculateTextRange(document: com.intellij.openapi.editor.Document, issue: SplintIssue): TextRange {
        // Splint is 1-based, IntelliJ is 0-based.
        // We use coerceIn to ensure we never crash if the file changed on disk vs memory.

        val startLine = (issue.line - 1).coerceIn(0, document.lineCount - 1)
        val startLineOffset = document.getLineStartOffset(startLine)
        val lineEndOffset = document.getLineEndOffset(startLine)

        // Calculate absolute start offset
        val startOffset = if (issue.column > 0) {
            (startLineOffset + issue.column - 1).coerceIn(startLineOffset, lineEndOffset)
        } else {
            startLineOffset
        }

        // Calculate absolute end offset
        val endOffset: Int = if (issue.endLine != null && issue.endColumn != null) {
            val endLine = (issue.endLine - 1).coerceIn(0, document.lineCount - 1)
            val endLineStart = document.getLineStartOffset(endLine)
            val endLineEnd = document.getLineEndOffset(endLine)

            // Splint end-column is usually exclusive, matching IntelliJ's expectation
            (endLineStart + issue.endColumn - 1).coerceIn(endLineStart, endLineEnd)
        } else {
            // Fallback: Highlight until end of line or next whitespace if no end coords provided
            (startOffset + 1).coerceAtMost(lineEndOffset)
        }

        // Ensure range is valid (start <= end)
        val finalStart = startOffset
        val finalEnd = endOffset.coerceAtLeast(startOffset)

        return TextRange(finalStart, finalEnd)
    }

    private fun notifyError(project: Project, stderr: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Splint Error")
            .createNotification("Splint Execution Failed", stderr, NotificationType.ERROR)
            .notify(project)
    }
}

// MARK: - Quick Fix Action

class SplintQuickFix(
    private val range: TextRange,
    private val replacement: String
) : IntentionAction {

    override fun getText(): String {
        val displayStr = if (replacement.length > 40) replacement.take(37) + "..." else replacement
        return "Replace with: $displayStr"
    }

    override fun getFamilyName(): String = "Splint fixes"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        return file != null && range.endOffset <= file.textLength
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null || file == null) return

        // 1. Check write permissions
        if (!FileModificationService.getInstance().prepareFileForWrite(file)) return

        // 2. Apply the change to the editor (In-Memory)
        editor.document.replaceString(range.startOffset, range.endOffset, replacement)

        // 3. Sync the IntelliJ Document with the PSI (Program Structure Interface)
        PsiDocumentManager.getInstance(project).commitDocument(editor.document)

        // 4. CRITICAL: Save to disk so the external 'splint' CLI sees the new content
        FileDocumentManager.getInstance().saveDocument(editor.document)

        // 5. CRITICAL: Force IntelliJ to re-run the External Annotator immediately
        DaemonCodeAnalyzer.getInstance(project).restart(file)
    }

    override fun startInWriteAction(): Boolean = true
}

// MARK: - Top-Level Helper Functions

/**
 * Parses NDJSON (Newline Delimited JSON) from Splint.
 * This is top-level to allow easy unit testing without instantiating the Annotator or Logger.
 */
fun parseSplintJsonOutput(output: String): List<SplintIssue> {
    if (output.isBlank()) return emptyList()

    val issues = mutableListOf<SplintIssue>()

    output.lineSequence().forEach { line ->
        if (line.trim().startsWith("{")) {
            try {
                val lineNum = findJsonInt(line, "line")
                // Skip if no line number, as we can't place it
                if (lineNum != null) {
                    issues.add(
                        SplintIssue(
                            line = lineNum,
                            column = findJsonInt(line, "column") ?: 1,
                            endLine = findJsonInt(line, "end-line"),
                            endColumn = findJsonInt(line, "end-column"),
                            message = findJsonString(line, "message") ?: "Splint issue",
                            level = findJsonString(line, "level") ?: "warning",
                            rule = findJsonString(line, "rule-name"),
                            alt = findJsonString(line, "alt")
                        )
                    )
                }
            } catch (e: Exception) {
                // In production, we swallow parsing errors for specific lines to keep the plugin alive
                // In development, you might want to println here
            }
        }
    }
    return issues
}

private fun resolveSplintPath(userSetting: String): String {
    if (userSetting.isNotBlank() && userSetting != "splint") {
        return userSetting
    }

    val candidates = listOf(
        "splint",
        "/usr/local/bin/splint",
        "/opt/homebrew/bin/splint",
        System.getProperty("user.home") + "/.local/bin/splint",
        System.getProperty("user.home") + "/bin/splint"
    )

    return candidates.firstOrNull { path ->
        try {
            val cmd = GeneralCommandLine().withExePath(path).withParameters("--version")
            val output = ExecUtil.execAndGetOutput(cmd, 1000)
            output.exitCode == 0
        } catch (e: Exception) {
            false
        }
    } ?: "splint"
}

// MARK: - JSON Parsing Helpers (Regex)

private fun findJsonInt(json: String, key: String): Int? {
    // Matches "key": 123  (tolerant of whitespace)
    val regex = Regex("\"$key\"\\s*:\\s*(\\d+)")
    return regex.find(json)?.groupValues?.get(1)?.toIntOrNull()
}

private fun findJsonString(json: String, key: String): String? {
    // Matches "key": "value"
    // The capture group [^"\\]*(?:\\.[^"\\]*)* handles escaped characters like \" inside the string
    val regex = Regex("\"$key\"\\s*:\\s*\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"")
    val match = regex.find(json) ?: return null

    // Unescape common JSON sequences
    return match.groupValues[1]
        .replace("\\\"", "\"")
        .replace("\\n", "\n")
        .replace("\\r", "\r")
        .replace("\\t", "\t")
        .replace("\\\\", "\\")
}