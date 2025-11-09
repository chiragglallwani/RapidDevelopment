package com.runanywhere.startup_hackathon20.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.listAvailableModels
import com.runanywhere.sdk.models.ModelInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * AI Assistant ViewModel for intelligent project and task management
 * Uses RunAnywhere SDK to provide AI-powered suggestions and automation
 */
class AIProjectAssistantViewModel : ViewModel() {

    private val _availableModels = MutableStateFlow<List<ModelInfo>>(emptyList())
    val availableModels: StateFlow<List<ModelInfo>> = _availableModels

    private val _currentModelId = MutableStateFlow<String?>(null)
    val currentModelId: StateFlow<String?> = _currentModelId

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _statusMessage = MutableStateFlow<String>("AI Assistant Ready")
    val statusMessage: StateFlow<String> = _statusMessage

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgress: StateFlow<Float?> = _downloadProgress

    // AI-generated content
    private val _generatedProjectIdea = MutableStateFlow<String>("")
    val generatedProjectIdea: StateFlow<String> = _generatedProjectIdea

    private val _generatedTasks = MutableStateFlow<List<TaskSuggestion>>(emptyList())
    val generatedTasks: StateFlow<List<TaskSuggestion>> = _generatedTasks

    private val _generatedDescription = MutableStateFlow<String>("")
    val generatedDescription: StateFlow<String> = _generatedDescription

    data class TaskSuggestion(
        val title: String,
        val description: String,
        val priority: String = "medium"
    )

    init {
        loadAvailableModels()
    }

