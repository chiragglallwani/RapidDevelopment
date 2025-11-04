const express = require("express");
const mongoose = require("mongoose");
const cors = require("cors");
const dotenv = require("dotenv");
const swaggerUi = require("swagger-ui-express");
const SwaggerSchema = require("./ui/swagger");
import ProjectRoutes from "./routes/ProjectRoutes";
import TaskRoutes from "./routes/TaskRoutes";
import AuthRoutes from "./routes/AuthRoutes";
import UserRoutes from "./routes/UserRoutes";
dotenv.config();

const app = express();

app.use(cors());
app.use(express.json());

const connectDB = async () => {
  try {
    await mongoose.connect(process.env.MONGO_URI);
    console.log("Connected to MongoDB");
  } catch (error) {
    console.log(error);
    process.exit(1);
  }
};

app.use("/api/v1/projects", ProjectRoutes);
app.use("/api/v1/tasks", TaskRoutes);
// app.use("/api/v1/ai", AIAssisstantRoutes);
app.use("/api/v1/auth", AuthRoutes);
app.use("/api/v1/users", UserRoutes);

app.use("/api", swaggerUi.serve, swaggerUi.setup(SwaggerSchema));

app.listen(process.env.PORT, async () => {
  await connectDB();
  console.log(`Server is running on port ${process.env.PORT}`);
  console.log(`Swagger is running on http://localhost:${process.env.PORT}/api`);
});
