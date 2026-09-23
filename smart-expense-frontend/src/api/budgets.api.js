import { http } from "./http";

export async function getCurrentBudget() {
  const { data } = await http.get("/api/budgets/me/current");
  return data;
}

export async function listBudgets() {
  const { data } = await http.get("/api/budgets/me");
  return data;
}

export async function createBudget(payload) {
  const { data } = await http.post("/api/budgets", payload);
  return data;
}

export async function setCurrentBudget(monthlyBudget) {
  const { data } = await http.put("/api/budgets/me/current", { monthlyBudget });
  return data;
}

export async function deleteBudget(budgetId) {
  await http.delete(`/api/budgets/${budgetId}`);
}


