import axios from "axios";
import keycloak from "../auth/keycloak";

const rawBaseURL = import.meta.env.VITE_API_BASE_URL?.trim();

export const http = axios.create({
  baseURL: rawBaseURL || "/",
  timeout: 15000,
});

http.interceptors.request.use(async (config) => {
  if (keycloak.authenticated && keycloak.token) {
    try {
      await keycloak.updateToken(30);
      config.headers.Authorization = `Bearer ${keycloak.token}`;
    } catch (error) {
      // Avoid redirect loops from background API calls; auth guard handles re-login.
      keycloak.clearToken();
      return Promise.reject(error);
    }
  }
  return config;
});
