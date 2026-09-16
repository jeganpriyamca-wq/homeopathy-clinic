import { useId } from "react";
import type { AppointmentSlot } from "../api/appointmentsApi";
import "./AppointmentSlotPicker.css";

type Props = {
  slots: AppointmentSlot[];
  time: string;
  loading: boolean;
  error: string;
  ready: boolean;
  onChange: (time: string) => void;
};

export default function AppointmentSlotPicker({ slots, time, loading, error, ready, onChange }: Props) {
  const helpId = useId();
  return (
        <fieldset className="appointment-slot-picker setup-wide" disabled={loading || !!error} aria-describedby={helpId}>
          <legend>Appointment time (IST) *</legend>
          <p id={helpId}>Choose a green time slot. Grey slots are unavailable.</p>
          {!loading && !error && slots.length > 0 && <div className="appointment-slot-grid">
            {slots.map(slot => <label key={slot.time} className={"appointment-slot" + (!slot.available ? " is-unavailable" : "") + (time === slot.time ? " is-selected" : "")}>
              <input type="radio" name="booking-time" value={slot.time} required disabled={!slot.available}
                checked={time === slot.time} onChange={()=>onChange(slot.time)}
                aria-label={`${slot.time.slice(0,5)} IST, ${slot.available ? "available" : "unavailable"}`} />
              <span className="appointment-slot-time">{slot.time.slice(0,5)}</span>
              <span className="appointment-slot-state">{!slot.available ? "Unavailable" : time === slot.time ? "Selected" : "Available"}</span>
            </label>)}
          </div>}
          {loading ? <p role="status">Checking availability...</p> : error ? <p role="alert">{error}</p> :
            !ready ? <p>Select a doctor and date to see time slots.</p> :
            slots.length===0 ? <p>No working hours for this date. Choose another date or doctor.</p> :
            !slots.some(slot=>slot.available) && <p role="status">No available slots. Choose another date or doctor.</p>}
          {time && <p role="status">Selected time: <strong>{time.slice(0,5)} IST</strong></p>}
        </fieldset>
  );
}
