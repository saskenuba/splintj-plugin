package com.example.splint

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.psi.PsiManager
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer

class SplintRunAction : AnAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.PSI_FILE) ?: return
        
        // Only run on Clojure files
        val fileName = file.name
        if (!fileName.endsWith(".clj") && 
            !fileName.endsWith(".cljs") && 
            !fileName.endsWith(".cljc")) {
            return
        }
        
        // Save the document first
        FileDocumentManager.getInstance().saveDocument(editor.document)
        
        // Trigger re-analysis in background
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Running Splint Analysis", false) {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Analyzing ${file.name}..."
                
                // Force re-run of external annotators
                ApplicationManager.getApplication().invokeLater {
                    DaemonCodeAnalyzer.getInstance(project).restart(file)
                }
            }
        })
    }
    
    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.PSI_FILE)
        val fileName = file?.name ?: ""
        
        // Only enable for Clojure files
        e.presentation.isEnabled = fileName.endsWith(".clj") || 
                                   fileName.endsWith(".cljs") || 
                                   fileName.endsWith(".cljc")
    }
}

class SplintRunProjectAction : AnAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Running Splint on Project", false) {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Analyzing Clojure files in project..."
                
                // Restart analysis for all files
                ApplicationManager.getApplication().invokeLater {
                    DaemonCodeAnalyzer.getInstance(project).restart()
                }
            }
        })
    }
    
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}