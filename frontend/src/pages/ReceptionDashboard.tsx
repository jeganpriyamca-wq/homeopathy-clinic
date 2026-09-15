import { Navigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import AppointmentsPage from "./AppointmentsPage";
export default function ReceptionDashboard() {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  if (user.role !== "RECEPTIONIST") return <Navigate to={user.role === "ADMIN" ? "/clinic-setup" : "/doctor"} replace />;
  return <AppointmentsPage dashboardTitle="Reception Dashboard" />;
}