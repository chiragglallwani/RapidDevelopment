import { Request, Response } from "express";
import { IProject, ITask, IUser, UserRole } from "../types/types";
const ProjectModel = require("../models/ProjectModel");
const UserModel = require("../models/UserModel");
const TaskModel = require("../models/TaskModel");

interface CreateProjectReqBody {
  name: string;
  description: string;
}

interface UpdateProjectReqBody {
  name?: string;
  description?: string;
}

export const createProject = async (
  req: Request<{}, {}, CreateProjectReqBody>,
  res: Response<{
    message: string;
    success: boolean;
    error?: string;
    data?: { _id: string };
  }>
) => {
  try {
    const { name, description } = req.body;
    const assignedTo = req.userId;

    if (!assignedTo) {
      return res.status(401).json({ success: false, message: "Unauthorized" });
    }

    const User: IUser | null = await UserModel.findById(assignedTo);
    if (!User) {
      return res
        .status(404)
        .json({ success: false, message: "User not found" });
    }

    if (!isUserManager(User._id, "project-manager")) {
      return res.status(403).json({
        success: false,
        message: "User is not authorized to create a project",
      });
    }

    const project: IProject = await ProjectModel.create({
      name,
      description,
      assignedTo,
    });
    res.status(200).json({
      message: "Project created successfully",
      success: true,
      data: {
        _id: project?._id,
      },
    });
  } catch (error) {
    console.error("Error creating project:", error);
    res.status(500).json({
      message: "Failed to create project",
      success: false,
      error: (error as Error).message,
    });
  }
};

export const getProjects = async (
  req: Request<{}, {}, {}>,
  res: Response<{
    data?: IProject[];
    success: boolean;
    error?: string;
    message?: string;
  }>
) => {
  try {
    const assignedTo = req.userId;

    if (!assignedTo) {
      return res.status(401).json({ success: false, message: "Unauthorized" });
    }
    const isManager = await isUserManager(assignedTo, "project-manager");
    if (isManager) {
      const projects: IProject[] = await getManagerProjects(assignedTo);
      if (!projects || projects.length === 0) {
        return res
          .status(200)
          .json({ success: true, data: [], message: "No projects found" });
      }
      return res.status(200).json({
        success: true,
        data: projects.map((project) => ({
          _id: project._id?.toString() || "",
          name: project.name,
          description: project.description,
          createdAt: project.createdAt,
        })),
      });
    } else {
      const projects: IProject[] = await getDeveloperProjects(assignedTo);
      if (!projects || projects.length === 0) {
        return res
          .status(200)
          .json({ success: true, data: [], message: "No projects found" });
      }
      return res.status(200).json({
        success: true,
        data: projects.map((project) => ({
          _id: project._id?.toString() || "",
          name: project.name,
          description: project.description,
          createdAt: project.createdAt,
        })),
      });
    }
  } catch (error) {
    console.error("Error fetching projects:", error);
    res.status(500).json({
      success: false,
      message: "Failed to fetch projects",
      error: (error as Error).message,
    });
  }
};

export const getProject = async (
  req: Request<{ id: string }>,
  res: Response<{
    data?: IProject;
    success: boolean;
    error?: string;
    message?: string;
  }>
) => {
  try {
    const { id } = req.params;
    const project: IProject | null = await ProjectModel.findById(id);
    if (!project) {
      return res
        .status(404)
        .json({ success: false, message: "Project not found" });
    }
    res.status(200).json({
      data: {
        _id: project._id?.toString() || "",
        name: project.name,
        description: project.description,
        createdAt: project.createdAt,
      },
      success: true,
    });
  } catch (error) {
    console.error("Error fetching project:", error);
    res.status(500).json({
      success: false,
      message: "Failed to fetch project",
      error: (error as Error).message,
    });
  }
};

export const updateProject = async (
  req: Request<{ id: string }, {}, UpdateProjectReqBody>,
  res: Response<{ success: boolean; error?: string; message?: string }>
) => {
  try {
    const { id } = req.params;
    const { name, description } = req.body;

    const updatePayload: UpdateProjectReqBody = {};
    if (name !== undefined) updatePayload.name = name;
    if (description !== undefined) updatePayload.description = description;

    const project: IProject | null = await ProjectModel.findByIdAndUpdate(
      id,
      updatePayload,
      { new: true }
    );

    if (!project) {
      return res
        .status(404)
        .json({ success: false, message: "Project not found" });
    }
    res
      .status(200)
      .json({ success: true, message: "Project updated successfully" });
  } catch (error) {
    console.error("Error updating project:", error);
    res.status(500).json({
      success: false,
      message: "Failed to update project",
      error: (error as Error).message,
    });
  }
};

