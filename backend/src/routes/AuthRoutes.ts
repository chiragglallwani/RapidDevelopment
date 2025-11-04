import { Router } from "express";
import { authLogin, authLogout, authRefreshToken, authRegister } from "../controllers/AuthController";
import { authMiddleware } from "../middleware/AuthMiddleware";

const AuthRoutes = Router();

/**
 * @swagger
 * /api/v1/auth/login:
 *   post:
 *     summary: Login
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             properties:
 *               email:
 *                 type: string
 *               password:
 *                 type: string
 *             required:
 *               - email
 *               - password
 *     responses:
 *       200:
 *         description: Logged in successfully
 *       401:
 *         description: Unauthorized
 *       500:
 *         description: Internal server error
 */
AuthRoutes.post("/login", authLogin);

/**
 * @swagger
 * /api/v1/auth/register:
 *   post:
 *     summary: Register
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             properties:
 *               name:
 *                 type: string
 *               email:
 *                 type: string
 *               password:
 *                 type: string
 *               role:
 *                 type: string
 *             required:
 *               - name
 *               - email
 *               - password
 *               - role
 *     responses:
 *       200:
 *         description: Registered successfully
 *       400:
 *         description: User already exists
 *       500:
 *         description: Internal server error
 */
AuthRoutes.post("/register", authRegister);

/**
 * @swagger
 * /api/v1/auth/refresh-token:
 *   post:
 *     summary: Refresh token
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             properties:
 *               refreshToken:
 *                 type: string
 *             required:
 *               - refreshToken
 *     responses:
 *       200:
 *         description: Token refreshed successfully
 *       401:
 *         description: Unauthorized
 *       500:
 *         description: Internal server error
 */
AuthRoutes.post("/refresh-token", authMiddleware, authRefreshToken);

/**
 * @swagger
 * /api/v1/auth/logout:
 *   post:
 *     summary: Log out
 *     security:
 *       - bearerAuth: []
 *     responses:
 *       200:
 *         description: Logged out successfully
 *       401:
 *         description: Unauthorized
 *       500:
 *         description: Internal server error
 */
AuthRoutes.post("/logout", authMiddleware, authLogout);

export default AuthRoutes;