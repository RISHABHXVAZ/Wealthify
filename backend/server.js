var express=require("express");
const app = express();
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

var cors=require("cors");
var dotenv=require("dotenv");
dotenv.config();

require('./config/db.js');
/*
   MIDDLEWARE
*/
app.use(cors());


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

authrouter=require("./routers/authrouter.js");
app.use("/auth",authrouter);