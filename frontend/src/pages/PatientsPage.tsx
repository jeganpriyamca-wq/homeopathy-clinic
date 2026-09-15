import PatientAppointments from "../components/PatientAppointments";
import { useEffect, useRef, useState } from "react";
import type { FormEvent } from "react";
import { Link, Navigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import LogoutButton from "../components/LogoutButton";
import { getPatient, patientError, savePatient, searchPatients } from "../api/patientsApi";
import type { Patient, PatientDetails, PatientPage, PatientSummary } from "../api/patientsApi";
import "./ClinicSetupPage.css";
import "./PatientsPage.css";

const blank = (): PatientDetails => ({ firstName: "", lastName: "", dateOfBirth: null, phone: "", email: "", address: "" });
const dateLabel = (date: string | null) => date ? date.split("-").reverse().join("/") : "Not recorded";
const todayInIndia = () => {
  const parts = new Intl.DateTimeFormat("en", { timeZone: "Asia/Kolkata", year: "numeric", month: "2-digit", day: "2-digit" }).formatToParts(new Date());
  return ["year", "month", "day"].map(type => parts.find(part => part.type === type)?.value).join("-");
};

export default function PatientsPage() {
  const { user } = useAuth();
  const [mode, setMode] = useState<"list" | "view" | "form">("list");
  const [query, setQuery] = useState("");
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);
  const [refresh, setRefresh] = useState(0);
  const [result, setResult] = useState<PatientPage>({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  const [selected, setSelected] = useState<Patient | null>(null);
  const [values, setValues] = useState<PatientDetails>(blank);
  const [matches, setMatches] = useState<PatientSummary[]>([]);
  const [acknowledged, setAcknowledged] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [loading, setLoading] = useState(false);
  const [loadFailed, setLoadFailed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [status, setStatus] = useState("");
  const inFlight = useRef(false);
  const firstName = useRef<HTMLInputElement>(null);
  const heading = useRef<HTMLHeadingElement>(null);
  const token = user?.accessToken;
  const canAccess = user && ["ADMIN", "DOCTOR", "RECEPTIONIST"].includes(user.role);

  useEffect(() => {
    if (query.trim() === search) return;
    const timer = window.setTimeout(() => {
      setSearch(query.trim()); setPage(0); setStatus("");
    }, 300);
    return () => window.clearTimeout(timer);
  }, [query, search]);
  useEffect(() => {
    if (!token || !canAccess || mode !== "list") return;
    if (query.trim() !== search) { setLoading(true); return; }
    const controller = new AbortController();
    setLoading(true);
    setLoadFailed(false);
    setError("");
    searchPatients(token, search, page, controller.signal).then(data => {
      if (!controller.signal.aborted) setResult(data);
    }).catch(error => {
      if (!controller.signal.aborted) { setError(patientError(error).message); setLoadFailed(true); }
    }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [token, canAccess, mode, query, search, page, refresh]);

  useEffect(() => {
    if (mode === "form") firstName.current?.focus();
    if (mode === "view") heading.current?.focus();
  }, [mode, selected?.id]);
  useEffect(() => {
    if (!dirty) return;
    const warn = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = ""; };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty]);

  function mayLeave() { return !busy && (!dirty || window.confirm("Discard unsaved patient changes?")); }
  function showList() {
    if (!mayLeave()) return;
    setMode("list"); setDirty(false); setMatches([]); setError(""); setStatus("");
  }
  function openForm(patient: Patient | null) {
    if (!mayLeave()) return;
    setSelected(patient); setValues(patient ? { ...patient.details } : blank());
    setMatches([]); setAcknowledged(false); setError(""); setStatus(""); setDirty(false); setMode("form");
  }
  async function view(id: number) {
    if (!token || inFlight.current || !mayLeave()) return;
    inFlight.current = true; setBusy(true); setError(""); setStatus("");
    try {
      const patient = await getPatient(token, id);
      setSelected(patient); setMatches([]); setDirty(false); setMode("view");
    } catch (error) { setError(patientError(error).message); }
    finally { inFlight.current = false; setBusy(false); }
  }
  function change(key: keyof PatientDetails, value: string) {
    setValues(current => ({ ...current, [key]: key === "dateOfBirth" ? value || null : value }));
    setDirty(true); setMatches([]); setAcknowledged(false); setError("");
  }
  async function save(event: FormEvent) {
    event.preventDefault();
    if (!token || inFlight.current || (matches.length > 0 && !acknowledged)) return;
    inFlight.current = true; setBusy(true); setError("");
    try {
      const details = { ...values, firstName: values.firstName.trim(), lastName: values.lastName.trim(),
        phone: values.phone.trim(), email: values.email.trim(), address: values.address.trim() };
      const saved = await savePatient(token, selected, details, acknowledged);
      setSelected(saved); setMode("view"); setDirty(false); setMatches([]);
      setStatus(selected ? "Patient details updated." : "Patient registered successfully.");
    } catch (error) {
      const problem = patientError(error);
      setError(problem.message); setMatches(problem.matches ?? []); setAcknowledged(false);
    } finally { inFlight.current = false; setBusy(false); }
  }

  if (!user) return <Navigate to="/login" replace />;
  if (!canAccess) return <Navigate to="/login" replace />;

  return <main className="clinic-setup"><div className="setup-shell">
    <header className="clinic-topbar">
      <nav className="clinic-admin-nav" aria-label="Clinic navigation">
        {user.role === "ADMIN" ? <>
          <Link to="/clinic-setup" onClick={event => { if (!mayLeave()) event.preventDefault(); }}>Clinic Setup</Link>
          <Link to="/manage-doctors" onClick={event => { if (!mayLeave()) event.preventDefault(); }}>Manage Doctors</Link>
        </> : <Link to={user.role === "DOCTOR" ? "/doctor" : "/reception"} onClick={event => { if (!mayLeave()) event.preventDefault(); }}>Dashboard</Link>}
        <Link to="/appointments" onClick={event => { if (!mayLeave()) event.preventDefault(); }}>Appointments</Link>
        <span aria-current="page">Patients</span>
      </nav>
      <LogoutButton disabled={busy} hasUnsavedChanges={dirty} />
    </header>
    <header className="setup-page-heading"><div><p className="setup-eyebrow">PATIENT RECORDS</p>
      <h1 ref={heading} tabIndex={-1}>{mode === "list" ? "Patients" : mode === "form" ? selected ? "Edit patient" : "Register patient" : "Patient profile"}</h1>
      <p>{mode === "list" ? "Register new patients and find existing records." : "Keep patient contact and registration details up to date."}</p>
    </div>{mode === "list" && <button className="patient-primary" disabled={busy} onClick={() => openForm(null)}>Register patient</button>}</header>
    {mode !== "list" && <button type="button" className="setup-text-button" disabled={busy} onClick={showList}>Back to patients</button>}
    <p className="setup-status" role="status">{status}</p>
    {error && <p className="setup-error" role="alert">{error}</p>}

    {mode === "list" && <>
      <div className="patient-search" role="search" aria-label="Find patients">
        <div className="setup-field"><label htmlFor="patient-search">Search patients</label>
          <input id="patient-search" type="search" placeholder="ID, name, phone or DOB (DD/MM/YYYY)" maxLength={100}
            value={query} onChange={event => setQuery(event.target.value)} /></div>
        <button type="button" className="patient-secondary" disabled={busy} onClick={() => {
          setQuery(""); setSearch(""); setPage(0); setRefresh(value => value + 1);
        }}>Clear</button>
      </div>
      {loading ? <p role="status">Loading patients...</p> : loadFailed ? <button className="patient-secondary" onClick={() => setRefresh(value => value + 1)}>Retry loading</button> : <>
        <p className="patient-count" role="status">{result.totalElements} {result.totalElements === 1 ? "patient" : "patients"}{search ? " found" : " registered"}</p>
        {result.items.length === 0 ? <section className="setup-section"><h2>{search ? "No matching patients" : "No patients yet"}</h2>
          <p>{search ? "Try another name, patient ID, phone number or DOB (DD/MM/YYYY or YYYY-MM-DD)." : "Register your first patient to get started."}</p></section> :
          <div className="patient-results">{result.items.map(patient => <article className="setup-section patient-card" key={patient.id}>
            <span className="patient-number">{patient.patientNumber}</span><h2>{patient.firstName} {patient.lastName}</h2>
            {!patient.active && <p className="patient-inactive">Inactive record</p>}
            <dl><div><dt>Date of birth</dt><dd>{dateLabel(patient.dateOfBirth)}</dd></div><div><dt>Phone</dt><dd>{patient.phone || "Not recorded"}</dd></div></dl>
            <button className="patient-secondary" disabled={busy} onClick={() => void view(patient.id)}>View patient<span className="patient-sr-only"> {patient.patientNumber}</span></button>
          </article>)}</div>}
        {result.totalPages > 1 && <nav className="patient-pagination" aria-label="Patient search pages">
          <button className="patient-secondary" disabled={page === 0 || busy} onClick={() => setPage(value => value - 1)}>Previous</button>
          <span>Page {page + 1} of {result.totalPages}</span>
          <button className="patient-secondary" disabled={page + 1 >= result.totalPages || busy} onClick={() => setPage(value => value + 1)}>Next</button>
        </nav>}
      </>}
    </>}

    {mode === "view" && selected && <section className="setup-section patient-profile">
      <div className="patient-profile-heading"><div><span className="patient-number">{selected.patientNumber}</span>
        <h2>{selected.details.firstName} {selected.details.lastName}</h2></div>
        <button className="patient-primary" disabled={busy} onClick={() => openForm(selected)}>Edit patient</button></div>
      {!selected.active && <p className="patient-inactive">Inactive record</p>}
      <dl className="patient-details">
        <div><dt>Date of birth</dt><dd>{dateLabel(selected.details.dateOfBirth)}</dd></div>
        <div><dt>Phone</dt><dd>{selected.details.phone || "Not recorded"}</dd></div>
        <div><dt>Email</dt><dd>{selected.details.email || "Not recorded"}</dd></div>
        <div className="setup-wide"><dt>Address</dt><dd>{selected.details.address || "Not recorded"}</dd></div>
      </dl>
    </section>}

    {mode === "view" && selected && token && <PatientAppointments key={selected.id} token={token} patientId={selected.id} doctor={user.role === "DOCTOR"} />}

    {mode === "form" && <form onSubmit={save} autoComplete="off">
      <fieldset disabled={busy} className="patient-fieldset">
        <section className="setup-section"><h2>{selected ? selected.patientNumber : "Patient details"}</h2>
          <p className="setup-required">Fields marked * are required. Leave date of birth blank if it is unknown.</p>
          <div className="setup-grid">
            <div className="setup-field"><label htmlFor="firstName">First name *</label><input ref={firstName} id="firstName" required pattern={".*\\S.*"} maxLength={100} value={values.firstName} onChange={event => change("firstName", event.target.value)} /></div>
            <div className="setup-field"><label htmlFor="lastName">Last name (optional)</label><input id="lastName" maxLength={100} value={values.lastName} onChange={event => change("lastName", event.target.value)} /></div>
            <div className="setup-field"><label htmlFor="dateOfBirth">Date of birth (optional)</label><input id="dateOfBirth" type="date" max={todayInIndia()} value={values.dateOfBirth ?? ""} onChange={event => change("dateOfBirth", event.target.value)} /></div>
            <div className="setup-field"><label htmlFor="phone">Contact number *</label><input id="phone" type="tel" inputMode="numeric" required pattern="[1-9][0-9]{9}" maxLength={10} aria-describedby="phone-hint" value={values.phone} onChange={event => change("phone", event.target.value)} /><small id="phone-hint">10 digits without +91 or a leading zero. A family contact number is welcome.</small></div>
            <div className="setup-field"><label htmlFor="patient-email">Email (optional)</label><input id="patient-email" type="email" maxLength={254} value={values.email} onChange={event => change("email", event.target.value)} /></div>
            <div className="setup-field setup-wide"><label htmlFor="address">Address *</label><textarea id="address" rows={3} required maxLength={1000} value={values.address} onChange={event => change("address", event.target.value)} /></div>
          </div>
        </section>
        {matches.length > 0 && <section className="patient-duplicates" aria-label="Possible duplicate patients">
          <h2>Review possible matches</h2><p>These records share the phone number or the same name and date of birth. Up to five matches are shown.</p>
          <ul>{matches.map(patient => <li key={patient.id}><div><strong>{patient.firstName} {patient.lastName}</strong>
            <p>{patient.patientNumber} · {patient.phone} · DOB: {dateLabel(patient.dateOfBirth)}</p></div>
            <button type="button" className="patient-secondary" onClick={() => void view(patient.id)}>View record<span className="patient-sr-only"> {patient.patientNumber}</span></button></li>)}</ul>
          <label className="patient-ack"><input type="checkbox" checked={acknowledged} onChange={event => setAcknowledged(event.target.checked)} /> I reviewed the matches and this is a separate patient.</label>
        </section>}
        <footer className="setup-actions"><button type="button" onClick={() => {
          if (!mayLeave()) return;
          setDirty(false); setMatches([]); setError(""); setMode(selected ? "view" : "list");
        }}>Cancel</button><button type="submit" disabled={matches.length > 0 && !acknowledged}>
          {busy ? "Saving..." : selected ? "Save changes" : "Register patient"}</button></footer>
      </fieldset>
    </form>}
  </div></main>;
}
