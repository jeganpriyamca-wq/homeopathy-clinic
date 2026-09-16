import AppointmentActions from "../components/AppointmentActions";
import AppointmentSlotPicker from "../components/AppointmentSlotPicker";
import { useEffect, useRef, useState } from "react";
import type { FormEvent } from "react";
import { Link, Navigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import LogoutButton from "../components/LogoutButton";
import { patientError, searchPatients } from "../api/patientsApi";
import type { PatientSummary } from "../api/patientsApi";
import { appointmentDoctors, appointmentSlots, appointmentStatusLabel as label, bookAppointment, changeAppointmentStatus, clinicToday, dailyAppointments, moveAppointment } from "../api/appointmentsApi";
import type { Appointment, AppointmentSlot, AppointmentStatus, BookingDoctor } from "../api/appointmentsApi";
import "./ClinicSetupPage.css";
import "./PatientsPage.css";
import "./AppointmentsPage.css";

const visitStages: AppointmentStatus[] = ["BOOKED", "ARRIVED", "IN_CONSULTATION", "COMPLETED"];
const visitTime = (value: string) => new Intl.DateTimeFormat("en-IN", {timeZone:"Asia/Kolkata",hour:"2-digit",minute:"2-digit"}).format(new Date(value));

export default function AppointmentsPage({ dashboardTitle }: { dashboardTitle?: string }) {
  const { user } = useAuth();
  const token = user?.accessToken;
  const manager = user?.role === "ADMIN" || user?.role === "RECEPTIONIST";
  const [date,setDate] = useState(clinicToday);
  const [filter,setFilter] = useState("");
  const [doctors,setDoctors] = useState<BookingDoctor[]>([]);
  const [items,setItems] = useState<Appointment[]>([]);
  const [loading,setLoading] = useState(true);
  const [error,setError] = useState("");
  const [actionError,setActionError] = useState("");
  const [message,setMessage] = useState("");
  const [refresh,setRefresh] = useState(0);
  const [open,setOpen] = useState(false);
  const [editing,setEditing] = useState<Appointment|null>(null);
  const [followUp,setFollowUp] = useState<Appointment|null>(null);
  const [stage,setStage] = useState<AppointmentStatus|"">("");
  const [doctorId,setDoctorId] = useState("");
  const [bookingDate,setBookingDate] = useState(clinicToday);
  const [time,setTime] = useState("");
  const [slots,setSlots] = useState<AppointmentSlot[]>([]);
  const [slotLoading,setSlotLoading] = useState(false);
  const [slotError,setSlotError] = useState("");
  const [query,setQuery] = useState("");
  const [matches,setMatches] = useState<PatientSummary[]>([]);
  const [patient,setPatient] = useState<PatientSummary|null>(null);
  const [patientLoading,setPatientLoading] = useState(false);
  const [patientSearchError,setPatientSearchError] = useState("");
  const [formError,setFormError] = useState("");
  const [busy,setBusy] = useState(false);
  const inFlight = useRef(false);
  const formHeading = useRef<HTMLHeadingElement>(null);
  const dashboardPath = user?.role === "DOCTOR" ? "/doctor" : user?.role === "RECEPTIONIST" ? "/reception" : "/dashboard";
  const visibleItems = stage ? items.filter(item=>item.status===stage) : items;

  useEffect(() => {
    if (open) { formHeading.current?.scrollIntoView({block:"start"}); formHeading.current?.focus(); }
  }, [open, editing, followUp]);

  useEffect(() => {
    if (!token || !date) return;
    const controller = new AbortController();
    setLoading(true); setError("");
    Promise.all([appointmentDoctors(token,controller.signal),
      dailyAppointments(token,date,filter ? Number(filter) : undefined,controller.signal)])
      .then(([roster,rows]) => { if (!controller.signal.aborted) {setDoctors(roster);setItems(rows);} })
      .catch(e => {if (!controller.signal.aborted) {setItems([]);setError(patientError(e).message);} })
      .finally(() => {if (!controller.signal.aborted) setLoading(false);});
    return () => controller.abort();
  },[token,date,filter,refresh]);

  useEffect(() => {
    setTime("");setSlots([]);setSlotError("");
    if (!token || !open || !doctorId || !bookingDate) {setSlotLoading(false);return;}
    const controller = new AbortController();setSlotLoading(true);
    appointmentSlots(token,Number(doctorId),bookingDate,editing?.id,controller.signal)
      .then(rows => {if (!controller.signal.aborted) setSlots(rows);})
      .catch(e => {if (!controller.signal.aborted) setSlotError(patientError(e).message);})
      .finally(() => {if (!controller.signal.aborted) setSlotLoading(false);});
    return () => controller.abort();
  },[token,open,doctorId,bookingDate,editing?.id,refresh]);

  useEffect(() => {
    setMatches([]);setPatientSearchError("");
    if (!token || !open || editing || followUp || query.trim().length < 2 || patient) {setPatientLoading(false);return;}
    const controller = new AbortController();setPatientLoading(true);
    const timer = window.setTimeout(() => {
      searchPatients(token,query.trim(),0,controller.signal)
        .then(page => {if (!controller.signal.aborted) setMatches(page.items.filter(p=>p.active));})
        .catch(e => {if (!controller.signal.aborted) setPatientSearchError(patientError(e).message);})
        .finally(() => {if (!controller.signal.aborted) setPatientLoading(false);});
    },300);
    return () => {window.clearTimeout(timer);controller.abort();};
  },[token,query,open,editing,followUp,patient]);

  useEffect(() => {
    if (!open) return;
    const warn = (e: BeforeUnloadEvent) => {e.preventDefault();e.returnValue="";};
    window.addEventListener("beforeunload",warn);
    return () => window.removeEventListener("beforeunload",warn);
  },[open]);

  function mayLeave() {return !busy && (!open || window.confirm("Discard this appointment form?"));}
  function begin(a: Appointment|null) {
    if (!mayLeave()) return;
    setFollowUp(null);setTime("");setSlots([]);
    setEditing(a);setDoctorId(String(a?.doctorId ?? (filter || doctors.find(d=>d.active)?.id || "")));
    setBookingDate(a?.date ?? (date < clinicToday() ? clinicToday() : date));
    setPatient(null);setQuery("");setFormError("");setMessage("");setOpen(true);setRefresh(n=>n+1);
  }
  function beginFollowUp(a: Appointment) {
    if (!mayLeave()) return;
    setEditing(null);setFollowUp(a);setDoctorId(String(a.doctorId));setBookingDate("");
    setTime("");setSlots([]);setPatient(null);setQuery("");setFormError("");setMessage("");setOpen(true);
  }
  async function save(e: FormEvent) {
    e.preventDefault();
    if (!token || inFlight.current || slotLoading || slotError || !slots.some(slot => slot.time === time && slot.available) || !doctorId || (!editing && !patient && !followUp)) return;
    inFlight.current=true;setBusy(true);setFormError("");
    try {
      const saved = editing ? await moveAppointment(token,editing,bookingDate,time)
        : await bookAppointment(token,Number(doctorId),followUp?.patientId ?? patient!.id,bookingDate,time,followUp?.id);
      setOpen(false);setDate(saved.date);setFilter(String(saved.doctorId));setRefresh(n=>n+1);
      setStage("");setMessage(editing ? "Appointment rescheduled." : followUp ? "Follow-up appointment booked." : "Appointment booked.");
    } catch(e) {setFormError(patientError(e).message);setRefresh(n=>n+1);}
    finally {inFlight.current=false;setBusy(false);}
  }
  async function update(a: Appointment,status: AppointmentStatus) {
    if (!token || inFlight.current) return;
    if (status === "CANCELLED" && !window.confirm("Cancel this appointment?")) return;
    inFlight.current=true;setBusy(true);setActionError("");setMessage("");
    try {await changeAppointmentStatus(token,a,status);setMessage("Appointment marked " + label(status).toLowerCase() + ".");}
    catch(e) {setActionError(patientError(e).message);}
    finally {setRefresh(n=>n+1);inFlight.current=false;setBusy(false);}
  }
  if (!user) return <Navigate to="/login" replace />;
  if (!["ADMIN","DOCTOR","RECEPTIONIST"].includes(user.role)) return <Navigate to="/login" replace />;

  return <main className="clinic-setup"><div className="setup-shell">
    <header className="clinic-topbar"><nav className="clinic-admin-nav" aria-label="Clinic navigation">
      {dashboardTitle ? <span aria-current="page">Dashboard</span> : <Link to={dashboardPath} onClick={e=>{if(!mayLeave())e.preventDefault();}}>Dashboard</Link>}
      {user.role === "ADMIN" && <><Link to="/clinic-setup" onClick={e=>{if(!mayLeave())e.preventDefault();}}>Clinic Setup</Link>
        <Link to="/manage-doctors" onClick={e=>{if(!mayLeave())e.preventDefault();}}>Manage Doctors</Link></>}
      <Link to="/patients" onClick={e=>{if(!mayLeave())e.preventDefault();}}>Patients</Link>
      {dashboardTitle ? <Link to="/appointments" onClick={e=>{if(!mayLeave())e.preventDefault();}}>Appointments</Link> : <span aria-current="page">Appointments</span>}
    </nav><LogoutButton disabled={busy} hasUnsavedChanges={open}/></header>
    <header className="setup-page-heading"><div><p className="setup-eyebrow">DAILY SCHEDULE</p>
      <h1>{dashboardTitle ?? "Appointments"}</h1><p>Check-in → Waiting → Consultation → Check-out → Follow-up. All times use IST.</p></div>
      {manager && <button className="patient-primary" disabled={busy || loading || !doctors.some(d=>d.active)} onClick={()=>begin(null)}>Book appointment</button>}
    </header>
    <p role="status" className="setup-status">{message}</p>
    <div className="patient-search">
      <div className="setup-field"><label htmlFor="schedule-date">Schedule date</label><input id="schedule-date" type="date" required value={date} disabled={busy} onChange={e=>{if(e.target.value)setDate(e.target.value);}}/></div>
      {manager && <div className="setup-field"><label htmlFor="schedule-doctor">Filter doctor</label><select id="schedule-doctor" value={filter} disabled={busy} onChange={e=>setFilter(e.target.value)}>
        <option value="">All doctors</option>{doctors.map(d=><option key={d.id} value={d.id}>{d.name}{d.active?"":" (inactive)"}</option>)}</select></div>}
      <button className="patient-secondary" disabled={busy} onClick={()=>setDate(clinicToday())}>Today</button>
      <button className="patient-secondary" disabled={busy} onClick={()=>setRefresh(n=>n+1)}>Refresh schedule</button>
    </div>

    {!loading && !error && <section className="visit-summary" aria-label="Visit progress for the selected date and doctor">
      <button type="button" aria-pressed={stage===""} disabled={busy} onClick={()=>setStage("")}><strong>{items.length}</strong><span>All visits</span></button>
      {visitStages.map(value=><button type="button" key={value} className={"visit-stage-"+value.toLowerCase()} aria-pressed={stage===value} disabled={busy} onClick={()=>setStage(value)}>
        <strong>{items.filter(item=>item.status===value).length}</strong><span>{label(value)}</span>
      </button>)}
    </section>}

    {open && manager && <form className="setup-section appointment-form" onSubmit={save}>
      <h2 ref={formHeading} tabIndex={-1}>{editing ? "Reschedule appointment" : followUp ? "Book follow-up appointment" : "Book appointment"}</h2>
      {editing && <p>{editing.patientName} · {editing.patientNumber}. Rescheduling keeps the same doctor.</p>}
      {followUp && <p>{followUp.patientName} · {followUp.patientNumber} · {followUp.doctorName}. Follow-up for the visit on {followUp.date}. Choose the next appointment date and time.</p>}
      {formError && <p role="alert" className="setup-error">{formError}</p>}
      <fieldset disabled={busy} className="patient-fieldset"><div className="setup-grid">
        <div className="setup-field"><label htmlFor="booking-doctor">Doctor *</label><select id="booking-doctor" required value={doctorId} disabled={!!editing || !!followUp} onChange={e=>setDoctorId(e.target.value)}>
          <option value="">Select doctor</option>{doctors.filter(d=>d.active || d.id===editing?.doctorId || d.id===followUp?.doctorId).map(d=><option key={d.id} value={d.id}>{d.name}{d.active?"":" (inactive)"}</option>)}</select></div>
        <div className="setup-field"><label htmlFor="booking-date">Appointment date *</label><input id="booking-date" type="date" required min={clinicToday()} value={bookingDate} onChange={e=>setBookingDate(e.target.value)}/></div>
        {!editing && !followUp && <div className="setup-field setup-wide"><label htmlFor="booking-patient">Find patient *</label>
          <input id="booking-patient" type="search" value={query} maxLength={100} placeholder="Name, patient ID, phone or DOB" onChange={e=>{setQuery(e.target.value);setPatient(null);}}/>
          {patient ? <p role="status">Selected: {patient.firstName} {patient.lastName} · {patient.patientNumber}</p> :
            <small>Type at least two characters. Up to 20 results; refine your search if needed.</small>}
          {patientSearchError && <p role="alert">{patientSearchError}</p>}
          {patientLoading ? <p role="status">Finding patients...</p> : !patient && query.trim().length>=2 && !patientSearchError &&
            <ul className="appointment-matches">{matches.length===0 ? <li>No active patients found.</li> : matches.map(p=><li key={p.id}>
              <button type="button" className="patient-secondary" onClick={()=>{setPatient(p);setQuery(p.firstName+" "+p.lastName);}}>
                {p.firstName} {p.lastName} · {p.patientNumber} · DOB {p.dateOfBirth ?? "unknown"} · {p.phone}</button></li>)}</ul>}
        </div>}
        <AppointmentSlotPicker slots={slots} time={time} loading={slotLoading} error={slotError}
          ready={!!doctorId && !!bookingDate} onChange={setTime} />
      </div><footer className="setup-actions"><button type="button" onClick={()=>{if(mayLeave())setOpen(false);}}>Cancel</button>
        <button type="submit" disabled={!time || slotLoading || !!slotError || (!editing&&!patient&&!followUp)}>{busy?"Saving...":editing?"Save new time":followUp?"Confirm follow-up":"Confirm booking"}</button></footer></fieldset>
    </form>}

    {actionError && <p className="setup-error" role="alert">{actionError}</p>}
    {error && <p className="setup-error" role="alert">{error}</p>}
    {loading ? <p role="status">Loading schedule...</p> : !error && <section aria-label="Daily appointments">
      <p className="patient-count" role="status">{visibleItems.length} appointments on {date}{stage ? " · "+label(stage) : ""}</p>
      {!visibleItems.length ? <div className="setup-section"><h2>{stage ? "No patients at this stage" : "No appointments for this date"}</h2><p>{stage ? "Choose another stage or All visits to see the full schedule." : manager?"Book an appointment or choose another date.":"Your appointments will appear here when booked."}</p></div> :
      <div className="appointment-list">{visibleItems.map(a=><article key={a.id} className="setup-section appointment-card">
        <div className="appointment-time">{a.time.slice(0,5)}–{a.endTime.slice(0,5)} <small>IST</small></div>
        <div><h2>{a.patientName}</h2><p>{a.patientNumber} · {a.doctorName}</p><span className={"appointment-status status-"+a.status.toLowerCase()}>{label(a.status)}</span>
          {a.followUpForId && <p className="visit-note">Follow-up visit</p>}
          <div className="visit-timestamps">{a.checkedInAt && <span>Checked in {visitTime(a.checkedInAt)}</span>}{a.consultationStartedAt && <span>Consultation {visitTime(a.consultationStartedAt)}</span>}{a.checkedOutAt && <span>Checked out {visitTime(a.checkedOutAt)}</span>}</div>
        </div>
        <AppointmentActions appointment={a} manager={manager} busy={busy} today={clinicToday()}
          onReschedule={begin} onFollowUp={beginFollowUp} onStatus={(appointment,status)=>void update(appointment,status)} />
      </article>)}</div>}
    </section>}
  </div></main>;
}
