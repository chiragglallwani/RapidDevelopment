package com.runanywhere.startup_hackathon20

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.runanywhere.startup_hackathon20.ai.AiProvider
import com.runanywhere.startup_hackathon20.automation.AutomationEnvelope
import com.runanywhere.startup_hackathon20.automation.ProjectAutomationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// Simple Message Data Class
data class ChatMessage(
    val text: String,
    val isUser: Boolean
)

// ViewModel
class ChatViewModel(
    private val aiProvider: AiProvider,
    private val automationManager: ProjectAutomationManager
) : ViewModel() {

    private val gson = Gson()
    private val automationRegex = Regex("<automation>([\\s\\S]*?)</automation>", RegexOption.IGNORE_CASE)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _availableModels = MutableStateFlow<List<String>>(emptyList())
    val availableModels: StateFlow<List<String>> = _availableModels

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgress: StateFlow<Float?> = _downloadProgress

    private val _currentModelId = MutableStateFlow<String?>(null)
    val currentModelId: StateFlow<String?> = _currentModelId

    private val _statusMessage = MutableStateFlow<String>("Initializing...")
    val statusMessage: StateFlow<String> = _statusMessage

    init {
        loadAvailableModels()
    }

    private fun loadAvailableModels() {
        viewModelScope.launch {
            try {
                val models = aiProvider.getModels()
                _availableModels.value = models
                _statusMessage.value = "Ready - Please select a model"
            } catch (e: Exception) {
                _statusMessage.value = "Error loading models: ${e.message}"
            }
        }
    }

    fun sendMessage(text: String) {
        if (_currentModelId.value == null) {
            _statusMessage.value = "Please select a model first"
            return
        }

        // Add user message
        _messages.value += ChatMessage(text, isUser = true)

        viewModelScope.launch {
            _isLoading.value = true

            try {
                val prompt = buildPrompt(text)
                var assistantResponse = ""

                aiProvider.generateContent(_currentModelId.value!!, prompt).collect { token ->
                    assistantResponse += token
                    val displayText = stripAutomationContent(assistantResponse)

                    val currentMessages = _messages.value.toMutableList()
                    if (currentMessages.lastOrNull()?.isUser == false) {
                        currentMessages[currentMessages.lastIndex] =
                            ChatMessage(displayText, isUser = false)
                    } else {
                        currentMessages.add(ChatMessage(displayText, isUser = false))
                    }
                    _messages.value = currentMessages
                }

                // Ensure the final assistant message is the cleaned text
                val cleaned = stripAutomationContent(assistantResponse)
                replaceLastAssistantMessage(cleaned)

                handleAutomationBlock(assistantResponse)
            } catch (e: Exception) {
                _messages.value += ChatMessage("Error: ${e.message}", isUser = false)
            }

            _isLoading.value = false
        }
    }

    private fun buildPrompt(userMessage: String): String {
        val history = _messages.value
            .takeLast(6)
            .joinToString(separator = "\n") { message ->
                val speaker = if (message.isUser) "User" else "Assistant"
                "$speaker: ${message.text}"
            }

        return """
            You are TechnoTrak's RunAnywhere-powered project automation assistant. Your job is to help manage software projects, create tasks, and coordinate developers.

            When responding:
            1. Provide a helpful, conversational response for the user.
            2. If an automation action is needed (e.g., create a project or task), append a single automation block after your human-readable reply using this exact format:
               <automation>{"actions":[{"type":"create_project","project":{"name":"...","description":"..."},"tasks":[{"title":"...","description":"...","assigned_to":"developer name","status":"to-do"}]}]}</automation>
            3. The automation block must be valid JSON. Do not include comments, and use snake_case for field names.
            4. If you need more information before taking action, include follow_up_questions inside the automation block (list of strings).
            5. If no automation is required, still include an empty block like <automation>{"actions":[]}</automation>.

            Conversation so far:
            $history

            User: $userMessage

            Respond as described above.
        """.trimIndent()
    }

    private fun stripAutomationContent(raw: String): String {
        return raw.replace(automationRegex, "").trimEnd()
    }

    private fun replaceLastAssistantMessage(newText: String) {
        val currentMessages = _messages.value.toMutableList()
        val lastIndex = currentMessages.indexOfLast { !it.isUser }
        if (lastIndex >= 0) {
            currentMessages[lastIndex] = ChatMessage(newText, isUser = false)
            _messages.value = currentMessages
        }
    }

    private suspend fun handleAutomationBlock(fullResponse: String) {
        val match = automationRegex.find(fullResponse) ?: return
        val jsonPayload = match.groupValues.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() } ?: return

        val envelope = try {
            gson.fromJson(jsonPayload, AutomationEnvelope::class.java)
        } catch (ex: JsonSyntaxException) {
            _messages.value += ChatMessage("[Automation] Failed to parse automation block: ${ex.message}", isUser = false)
            null
        }

        envelope?.let {
            val summary = automationManager.executeAutomation(it)
            val summaryText = buildSummaryMessage(summary, it.summary)
            if (summaryText.isNotBlank()) {
                _messages.value += ChatMessage(summaryText, isUser = false)
            }
        }
    }

    private fun buildSummaryMessage(
        summary: ProjectAutomationManager.AutomationSummary,
        modelSummary: String?
    ): String {
        val sections = mutableListOf<String>()

        if (!modelSummary.isNullOrBlank()) {
            sections += "[Automation] 📝 Summary:\n$modelSummary".trimEnd()
        }

        if (summary.createdMessages.isNotEmpty()) {
            sections += buildString {
                appendLine("[Automation] ✅ Actions executed:")
                summary.createdMessages.forEach { appendLine(it) }
            }.trimEnd()
        }

        if (summary.errorMessages.isNotEmpty()) {
            sections += buildString {
                appendLine("[Automation] ⚠️ Issues detected:")
                summary.errorMessages.forEach { appendLine("- $it") }
            }.trimEnd()
        }

        if (summary.followUps.isNotEmpty()) {
            sections += buildString {
                appendLine("[Automation] ❓ Next questions:")
                summary.followUps.forEach { appendLine("- $it") }
            }.trimEnd()
        }

        return sections.joinToString(separator = "\n\n").trim()
    }

    fun selectModel(modelId: String) {
        _currentModelId.value = modelId
        _statusMessage.value = "Model selected: $modelId"
    }

    fun refreshModels() {
        loadAvailableModels()
    }
}
