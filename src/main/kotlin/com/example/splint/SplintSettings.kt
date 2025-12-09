package com.example.splint

import com.intellij.openapi.components.*
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.util.xmlb.XmlSerializerUtil
import java.awt.event.ActionEvent
import javax.swing.*

// ... (Keep the SplintSettings class as is) ...
@State(
    name = "SplintSettings",
    storages = [Storage("splint.xml")]
)
@Service
class SplintSettings : PersistentStateComponent<SplintSettings> {
    
    var splintExecutablePath: String = "splint"
    var enableAutoAnalysis: Boolean = true
    var analysisTimeoutSeconds: Int = 30
    var additionalArgs: String = ""
    
    override fun getState(): SplintSettings = this
    
    override fun loadState(state: SplintSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }
    
    companion object {
        fun getInstance(): SplintSettings {
            return service<SplintSettings>()
        }
    }
}

class SplintSettingsConfigurable : Configurable {
    
    private var settingsPanel: JPanel? = null
    private var executablePathField: JBTextField? = null
    private var enableAutoAnalysisCheckbox: JBCheckBox? = null
    private var timeoutField: JBTextField? = null
    private var additionalArgsField: JBTextField? = null
    
    override fun getDisplayName(): String = "SplintJ"
    
    override fun createComponent(): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        
        // Executable path
        val execPathPanel = JPanel()
        execPathPanel.layout = BoxLayout(execPathPanel, BoxLayout.X_AXIS)
        execPathPanel.add(JLabel("Splint executable path:"))
        
        executablePathField = JBTextField(30)
        execPathPanel.add(executablePathField)

        // START CHANGE: Add Download Button
        val downloadBtn = JButton("Download JAR")
        downloadBtn.addActionListener {
            val project = ProjectManager.getInstance().openProjects.firstOrNull()
            SplintDownloader.downloadSplint(project) { newPath ->
                executablePathField?.text = newPath
            }
        }
        execPathPanel.add(Box.createHorizontalStrut(5))
        execPathPanel.add(downloadBtn)
        // END CHANGE
        
        panel.add(execPathPanel)
        
        panel.add(Box.createVerticalStrut(10))
        
        // Enable auto-analysis
        enableAutoAnalysisCheckbox = JBCheckBox("Enable automatic analysis on file save")
        panel.add(enableAutoAnalysisCheckbox)
        
        panel.add(Box.createVerticalStrut(10))
        
        // Timeout
        val timeoutPanel = JPanel()
        timeoutPanel.layout = BoxLayout(timeoutPanel, BoxLayout.X_AXIS)
        timeoutPanel.add(JLabel("Analysis timeout (seconds):"))
        timeoutField = JBTextField(10)
        timeoutPanel.add(timeoutField)
        panel.add(timeoutPanel)
        
        panel.add(Box.createVerticalStrut(10))
        
        // Additional args
        val argsPanel = JPanel()
        argsPanel.layout = BoxLayout(argsPanel, BoxLayout.X_AXIS)
        argsPanel.add(JLabel("Additional arguments:"))
        additionalArgsField = JBTextField(40)
        argsPanel.add(additionalArgsField)
        panel.add(argsPanel)
        
        panel.add(Box.createVerticalGlue())
        
        settingsPanel = panel
        return panel
    }
    
    override fun isModified(): Boolean {
        val settings = SplintSettings.getInstance()
        return executablePathField?.text != settings.splintExecutablePath ||
               enableAutoAnalysisCheckbox?.isSelected != settings.enableAutoAnalysis ||
               timeoutField?.text != settings.analysisTimeoutSeconds.toString() ||
               additionalArgsField?.text != settings.additionalArgs
    }
    
    override fun apply() {
        val settings = SplintSettings.getInstance()
        executablePathField?.text?.let { settings.splintExecutablePath = it }
        enableAutoAnalysisCheckbox?.isSelected?.let { settings.enableAutoAnalysis = it }
        timeoutField?.text?.toIntOrNull()?.let { settings.analysisTimeoutSeconds = it }
        additionalArgsField?.text?.let { settings.additionalArgs = it }
    }
    
    override fun reset() {
        val settings = SplintSettings.getInstance()
        executablePathField?.text = settings.splintExecutablePath
        enableAutoAnalysisCheckbox?.isSelected = settings.enableAutoAnalysis
        timeoutField?.text = settings.analysisTimeoutSeconds.toString()
        additionalArgsField?.text = settings.additionalArgs
    }
}