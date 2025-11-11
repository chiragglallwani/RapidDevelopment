const TaskModel = require("../models/TaskModel");
const ProjectModel = require("../models/ProjectModel");
import { Request, Response } from "express";
import { IProject, ITask, TaskStatus } from "../types/types";
import { isUserManager } from "./ProjectController";

interface CreateTaskReqBody {
  title: string;
  description: string;
  status: TaskStatus;
  blockReason?: string;
  projectId: string;
  assignedTo?: string;
}

interface GetTasksReqQuery {
  projectId?: string;
}

interface UpdateTaskReqBody {
  title?: string;
  description?: string;
  status?: TaskStatus;
  blockReason?: string;
  assignedTo?: string;
}

export const createTask = async (
  req: Request<{}, {}, CreateTaskReqBody>,
  res: Response<{
    success: boolean;
    error?: string;
    message: string;
  }>
) => {
  try {
    const { title, description, status, blockReason, projectId, assignedTo } =
      req.body;

    const Project: IProject | null = await ProjectModel.findById(projectId);
    if (!Project) {
      return res
        .status(404)
        .json({ success: false, message: "Project not found" });
    }

    const createdTask = await TaskModel.create({
      title,
      description,
      status,
      blockReason,
      projectId,
      assignedTo,
    });

    res.status(200).json({
      success: true,
      message: "Task created successfully",
    });
  } catch (error) {
    console.error("Error creating task:", error);
    res.status(500).json({
      success: false,
      message: "Failed to create task",
      error: (error as Error).message,
    });
  }
};

export const getProjectTasks = async (
  req: Request<{}, {}, {}, GetTasksReqQuery>,
  res: Response<{
    data?: ITask[];
    success: boolean;
    error?: string;
    message?: string;
  }>
) => {
  try {
    const { projectId } = req.query;
    if (!projectId) {
      return res
        .status(400)
        .json({ success: false, message: "Project ID is required" });
    }
    const userId = req.userId;
    if (!userId) {
      return res.status(401).json({ success: false, message: "Unauthorized" });
    }
    const isManager = await isUserManager(userId, "project-manager");
    if (isManager) {
      const tasks: ITask[] = await TaskModel.find({ projectId });
      res.status(200).json({
        data: tasks.map((task) => {
          return {
            _id: task._id?.toString() || "",
            title: task.title,
            description: task.description,
            status: task.status,
            blockReason: task.blockReason ?? "",
            projectId: task.projectId?.toString() || "",
            assignedTo: task.assignedTo?.toString() || "",
            createdAt: task.createdAt,
            updatedAt: task.updatedAt,
          };
        }),
        success: true,
      });
    } else {
      const tasks: ITask[] = await TaskModel.find({ assignedTo: userId });
      res.status(200).json({
        data: tasks.map((task) => {
          return {
            _id: task._id?.toString() || "",
            title: task.title,
            description: task.description,
            status: task.status,
            blockReason: task.blockReason ?? "",
            projectId: task.projectId?.toString() || "",
            assignedTo: task.assignedTo?.toString() || "",
            createdAt: task.createdAt,
            updatedAt: task.updatedAt,
          };
        }),
        success: true,
      });
    }
  } catch (error) {
    console.error("Error fetching tasks:", error);
    res.status(500).json({
      success: false,
      message: "Failed to fetch tasks",
      error: (error as Error).message,
    });
  }
};

export const updateTask = async (
  req: Request<{ id: string }, {}, UpdateTaskReqBody>,
  res: Response<{
    success: boolean;
    error?: string;
    message?: string;
    data?: any;
  }>
) => {
  const { id } = req.params;
  const updatePayload: UpdateTaskReqBody = req.body;

  // Validate: blockReason is required when status is "blocked"
  if (
    updatePayload.status === "blocked" &&
    (!updatePayload.blockReason || updatePayload.blockReason.trim() === "")
  ) {
    return res.status(400).json({
      success: false,
      message: "Block reason is required when status is 'blocked'",
    });
  }

  try {
    const task: ITask | null = await TaskModel.findByIdAndUpdate(
      id,
      updatePayload,
      { new: true }
    );

    if (!task) {
      return res
        .status(404)
        .json({ success: false, message: "Task not found" });
    }

    res.status(200).json({
      success: true,
      message: "Task updated successfully",
      data: {
        _id: task._id?.toString() || "",
        title: task.title,
        description: task.description,
        status: task.status,
        blockReason: task.blockReason ?? "",
        projectId: task.projectId?.toString() || "",
        assignedTo: task.assignedTo?.toString() || null,
        createdAt: task.createdAt,
        updatedAt: task.updatedAt,
      },
    });
  } catch (error) {
    console.error("Error updating task:", error);
    res.status(500).json({
      success: false,
      message: "Failed to update task",
      error: (error as Error).message,
    });
  }
};

