const authmodel = require('../models/authmodel.js');
const bcrypt = require('bcrypt');
async function register(req,resp){
    try{
        const {email,password}=req.body;
        const password_hash = await bcrypt.hash(password, 10);
        const user= await authmodel.createUser({email,password_hash});
        resp.json({status:true,msg:"registered successfully",data:user});
    } catch(err){
        console.log(err);
    }
};

async function login(req, res) {
  try {
    const {email,password} = req.body;
    const user = await authmodel.getUserByEmail(email);
    if (!user) {
      return res.status(400).json({ msg: "User not found" });
    }
    const match = await bcrypt.compare(password, user.password_hash);
    if (!match) {
      return res.status(400).json({ msg: "Invalid password" });
    }
    res.json({ status: true, msg: "Login successful", user });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
}

module.exports={register,login};