import React from "react";
import { createBrowserRouter, Navigate } from "react-router-dom";

import RequireAuth from "../auth/RequireAuth";
import AppShell from "../components/layout/AppShell";

import DashboardPage from "../pages/dashboard/DashboardPage";
import ProfilePage from "../pages/profile/ProfilePage";
import ExpensesPage from "../pages/expenses/ExpensesPage";
import BudgetsPage from "../pages/budgets/BudgetsPage";
import InsightsPage from "../pages/insights/InsightsPage";
import LoginPage from "../pages/auth/LoginPage";

export const router = createBrowserRouter([
  { path: "/login", element: <LoginPage /> },
  {
    path: "/",
    element: (
      <RequireAuth>
        <AppShell />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <Navigate to="/dashboard" replace /> },
      { path: "dashboard", element: <DashboardPage /> },
      { path: "profile", element: <ProfilePage /> },
      { path: "expenses", element: <ExpensesPage /> },
      { path: "budgets", element: <BudgetsPage /> },
      { path: "insights", element: <InsightsPage /> },
    ],
  },
  { path: "*", element: <Navigate to="/login" replace /> },
]);