    private fun loadAvailableModels() {
        viewModelScope.launch {
            try {
                val models = listAvailableModels()
                _availableModels.value = models
                
                // Auto-load first downloaded model if available
                val downloadedModel = models.firstOrNull { it.isDownloaded }
                if (downloadedModel != null) {
                    loadModel(downloadedModel.id)
                } else {
                    _statusMessage.value = "Please download and load a model to use AI assistance"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error loading models: ${e.message}"
            }
        }
    }

    fun downloadModel(modelId: String) {
        viewModelScope.launch {
            try {
                _statusMessage.value = "Downloading model..."
                RunAnywhere.downloadModel(modelId).collect { progress ->
                    _downloadProgress.value = progress
                    _statusMessage.value = "Downloading: ${(progress * 100).toInt()}%"
                }
                _downloadProgress.value = null
                _statusMessage.value = "Download complete! Loading model..."
                loadModel(modelId)
            } catch (e: Exception) {
                _statusMessage.value = "Download failed: ${e.message}"
                _downloadProgress.value = null
            }
        }
    }

    fun loadModel(modelId: String) {
        viewModelScope.launch {
            try {
                _statusMessage.value = "Loading model..."
                val success = RunAnywhere.loadModel(modelId)
                if (success) {
                    _currentModelId.value = modelId
                    _statusMessage.value = "AI Assistant Ready"
                } else {
                    _statusMessage.value = "Failed to load model"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error loading model: ${e.message}"
            }
        }
    }

    /**
     * Generate a project idea based on a topic or industry
     */
    fun generateProjectIdea(topic: String) {
        if (_currentModelId.value == null) {
            _statusMessage.value = "Please load a model first"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _statusMessage.value = "Generating project idea..."
            
            try {
                val prompt = """
                    You are a project management assistant. Generate a creative and practical project idea based on the following topic:
                    
                    Topic: $topic
                    
                    Provide:
                    1. Project Title (short and catchy)
                    2. Brief Description (2-3 sentences)
                    3. Key Goals (3 main objectives)
                    
                    Format your response clearly and concisely.
                """.trimIndent()

                var response = ""
                RunAnywhere.generateStream(prompt).collect { token ->
                    response += token
                    _generatedProjectIdea.value = response
                }
                
                _statusMessage.value = "Project idea generated!"
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
                _generatedProjectIdea.value = "Error generating project idea: ${e.message}"
            }
            
            _isLoading.value = false
        }
    }

    /**
     * Break down a project into actionable tasks using AI
     */
    fun breakdownProjectIntoTasks(projectName: String, projectDescription: String) {
        if (_currentModelId.value == null) {
            _statusMessage.value = "Please load a model first"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _statusMessage.value = "Breaking down project into tasks..."
            
            try {
                val prompt = """
                    You are a project management assistant. Break down the following project into 5-7 actionable tasks:
                    
                    Project: $projectName
                    Description: $projectDescription
                    
                    For each task, provide:
                    1. Task Title (clear and action-oriented)
                    2. Brief Description (what needs to be done)
                    
                    Format each task as:
                    TASK: [Title]
                    DESC: [Description]
                    
                    Keep tasks specific, measurable, and achievable.
                """.trimIndent()

                var response = ""
                RunAnywhere.generateStream(prompt).collect { token ->
                    response += token
                }
                
                // Parse the response into task suggestions
                val tasks = parseTasksFromResponse(response)
                _generatedTasks.value = tasks
                
                _statusMessage.value = "Generated ${tasks.size} task suggestions"
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
                _generatedTasks.value = emptyList()
            }
            
            _isLoading.value = false
        }
    }

    /**
     * Generate a detailed description for a project
     */
    fun generateProjectDescription(projectTitle: String, keywords: String = "") {
        if (_currentModelId.value == null) {
            _statusMessage.value = "Please load a model first"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _statusMessage.value = "Generating description..."
            
            try {
                val prompt = """
                    You are a project management assistant. Write a clear and professional project description for:
                    
                    Project Title: $projectTitle
                    ${if (keywords.isNotEmpty()) "Keywords: $keywords" else ""}
                    
                    The description should:
                    - Be 2-3 paragraphs
                    - Explain the project's purpose and goals
                    - Be professional yet engaging
                    - Focus on outcomes and value
                """.trimIndent()

                var response = ""
                RunAnywhere.generateStream(prompt).collect { token ->
                    response += token
                    _generatedDescription.value = response
                }
                
                _statusMessage.value = "Description generated!"
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
                _generatedDescription.value = "Error generating description: ${e.message}"
            }
            
            _isLoading.value = false
        }
    }

    /**
     * Create a task from natural language input
     */
    fun createTaskFromNaturalLanguage(naturalLanguageInput: String, onResult: (TaskSuggestion) -> Unit) {
        if (_currentModelId.value == null) {
            _statusMessage.value = "Please load a model first"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _statusMessage.value = "Processing task..."
            
            try {
                val prompt = """
                    You are a project management assistant. Convert this natural language input into a structured task:
                    
                    Input: $naturalLanguageInput
                    
                    Provide:
                    TITLE: [Clear, action-oriented title]
                    DESCRIPTION: [Detailed description of what needs to be done]
                    PRIORITY: [high/medium/low based on urgency words or default to medium]
                    
                    Be concise and professional.
                """.trimIndent()

                var response = ""
                RunAnywhere.generateStream(prompt).collect { token ->
                    response += token
                }
                
                // Parse the response into a task
                val task = parseTaskFromResponse(response)
                onResult(task)
                
                _statusMessage.value = "Task created from input"
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            }
            
            _isLoading.value = false
        }
    }

    /**
     * Get smart suggestions for next steps in a project
     */
    fun suggestNextSteps(projectName: String, completedTasks: List<String>) {
        if (_currentModelId.value == null) {
            _statusMessage.value = "Please load a model first"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _statusMessage.value = "Analyzing project progress..."
            
            try {
                val prompt = """
                    You are a project management assistant. Based on the project and completed tasks, suggest 3-5 logical next steps:
                    
                    Project: $projectName
                    Completed Tasks:
                    ${completedTasks.joinToString("\n") { "- $it" }}
                    
                    Suggest tasks that:
                    - Follow logically from completed work
                    - Help move the project forward
                    - Are specific and actionable
                    
                    Format each as:
                    TASK: [Title]
                    DESC: [Description]
                """.trimIndent()

                var response = ""
                RunAnywhere.generateStream(prompt).collect { token ->
                    response += token
                }
                
                val tasks = parseTasksFromResponse(response)
                _generatedTasks.value = tasks
                
                _statusMessage.value = "Generated ${tasks.size} suggestions"
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            }
            
            _isLoading.value = false
        }
    }

    // Helper function to parse tasks from AI response
    private fun parseTasksFromResponse(response: String): List<TaskSuggestion> {
        val tasks = mutableListOf<TaskSuggestion>()
        val lines = response.lines()
        
        var currentTitle = ""
        var currentDesc = ""
        var currentPriority = "medium"
        
        for (line in lines) {
            when {
                line.trim().startsWith("TASK:", ignoreCase = true) -> {
                    // Save previous task if exists
                    if (currentTitle.isNotEmpty()) {
                        tasks.add(TaskSuggestion(currentTitle, currentDesc, currentPriority))
                    }
                    currentTitle = line.substringAfter(":").trim()
                    currentDesc = ""
                    currentPriority = "medium"
                }
                line.trim().startsWith("DESC:", ignoreCase = true) -> {
                    currentDesc = line.substringAfter(":").trim()
                }
                line.trim().startsWith("PRIORITY:", ignoreCase = true) -> {
                    currentPriority = line.substringAfter(":").trim().lowercase()
                }
            }
        }
        
        // Add last task
        if (currentTitle.isNotEmpty()) {
            tasks.add(TaskSuggestion(currentTitle, currentDesc, currentPriority))
        }
        
        // If no structured format found, try to extract from numbered/bulleted list
        if (tasks.isEmpty()) {
            val taskPattern = Regex("""(?:^\d+\.|^[-•*])\s*(.+)""", RegexOption.MULTILINE)
            val matches = taskPattern.findAll(response)
            matches.forEach { match ->
                val taskText = match.groupValues[1].trim()
                if (taskText.isNotEmpty()) {
                    tasks.add(TaskSuggestion(taskText, taskText, "medium"))
                }
            }
        }
        
        return tasks
    }

    // Helper function to parse a single task from AI response
    private fun parseTaskFromResponse(response: String): TaskSuggestion {
        val lines = response.lines()
        var title = ""
        var description = ""
        var priority = "medium"
        
        for (line in lines) {
            when {
                line.trim().startsWith("TITLE:", ignoreCase = true) -> {
                    title = line.substringAfter(":").trim()
                }
                line.trim().startsWith("DESC:", ignoreCase = true) || 
                line.trim().startsWith("DESCRIPTION:", ignoreCase = true) -> {
                    description = line.substringAfter(":").trim()
                }
                line.trim().startsWith("PRIORITY:", ignoreCase = true) -> {
                    priority = line.substringAfter(":").trim().lowercase()
                }
            }
        }
        
        // Fallback if no structured format
        if (title.isEmpty()) {
            val firstLine = lines.firstOrNull { it.trim().isNotEmpty() } ?: "New Task"
            title = firstLine.take(100).trim()
            description = response.trim()
        }
        
        return TaskSuggestion(title, description, priority)
    }

    fun clearGeneratedContent() {
        _generatedProjectIdea.value = ""
        _generatedTasks.value = emptyList()
        _generatedDescription.value = ""
    }

    fun refreshModels() {
        loadAvailableModels()
    }
}