export const findProject = async (
  req: Request<{ text: string }>,
  res: Response<{
    data?: IProject;
    success: boolean;
    error?: string;
    message?: string;
  }>
) => {
  try {
    const { text } = req.params;
    const assignedTo = req.userId;
    if (!assignedTo) {
      return res.status(401).json({ success: false, message: "Unauthorized" });
    }
    if (!text || text.trim().length === 0) {
      return res.status(400).json({
        success: false,
        message: "Search text cannot be empty",
      });
    }

    // Find all projects that match the search text within user's scope
    const searchText = text.trim();
    const escapedSearchText = searchText.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    const searchRegex = new RegExp(escapedSearchText, "i");
    const lowerSearchText = searchText.toLowerCase();

    // Determine user scope - get projects user has access to
    const isManager = await isUserManager(assignedTo, "project-manager");
    let userProjects: IProject[] = [];

    if (isManager) {
      // Manager can search in projects they manage
      userProjects = await getManagerProjects(assignedTo);
    } else {
      // Developer can search in projects they have tasks in
      userProjects = await getDeveloperProjects(assignedTo);
    }

    // Filter projects that match the search text
    const allProjects: IProject[] = userProjects.filter((project) => {
      return (
        searchRegex.test(project.name) || searchRegex.test(project.description)
      );
    });

    if (!allProjects || allProjects.length === 0) {
      return res.status(404).json({
        success: false,
        message: "No matching project found",
      });
    }

    // Calculate relevance score for each project
    const projectsWithScore = allProjects.map((project) => {
      let score = 0;
      const lowerName = project.name.toLowerCase();
      const lowerDescription = project.description.toLowerCase();

      // Score name matches (highest priority first)
      if (lowerName === lowerSearchText) {
        score += 100; // Exact match in name
      } else if (lowerName.startsWith(lowerSearchText)) {
        score += 60; // Name starts with search text
      } else if (
        new RegExp(`\\b${escapedSearchText}`, "i").test(project.name)
      ) {
        score += 30; // Word boundary match in name
      } else if (lowerName.includes(lowerSearchText)) {
        score += 10; // Contains match in name
      }

      // Score description matches (highest priority first)
      if (lowerDescription === lowerSearchText) {
        score += 80; // Exact match in description
      } else if (lowerDescription.startsWith(lowerSearchText)) {
        score += 40; // Description starts with search text
      } else if (
        new RegExp(`\\b${escapedSearchText}`, "i").test(project.description)
      ) {
        score += 20; // Word boundary match in description
      } else if (lowerDescription.includes(lowerSearchText)) {
        score += 5; // Contains match in description
      }

      return { project, score };
    });

    // Sort by score (descending) and get the best match
    projectsWithScore.sort((a, b) => b.score - a.score);
    const bestMatch = projectsWithScore[0];

    if (!bestMatch) {
      return res.status(404).json({
        success: false,
        message: "No matching project found",
      });
    }

    const project = bestMatch.project;
    res.status(200).json({
      success: true,
      data: {
        _id: project._id?.toString() || "",
        name: project.name,
        description: project.description,
        createdAt: project.createdAt,
      },
    });
  } catch (error) {
    console.error("Error finding project:", error);
    res.status(500).json({
      success: false,
      message: "Failed to find project",
      error: (error as Error).message,
    });
  }
};

export const deleteProject = async (
  req: Request<{ id: string }>,
  res: Response<{ success: boolean; error?: string; message?: string }>
) => {
  try {
    const { id } = req.params;
    const project: IProject | null = await ProjectModel.findByIdAndDelete(id);
    if (!project) {
      return res
        .status(404)
        .json({ success: false, message: "Project not found" });
    }
    res
      .status(200)
      .json({ success: true, message: "Project deleted successfully" });
  } catch (error) {
    console.error("Error deleting project:", error);
    res.status(500).json({
      success: false,
      message: "Failed to delete project",
      error: (error as Error).message,
    });
  }
};

export const isUserManager = async (userId: string, role: UserRole) => {
  const User: IUser | null = await UserModel.findById(userId);
  if (!User) {
    return false;
  }
  return User.role === role;
};

const getManagerProjects = async (userId: string) => {
  const projects: IProject[] = await ProjectModel.find({ assignedTo: userId });
  return projects;
};

const getDeveloperProjects = async (userId: string) => {
  const tasks: ITask[] = await TaskModel.find({ assignedTo: userId });
  const projects: IProject[] = await ProjectModel.find({
    _id: { $in: tasks.map((task) => task.projectId) },
  });
  return projects;
};
