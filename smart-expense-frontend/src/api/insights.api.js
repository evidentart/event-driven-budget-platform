import { http } from "./http";

export async function listMyInsights() {
  const { data } = await http.get("/api/insights/me");
  return data;
}

export async function getLatestInsight() {
  const { data } = await http.get("/api/insights/me/latest");
  return data;
}

export async function deleteInsightByExpense(expenseId) {
  await http.delete(`/api/insights/me/expense/${expenseId}`);
}
