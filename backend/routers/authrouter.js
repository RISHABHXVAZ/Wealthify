var express=require("express");
var appRouter=express.Router();
var obj=require("../controllers/authcontroller.js");
appRouter.post("/register",obj.register);
module.exports=appRouter;