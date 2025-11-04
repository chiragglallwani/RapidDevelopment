export type TaskStatus = "to-do" | "in-progress" | "blocked" | "done";

export type UserRole = "admin" | "project-manager" | "developer";

export interface IProject {
  id: string;
  name: string;
  description: string;
  createdAt: Date;
}

export interface IUser {
  id: string;
  name: string;
  email: string;
  password: string;
  role: UserRole;
  createdAt: Date;
  updatedAt: Date;
}

/**
 * Interface for the Task document.
 */
export interface ITask {
  id: string;
  title: string;
  description: string;
  status: TaskStatus;
  blockReason?: string;
  projectId: string;
  createdAt: Date;
}

export interface IResponse<T> {
  success: boolean;
  message?: string;
  data?: T;
  error?: string;
}
