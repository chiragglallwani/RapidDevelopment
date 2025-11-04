package com.runanywhere.startup_hackathon20.data.repository

import com.runanywhere.startup_hackathon20.data.api.RetrofitClient
import com.runanywhere.startup_hackathon20.data.models.CreateProjectRequest
import com.runanywhere.startup_hackathon20.data.models.Project
import com.runanywhere.startup_hackathon20.data.models.UpdateProjectRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class ProjectResult {
    data class Success(val projects: List<Project>) : ProjectResult()
    data class SingleSuccess(val project: Project) : ProjectResult()
    data class Error(val message: String) : ProjectResult()
}

class ProjectRepository {
    
    private val apiService = RetrofitClient.apiService
    
    suspend fun getProjects(): ProjectResult {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getProjects()
                
                if (response.isSuccessful && response.body() != null) {
                    val projectResponse = response.body()!!
                    
                    if (projectResponse.success && projectResponse.data != null) {
                        ProjectResult.Success(projectResponse.data)
                    } else {
                        ProjectResult.Success(emptyList())
                    }
                } else {
                    ProjectResult.Error(response.message() ?: "Failed to fetch projects")
                }
            } catch (e: Exception) {
                ProjectResult.Error(e.message ?: "Network error occurred")
            }
        }
    }
    
    suspend fun getProject(projectId: String): ProjectResult {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getProject(projectId)
                
                if (response.isSuccessful && response.body() != null) {
                    val projectResponse = response.body()!!
                    
                    if (projectResponse.success && projectResponse.data != null) {
                        ProjectResult.SingleSuccess(projectResponse.data)
                    } else {
                        ProjectResult.Error(projectResponse.message ?: "Project not found")
                    }
                } else {
                    ProjectResult.Error(response.message() ?: "Failed to fetch project")
                }
            } catch (e: Exception) {
                ProjectResult.Error(e.message ?: "Network error occurred")
            }
        }
    }
    
    suspend fun createProject(name: String, description: String): ProjectResult {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.createProject(CreateProjectRequest(name, description))
                
                if (response.isSuccessful && response.body() != null) {
                    val projectResponse = response.body()!!
                    
                    if (projectResponse.success && projectResponse.data != null) {
                        ProjectResult.SingleSuccess(projectResponse.data)
                    } else {
                        ProjectResult.Error(projectResponse.message ?: "Failed to create project")
                    }
                } else {
                    ProjectResult.Error(response.message() ?: "Failed to create project")
                }
            } catch (e: Exception) {
                ProjectResult.Error(e.message ?: "Network error occurred")
            }
        }
    }
    
    suspend fun updateProject(projectId: String, name: String?, description: String?): ProjectResult {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.updateProject(
                    projectId,
                    UpdateProjectRequest(name, description)
                )
                
                if (response.isSuccessful && response.body() != null) {
                    val projectResponse = response.body()!!
                    
                    if (projectResponse.success && projectResponse.data != null) {
                        ProjectResult.SingleSuccess(projectResponse.data)
                    } else {
                        ProjectResult.Error(projectResponse.message ?: "Failed to update project")
                    }
                } else {
                    ProjectResult.Error(response.message() ?: "Failed to update project")
                }
            } catch (e: Exception) {
                ProjectResult.Error(e.message ?: "Network error occurred")
            }
        }
    }
    
    suspend fun deleteProject(projectId: String): ProjectResult {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.deleteProject(projectId)
                
                if (response.isSuccessful) {
                    ProjectResult.Success(emptyList())
                } else {
                    ProjectResult.Error(response.message() ?: "Failed to delete project")
                }
            } catch (e: Exception) {
                ProjectResult.Error(e.message ?: "Network error occurred")
            }
        }
    }
}

