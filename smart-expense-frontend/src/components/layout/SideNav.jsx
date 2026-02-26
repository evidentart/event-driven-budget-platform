import React from "react";
import { NavLink } from "react-router-dom";
import { List, ListItemButton, ListItemIcon, ListItemText, Paper, Typography } from "@mui/material";

import DashboardIcon from "@mui/icons-material/Dashboard";
import PersonIcon from "@mui/icons-material/Person";
import ReceiptLongIcon from "@mui/icons-material/ReceiptLong";
import AccountBalanceWalletIcon from "@mui/icons-material/AccountBalanceWallet";
import AutoAwesomeIcon from "@mui/icons-material/AutoAwesome";

const items = [
  { to: "/dashboard", label: "Dashboard", icon: <DashboardIcon /> },
  { to: "/profile", label: "Profile", icon: <PersonIcon /> },
  { to: "/expenses", label: "Expenses", icon: <ReceiptLongIcon /> },
  { to: "/budgets", label: "Budgets", icon: <AccountBalanceWalletIcon /> },
  { to: "/insights", label: "AI Insights", icon: <AutoAwesomeIcon /> },
];

export default function SideNav() {
  return (
    <Paper square sx={{ p: 2, borderRight: "1px solid", borderColor: "divider" }}>
      <Typography variant="h6" sx={{ fontWeight: 900, mb: 2 }}>
        Smart Expense
      </Typography>

      <List sx={{ display: "grid", gap: 1 }}>
        {items.map((it) => (
          <ListItemButton
            key={it.to}
            component={NavLink}
            to={it.to}
            sx={{
              borderRadius: 2,
              "&.active": { bgcolor: "action.selected" },
            }}
          >
            <ListItemIcon>{it.icon}</ListItemIcon>
            <ListItemText primary={it.label} />
          </ListItemButton>
        ))}
      </List>
    </Paper>
  );
}
