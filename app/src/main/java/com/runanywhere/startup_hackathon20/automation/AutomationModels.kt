package com.runanywhere.startup_hackathon20.automation

import com.google.gson.annotations.SerializedName

/**
 * Represents the automation payload emitted by the AI assistant inside the <automation> block.
 */
data class AutomationEnvelope(
    val actions: List<AutomationAction>? = null,
    @SerializedName("summary") val summary: String? = null,
    @SerializedName("follow_up_questions") val followUpQuestions: List<String>? = null
)

/**
 * Supported automation actions. Currently we only handle project creation,
 * but the structure is flexible for future actions.
 */
data class AutomationAction(
    val type: String,
    val project: AutomationProject? = null,
    val tasks: List<AutomationTask>? = null,
    @SerializedName("notes") val notes: String? = null
)

/**
 * Minimal project definition needed to create a project via the backend API.
 */
data class AutomationProject(
    val name: String? = null,
    val description: String? = null,
    @SerializedName("timeline") val timeline: String? = null
)

/**
 * Task definition requested by the AI. The assistant should provide at least title/description.
 */
data class AutomationTask(
    val title: String? = null,
    val description: String? = null,
    @SerializedName("assigned_to") val assignedTo: String? = null,
    val status: String? = null,
    @SerializedName("due_date") val dueDate: String? = null
)
