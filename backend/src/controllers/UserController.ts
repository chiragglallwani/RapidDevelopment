import { IResponse, IUser } from "../types/types";
import { Request, Response } from "express";
const UserModel = require("../models/UserModel");

export const getDevelopers = async (req: Request<{}, {}, {}>, res: Response<IResponse<IUser[]>>) => {
  try {
    const developers: IUser[] = await UserModel.find({ role: "developer" });
    return res.status(200).json({
      success: true,
      message: "Developers fetched successfully",
      data: developers,
    });
  } catch (error) {
    return res.status(500).json({ message: "Failed to get users", success: false, error: (error as Error).message });
  }
}