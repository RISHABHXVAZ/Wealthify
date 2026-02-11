const authmodel = require('../models/authmodel.js');
async function register(req,resp){
    try{
        const obj=req.body;
        console.log(obj);
        const user= await authmodel.createUser(obj);
        resp.json({status:true,msg:"registered successfully",data:user});
    } catch(err){
        console.log(err);
    }
};
module.exports={register};