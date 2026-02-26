import React from "react";
import { AppBar, Box, Button, Toolbar, Typography } from "@mui/material";
import { startLogout } from "../../auth/keycloak";

export default function TopBar() {
  return (
    <AppBar position="sticky" elevation={0} color="transparent">
      <Toolbar sx={{ borderBottom: "1px solid", borderColor: "divider" }}>
        <Typography variant="h6" sx={{ fontWeight: 800 }}>
          Budget Portal
        </Typography>

        <Box sx={{ flex: 1 }} />

        <Button variant="outlined" onClick={() => startLogout(window.location.origin)}>
          Logout
        </Button>
      </Toolbar>
    </AppBar>
  );
}
