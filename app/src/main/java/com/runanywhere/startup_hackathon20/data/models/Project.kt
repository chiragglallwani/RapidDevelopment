package com.runanywhere.startup_hackathon20.data.models

import com.google.gson.annotations.SerializedName

data class Project(
    @SerializedName("_id")
    val id: String,
    val name: String,
    val description: String,
    val createdBy: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

data class CreateProjectRequest(
    val name: String,
    val description: String
)

data class UpdateProjectRequest(
    val name: String? = null,
    val description: String? = null
)

data class ProjectResponse(
    val success: Boolean,
    val message: String? = null,
    val data: Project? = null
)

data class ProjectListResponse(
    val success: Boolean,
    val message: String? = null,
    val data: List<Project>? = null
)

