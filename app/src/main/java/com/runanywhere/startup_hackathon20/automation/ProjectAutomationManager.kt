package com.runanywhere.startup_hackathon20.automation

import android.util.Log
import com.runanywhere.startup_hackathon20.data.models.Task
import com.runanywhere.startup_hackathon20.data.models.User
import com.runanywhere.startup_hackathon20.data.repository.ProjectRepository
import com.runanywhere.startup_hackathon20.data.repository.ProjectResult
import com.runanywhere.startup_hackathon20.data.repository.TaskRepository
import com.runanywhere.startup_hackathon20.data.repository.TaskResult
import com.runanywhere.startup_hackathon20.data.repository.UserRepository
import com.runanywhere.startup_hackathon20.data.repository.UserResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Handles automation actions requested by the AI assistant. The manager translates
 * high-level instructions into concrete API calls and produces a human-readable summary.
 */
class ProjectAutomationManager(
    private val projectRepository: ProjectRepository,
    private val taskRepository: TaskRepository,
    private val userRepository: UserRepository
) {

    suspend fun executeAutomation(envelope: AutomationEnvelope?): AutomationSummary {
        if (envelope?.actions.isNullOrEmpty()) {
            return AutomationSummary(
                createdMessages = emptyList(),
                errorMessages = emptyList(),
                followUps = envelope?.followUpQuestions.orEmpty()
            )
        }

        return withContext(Dispatchers.IO) {
            val developers = fetchDevelopers()
            val actionSummaries = mutableListOf<String>()
            val errors = mutableListOf<String>()
            val followUps = envelope.followUpQuestions.orEmpty().toMutableList()

            envelope.actions?.forEach { action ->
                when (action.type.lowercase(Locale.getDefault())) {
                    "create_project" -> {
                        val result = handleCreateProject(action, developers)
                        actionSummaries += result.createdMessages
                        errors += result.errorMessages
                        followUps += result.followUps
                    }
                    else -> {
                        errors += "Unsupported automation action: ${action.type}"
                    }
                }
            }

            AutomationSummary(actionSummaries, errors, followUps)
        }
    }

    private suspend fun fetchDevelopers(): List<User> {
        return when (val result = userRepository.getDevelopers()) {
            is UserResult.Success -> result.users
            is UserResult.Error -> {
                Log.e("AutomationManager", "Failed to load developers: ${result.message}")
                emptyList()
            }
        }
    }

    private suspend fun handleCreateProject(
        action: AutomationAction,
        developers: List<User>
    ): ActionResult {
        val projectInfo = action.project
        if (projectInfo?.name.isNullOrBlank() || projectInfo?.description.isNullOrBlank()) {
            return ActionResult(
                createdMessages = emptyList(),
                errorMessages = listOf("Cannot create project without both name and description."),
                followUps = listOf("Please provide the project name and a short description so I can create it.")
            )
        }

        val projectResult = projectRepository.createProject(
            projectInfo!!.name!!.trim(),
            projectInfo.description!!.trim()
        )

        return when (projectResult) {
            is ProjectResult.SingleSuccess -> {
                val project = projectResult.project
                val taskMessages = mutableListOf<String>()
                val taskErrors = mutableListOf<String>()

                val createdTasks = mutableListOf<Task>()
                action.tasks.orEmpty().forEach { automationTask ->
                    val title = automationTask.title?.trim().orEmpty()
                    val description = automationTask.description?.trim().orEmpty()
                    if (title.isBlank() || description.isBlank()) {
                        taskErrors += "Skipped task because title/description was missing."
                        return@forEach
                    }

                    val assigneeName = automationTask.assignedTo?.trim().orEmpty()
                    val assigneeId = findDeveloperId(assigneeName, developers)
                    if (assigneeName.isNotEmpty() && assigneeId == null) {
                        taskErrors += "Could not find developer '$assigneeName' for task '$title'."
                    }

                    when (val taskResult = taskRepository.createTask(
                        title = title,
                        description = description,
                        projectId = project.projectId,
                        status = automationTask.status?.takeIf { it.isNotBlank() } ?: "to-do",
                        assignedTo = assigneeId
                    )) {
                        is TaskResult.SingleSuccess -> {
                            val createdTask = taskResult.task
                            createdTasks += createdTask
                            val assignedLabel = assigneeName.takeIf { it.isNotBlank() } ?: "Unassigned"
                            taskMessages += "• Task '${createdTask.title}' assigned to $assignedLabel"
                        }
                        is TaskResult.Error -> {
                            taskErrors += "Failed to create task '$title': ${taskResult.message}"
                        }
                        else -> {}
                    }
                }

                val projectMessage = "Created project '${project.name}' with ${createdTasks.size} tasks."

                ActionResult(
                    createdMessages = listOf(projectMessage) + taskMessages,
                    errorMessages = taskErrors,
                    followUps = emptyList()
                )
            }
            is ProjectResult.Error -> {
                ActionResult(
                    createdMessages = emptyList(),
                    errorMessages = listOf("Failed to create project: ${projectResult.message}"),
                    followUps = emptyList()
                )
            }
            else -> ActionResult(emptyList(), emptyList(), emptyList())
        }
    }

    private fun findDeveloperId(name: String, developers: List<User>): String? {
        if (name.isBlank()) return null
        val normalized = name.lowercase(Locale.getDefault())
        val exactMatch = developers.firstOrNull { developer ->
            developer.name.lowercase(Locale.getDefault()) == normalized
        }
        if (exactMatch != null) return exactMatch.userId

        return developers.firstOrNull { developer ->
            developer.name.lowercase(Locale.getDefault()).contains(normalized)
        }?.userId
    }

    data class AutomationSummary(
        val createdMessages: List<String>,
        val errorMessages: List<String>,
        val followUps: List<String>
    )

    private data class ActionResult(
        val createdMessages: List<String>,
        val errorMessages: List<String>,
        val followUps: List<String>
    )
}
