import { http } from "./http";

export async function listMyExpenses() {
  const { data } = await http.get("/api/expenses/me");
  return data;
}

export async function createExpense(payload) {
  const { data } = await http.post("/api/expenses", payload);
  return data;
}

export async function deleteExpense(id) {
  await http.delete(`/api/expenses/${id}`);
}
