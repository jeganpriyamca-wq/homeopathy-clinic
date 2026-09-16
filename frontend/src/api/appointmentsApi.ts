import axios from "axios";
export type AppointmentStatus = "BOOKED" | "ARRIVED" | "COMPLETED" | "CANCELLED" | "NO_SHOW";
export type Appointment = { id: number; version: number; doctorId: number; doctorName: string;
  patientId: number; patientNumber: string; patientName: string; date: string; time: string; endTime: string; status: AppointmentStatus };
export async function patientAppointments(token: string, patientId: number, signal?: AbortSignal): Promise<Appointment[]> {
  return (await axios.get(url + "/patient/" + patientId, {...config(token), signal})).data;
}
export type BookingDoctor = { id: number; name: string; active: boolean };
export type AppointmentSlot = { time: string; available: boolean };
const url = `${import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080"}/api/appointments`;
const config = (token: string) => ({ headers: { Authorization: `Bearer ${token}` } });
export const clinicToday = () => {
  const parts = new Intl.DateTimeFormat("en", {timeZone:"Asia/Kolkata",year:"numeric",month:"2-digit",day:"2-digit"}).formatToParts(new Date());
  return ["year","month","day"].map(t => parts.find(p => p.type === t)?.value).join("-");
};
export async function appointmentDoctors(token: string, signal?: AbortSignal): Promise<BookingDoctor[]> {
  return (await axios.get(url + "/doctors", {...config(token), signal})).data;
}
export async function dailyAppointments(token: string, date: string, doctorId?: number, signal?: AbortSignal): Promise<Appointment[]> {
  return (await axios.get(url, {...config(token), params:{date,doctorId}, signal})).data;
}
export async function appointmentSlots(token: string, doctorId: number, date: string, excludeId?: number, signal?: AbortSignal): Promise<AppointmentSlot[]> {
  return (await axios.get(url + "/slots", {...config(token), params:{doctorId,date,excludeId}, signal})).data.slots;
}
export async function bookAppointment(token: string, doctorId: number, patientId: number, date: string, time: string): Promise<Appointment> {
  return (await axios.post(url,{doctorId,patientId,date,time},config(token))).data;
}
export async function moveAppointment(token: string, appointment: Appointment, date: string, time: string): Promise<Appointment> {
  return (await axios.put(url + "/" + appointment.id,{version:appointment.version,date,time},config(token))).data;
}
export async function changeAppointmentStatus(token: string, appointment: Appointment, status: AppointmentStatus): Promise<Appointment> {
  return (await axios.patch(url + "/" + appointment.id + "/status",{version:appointment.version,status},config(token))).data;
}
