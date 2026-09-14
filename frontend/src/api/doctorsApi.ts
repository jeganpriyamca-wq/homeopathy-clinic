import axios from "axios";

export const weekdays = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"] as const;
export type WorkingDay = { day: typeof weekdays[number]; closed: boolean; opensAt: string | null; closesAt: string | null };
export type DoctorDetails = {
  firstName: string; lastName: string; email: string; mobile: string;
  qualification: string; registrationNumber: string; specialization: string;
  consultationFee: number; appointmentDuration: number; workingHours: WorkingDay[];
};
export type Doctor = { id: number; version: number; userId: number; active: boolean; details: DoctorDetails };
const url = `${import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080"}/api/admin/doctors`;
const config = (token: string) => ({ headers: { Authorization: `Bearer ${token}` } });

export async function listDoctors(token: string, signal?: AbortSignal): Promise<Doctor[]> {
  return (await axios.get<Doctor[]>(url, { ...config(token), signal })).data;
}
export async function createDoctor(token: string, details: DoctorDetails, password: string): Promise<Doctor> {
  return (await axios.post<Doctor>(url, { details, password }, config(token))).data;
}
export async function updateDoctor(token: string, doctor: Doctor, details: DoctorDetails): Promise<Doctor> {
  return (await axios.put<Doctor>(`${url}/${doctor.id}`, { version: doctor.version, details }, config(token))).data;
}
export async function setDoctorActive(token: string, doctor: Doctor): Promise<Doctor> {
  return (await axios.patch<Doctor>(`${url}/${doctor.id}/status`,
    { version: doctor.version, active: !doctor.active }, config(token))).data;
}
export function doctorError(error: unknown): string {
  if (axios.isAxiosError(error)) {
    if (error.response?.status === 401) return "Your session expired. Sign in again to continue.";
    if (error.response?.status === 403) return "Only administrators can manage doctors.";
    const data = error.response?.data;
    if (data?.errors && typeof data.errors === "object") {
      return Object.entries(data.errors).map(([field, message]) => `${field.replace(/^details[.]/, "")}: ${String(message)}`).join(" ");
    }
    if (typeof data?.detail === "string") return data.detail;
  }
  return "The request could not be completed. Check your connection and try again.";
}
