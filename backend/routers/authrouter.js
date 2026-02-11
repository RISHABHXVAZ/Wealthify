var express=require("express");
var appRouter=express.Router();
var obj=require("../controllers/authcontroller.js");
appRouter.post("/register",obj.register);
appRouter.post("/login",obj.login);
module.exports=appRouter;