package com.example.splint

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.SystemInfo
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.swing.SwingUtilities

object SplintDownloader {
    // START: Customizable Version
    private const val SPLINT_VERSION = "1.21.0"
    // END: Customizable Version

    private const val JAR_NAME = "splint-$SPLINT_VERSION-standalone.jar"
    private const val DOWNLOAD_URL = "https://github.com/NoahTheDuke/splint/releases/download/v$SPLINT_VERSION/$JAR_NAME"

    fun downloadSplint(project: Project?, onSuccess: (String) -> Unit) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Downloading Splint $SPLINT_VERSION", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.isIndeterminate = true
                    indicator.text = "Initializing download..."

                    // 1. Determine download location (Plugins cache directory)
                    val pluginPath = PathManager.getPluginTempPath()
                    val targetDir = File(pluginPath, "splint-bin")
                    if (!targetDir.exists()) {
                        targetDir.mkdirs()
                    }
                    
                    val targetFile = File(targetDir, JAR_NAME)
                    
                    // 2. Download
                    indicator.text = "Downloading from GitHub..."
                    val url = URL(DOWNLOAD_URL)
                    
                    url.openStream().use { input ->
                        Files.copy(input, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }

                    // 3. Update UI on Success
                    SwingUtilities.invokeLater {
                        Messages.showInfoMessage("Splint downloaded successfully to:\n${targetFile.absolutePath}", "Download Complete")
                        onSuccess(targetFile.absolutePath)
                    }

                } catch (e: Exception) {
                    SwingUtilities.invokeLater {
                        Messages.showErrorDialog("Failed to download Splint: ${e.message}", "Download Error")
                    }
                }
            }
        })
    }
}