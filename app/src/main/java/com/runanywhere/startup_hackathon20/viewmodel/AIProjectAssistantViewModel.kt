package com.runanywhere.startup_hackathon20.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import com.runanywhere.startup_hackathon20.ai.*
import com.runanywhere.startup_hackathon20.data.repository.ProjectRepository
import com.runanywhere.startup_hackathon20.data.repository.ProjectResult
import com.runanywhere.startup_hackathon20.data.repository.TaskRepository
import com.runanywhere.startup_hackathon20.data.repository.TaskResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * AI Assistant ViewModel for intelligent project and task management
 * Now supports multiple AI providers (RunAnywhere SDK and Gemini API)
 */
class AIProjectAssistantViewModel(
    private val context: Context,
    private val projectRepository: ProjectRepository,
    private val taskRepository: TaskRepository
) : ViewModel() {

    private val aiProviderManager = AIProviderManager(context)

    private val _availableModels = MutableStateFlow<List<AIModel>>(emptyList())
    val availableModels: StateFlow<List<AIModel>> = _availableModels

    private val _currentModelId = MutableStateFlow<String?>(null)
    val currentModelId: StateFlow<String?> = _currentModelId

    private val _currentProviderType = MutableStateFlow<AIProviderType?>(null)
    val currentProviderType: StateFlow<AIProviderType?> = _currentProviderType

    private val _availableProviders =
        MutableStateFlow<List<Pair<AIProviderType, Boolean>>>(emptyList())
    val availableProviders: StateFlow<List<Pair<AIProviderType, Boolean>>> = _availableProviders

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _statusMessage = MutableStateFlow<String>("AI Assistant Initializing...")
    val statusMessage: StateFlow<String> = _statusMessage

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgress: StateFlow<Float?> = _downloadProgress

    // AI command execution result
    private val _executionResult = MutableStateFlow<CommandExecutionResult?>(null)
    val executionResult: StateFlow<CommandExecutionResult?> = _executionResult

    // Gemini API key management
    private val _geminiApiKey = MutableStateFlow<String>("")
    val geminiApiKey: StateFlow<String> = _geminiApiKey

    // Data classes for AI response parsing
    data class AICommandResponse(
        @SerializedName("Action") val action: Any? = null, // Can be String or List<String>
        @SerializedName("Title") val title: String? = null,
        @SerializedName("Description") val description: String? = null,
        @SerializedName("Project Title") val projectTitle: String? = null,
        @SerializedName("Project Description") val projectDescription: String? = null,
        @SerializedName("Tasks") val tasks: List<TaskCommand>? = null,
        @SerializedName("Status") val status: String? = null,
        @SerializedName("Assigned To") val assignedTo: String? = null,
        @SerializedName("Search Text") val searchText: String? = null
    )

    data class TaskCommand(
        @SerializedName("Title") val title: String,
        @SerializedName("Description") val description: String,
        @SerializedName("Status") val status: String? = "to-do",
        @SerializedName("Assigned To") val assignedTo: String? = null
    )

    data class CommandExecutionResult(
        val success: Boolean,
        val message: String,
        val actions: List<String> = emptyList(),
        val createdProjectId: String? = null,
        val createdTaskIds: List<String> = emptyList()
    )

    private val gson = Gson()

    // Intent types for focused prompts
    private enum class UserIntent {
        CREATE_PROJECT,
        CREATE_TASK,
        UPDATE_PROJECT,
        UPDATE_TASK,
        DELETE_PROJECT,
        DELETE_TASK,
        UNKNOWN
    }

    init {
        initializeAIProviders()
    }

    private fun initializeAIProviders() {
        viewModelScope.launch {
            try {
                Log.d("AIAssistant", "Initializing AI provider manager...")

                when (val result = aiProviderManager.initialize()) {
                    is AIProviderResult.Success -> {
                        Log.d("AIAssistant", "AI provider manager initialized successfully")
                        _statusMessage.value = "AI Assistant Ready"

                        // Update provider status
                        updateProviderStatus()

                        // Load available models
                        loadAvailableModels()

                        // Load saved Gemini API key
                        loadGeminiApiKey()

                        // Auto-load first downloaded model if available
                        autoLoadModel()
                    }

                    is AIProviderResult.Error -> {
                        Log.e(
                            "AIAssistant",
                            "Failed to initialize AI provider manager: ${result.message}"
                        )
                        _statusMessage.value = "Error: ${result.message}"
                    }
                }
            } catch (e: Exception) {
                Log.e("AIAssistant", "Exception during AI provider initialization", e)
                _statusMessage.value = "Error initializing AI providers: ${e.message}"
            }
        }
    }

    private suspend fun updateProviderStatus() {
        _availableProviders.value = aiProviderManager.getAvailableProviders()
        _currentProviderType.value = aiProviderManager.getCurrentProviderType()
    }

    private suspend fun loadAvailableModels() {
        when (val result = aiProviderManager.getAvailableModels()) {
            is AIProviderResult.Success -> {
                _availableModels.value = result.data
                Log.d("AIAssistant", "Loaded ${result.data.size} available models")
            }

            is AIProviderResult.Error -> {
                Log.e("AIAssistant", "Failed to load available models: ${result.message}")
                _statusMessage.value = "Error loading models: ${result.message}"
            }
        }
    }

    private suspend fun loadGeminiApiKey() {
        val apiKey = aiProviderManager.getGeminiApiKey()
        _geminiApiKey.value = apiKey ?: ""
    }

    private suspend fun autoLoadModel() {
        val models = _availableModels.value
        val downloadedModel = models.firstOrNull { it.isDownloaded }

        if (downloadedModel != null) {
            Log.d("AIAssistant", "Auto-loading downloaded model: ${downloadedModel.name}")
            loadModel(downloadedModel.id)
        } else {
            _statusMessage.value = when (_currentProviderType.value) {
                AIProviderType.GEMINI -> "Gemini ready - select a model to begin"
                AIProviderType.RUN_ANYWHERE -> "Please download and load a model to use AI assistance"
                null -> "Please select an AI provider and load a model"
            }
            Log.d("AIAssistant", "No downloaded models found")
        }
    }

    /**
     * Switch between AI providers
     */
    fun switchProvider(providerType: AIProviderType) {
        viewModelScope.launch {
            try {
                _statusMessage.value = "Switching to ${providerType.name}..."

                when (val result = aiProviderManager.switchProvider(providerType)) {
                    is AIProviderResult.Success -> {
                        Log.d("AIAssistant", "Successfully switched to ${providerType.name}")
                        _statusMessage.value = "Switched to ${providerType.name}"

                        // Update UI state
                        updateProviderStatus()
                        loadAvailableModels()
                        _currentModelId.value = null // Clear current model

                        // Auto-load model if available
                        autoLoadModel()
                    }

                    is AIProviderResult.Error -> {
                        Log.e("AIAssistant", "Failed to switch provider: ${result.message}")
                        _statusMessage.value = "Failed to switch provider: ${result.message}"
                    }
                }
            } catch (e: Exception) {
                Log.e("AIAssistant", "Exception during provider switch", e)
                _statusMessage.value = "Error switching provider: ${e.message}"
            }
        }
    }

    /**
     * Update Gemini API key
     */
    fun updateGeminiApiKey(apiKey: String) {
        viewModelScope.launch {
            try {
                when (val result = aiProviderManager.updateGeminiApiKey(apiKey)) {
                    is AIProviderResult.Success -> {
                        _geminiApiKey.value = apiKey
                        _statusMessage.value = "Gemini API key updated successfully"

                        // Refresh provider status
                        updateProviderStatus()

                        // If currently using Gemini, reload models
                        if (_currentProviderType.value == AIProviderType.GEMINI) {
                            loadAvailableModels()
                        }
                    }

                    is AIProviderResult.Error -> {
                        _statusMessage.value = "Failed to update API key: ${result.message}"
                    }
                }
            } catch (e: Exception) {
                Log.e("AIAssistant", "Exception updating Gemini API key", e)
                _statusMessage.value = "Error updating API key: ${e.message}"
            }
        }
    }

    fun downloadModel(modelId: String) {
        viewModelScope.launch {
            try {
                _statusMessage.value = "Downloading model..."
                aiProviderManager.downloadModel(modelId).collect { progress ->
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
                Log.d("AIAssistant", "Attempting to load model: $modelId")

                when (val result = aiProviderManager.loadModel(modelId)) {
                    is AIProviderResult.Success -> {
                        _currentModelId.value = modelId
                        _statusMessage.value = "Model loaded! Ready to assist."
                        Log.d("AIAssistant", "Model loaded successfully: $modelId")
                    }

                    is AIProviderResult.Error -> {
                        _statusMessage.value = "Failed to load model: ${result.message}"
                        _currentModelId.value = null
                        Log.e("AIAssistant", "Model loading failed: ${result.message}")
                    }
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error loading model: ${e.message}"
                _currentModelId.value = null
                Log.e("AIAssistant", "Exception during model loading", e)
            }
        }
    }

    /**
     * Main function to process natural language commands and execute corresponding actions
     */
    fun processNaturalLanguageCommand(userInput: String) {
        if (!aiProviderManager.isReady()) {
            _statusMessage.value = "Please load a model first"
            Log.w("AIAssistant", "No model ready, cannot process command")
            _executionResult.value = CommandExecutionResult(
                success = false,
                message = "No AI model is currently loaded. Please select a provider and load a model first."
            )
            return
        }

        Log.d("AIAssistant", "Processing natural language command: $userInput")

        viewModelScope.launch {
            _isLoading.value = true
            _statusMessage.value = "Processing command..."
            val currentProvider = _currentProviderType.value
            var prompt = ""
            
            try {
                if (currentProvider == AIProviderType.RUN_ANYWHERE) {
                    prompt = userInput
                } else {
                    // Step 1: Detect intent from user input
                Log.d("AIAssistant", "Detecting intent from user input...")
                val intent = detectIntent(userInput)
                Log.d("AIAssistant", "Detected intent: $intent")
                
                if (intent == UserIntent.UNKNOWN) {
                    _statusMessage.value = "Could not understand the request"
                    _executionResult.value = CommandExecutionResult(
                        success = false,
                        message = "Could not understand your request. Please try:\n" +
                                "• 'Create a project called [name]'\n" +
                                "• 'Create task for [project name]'\n" +
                                "• 'Update [project name] project'\n" +
                                "• 'Delete [project name] project'"
                    )
                    _isLoading.value = false
                    return@launch
                }
                
               
                    prompt = createFocusedPrompt(userInput, intent)
                
                Log.d("AIAssistant", "Generated prompt length: ${prompt.length} characters")
                    prompt = createFocusedPrompt(userInput, intent)
                }
                

                // Get AI response with better error handling
                var aiResponse = ""
                Log.d("AIAssistant", "Starting AI generation stream...")
                Log.d("AIAssistant", "Current provider: ${_currentProviderType.value}")
                Log.d("AIAssistant", "Is model ready: ${aiProviderManager.isReady()}")
                Log.d("AIAssistant", "Current model: ${aiProviderManager.getCurrentModel()}")
                Log.d("AIAssistant", "Prompt preview (first 200 chars): ${prompt.take(200)}")

                try {
                    aiProviderManager.generateStream(prompt).collect { token ->
                        Log.d("AIAssistant", "Received token: ${token.take(100)}")
                        aiResponse += token
                    }

                    Log.d(
                        "AIAssistant",
                        "AI generation completed. Response length: ${aiResponse.length}"
                    )
                    Log.d("AIAssistant", "Full response: $aiResponse")

                } catch (e: IllegalStateException) {
                    Log.e("AIAssistant", "Model not properly loaded for generation", e)
                    _statusMessage.value = "Model not ready - please reload"
                    _executionResult.value = CommandExecutionResult(
                        success = false,
                        message = "Model not ready. Please try reloading the model."
                    )
                    return@launch
                } catch (e: Exception) {
                    Log.e("AIAssistant", "AI generation failed", e)
                    _statusMessage.value = "Generation failed"
                    _executionResult.value = CommandExecutionResult(
                        success = false,
                        message = "AI generation failed: ${e.message}"
                    )
                    return@launch
                }

                if (aiResponse.isBlank()) {
                    Log.w("AIAssistant", "AI returned empty response - stopping process")
                    _statusMessage.value = "Empty response received"
                    _executionResult.value = CommandExecutionResult(
                        success = false,
                        message = "AI returned empty response. Please try:\n• Reloading the model\n• Using simpler commands\n• Different wording"
                    )
                    _isLoading.value = false
                    return@launch
                }

                // Check if using RunAnywhere SDK - if so, show raw response only without parsing or executing
                if (currentProvider == AIProviderType.RUN_ANYWHERE) {
                    Log.d("AIAssistant", "RunAnywhere SDK detected - showing raw response only, skipping all operations")
                    _statusMessage.value = "Response received (Preview Mode)"
                    _executionResult.value = CommandExecutionResult(
                        success = true,
                        message = "AI Response (RunAnywhere SDK - Preview Mode):\n\n$aiResponse\n\n" +
                                "Note: No operations (CREATE, UPDATE, DELETE) are executed when using RunAnywhere SDK. " +
                                "This is a preview of what the AI would do.",
                        actions = emptyList()
                    )
                    _isLoading.value = false
                    return@launch
                }

                // Parse and execute the command (only for Gemini)
                executeCommand(aiResponse, userInput)

            } catch (e: Exception) {
                Log.e("AIAssistant", "Error in processNaturalLanguageCommand", e)
                _statusMessage.value = "Error: ${e.message}"
                _executionResult.value = CommandExecutionResult(
                    success = false,
                    message = "Error processing command: ${e.message}"
                )
            }
            
            _isLoading.value = false
        }
    }

    /**
     * Detect user intent from input text
     */
    private fun detectIntent(userInput: String): UserIntent {
        val inputLower = userInput.lowercase()
        
        // Check for delete/remove first (highest priority)
        if (inputLower.contains("delete") || inputLower.contains("remove")) {
            if (inputLower.contains("project")) {
                return UserIntent.DELETE_PROJECT
            }
            if (inputLower.contains("task")) {
                return UserIntent.DELETE_TASK
            }
        }
        
        // Check for update/modify/edit/change
        if (inputLower.contains("update") || inputLower.contains("modify") || 
            inputLower.contains("edit") || inputLower.contains("change")) {
            if (inputLower.contains("project")) {
                return UserIntent.UPDATE_PROJECT
            }
            if (inputLower.contains("task")) {
                return UserIntent.UPDATE_TASK
            }
        }
        
        // Check for create/add/new/make
        if (inputLower.contains("create") || inputLower.contains("add") || 
            inputLower.contains("new") || inputLower.contains("make")) {
            // Check if it's about creating tasks for an existing project
            // Patterns like: "create task for X", "add task to X", "create task in X"
            if (inputLower.contains("task") && 
                (inputLower.contains("for") || inputLower.contains("to") || inputLower.contains("in"))) {
                return UserIntent.CREATE_TASK
            }
            // Check if it's about tasks (without project creation context)
            if (inputLower.contains("task") && !inputLower.contains("project")) {
                return UserIntent.CREATE_TASK
            }
            // Check for project creation (with or without tasks)
            if (inputLower.contains("project")) {
                return UserIntent.CREATE_PROJECT
            }
            // If no specific mention, default to project creation
            // (most common use case)
            return UserIntent.CREATE_PROJECT
        }
        
        return UserIntent.UNKNOWN
    }

    /**
     * Create a focused prompt based on the detected intent
     */
    private fun createFocusedPrompt(userInput: String, intent: UserIntent): String {
        return when (intent) {
            UserIntent.CREATE_PROJECT -> createProjectPrompt(userInput)
            UserIntent.CREATE_TASK -> createTaskPrompt(userInput)
            UserIntent.UPDATE_PROJECT -> updateProjectPrompt(userInput)
            UserIntent.UPDATE_TASK -> updateTaskPrompt(userInput)
            UserIntent.DELETE_PROJECT -> deleteProjectPrompt(userInput)
            UserIntent.DELETE_TASK -> deleteTaskPrompt(userInput)
            UserIntent.UNKNOWN -> ""
        }
    }

    /**
     * Prompt for creating a project
     */
    private fun createProjectPrompt(userInput: String): String {
        return """
            TASK: Extract and generate project information from the user input to create a new project.

            USER INPUT: "$userInput"

            IMPORTANT: For CREATE operations, you should use your knowledge to generate meaningful descriptions and send response in the RESPONSE FORMAT and replace the words and characters between <> by your value. 
            Extract information from the user input and enhance it with relevant details.

            Extract and generate the following:
            - Project title/name (from user input)
            - Project description (generate a meaningful description based on the project title/context if not fully provided)

            If the user mentions tasks, also extract and generate:
            - Task titles (from user input)
            - Task descriptions (generate meaningful descriptions based on task titles if not fully provided)

            Examples:
            - User: "Create a mobile app project" → Generate description about mobile app development
            - User: "Create e-commerce project with shopping cart and payment" → Generate descriptions for project and tasks

            RESPONSE FORMAT:
            ACTION: Create Project
            TITLE: <vaue here should be written by gemini>
            DESCRIPTION: <vaue here should be written by gemini>
            Return ONLY the formatted response. No explanations.
        """.trimIndent()
    }

    /**
     * Prompt for creating a task
     */
    private fun createTaskPrompt(userInput: String): String {
        return """
            TASK: Extract task information and project name from the user input. The system will search the database to find the project.

            USER INPUT: "$userInput"

            IMPORTANT: 
            - Extract the project name from user input (the system will search the database to find it)
            - For task descriptions, use your knowledge to generate meaningful descriptions if not fully provided by the user

            Extract the following:
            - Project name (to search for existing project in database)
            - Task title(s) (from user input)
            - Task description(s) (generate meaningful descriptions based on task titles if not fully provided)

            Examples:
            - User: "Create task for website project: Design homepage" → Generate description about homepage design
            - User: "Add login task to mobile app project" → Generate description about login functionality

            RESPONSE FORMAT:
            ACTION: Create Task
            SEARCH: [extract project name from user input]
            TASKS:
            - [Task Title from user input] | [generate meaningful task description]

            If multiple tasks:
            TASKS:
            - [Task 1 Title] | [generate meaningful task description]
            - [Task 2 Title] | [generate meaningful task description]

            Return ONLY the formatted response. No explanations.
        """.trimIndent()
    }

    /**
     * Prompt for updating a project
     */
    private fun updateProjectPrompt(userInput: String): String {
        return """
            TASK: Extract project information from the user input. The system will search the database to find the project.

            USER INPUT: "$userInput"

            IMPORTANT: You only need to extract information from the user input. The system will handle searching the database.

            Extract the following from the user input:
            - The existing project name (what to search for in the database)
            - New title (if the user wants to change the title)
            - New description (if the user wants to change the description)

            Examples of user input patterns:
            - "Update the project with name Social Media Project to Test Project" → SEARCH: Social Media Project, TITLE: Test Project
            - "Update Social Media project description to New description" → SEARCH: Social Media, DESCRIPTION: New description
            - "Change website project title to E-commerce Platform" → SEARCH: website, TITLE: E-commerce Platform

            RESPONSE FORMAT:
            ACTION: Update Project
            SEARCH: [extract the existing project name from user input]
            TITLE: [extract new title if user wants to change it]
            DESCRIPTION: [extract new description if user wants to change it]

            Return ONLY the formatted response. No explanations.
        """.trimIndent()
    }

    /**
     * Prompt for updating a task
     */
    private fun updateTaskPrompt(userInput: String): String {
        return """
            TASK: Extract task information from the user input. The system will search the database to find the task.

            USER INPUT: "$userInput"

            IMPORTANT: You only need to extract information from the user input. The system will handle searching the database.

            Extract the following from the user input:
            - The existing task name (what to search for in the database)
            - New title (if the user wants to change the title)
            - New description (if the user wants to change the description)
            - New status (if the user wants to change the status, e.g., "to-do", "in-progress", "done")
            - Assignee (if the user wants to assign it to someone)

            Examples of user input patterns:
            - "Update the task with name Design UI to Design Homepage" → SEARCH: Design UI, TITLE: Design Homepage
            - "Change API Development task status to in-progress" → SEARCH: API Development, STATUS: in-progress
            - "Update Login task description to Implement authentication" → SEARCH: Login, DESCRIPTION: Implement authentication

            RESPONSE FORMAT:
            ACTION: Update Task
            SEARCH: [extract the existing task name from user input]
            TITLE: [extract new title if user wants to change it]
            DESCRIPTION: [extract new description if user wants to change it]
            STATUS: [extract new status if user wants to change it]
            ASSIGN TO: [extract assignee if user wants to assign it]

            Return ONLY the formatted response. No explanations.
        """.trimIndent()
    }

    /**
     * Prompt for deleting a project
     */
    private fun deleteProjectPrompt(userInput: String): String {
        return """
            TASK: Extract project name from the user input. The system will search the database to find and delete the project.

            USER INPUT: "$userInput"

            IMPORTANT: You only need to extract the project name from the user input & place it in the SEARCH field. The system will handle searching the database and deleting it.

            Extract the project name/identifier that the user wants to delete.

            Examples of user input patterns:
            - "Delete the project with name Social Media project" to SEARCH: Social Media project
            - "Remove website project" to SEARCH: website
            - "Delete my test project" to SEARCH: test
            - "Remove the project called E-commerce Platform" to SEARCH: E-commerce Platform

            Example RESPONSE FORMAT:
            ACTION: Delete Project
            SEARCH: Social Media project

            Return ONLY the formatted response. No explanations.
        """.trimIndent()
    }

    /**
     * Prompt for deleting a task
     */
    private fun deleteTaskPrompt(userInput: String): String {
        return """
            TASK: Extract task name from the user input. The system will search the database to find and delete the task.

            USER INPUT: "$userInput"

            IMPORTANT: You only need to extract the task name from the user input. The system will handle searching the database and deleting it.

            Extract the task name/identifier that the user wants to delete.

            Examples of user input patterns:
            - "Delete the task with name Design UI" → SEARCH: Design UI
            - "Remove API Development task" → SEARCH: API Development
            - "Delete my login task" → SEARCH: login
            - "Remove the task called Homepage Design" → SEARCH: Homepage Design

            RESPONSE FORMAT:
            ACTION: Delete Task
            SEARCH: [extract the task name from user input]

            Return ONLY the formatted response. No explanations.
        """.trimIndent()
    }

    private suspend fun executeCommand(aiResponse: String, originalUserInput: String) {
        Log.d("AIAssistant", "Starting executeCommand with response: $aiResponse")
        Log.d("AIAssistant", "Full AI Response:")
        Log.d("AIAssistant", "==================================================")
        Log.d("AIAssistant", aiResponse)
        Log.d("AIAssistant", "==================================================")

        try {
            // Parse text response instead of JSON
            Log.d("AIAssistant", "Parsing text response...")
            val command = parseTextResponse(aiResponse)

            if (command == null) {
                Log.e("AIAssistant", "Failed to parse text response")
                _executionResult.value = CommandExecutionResult(
                    success = false,
                    message = "Failed to understand AI response. This might be due to:\n" +
                            "• Malformed response format\n" +
                            "• Missing ACTION field\n" +
                            "• Unsupported action type\n\n" +
                            "Raw response (first 300 chars): ${aiResponse.take(300)}..."
                )
                return
            }

            Log.d("AIAssistant", "Successfully parsed command: $command")
            Log.d("AIAssistant", "Command action: ${command.action}")
            Log.d("AIAssistant", "Command title: ${command.title}")
            Log.d("AIAssistant", "Command projectTitle: ${command.projectTitle}")
            Log.d("AIAssistant", "Command searchText: ${command.searchText}")

            // Convert Action to list of strings with better validation
            val actions = when (command.action) {
                is String -> {
                    // Handle comma-separated actions in text
                    val actionList = command.action.split(",")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                    Log.d("AIAssistant", "Action is String with multiple: $actionList")
                    actionList
                }

                is List<*> -> {
                    Log.d("AIAssistant", "Action is List: ${command.action}")
                    val stringActions = command.action.filterIsInstance<String>()
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                    Log.d("AIAssistant", "Filtered string actions: $stringActions")
                    stringActions
                }

                null -> {
                    Log.w("AIAssistant", "Action is null")
                    emptyList()
                }

                else -> {
                    Log.w(
                        "AIAssistant",
                        "Action is unknown type: ${command.action?.javaClass?.simpleName}"
                    )
                    // Try to convert to string and parse
                    val actionStr = command.action.toString().trim()
                    if (actionStr.isNotBlank() && actionStr != "null") {
                        listOf(actionStr)
                    } else {
                        emptyList()
                    }
                }
            }

            Log.d("AIAssistant", "Final actions list: $actions")

            // Log each individual action for debugging
            for ((index, action) in actions.withIndex()) {
                Log.d(
                    "AIAssistant",
                    "Action ${index + 1}: '$action' (type: ${action.javaClass.simpleName})"
                )
            }

            if (actions.isEmpty()) {
                Log.w("AIAssistant", "No valid actions detected in command")
                _executionResult.value = CommandExecutionResult(
                    success = false,
                    message = "No valid actions detected. Please try rephrasing your request.\n\n" +
                            "Examples:\n" +
                            "• 'Create a project called Mobile App'\n" +
                            "• 'Create task for website project'\n" +
                            "• 'Update mobile app project description'\n\n" +
                            "AI response preview: ${aiResponse.take(200)}..."
                )
                return
            }

            // CRITICAL SAFETY CHECK: Prevent create actions when user explicitly requests delete/remove
            val userInputLower = originalUserInput.lowercase()
            val hasDeleteIntent = userInputLower.contains("delete") || userInputLower.contains("remove")
            val hasUpdateIntent = userInputLower.contains("update") || userInputLower.contains("modify") || 
                                 userInputLower.contains("edit") || userInputLower.contains("change")
            
            val hasCreateAction = actions.any { action ->
                val normalized = normalizeActionName(action)
                normalized == "create project" || normalized == "create task"
            }
            
            if (hasDeleteIntent && hasCreateAction) {
                Log.e("AIAssistant", "SAFETY BLOCK: User requested DELETE but AI returned CREATE action")
                _executionResult.value = CommandExecutionResult(
                    success = false,
                    message = "Error: You requested to DELETE a project, but the system tried to CREATE instead.\n\n" +
                            "This appears to be a misinterpretation. Please try:\n" +
                            "• 'Delete [project name] project'\n" +
                            "• 'Remove [project name] project'\n\n" +
                            "Your original request: \"$originalUserInput\""
                )
                return
            }
            
            if (hasUpdateIntent && hasCreateAction) {
                Log.e("AIAssistant", "SAFETY BLOCK: User requested UPDATE but AI returned CREATE action")
                _executionResult.value = CommandExecutionResult(
                    success = false,
                    message = "Error: You requested to UPDATE a project, but the system tried to CREATE instead.\n\n" +
                            "This appears to be a misinterpretation. Please try:\n" +
                            "• 'Update [project name] project description'\n" +
                            "• 'Modify [project name] project'\n\n" +
                            "Your original request: \"$originalUserInput\""
                )
                return
            }

            // Validate actions before execution
            val validationResult = validateActions(actions, command, originalUserInput)
            if (!validationResult.isValid) {
                Log.w("AIAssistant", "Action validation failed: ${validationResult.errorMessage}")
                _executionResult.value = CommandExecutionResult(
                    success = false,
                    message = validationResult.errorMessage
                )
                return
            }


            Log.d("AIAssistant", "Executing ${actions.size} validated actions sequentially...")
            // Execute actions in sequence
            executeActionsSequentially(actions, command)

        } catch (e: Exception) {
            Log.e("AIAssistant", "Command execution error", e)
            _executionResult.value = CommandExecutionResult(
                success = false,
                message = "Error executing command: ${e.message}\n\n" +
                        "This might be due to:\n" +
                        "• Network connectivity issues\n" +
                        "• Database errors\n" +
                        "• Invalid command format\n\n" +
                        "Please try again or rephrase your request."
            )
        }
    }

    private fun parseTextResponse(response: String): AICommandResponse? {
        Log.d("AIAssistant", "Parsing text response of length: ${response.length}")

        try {
            // Clean and normalize the response
            val cleanResponse = response.trim()
            if (cleanResponse.isEmpty()) {
                Log.w("AIAssistant", "Empty response after cleaning")
                return null
            }

            val lines = cleanResponse.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .filterNot { it.startsWith("```") } // Remove markdown code blocks
                .filterNot { it.startsWith("OUTPUT:") } // Remove OUTPUT: headers
                .filterNot { it.startsWith("Input:") } // Remove example inputs

            if (lines.isEmpty()) {
                Log.w("AIAssistant", "No valid content lines found")
                return null
            }

            var action: String? = null
            var title: String? = null
            var description: String? = null
            var projectTitle: String? = null
            var projectDescription: String? = null
            var searchText: String? = null
            var assignedTo: String? = null
            var status: String? = null
            val tasks = mutableListOf<TaskCommand>()

            var inTasksSection = false

            for (line in lines) {
                val cleanLine = line.trim()

                when {
                    // Action parsing - handle various formats
                    cleanLine.startsWith("ACTION:", ignoreCase = true) -> {
                        action = extractValue(cleanLine, "ACTION:")
                        inTasksSection = false
                        Log.d("AIAssistant", "Parsed ACTION: $action")
                    }

                    // Title parsing
                    cleanLine.startsWith("TITLE:", ignoreCase = true) -> {
                        title = extractValue(cleanLine, "TITLE:")
                        inTasksSection = false
                    }

                    // Description parsing
                    cleanLine.startsWith("DESCRIPTION:", ignoreCase = true) -> {
                        description = extractValue(cleanLine, "DESCRIPTION:")
                        inTasksSection = false
                    }

                    // Project-specific fields
                    cleanLine.startsWith("PROJECT TITLE:", ignoreCase = true) -> {
                        projectTitle = extractValue(cleanLine, "PROJECT TITLE:")
                        inTasksSection = false
                    }

                    cleanLine.startsWith("PROJECT DESCRIPTION:", ignoreCase = true) -> {
                        projectDescription = extractValue(cleanLine, "PROJECT DESCRIPTION:")
                        inTasksSection = false
                    }

                    // Search text for finding existing items
                    cleanLine.startsWith("SEARCH:", ignoreCase = true) -> {
                        searchText = extractValue(cleanLine, "SEARCH:")
                        inTasksSection = false
                    }

                    // Assignment and status
                    cleanLine.startsWith("ASSIGN TO:", ignoreCase = true) ||
                            cleanLine.startsWith("ASSIGNED TO:", ignoreCase = true) -> {
                        assignedTo = extractValue(
                            cleanLine,
                            if (cleanLine.contains("ASSIGN TO:")) "ASSIGN TO:" else "ASSIGNED TO:"
                        )
                        inTasksSection = false
                    }

                    cleanLine.startsWith("STATUS:", ignoreCase = true) -> {
                        status = extractValue(cleanLine, "STATUS:")
                        inTasksSection = false
                    }

                    // Tasks section
                    cleanLine.equals("TASKS:", ignoreCase = true) -> {
                        inTasksSection = true
                        Log.d("AIAssistant", "Entering TASKS section")
                    }

                    // Parse individual tasks
                    inTasksSection && (cleanLine.startsWith("-") || cleanLine.startsWith("•") || cleanLine.startsWith(
                        "*"
                    )) -> {
                        val parsedTask = parseTaskLine(cleanLine)
                        if (parsedTask != null) {
                            tasks.add(parsedTask)
                            Log.d("AIAssistant", "Parsed task: ${parsedTask.title}")
                        }
                    }

                    // Handle cases where action might be embedded in other formats - BE MORE STRICT
                    cleanLine.contains("Create Project", ignoreCase = true) && action == null &&
                            (cleanLine.startsWith(
                                "ACTION:",
                                ignoreCase = true
                            ) || cleanLine.contains("ACTION:", ignoreCase = true)) -> {
                        action = "Create Project"
                        Log.d("AIAssistant", "Inferred ACTION from content: $action")
                    }

                    cleanLine.contains("Create Task", ignoreCase = true) && action == null &&
                            (cleanLine.startsWith(
                                "ACTION:",
                                ignoreCase = true
                            ) || cleanLine.contains("ACTION:", ignoreCase = true)) -> {
                        action = "Create Task"
                        Log.d("AIAssistant", "Inferred ACTION from content: $action")
                    }

                    cleanLine.contains("Update Project", ignoreCase = true) && action == null &&
                            (cleanLine.startsWith(
                                "ACTION:",
                                ignoreCase = true
                            ) || cleanLine.contains("ACTION:", ignoreCase = true)) -> {
                        action = "Update Project"
                        Log.d("AIAssistant", "Inferred ACTION from content: $action")
                    }

                    cleanLine.contains("Delete Project", ignoreCase = true) && action == null &&
                            (cleanLine.startsWith(
                                "ACTION:",
                                ignoreCase = true
                            ) || cleanLine.contains("ACTION:", ignoreCase = true)) -> {
                        action = "Delete Project"
                        Log.d("AIAssistant", "Inferred ACTION from content: $action")
                    }
                }
            }

            // Validate required fields based on action type
            if (action.isNullOrBlank()) {
                Log.w("AIAssistant", "No valid action found in response")
                return null
            }

            // Auto-generate missing fields where possible
            if (action.contains("Create Project", ignoreCase = true)) {
                if (title.isNullOrBlank() && projectTitle.isNullOrBlank()) {
                    Log.w("AIAssistant", "Create Project action requires title")
                    return null
                }
                // Use projectTitle if title is missing
                if (title.isNullOrBlank()) title = projectTitle
                if (description.isNullOrBlank()) description = projectDescription
            }

            Log.d(
                "AIAssistant",
                "Successfully parsed - Action: $action, Title: $title, Tasks: ${tasks.size}"
            )

            return AICommandResponse(
                action = action,
                title = title,
                description = description,
                projectTitle = projectTitle,
                projectDescription = projectDescription,
                tasks = if (tasks.isNotEmpty()) tasks else null,
                searchText = searchText,
                assignedTo = assignedTo,
                status = status
            )

        } catch (e: Exception) {
            Log.e("AIAssistant", "Error parsing text response", e)
            return null
        }
    }

    /**
     * Helper method to extract values from formatted lines
     */
    private fun extractValue(line: String, prefix: String): String? {
        val value = line.substringAfter(prefix, "").trim()
        return if (value.isNotBlank() && value != line) value else null
    }

    /**
     * Parse individual task lines in various formats
     */
    private fun parseTaskLine(line: String): TaskCommand? {
        try {
            // Remove bullet points and clean
            val taskContent = line.removePrefix("-")
                .removePrefix("•")
                .removePrefix("*")
                .trim()

            if (taskContent.isBlank()) return null

            // Try different formats:
            // Format 1: "Title | Description"
            if (taskContent.contains("|")) {
                val parts = taskContent.split("|", limit = 2)
                if (parts.size == 2) {
                    val taskTitle = parts[0].trim()
                    val taskDesc = parts[1].trim()
                    if (taskTitle.isNotBlank()) {
                        return TaskCommand(taskTitle, taskDesc.ifBlank { taskTitle }, "to-do", null)
                    }
                }
            }

            // Format 2: "Title: Description" 
            if (taskContent.contains(":")) {
                val parts = taskContent.split(":", limit = 2)
                if (parts.size == 2) {
                    val taskTitle = parts[0].trim()
                    val taskDesc = parts[1].trim()
                    if (taskTitle.isNotBlank()) {
                        return TaskCommand(taskTitle, taskDesc.ifBlank { taskTitle }, "to-do", null)
                    }
                }
            }

            // Format 3: Just the title/description
            if (taskContent.isNotBlank()) {
                return TaskCommand(taskContent, taskContent, "to-do", null)
            }

        } catch (e: Exception) {
            Log.w("AIAssistant", "Error parsing task line: $line", e)
        }

        return null
    }

    private suspend fun executeActionsSequentially(
        actions: List<String>,
        command: AICommandResponse
    ) {
        Log.d("AIAssistant", "Starting sequential execution of actions: $actions")

        val results = mutableListOf<String>()
        var projectId: String? = null
        val taskIds = mutableListOf<String>()

        for ((index, rawAction) in actions.withIndex()) {
            // Normalize action names to handle variations
            val action = normalizeActionName(rawAction.trim())
            Log.d(
                "AIAssistant",
                "Executing action ${index + 1}/${actions.size}: $rawAction -> $action"
            )

            when (action) {
                "create project" -> {
                    Log.d("AIAssistant", "Executing create project action")
                    val result = executeCreateProject(command)
                    Log.d(
                        "AIAssistant",
                        "Create project result: ${result.success}, message: ${result.message}"
                    )
                    results.add(result.message)
                    if (result.success) {
                        projectId = result.createdProjectId
                        Log.d("AIAssistant", "Created project ID: $projectId")
                    } else {
                        // If project creation fails, stop execution
                        Log.w("AIAssistant", "Project creation failed, stopping execution")
                        _executionResult.value = CommandExecutionResult(
                            success = false,
                            message = result.message,
                            actions = listOf(rawAction)
                        )
                        return
                    }
                }

                "create task" -> {
                    Log.d("AIAssistant", "Executing create task action with projectId: $projectId")
                    var targetProjectId = projectId
                    
                    // If no projectId from previous action, search for project using searchText
                    if (targetProjectId == null && !command.searchText.isNullOrBlank()) {
                        Log.d("AIAssistant", "No projectId available, searching for project: ${command.searchText}")
                        when (val searchResult = projectRepository.searchProject(command.searchText)) {
                            is ProjectResult.SingleSuccess -> {
                                targetProjectId = searchResult.project.projectId
                                Log.d("AIAssistant", "Found project with ID: $targetProjectId")
                            }
                            is ProjectResult.Error -> {
                                Log.w("AIAssistant", "Project not found: ${searchResult.message}")
                                _executionResult.value = CommandExecutionResult(
                                    success = false,
                                    message = "Cannot create tasks: Project not found. ${searchResult.message}",
                                    actions = listOf(rawAction)
                                )
                                return
                            }
                            else -> {
                                Log.w("AIAssistant", "Unexpected result from project search")
                                _executionResult.value = CommandExecutionResult(
                                    success = false,
                                    message = "Cannot create tasks: Unexpected error searching for project",
                                    actions = listOf(rawAction)
                                )
                                return
                            }
                        }
                    }
                    
                    if (targetProjectId != null) {
                        val result = executeCreateTasks(command, targetProjectId)
                        Log.d(
                            "AIAssistant",
                            "Create tasks result: ${result.success}, created ${result.createdTaskIds.size} tasks"
                        )
                        results.add(result.message)
                        if (result.success) {
                            taskIds.addAll(result.createdTaskIds)
                        }
                    } else {
                        Log.w("AIAssistant", "Cannot create tasks: No project ID available and no search text provided")
                        results.add("Cannot create tasks: No project ID available. Please specify the project name to search for.")
                    }
                }

                "update project" -> {
                    Log.d("AIAssistant", "Executing update project action")
                    val result = executeUpdateProject(command)
                    Log.d("AIAssistant", "Update project result: ${result.success}")
                    results.add(result.message)
                }

                "delete project" -> {
                    Log.w("AIAssistant", "ATTEMPTING DELETE PROJECT OPERATION")
                    Log.w("AIAssistant", "Search text: ${command.searchText}")

                    // Safety check for delete operations
                    if (command.searchText.isNullOrBlank()) {
                        Log.w("AIAssistant", "BLOCKING DELETE: No search text provided")
                        results.add("⚠️ Delete operation blocked: No project identifier specified")
                    } else {
                        Log.d("AIAssistant", "Executing delete project action")
                        val result = executeDeleteProject(command)
                        Log.d("AIAssistant", "Delete project result: ${result.success}")
                        results.add(result.message)
                    }
                }

                "update task" -> {
                    Log.d("AIAssistant", "Executing update task action")
                    val result = executeUpdateTask(command)
                    Log.d("AIAssistant", "Update task result: ${result.success}")
                    results.add(result.message)
                }

                "delete task" -> {
                    Log.w("AIAssistant", "ATTEMPTING DELETE TASK OPERATION")
                    Log.w("AIAssistant", "Search text: ${command.searchText}")

                    // Safety check for delete operations
                    if (command.searchText.isNullOrBlank()) {
                        Log.w("AIAssistant", "BLOCKING DELETE: No search text provided")
                        results.add("⚠️ Delete operation blocked: No task identifier specified")
                    } else {
                        Log.d("AIAssistant", "Executing delete task action")
                        val result = executeDeleteTask(command)
                        Log.d("AIAssistant", "Delete task result: ${result.success}")
                        results.add(result.message)
                    }
                }

                "assign task", "assign task to" -> {
                    Log.d("AIAssistant", "Executing assign task action")
                    val result = executeAssignTask(command)
                    Log.d("AIAssistant", "Assign task result: ${result.success}")
                    results.add(result.message)
                }

                else -> {
                    Log.w("AIAssistant", "Unknown action: $rawAction (normalized: $action)")
                    results.add("Unknown action: $rawAction")
                }
            }
        }

        Log.d("AIAssistant", "All actions completed. Final results: $results")
        _executionResult.value = CommandExecutionResult(
            success = true,
            message = results.joinToString("\n"),
            actions = actions,
            createdProjectId = projectId,
            createdTaskIds = taskIds
        )
    }

    /**
     * Normalize action names to handle variations and ensure consistent matching
     * (Extra safety: deletion actions are only detected if they start with delete/remove)
     */
    private fun normalizeActionName(action: String): String {
        val normalized = action.lowercase().trim()

        Log.d("AIAssistant", "Normalizing action: '$action' -> '$normalized'")

        return when {
            // Project creation variations
            normalized.contains("create") && normalized.contains("project") -> {
                Log.d("AIAssistant", "Detected create project action")
                "create project"
            }

            normalized.contains("add") && normalized.contains("project") -> {
                Log.d("AIAssistant", "Detected add project action -> create project")
                "create project"
            }

            normalized.contains("new") && normalized.contains("project") -> {
                Log.d("AIAssistant", "Detected new project action -> create project")
                "create project"
            }

            normalized.contains("make") && normalized.contains("project") -> {
                Log.d("AIAssistant", "Detected make project action -> create project")
                "create project"
            }

            // Task creation variations
            normalized.contains("create") && normalized.contains("task") -> {
                Log.d("AIAssistant", "Detected create task action")
                "create task"
            }

            normalized.contains("add") && normalized.contains("task") -> {
                Log.d("AIAssistant", "Detected add task action -> create task")
                "create task"
            }

            normalized.contains("new") && normalized.contains("task") -> {
                Log.d("AIAssistant", "Detected new task action -> create task")
                "create task"
            }

            normalized.contains("make") && normalized.contains("task") -> {
                Log.d("AIAssistant", "Detected make task action -> create task")
                "create task"
            }

            // Project update variations - ONLY if explicitly mentioned
            normalized.startsWith("update") && normalized.contains("project") -> {
                Log.d("AIAssistant", "Detected explicit update project action")
                "update project"
            }

            normalized.startsWith("modify") && normalized.contains("project") -> {
                Log.d("AIAssistant", "Detected explicit modify project action -> update project")
                "update project"
            }

            normalized.startsWith("edit") && normalized.contains("project") -> {
                Log.d("AIAssistant", "Detected explicit edit project action -> update project")
                "update project"
            }

            normalized.startsWith("change") && normalized.contains("project") -> {
                Log.d("AIAssistant", "Detected explicit change project action -> update project")
                "update project"
            }

            // Task update variations - ONLY if explicitly mentioned
            normalized.startsWith("update") && normalized.contains("task") -> {
                Log.d("AIAssistant", "Detected explicit update task action")
                "update task"
            }

            normalized.startsWith("modify") && normalized.contains("task") -> {
                Log.d("AIAssistant", "Detected explicit modify task action -> update task")
                "update task"
            }

            normalized.startsWith("edit") && normalized.contains("task") -> {
                Log.d("AIAssistant", "Detected explicit edit task action -> update task")
                "update task"
            }

            normalized.startsWith("change") && normalized.contains("task") -> {
                Log.d("AIAssistant", "Detected explicit change task action -> update task")
                "update task"
            }

            // Project deletion variations - VERY STRICT - must start with delete/remove
            normalized.startsWith("delete") && normalized.contains("project") -> {
                Log.w(
                    "AIAssistant",
                    "DETECTED DELETE PROJECT ACTION - This is a destructive operation!"
                )
                "delete project"
            }

            normalized.startsWith("remove") && normalized.contains("project") -> {
                Log.w(
                    "AIAssistant",
                    "DETECTED REMOVE PROJECT ACTION - This is a destructive operation!"
                )
                "delete project"
            }

            // Task deletion variations - VERY STRICT - must start with delete/remove
            normalized.startsWith("delete") && normalized.contains("task") -> {
                Log.w(
                    "AIAssistant",
                    "DETECTED DELETE TASK ACTION - This is a destructive operation!"
                )
                "delete task"
            }

            normalized.startsWith("remove") && normalized.contains("task") -> {
                Log.w(
                    "AIAssistant",
                    "DETECTED REMOVE TASK ACTION - This is a destructive operation!"
                )
                "delete task"
            }

            // Task assignment variations
            normalized.contains("assign") && normalized.contains("task") -> {
                Log.d("AIAssistant", "Detected assign task action")
                "assign task"
            }

            normalized.contains("assign") -> {
                Log.d("AIAssistant", "Detected assign action -> assign task")
                "assign task"
            }

            // Return as-is if no match found
            else -> {
                Log.d(
                    "AIAssistant",
                    "No specific action pattern matched, returning as-is: '$normalized'"
                )
                normalized
            }
        }
    }

    /**
     * Validation result for actions
     */
    private data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String = ""
    )

    /**
     * Validate actions and required fields before execution
     */
    private fun validateActions(
        actions: List<String>,
        command: AICommandResponse,
        originalUserInput: String = ""
    ): ValidationResult {
        for (action in actions) {
            val normalizedAction = normalizeActionName(action)

            when (normalizedAction) {
                "create project" -> {
                    val title = command.title ?: command.projectTitle
                    if (title.isNullOrBlank()) {
                        return ValidationResult(
                            false,
                            "Create Project action requires a title. Please specify the project name."
                        )
                    }
                }

                "create task" -> {
                    if (command.tasks.isNullOrEmpty()) {
                        return ValidationResult(
                            false,
                            "Create Task action requires task details. Please specify what tasks to create."
                        )
                    }
                    // Check if we have a way to find the project
                    if (command.searchText.isNullOrBlank() && !actions.contains("Create Project")) {
                        return ValidationResult(
                            false,
                            "Create Task action requires either:\n" +
                                    "• A project name to search for (e.g., 'for mobile app project')\n" +
                                    "• Or create the project first"
                        )
                    }
                }

                "update project", "delete project" -> {
                    if (command.searchText.isNullOrBlank()) {
                        return ValidationResult(
                            false,
                            "${action.replaceFirstChar { it.uppercaseChar() }} action requires a project identifier. " +
                                    "Please specify which project to ${normalizedAction.split(" ")[0]}."
                        )
                    }
                }

                "update task", "delete task" -> {
                    if (command.searchText.isNullOrBlank()) {
                        return ValidationResult(
                            false,
                            "${action.replaceFirstChar { it.uppercaseChar() }} action requires a task identifier. " +
                                    "Please specify which task to ${normalizedAction.split(" ")[0]}."
                        )
                    }
                }

                "assign task" -> {
                    if (command.searchText.isNullOrBlank() || command.assignedTo.isNullOrBlank()) {
                        return ValidationResult(
                            false,
                            "Assign Task action requires both:\n" +
                                    "• Task identifier (which task to assign)\n" +
                                    "• Assignee (who to assign it to)"
                        )
                    }
                }
            }
        }

        return ValidationResult(true)
    }

    private suspend fun executeCreateProject(command: AICommandResponse): CommandExecutionResult {
        val title = command.title ?: command.projectTitle
        val description = command.description ?: command.projectDescription

        if (title.isNullOrBlank() || description.isNullOrBlank()) {
            return CommandExecutionResult(
                success = false,
                message = "Project title and description are required"
            )
        }

        return when (val result = projectRepository.createProject(title, description)) {
            is ProjectResult.SingleSuccess -> {
                CommandExecutionResult(
                    success = true,
                    message = " Project '$title' created successfully",
                    createdProjectId = result.project.projectId
                )
            }
            is ProjectResult.Error -> {
                CommandExecutionResult(
                    success = false,
                    message = " Failed to create project: ${result.message}"
                )
            }

            else -> {
                CommandExecutionResult(
                    success = false,
                    message = " Unexpected result from project creation"
                )
            }
        }
    }

    private suspend fun executeCreateTasks(
        command: AICommandResponse,
        projectId: String
    ): CommandExecutionResult {
        val tasks = command.tasks
        if (tasks.isNullOrEmpty()) {
            return CommandExecutionResult(
                success = false,
                message = "No tasks specified for creation"
            )
        }

        val createdTaskIds = mutableListOf<String>()
        val results = mutableListOf<String>()

        for (task in tasks) {
            when (val result = taskRepository.createTask(
                title = task.title,
                description = task.description,
                projectId = projectId,
                status = task.status ?: "to-do",
                assignedTo = task.assignedTo
            )) {
                is TaskResult.SingleSuccess -> {
                    createdTaskIds.add(result.task.id ?: "")
                    results.add(" Task '${task.title}' created")
                }
                is TaskResult.Error -> {
                    results.add(" Failed to create task '${task.title}': ${result.message}")
                }
                else -> {
                    results.add(" Unexpected result for task '${task.title}'")
                }
            }
        }

        return CommandExecutionResult(
            success = createdTaskIds.isNotEmpty(),
            message = results.joinToString("\n"),
            createdTaskIds = createdTaskIds
        )
    }

    private suspend fun executeUpdateProject(command: AICommandResponse): CommandExecutionResult {
        val searchText = command.searchText
        if (searchText.isNullOrBlank()) {
            return CommandExecutionResult(
                success = false,
                message = "Search text is required for project update"
            )
        }

        // First, search for the project
        return when (val searchResult = projectRepository.searchProject(searchText)) {
            is ProjectResult.SingleSuccess -> {
                val projectId = searchResult.project.projectId
                when (val updateResult = projectRepository.updateProject(
                    projectId,
                    command.title,
                    command.description
                )) {
                    is ProjectResult.SingleSuccess -> {
                        CommandExecutionResult(
                            success = true,
                            message = " Project '${searchResult.project.name}' updated successfully"
                        )
                    }

                    is ProjectResult.Error -> {
                        CommandExecutionResult(
                            success = false,
                            message = " Failed to update project: ${updateResult.message}"
                        )
                    }

                    else -> {
                        CommandExecutionResult(
                            success = false,
                            message = " Unexpected result from project update"
                        )
                    }
                }
            }
            is ProjectResult.Error -> {
                CommandExecutionResult(
                    success = false,
                    message = " Project not found: ${searchResult.message}"
                )
            }

            else -> {
                CommandExecutionResult(
                    success = false,
                    message = " Unexpected result from project search"
                )
            }
        }
    }

    private suspend fun executeDeleteProject(command: AICommandResponse): CommandExecutionResult {
        val searchText = command.searchText
        if (searchText.isNullOrBlank()) {
            return CommandExecutionResult(
                success = false,
                message = "Search text is required for project deletion"
            )
        }

        return when (val searchResult = projectRepository.searchProject(searchText)) {
            is ProjectResult.SingleSuccess -> {
                val projectId = searchResult.project.projectId
                when (val deleteResult = projectRepository.deleteProject(projectId)) {
                    is ProjectResult.Success -> {
                        CommandExecutionResult(
                            success = true,
                            message = " Project '${searchResult.project.name}' deleted successfully"
                        )
                    }

                    is ProjectResult.Error -> {
                        CommandExecutionResult(
                            success = false,
                            message = " Failed to delete project: ${deleteResult.message}"
                        )
                    }

                    else -> {
                        CommandExecutionResult(
                            success = false,
                            message = " Unexpected result from project deletion"
                        )
                    }
                }
            }

            is ProjectResult.Error -> {
                CommandExecutionResult(
                    success = false,
                    message = " Project not found: ${searchResult.message}"
                )
            }

            else -> {
                CommandExecutionResult(
                    success = false,
                    message = " Unexpected result from project search"
                )
            }
        }
    }

    private suspend fun executeUpdateTask(command: AICommandResponse): CommandExecutionResult {
        val searchText = command.searchText
        if (searchText.isNullOrBlank()) {
            return CommandExecutionResult(
                success = false,
                message = "Search text is required for task update"
            )
        }

        return when (val searchResult = taskRepository.searchTask(searchText)) {
            is TaskResult.SingleSuccess -> {
                val taskId = searchResult.task.id ?: ""
                when (val updateResult = taskRepository.updateTask(
                    taskId,
                    command.title,
                    command.description,
                    command.status,
                    assignedTo = command.assignedTo
                )) {
                    is TaskResult.SingleSuccess -> {
                        CommandExecutionResult(
                            success = true,
                            message = " Task '${searchResult.task.title}' updated successfully"
                        )
                    }

                    is TaskResult.Error -> {
                        CommandExecutionResult(
                            success = false,
                            message = " Failed to update task: ${updateResult.message}"
                        )
                    }

                    else -> {
                        CommandExecutionResult(
                            success = false,
                            message = " Unexpected result from task update"
                        )
                    }
                }
            }

            is TaskResult.Error -> {
                CommandExecutionResult(
                    success = false,
                    message = " Task not found: ${searchResult.message}"
                )
            }

            else -> {
                CommandExecutionResult(
                    success = false,
                    message = " Unexpected result from task search"
                )
            }
        }
    }

    private suspend fun executeDeleteTask(command: AICommandResponse): CommandExecutionResult {
        val searchText = command.searchText
        if (searchText.isNullOrBlank()) {
            return CommandExecutionResult(
                success = false,
                message = "Search text is required for task deletion"
            )
        }

        return when (val searchResult = taskRepository.searchTask(searchText)) {
            is TaskResult.SingleSuccess -> {
                val taskId = searchResult.task.id ?: ""
                when (val deleteResult = taskRepository.deleteTask(taskId)) {
                    is TaskResult.Success -> {
                        CommandExecutionResult(
                            success = true,
                            message = " Task '${searchResult.task.title}' deleted successfully"
                        )
                    }

                    is TaskResult.Error -> {
                        CommandExecutionResult(
                            success = false,
                            message = " Failed to delete task: ${deleteResult.message}"
                        )
                    }

                    else -> {
                        CommandExecutionResult(
                            success = false,
                            message = " Unexpected result from task deletion"
                        )
                    }
                }
            }
            is TaskResult.Error -> {
                CommandExecutionResult(
                    success = false,
                    message = " Task not found: ${searchResult.message}"
                )
            }

            else -> {
                CommandExecutionResult(
                    success = false,
                    message = " Unexpected result from task search"
                )
            }
        }
    }

    private suspend fun executeAssignTask(command: AICommandResponse): CommandExecutionResult {
        val searchText = command.searchText
        val assignee = command.assignedTo

        if (searchText.isNullOrBlank() || assignee.isNullOrBlank()) {
            return CommandExecutionResult(
                success = false,
                message = "Search text and assignee email are required for task assignment"
            )
        }

        return when (val searchResult = taskRepository.searchTask(searchText)) {
            is TaskResult.SingleSuccess -> {
                val taskId = searchResult.task.id ?: ""
                when (val updateResult = taskRepository.updateTask(
                    taskId,
                    assignedTo = assignee
                )) {
                    is TaskResult.SingleSuccess -> {
                        CommandExecutionResult(
                            success = true,
                            message = " Task '${searchResult.task.title}' assigned to $assignee"
                        )
                    }

                    is TaskResult.Error -> {
                        CommandExecutionResult(
                            success = false,
                            message = " Failed to assign task: ${updateResult.message}"
                        )
                    }

                    else -> {
                        CommandExecutionResult(
                            success = false,
                            message = " Unexpected result from task assignment"
                        )
                    }
                }
            }

            is TaskResult.Error -> {
                CommandExecutionResult(
                    success = false,
                    message = " Task not found: ${searchResult.message}"
                )
            }

            else -> {
                CommandExecutionResult(
                    success = false,
                    message = " Unexpected result from task search"
                )
            }
        }
    }

    fun clearExecutionResult() {
        _executionResult.value = null
    }

    fun refreshModels() {
        viewModelScope.launch {
            loadAvailableModels()
        }
    }

    /**
     * Check if the AI model is properly loaded and ready for use
     */
    fun isModelReady(): Boolean {
        return aiProviderManager.isReady()
    }

    /**
     * Force reload the current model if there are issues
     */
    fun reloadCurrentModel() {
        val modelId = _currentModelId.value
        if (modelId != null) {
            Log.d("AIAssistant", "Force reloading current model: $modelId")
            _currentModelId.value = null
            loadModel(modelId)
        } else {
            Log.w("AIAssistant", "No current model to reload")
            _statusMessage.value = "No model currently loaded to reload"
        }
    }

    // Legacy methods for backward compatibility
    @Deprecated("Use processNaturalLanguageCommand instead")
    fun generateProjectIdea(topic: String) {
        processNaturalLanguageCommand("Create a project about $topic")
    }

    @Deprecated("Use processNaturalLanguageCommand instead")
    fun breakdownProjectIntoTasks(projectName: String, projectDescription: String) {
        processNaturalLanguageCommand("Create project '$projectName' with description '$projectDescription' and break it into tasks")
    }
}
