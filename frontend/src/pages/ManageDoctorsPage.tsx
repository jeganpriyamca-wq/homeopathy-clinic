import LogoutButton from "../components/LogoutButton";
import { useEffect, useRef, useState } from "react";
import type { FormEvent } from "react";
import { Link, Navigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { createDoctor, doctorError, listDoctors, setDoctorActive, updateDoctor, weekdays } from "../api/doctorsApi";
import type { Doctor, DoctorDetails, WorkingDay } from "../api/doctorsApi";
import "./ClinicSetupPage.css";
import "./ManageDoctorsPage.css";

type FormValues = Omit<DoctorDetails, "consultationFee" | "appointmentDuration"> & { consultationFee: string; appointmentDuration: string };
const blank = (): FormValues => ({
  firstName: "", lastName: "", email: "", mobile: "", qualification: "", registrationNumber: "", specialization: "",
  consultationFee: "", appointmentDuration: "30",
  workingHours: weekdays.map(day => ({ day, closed: day === "SUNDAY", opensAt: day === "SUNDAY" ? null : "09:00", closesAt: day === "SUNDAY" ? null : "18:00" })),
});

export default function ManageDoctorsPage() {
  const { user } = useAuth();
  const [doctors, setDoctors] = useState<Doctor[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadFailed, setLoadFailed] = useState(false);
  const [refresh, setRefresh] = useState(0);
  const [editing, setEditing] = useState<Doctor | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [values, setValues] = useState<FormValues>(blank);
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [status, setStatus] = useState("");
  const [dirty, setDirty] = useState(false);
  const firstInput = useRef<HTMLInputElement>(null);
  const inFlight = useRef(false);
  const token = user?.accessToken;

  useEffect(() => {
    if (!token || user?.role !== "ADMIN") return;
    const controller = new AbortController();
    setLoading(true);
    setLoadFailed(false);
    listDoctors(token, controller.signal).then(setDoctors).catch(error => {
      if (!controller.signal.aborted) { setError(doctorError(error)); setLoadFailed(true); }
    }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [token, user?.role, refresh]);

  useEffect(() => { if (showForm) firstInput.current?.focus(); }, [showForm, editing]);
  useEffect(() => {
    if (!dirty) return;
    const warn = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = ""; };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty]);

  function open(doctor: Doctor | null) {
    if (busy) return;
    setEditing(doctor);
    setValues(doctor ? { ...doctor.details, consultationFee: String(doctor.details.consultationFee),
      appointmentDuration: String(doctor.details.appointmentDuration),
      workingHours: weekdays.map(day => ({ ...doctor.details.workingHours.find(row => row.day === day)! })) } : blank());
    setPassword("");
    setDirty(false);
    setError("");
    setStatus("");
    setShowForm(true);
  }
  function change(key: keyof Omit<FormValues, "workingHours">, value: string) {
    setValues(current => ({ ...current, [key]: value }));
    setDirty(true);
  }
  function changeDay(day: WorkingDay["day"], change: Partial<WorkingDay>) {
    setValues(current => ({ ...current, workingHours: current.workingHours.map(row => row.day === day ? { ...row, ...change } : row) }));
    setDirty(true);
  }
  function replaceDoctor(doctor: Doctor) {
    setDoctors(current => current.some(row => row.id === doctor.id) ? current.map(row => row.id === doctor.id ? doctor : row) : [...current, doctor]);
  }
  async function save(event: FormEvent) {
    event.preventDefault();
    if (!token || inFlight.current) return;
    if (values.workingHours.some(row => !row.closed && (!row.opensAt || !row.closesAt || row.opensAt >= row.closesAt))) {
      setError("Each open day needs a closing time later than its opening time."); return;
    }
    if (!editing && new TextEncoder().encode(password).length > 72) {
      setError("Password must be at most 72 bytes. Use fewer characters."); return;
    }
    inFlight.current = true;
    setBusy(true);
    setError("");
    try {
      const details: DoctorDetails = { ...values, firstName: values.firstName.trim(), lastName: values.lastName.trim(),
        email: values.email.trim(), mobile: values.mobile.trim(), qualification: values.qualification.trim(),
        registrationNumber: values.registrationNumber.trim(), specialization: values.specialization.trim(),
        consultationFee: Number(values.consultationFee), appointmentDuration: Number(values.appointmentDuration) };
      const saved = editing ? await updateDoctor(token, editing, details) : await createDoctor(token, details, password);
      replaceDoctor(saved);
      setShowForm(false);
      setDirty(false);
      setPassword("");
      setStatus(editing ? "Doctor updated." : "Doctor added. Their login account is ready.");
    } catch (error) { setError(doctorError(error)); }
    finally { inFlight.current = false; setBusy(false); }
  }
  async function toggle(doctor: Doctor) {
    if (!token || inFlight.current) return;
    inFlight.current = true;
    setBusy(true);
    setError("");
    setStatus("");
    try {
      replaceDoctor(await setDoctorActive(token, doctor));
      setStatus(doctor.active ? "Doctor deactivated. Login and API access are blocked." : "Doctor reactivated.");
    } catch (error) { setError(doctorError(error)); }
    finally { inFlight.current = false; setBusy(false); }
  }

  if (!user) return <Navigate to="/login" replace />;
  if (user.role !== "ADMIN") return <Navigate to={user.role === "DOCTOR" ? "/doctor" : "/reception"} replace />;

  const fields = [
    ["firstName", "First name", true, 100], ["lastName", "Last name", false, 100],
    ["email", "Login email", true, 254], ["mobile", "Mobile number", false, 10],
    ["qualification", "Qualification", true, 200], ["registrationNumber", "Registration number", true, 100],
    ["specialization", "Specialization", true, 200],
  ] as const;

  return <main className="clinic-setup"><div className="setup-shell">
    <div className="clinic-topbar"><nav className="clinic-admin-nav" aria-label="Administration">
      <Link to="/clinic-setup" onClick={event => { if (busy || (dirty && !window.confirm("Discard unsaved doctor changes?"))) event.preventDefault(); }}>Clinic Setup</Link>
      <span aria-current="page">Manage Doctors</span>
    </nav><LogoutButton disabled={busy} hasUnsavedChanges={dirty} /></div>
    <header className="setup-page-heading"><div><p className="setup-eyebrow">CLINIC TEAM</p><h1>Manage Doctors</h1>
      <p>Give each doctor their own login, fees and working hours.</p></div>
      {!showForm && <button className="doctor-primary" onClick={() => open(null)} disabled={loading || loadFailed || busy}>Add doctor</button>}
    </header>
    <p role="status" className="setup-status">{status}</p>
    {error && <div className="setup-error" role="alert">{error}</div>}
    {!showForm && <button className="setup-text-button" disabled={loading || busy} onClick={() => { setError(""); setStatus(""); setRefresh(value => value + 1); }}>Reload doctors</button>}
    {loading ? <p role="status">Loading doctors...</p> : !showForm && !loadFailed && (
      doctors.length === 0 ? <section className="setup-section"><h2>Your first doctor</h2><p>Complete Clinic Setup, then add a doctor to create their account.</p></section> :
      <div className="doctor-list">{doctors.map(doctor => <article className="setup-section doctor-card" key={doctor.id}>
        <div className="doctor-card-heading"><h2>{doctor.details.firstName} {doctor.details.lastName}</h2><span className={doctor.active ? "setup-badge" : "doctor-inactive"}>{doctor.active ? "Active" : "Inactive"}</span></div>
        <p>{doctor.details.qualification} · {doctor.details.specialization}</p>
        <p>Registration: {doctor.details.registrationNumber}</p><p>{doctor.details.email}</p>
        <p>{new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" }).format(doctor.details.consultationFee)} · {doctor.details.appointmentDuration} minute appointments</p>
        <div className="doctor-card-actions"><button onClick={() => open(doctor)} disabled={busy}>Edit doctor</button>
          <button onClick={() => void toggle(doctor)} disabled={busy}>{doctor.active ? "Deactivate" : "Reactivate"}</button></div>
      </article>)}</div>
    )}
    {showForm && <form onSubmit={save} className="setup-form">
      <fieldset disabled={busy} className="doctor-fieldset">
        <section className="setup-section"><h2>{editing ? "Edit doctor" : "Add doctor"}</h2>
          <p className="setup-required">Fields marked * are required. Doctor hours use Asia/Kolkata; fees use INR.</p>
          <div className="setup-grid">
            {fields.map(([key, label, required, maxLength]) => <div className="setup-field" key={key}>
              <label htmlFor={key}>{label}{required ? " *" : " (optional)"}</label>
              <input ref={key === "firstName" ? firstInput : undefined} id={key} name={key}
                type={key === "email" ? "email" : key === "mobile" ? "tel" : "text"} required={required}
                maxLength={maxLength} pattern={key === "mobile" ? "[6-9][0-9]{9}" : required && key !== "email" ? ".*\\S.*" : undefined}
                autoComplete={key === "email" ? "off" : undefined} value={values[key]}
                onChange={event => change(key, event.target.value)} />
              {key === "mobile" && <small>10 digits without +91.</small>}
            </div>)}
            {!editing && <div className="setup-field"><label htmlFor="doctor-password">Login password *</label>
              <input id="doctor-password" type="password" required minLength={12} maxLength={72} autoComplete="new-password"
                value={password} onChange={event => { setPassword(event.target.value); setDirty(true); }} />
              <small>12–72 characters, at most 72 UTF-8 bytes. Share the login details with the doctor securely.</small>
            </div>}
            <div className="setup-field"><label htmlFor="fee">Consultation fee (INR) *</label>
              <input id="fee" type="number" required min="0" max="1000000" step="0.01" value={values.consultationFee} onChange={event => change("consultationFee", event.target.value)} /></div>
            <div className="setup-field"><label htmlFor="duration">Appointment duration (minutes) *</label>
              <input id="duration" type="number" required min="5" max="240" step="1" value={values.appointmentDuration} onChange={event => change("appointmentDuration", event.target.value)} /></div>
          </div>
        </section>
        <section className="setup-section"><h2>Weekly working hours</h2><p className="setup-required">One interval per day. Closing time must be later on the same day.</p>
          <div className="doctor-hours">{values.workingHours.map(row => <div className="doctor-day" key={row.day}>
            <strong>{row.day.charAt(0) + row.day.slice(1).toLowerCase()}</strong>
            <label><input type="checkbox" checked={row.closed} onChange={event => changeDay(row.day, { closed: event.target.checked, opensAt: event.target.checked ? null : "09:00", closesAt: event.target.checked ? null : "18:00" })} /> Off</label>
            {!row.closed && <><label className="setup-field">Opens<input type="time" required aria-label={row.day + " opening time"} value={row.opensAt ?? ""} onChange={event => changeDay(row.day, { opensAt: event.target.value })} /></label>
              <label className="setup-field">Closes<input type="time" required aria-label={row.day + " closing time"} value={row.closesAt ?? ""} onChange={event => changeDay(row.day, { closesAt: event.target.value })} /></label></>}
          </div>)}</div>
        </section>
        <footer className="setup-actions"><button type="button" onClick={() => {
          if (dirty && !window.confirm("Discard unsaved doctor changes?")) return;
          setShowForm(false); setPassword(""); setDirty(false); setError("");
        }}>Cancel</button><button type="submit">{busy ? "Saving..." : editing ? "Save changes" : "Create doctor & login"}</button></footer>
      </fieldset>
    </form>}
  </div></main>;
}
