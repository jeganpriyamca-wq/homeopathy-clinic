import AppointmentsPage from "./pages/AppointmentsPage";
import PatientsPage from "./pages/PatientsPage";
import {
  Navigate,
  Route,
  Routes,
} from "react-router-dom";

import ManageDoctorsPage from "./pages/ManageDoctorsPage";
import ClinicSetupPage from "./pages/ClinicSetupPage";
import DoctorDashboard from "./pages/DoctorDashboard";
import LoginPage from "./pages/LoginPage";
import ReceptionDashboard from "./pages/ReceptionDashboard";

export default function App() {
  return (
    <Routes>
      <Route path="/dashboard" element={<AppointmentsPage dashboardTitle="Clinic Dashboard" />} />
      <Route path="/appointments" element={<AppointmentsPage />} />
      <Route path="/patients" element={<PatientsPage />} />
      <Route path="/manage-doctors" element={<ManageDoctorsPage />} />
      <Route
        path="/login"
        element={<LoginPage />}
      />

      <Route
        path="/clinic-setup"
        element={<ClinicSetupPage />}
      />

      <Route
        path="/doctor"
        element={<DoctorDashboard />}
      />

      <Route
        path="/reception"
        element={<ReceptionDashboard />}
      />

      <Route
        path="*"
        element={
          <Navigate
            to="/login"
            replace
          />
        }
      />
    </Routes>
  );
}
