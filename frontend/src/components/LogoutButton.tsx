import { useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import "./LogoutButton.css";

export default function LogoutButton({ disabled = false, hasUnsavedChanges = false }: { disabled?: boolean; hasUnsavedChanges?: boolean }) {
  const { logout } = useAuth();
  const navigate = useNavigate();
  function signOut() {
    if (hasUnsavedChanges && !window.confirm("Log out and discard unsaved changes?")) return;
    logout();
    navigate("/login", { replace: true });
  }
  return <button type="button" className="clinic-logout" disabled={disabled} onClick={signOut}>Log out</button>;
}
