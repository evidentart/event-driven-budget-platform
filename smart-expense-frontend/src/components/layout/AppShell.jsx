import React from "react";
import { Box } from "@mui/material";
import { Outlet } from "react-router-dom";
import SideNav from "./SideNav";
import TopBar from "./TopBar";

export default function AppShell() {
  return (
    <Box sx={{ display: "grid", gridTemplateColumns: "280px 1fr", minHeight: "100vh" }}>
      <SideNav />
      <Box sx={{ display: "flex", flexDirection: "column" }}>
        <TopBar />
        <Box component="main" sx={{ p: 3 }}>
          <Outlet />
        </Box>
      </Box>
    </Box>
  );
}
