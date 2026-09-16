import { Navigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import AppointmentsPage from "./AppointmentsPage";
export default function DoctorDashboard() {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  if (user.role !== "DOCTOR") return <Navigate to={user.role === "ADMIN" ? "/clinic-setup" : "/reception"} replace />;
  return <AppointmentsPage dashboardTitle="Doctor Dashboard" />;
}