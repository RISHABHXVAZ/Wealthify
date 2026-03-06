# Wealthify
An AI-based powerful expense tracker which prevents over-spending, generate summaries and helps you to invest efficiently.

Login/Register APIs: 
1. POST http://localhost:8080/api/auth/register
     request -> name , email, password
     response -> user registered successfully

2. POST http://localhost:8080/api/auth/login
      request -> email, password
      response -> JWT Token, name, email

Expense APIs: 
1. POST http://localhost:8080/api/expenses               --to add an expense
    request -> amount(int), description, categoryId, expenseDate(automatically takes today's date)