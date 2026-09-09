import {
  Navigate,
  Route,
  Routes,
} from "react-router-dom";

import ClinicSetupPage from "./pages/ClinicSetupPage";
import DoctorDashboard from "./pages/DoctorDashboard";
import LoginPage from "./pages/LoginPage";
import ReceptionDashboard from "./pages/ReceptionDashboard";

export default function App() {
  return (
    <Routes>
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
