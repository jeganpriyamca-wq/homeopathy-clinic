import { Navigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import LogoutButton from "../components/LogoutButton";
import "./ClinicSetupPage.css";

export default function ReceptionDashboard() {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  if (user.role !== "RECEPTIONIST") return <Navigate to={user.role === "ADMIN" ? "/clinic-setup" : "/doctor"} replace />;
  return <main className="clinic-setup"><div className="setup-shell">
    <header className="clinic-topbar"><h1>Reception Dashboard</h1><LogoutButton /></header>
  </div></main>;
}
