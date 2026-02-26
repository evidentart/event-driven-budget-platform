import React, { useEffect, useState } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { Box, CircularProgress } from "@mui/material";
import keycloak, { initKeycloak } from "./keycloak";

export default function RequireAuth({ children }) {
  const [status, setStatus] = useState("loading"); // loading | unauthenticated | ready | error
  const location = useLocation();

  useEffect(() => {
    let isMounted = true;
    const timeoutId = setTimeout(() => {
      if (isMounted) setStatus("unauthenticated");
    }, 4000);

    initKeycloak()
      .then((authenticated) => {
        if (!isMounted) return;
        clearTimeout(timeoutId);

        if (!authenticated) {
          setStatus("unauthenticated");
          return;
        }

        setStatus("ready");
      })
      .catch(() => {
        if (!isMounted) return;
        clearTimeout(timeoutId);
        setStatus("error");
      });

    return () => {
      isMounted = false;
      clearTimeout(timeoutId);
    };
  }, []);

  if (status === "loading") {
    return (
      <Box sx={{ height: "100vh", display: "grid", placeItems: "center" }}>
        <CircularProgress />
      </Box>
    );
  }

  if (status === "error") {
    return <Navigate to="/login" replace state={{ from: location.pathname, authError: true }} />;
  }

  if (status === "unauthenticated" || !keycloak.authenticated) {
    const from = `${location.pathname}${location.search}`;
    return <Navigate to="/login" replace state={{ from }} />;
  }

  return children;
}
