import {
  createContext,
  ReactNode,
  useContext,
  useState,
} from "react";

import {
  login as loginApi,
  LoginResponse,
} from "../api/authApi";

type AuthContextType = {
  user: LoginResponse | null;
  login: (email: string, password: string) => Promise<LoginResponse>;
  logout: () => void;
};

const AuthContext = createContext<AuthContextType | undefined>(
  undefined
);

export function AuthProvider({
  children,
}: {
  children: ReactNode;
}) {
  const [user, setUser] = useState<LoginResponse | null>(null);

  async function login(
    email: string,
    password: string
  ) {
    const response = await loginApi({
      email,
      password,
    });

    setUser(response);

    return response;
  }

  function logout() {
    setUser(null);
  }

  return (
    <AuthContext.Provider
      value={{
        user,
        login,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);

  if (!context) {
    throw new Error(
      "useAuth must be used inside AuthProvider"
    );
  }

  return context;
}
