import { http } from "./http";

export async function getCurrentBudget(userId) {
  const { data } = await http.get(`/api/budgets/user/${userId}/current`);
  return data;
}

export async function listBudgets(userId) {
  const { data } = await http.get(`/api/budgets/user/${userId}`);
  return data;
}

export async function createBudget(payload) {
  const { data } = await http.post("/api/budgets", payload);
  return data;
}

export async function setCurrentBudget(userId, monthlyBudget) {
  const { data } = await http.put(`/api/budgets/user/${userId}/current`, { monthlyBudget });
  return data;
}

export async function deleteBudget(budgetId) {
  await http.delete(`/api/budgets/${budgetId}`);
}
