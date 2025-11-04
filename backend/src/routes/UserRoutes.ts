import { Router } from "express";
import { getDevelopers } from "../controllers/UserController";
import { roleMiddleware } from "../middleware/RoleMiddleware";

const UserRoutes = Router();

/**
 * @swagger
 * /api/v1/users/developers:
 *   get:
 *     summary: Get all developers
 *     description: Get all developers
 *     tags: [Users]
 *     security:
 *       - bearerAuth: []
 *     responses:
 *       200:
 *         description: Developers fetched successfully
 *         content:
 *           application/json:
 *             schema:
 *               type: array
 *               items:
 *                 $ref: '#/components/schemas/User'
 *       401:
 *         description: Unauthorized
 *       500:
 *         description: Internal server error
 */
UserRoutes.get("/developers", roleMiddleware, getDevelopers);

export default UserRoutes;