export const deleteTask = async (
  req: Request<{ id: string }>,
  res: Response<{ success: boolean; error?: string; message?: string }>
) => {
  const { id } = req.params;
  const userId = req.userId;
  if (!userId) {
    return res.status(401).json({ success: false, message: "Unauthorized" });
  }
  const isManager = await isUserManager(userId, "project-manager");
  if (isManager) {
    const task: ITask | null = await TaskModel.findByIdAndDelete(id);

    if (!task) {
      return res
        .status(404)
        .json({ success: false, message: "Task not found" });
    }

    res
      .status(200)
      .json({ success: true, message: "Task deleted successfully" });
  }
};

export const findTask = async (
  req: Request<{ text: string }>,
  res: Response<{
    data?: ITask;
    success: boolean;
    error?: string;
    message?: string;
  }>
) => {
  try {
    const { text } = req.params;
    const userId = req.userId;

    if (!userId) {
      return res.status(401).json({ success: false, message: "Unauthorized" });
    }

    if (!text || text.trim().length === 0) {
      return res.status(400).json({
        success: false,
        message: "Search text cannot be empty",
      });
    }

    // Find all tasks that match the search text within user's scope
    const searchText = text.trim();
    const escapedSearchText = searchText.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    const searchRegex = new RegExp(escapedSearchText, "i");
    const lowerSearchText = searchText.toLowerCase();

    // Determine user scope - get tasks user has access to
    const isManager = await isUserManager(userId, "project-manager");
    let userTasks: ITask[] = [];

    if (isManager) {
      // Manager can search in tasks of projects they manage
      const managerProjects = await ProjectModel.find({ assignedTo: userId });
      const projectIds = managerProjects.map((p: IProject) => p._id);
      userTasks = await TaskModel.find({
        projectId: { $in: projectIds },
      });
    } else {
      // Developer can search in tasks assigned to them
      userTasks = await TaskModel.find({ assignedTo: userId });
    }

    // Filter tasks that match the search text
    const allTasks: ITask[] = userTasks.filter((task) => {
      return searchRegex.test(task.title) || searchRegex.test(task.description);
    });

    if (!allTasks || allTasks.length === 0) {
      return res.status(404).json({
        success: false,
        message: "No matching task found",
      });
    }

    // Calculate relevance score for each task
    const tasksWithScore = allTasks.map((task) => {
      let score = 0;
      const lowerTitle = task.title.toLowerCase();
      const lowerDescription = task.description.toLowerCase();

      // Score title matches (highest priority first)
      if (lowerTitle === lowerSearchText) {
        score += 100; // Exact match in title
      } else if (lowerTitle.startsWith(lowerSearchText)) {
        score += 60; // Title starts with search text
      } else if (new RegExp(`\\b${escapedSearchText}`, "i").test(task.title)) {
        score += 30; // Word boundary match in title
      } else if (lowerTitle.includes(lowerSearchText)) {
        score += 10; // Contains match in title
      }

      // Score description matches (highest priority first)
      if (lowerDescription === lowerSearchText) {
        score += 80; // Exact match in description
      } else if (lowerDescription.startsWith(lowerSearchText)) {
        score += 40; // Description starts with search text
      } else if (
        new RegExp(`\\b${escapedSearchText}`, "i").test(task.description)
      ) {
        score += 20; // Word boundary match in description
      } else if (lowerDescription.includes(lowerSearchText)) {
        score += 5; // Contains match in description
      }

      return { task, score };
    });

    // Sort by score (descending) and get the best match
    tasksWithScore.sort((a, b) => b.score - a.score);
    const bestMatch = tasksWithScore[0];

    if (!bestMatch) {
      return res.status(404).json({
        success: false,
        message: "No matching task found",
      });
    }

    const task = bestMatch.task;
    res.status(200).json({
      success: true,
      data: {
        _id: task._id?.toString() || "",
        title: task.title,
        description: task.description,
        status: task.status,
        blockReason: task.blockReason ?? "",
        projectId: task.projectId?.toString() || "",
        assignedTo: task.assignedTo?.toString() || "",
        createdAt: task.createdAt,
        updatedAt: task.updatedAt,
      },
    });
  } catch (error) {
    console.error("Error finding task:", error);
    res.status(500).json({
      success: false,
      message: "Failed to find task",
      error: (error as Error).message,
    });
  }
};
