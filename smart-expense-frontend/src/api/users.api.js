import { http } from "./http";

export async function getMyProfile() {
  const { data } = await http.get("/api/users/profile");
  return data;
}

export async function updateMyProfile(payload) {
  const { data } = await http.patch("/api/users/profile", payload);
  return data;
}
