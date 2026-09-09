import axios from "axios";

const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

export type LoginRequest = {
  email: string;
  password: string;
};

export type LoginResponse = {
  accessToken: string;
  tokenType: string;
  userId: number;
  firstName: string;
  lastName: string;
  role: "ADMIN" | "DOCTOR" | "RECEPTIONIST";
};

export async function login(
  request: LoginRequest
): Promise<LoginResponse> {
  const response = await axios.post<LoginResponse>(
    `${API_BASE_URL}/api/auth/login`,
    request
  );

  return response.data;
}
