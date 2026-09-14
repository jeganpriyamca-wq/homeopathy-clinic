import { Navigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import LogoutButton from "../components/LogoutButton";
import "./ClinicSetupPage.css";

export default function DoctorDashboard() {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  if (user.role !== "DOCTOR") return <Navigate to={user.role === "ADMIN" ? "/clinic-setup" : "/reception"} replace />;
  return <main className="clinic-setup"><div className="setup-shell">
    <header className="clinic-topbar"><h1>Doctor Dashboard</h1><LogoutButton /></header>
  </div></main>;
}
