import { useEffect, useState } from "react";
import { patientAppointments } from "../api/appointmentsApi";
import type { Appointment } from "../api/appointmentsApi";

export default function PatientAppointments({ token, patientId, doctor }: { token: string; patientId: number; doctor: boolean }) {
  const [items, setItems] = useState<Appointment[]>([]);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [refresh, setRefresh] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setFailed(false); setItems([]);
    patientAppointments(token, patientId, controller.signal)
      .then(data => { if (!controller.signal.aborted) setItems(data); })
      .catch(() => { if (!controller.signal.aborted) setFailed(true); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [token, patientId, refresh]);
  return <section className="setup-section" aria-labelledby="patient-appointments-heading">
    <h2 id="patient-appointments-heading">Appointments</h2>
    <p>All times are in Asia/Kolkata (IST).{doctor ? " Only your appointments are shown." : ""}</p>
    {loading ? <p role="status">Loading appointments...</p> : failed ? <>
      <p role="alert">Unable to load appointments.</p>
      <button type="button" className="patient-secondary" onClick={() => setRefresh(value => value + 1)}>Retry appointments</button>
    </> : items.length === 0 ? <p>No appointments scheduled</p> : <div className="patient-results">
      {items.map(item => <article className="patient-card" key={item.id}>
        <h3>{item.date.split("-").reverse().join("/")} · {item.time.slice(0, 5)}–{item.endTime.slice(0, 5)}</h3>
        <p>Doctor: {item.doctorName}</p>
        <p>Status: {item.status.toLowerCase().replaceAll("_", " ")}</p>
      </article>)}
    </div>}
  </section>;
}
