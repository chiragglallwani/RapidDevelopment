package com.runanywhere.startup_hackathon20.data.repository

import com.runanywhere.startup_hackathon20.data.api.RetrofitClient
import com.runanywhere.startup_hackathon20.data.models.CreateTaskRequest
import com.runanywhere.startup_hackathon20.data.models.Task
import com.runanywhere.startup_hackathon20.data.models.UpdateTaskRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class TaskResult {
    data class Success(val tasks: List<Task>) : TaskResult()
    data class SingleSuccess(val task: Task) : TaskResult()
    data class Error(val message: String) : TaskResult()
}

class TaskRepository {
    
    private val apiService = RetrofitClient.apiService
    
    suspend fun getTasks(projectId: String): TaskResult {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getTasks(projectId)
                
                if (response.isSuccessful && response.body() != null) {
                    val taskResponse = response.body()!!
                    
                    if (taskResponse.success && taskResponse.data != null) {
                        TaskResult.Success(taskResponse.data)
                    } else {
                        TaskResult.Success(emptyList())
                    }
                } else {
                    TaskResult.Error(response.message() ?: "Failed to fetch tasks")
                }
            } catch (e: Exception) {
                TaskResult.Error(e.message ?: "Network error occurred")
            }
        }
    }
    
    suspend fun createTask(
        title: String,
        description: String,
        projectId: String,
        status: String = "to-do",
        blockReason: String? = null,
        assignedTo: String? = null
    ): TaskResult {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.createTask(
                    CreateTaskRequest(title, description, projectId, status, blockReason, assignedTo)
                )
                
                if (response.isSuccessful && response.body() != null) {
                    val taskResponse = response.body()!!
                    
                    if (taskResponse.success && taskResponse.data != null) {
                        TaskResult.SingleSuccess(taskResponse.data)
                    } else {
                        TaskResult.Error(taskResponse.message ?: "Failed to create task")
                    }
                } else {
                    TaskResult.Error(response.message() ?: "Failed to create task")
                }
            } catch (e: Exception) {
                TaskResult.Error(e.message ?: "Network error occurred")
            }
        }
    }
    
    suspend fun updateTask(
        taskId: String,
        title: String? = null,
        description: String? = null,
        status: String? = null,
        blockReason: String? = null,
        assignedTo: String? = null
    ): TaskResult {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.updateTask(
                    taskId,
                    UpdateTaskRequest(title, description, status, blockReason, assignedTo)
                )
                
                if (response.isSuccessful && response.body() != null) {
                    val taskResponse = response.body()!!
                    
                    if (taskResponse.success && taskResponse.data != null) {
                        TaskResult.SingleSuccess(taskResponse.data)
                    } else {
                        TaskResult.Error(taskResponse.message ?: "Failed to update task")
                    }
                } else {
                    TaskResult.Error(response.message() ?: "Failed to update task")
                }
            } catch (e: Exception) {
                TaskResult.Error(e.message ?: "Network error occurred")
            }
        }
    }
    
    suspend fun deleteTask(taskId: String): TaskResult {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.deleteTask(taskId)
                
                if (response.isSuccessful) {
                    TaskResult.Success(emptyList())
                } else {
                    TaskResult.Error(response.message() ?: "Failed to delete task")
                }
            } catch (e: Exception) {
                TaskResult.Error(e.message ?: "Network error occurred")
            }
        }
    }
}

