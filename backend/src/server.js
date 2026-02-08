import express from "express";
import cors from "cors";
import dotenv from "dotenv";

dotenv.config();

const app = express();

/*
   MIDDLEWARE
*/
app.use(cors());
app.use(express.json());

/*
   TEST ROUTE
*/
app.get("/", (req, res) => {
    res.send("AI Expense Tracker API Running");
});

/*
   START SERVER
*/
const PORT = process.env.PORT || 5000;

app.listen(PORT, () => {
    console.log(`Server running on port ${PORT}`);
});
