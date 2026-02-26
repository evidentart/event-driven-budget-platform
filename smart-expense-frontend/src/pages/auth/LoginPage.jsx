import React, { useEffect, useMemo, useState } from "react";
import { Alert, Box, Button, Card, CardContent, Stack, Typography } from "@mui/material";
import { useLocation, useNavigate } from "react-router-dom";
import { initKeycloak, startLogin as startKeycloakLogin } from "../../auth/keycloak";

const REDIRECT_STORAGE_KEY = "post_login_redirect";

export default function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();

  const redirectPath = useMemo(() => {
    if (typeof location.state?.from === "string" && location.state.from.startsWith("/")) {
      return location.state.from;
    }

    const savedPath = sessionStorage.getItem(REDIRECT_STORAGE_KEY);
    if (typeof savedPath === "string" && savedPath.startsWith("/")) {
      return savedPath;
    }

    return "/dashboard";
  }, [location.state]);

  const [status, setStatus] = useState("ready"); // ready | error
  const [loginError, setLoginError] = useState("");

  useEffect(() => {
    initKeycloak()
      .then((authenticated) => {
        if (authenticated) {
          sessionStorage.removeItem(REDIRECT_STORAGE_KEY);
          navigate(redirectPath, { replace: true });
        }
      })
      .catch(() => {
        setStatus("error");
      });
  }, [navigate, redirectPath]);

  const startLogin = () => {
    setLoginError("");
    sessionStorage.setItem(REDIRECT_STORAGE_KEY, redirectPath);
    startKeycloakLogin(window.location.origin)
      .catch(() => {
        setStatus("error");
        setLoginError("Could not open Keycloak login. Please check if Keycloak is running.");
      });
  };

return (
  <Box
    sx={{
      minHeight: "100vh",
      display: "grid",
      placeItems: "center",
      px: 2,
      background:
        "radial-gradient(1200px 500px at 15% 10%, rgba(32,77,255,0.20), transparent), radial-gradient(900px 450px at 90% 90%, rgba(16,192,136,0.16), transparent)",
    }}
  >
    <Card
      elevation={6}
      sx={{
        width: "100%",
        maxWidth: 480,
        borderRadius: 4,
      }}
    >
      <CardContent sx={{ p: 5 }}>
        <Stack spacing={3} alignItems="center" textAlign="center">
          
          <Typography
            variant="h5"
            sx={{
              fontWeight: 800,
              background: "linear-gradient(90deg, #204DFF, #10C088)",
              WebkitBackgroundClip: "text",
              WebkitTextFillColor: "transparent",
            }}
          >
            Smart Expense Analyzer
          </Typography>

          <Typography variant="h4" sx={{ fontWeight: 700 }}>
            Welcome Back
          </Typography>

          <Typography color="text.secondary">
            Sign in to access your dashboard and expenses.
          </Typography>

          {location.state?.authError && (
            <Alert severity="error" sx={{ width: "100%" }}>
              Authentication service is unavailable. Try again shortly.
            </Alert>
          )}

          {status === "error" && (
            <Alert severity="error" sx={{ width: "100%" }}>
              Could not connect to authentication service.
            </Alert>
          )}

          {loginError && (
            <Alert severity="error" sx={{ width: "100%" }}>
              {loginError}
            </Alert>
          )}

          <Button
            variant="contained"
            size="large"
            fullWidth
            sx={{ mt: 1, py: 1.5, fontWeight: 600, borderRadius: 3 }}
            onClick={startLogin}
            disabled={status === "error"}
          >
            Sign in with Keycloak
          </Button>
        </Stack>
      </CardContent>
    </Card>
  </Box>
);
}
