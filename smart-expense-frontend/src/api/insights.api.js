import { http } from "./http";

export async function listInsights(userId) {
  const { data } = await http.get(`/api/insights/${userId}`);
  return data;
}

export async function getLatestInsight(userId) {
  const { data } = await http.get(`/api/insights/${userId}/latest`);
  return data;
}

export async function deleteInsightByExpense(userId, expenseId) {
  await http.delete(`/api/insights/${userId}/expense/${expenseId}`);
}
