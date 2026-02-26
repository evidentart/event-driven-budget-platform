import Keycloak from "keycloak-js";

const KEYCLOAK_SCOPE = "openid profile email";

const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL,
  realm: import.meta.env.VITE_KEYCLOAK_REALM,
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID,
});

let initPromise;

export function initKeycloak() {
  if (!initPromise) {
    initPromise = keycloak.init({
      checkLoginIframe: false,
      pkceMethod: "S256",
    });
  }

  return initPromise;
}

export function startLogin(redirectUri = window.location.origin) {
  return keycloak.login({
    redirectUri,
    scope: KEYCLOAK_SCOPE,
  });
}

export function startLogout(redirectUri = window.location.origin) {
  return keycloak.logout({ redirectUri });
}

export default keycloak;
