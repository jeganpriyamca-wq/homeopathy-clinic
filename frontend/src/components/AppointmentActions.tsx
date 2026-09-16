import type { Appointment, AppointmentStatus } from "../api/appointmentsApi";

type Props = {
  appointment: Appointment;
  manager: boolean;
  busy: boolean;
  today: string;
  onReschedule: (appointment: Appointment) => void;
  onFollowUp: (appointment: Appointment) => void;
  onStatus: (appointment: Appointment, status: AppointmentStatus) => void;
};

export default function AppointmentActions({ appointment: a, manager, busy, today, onReschedule, onFollowUp, onStatus }: Props) {
  return (
        <div className="appointment-actions">
          {manager && a.status==="BOOKED" && <button className="patient-secondary" disabled={busy} onClick={()=>onReschedule(a)}>Reschedule</button>}
          {manager && a.status==="BOOKED" && a.date<=today && <button className="patient-primary" disabled={busy} onClick={()=>onStatus(a,"ARRIVED")}>Check in</button>}
          {a.status==="ARRIVED" && a.date<=today && <button className="patient-primary" disabled={busy} onClick={()=>onStatus(a,"IN_CONSULTATION")}>Start consultation</button>}
          {manager && a.status==="IN_CONSULTATION" && a.date<=today && <button className="patient-primary" disabled={busy} onClick={()=>onStatus(a,"COMPLETED")}>Check out</button>}
          {!manager && a.status==="IN_CONSULTATION" && <p className="visit-note">Reception will check out the patient after the consultation.</p>}
          {manager && a.status==="COMPLETED" && <button className="patient-primary" disabled={busy} onClick={()=>onFollowUp(a)}>Book follow-up</button>}
          {manager && a.status==="BOOKED" && new Date(a.date+"T"+a.endTime+"+05:30").getTime()<=Date.now() && <button className="patient-secondary" disabled={busy} onClick={()=>onStatus(a,"NO_SHOW")}>No-show</button>}
          {manager && ["BOOKED","ARRIVED"].includes(a.status) && <button className="patient-secondary" disabled={busy} onClick={()=>onStatus(a,"CANCELLED")}>Cancel appointment</button>}
        </div>
  );
}
