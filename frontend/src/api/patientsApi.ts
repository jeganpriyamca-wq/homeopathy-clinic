import axios from "axios";

export type PatientDetails = {
  firstName: string; lastName: string; dateOfBirth: string | null;
  phone: string; email: string; address: string;
};
export type Patient = { id: number; version: number; patientNumber: string; active: boolean; details: PatientDetails };
export type PatientSummary = { id: number; patientNumber: string; firstName: string; lastName: string; dateOfBirth: string | null; phone: string | null; active: boolean };
export type PatientPage = { items: PatientSummary[]; page: number; size: number; totalElements: number; totalPages: number };
const url = `${import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080"}/api/patients`;
const config = (token: string) => ({ headers: { Authorization: `Bearer ${token}` } });

export async function searchPatients(token: string, q: string, page: number, signal?: AbortSignal): Promise<PatientPage> {
  return (await axios.get<PatientPage>(url, { ...config(token), params: { q, page, size: 20 }, signal })).data;
}
export async function getPatient(token: string, id: number, signal?: AbortSignal): Promise<Patient> {
  return (await axios.get<Patient>(`${url}/${id}`, { ...config(token), signal })).data;
}
export async function savePatient(token: string, patient: Patient | null, details: PatientDetails, duplicateAcknowledged: boolean): Promise<Patient> {
  const body = { details, duplicateAcknowledged };
  return patient
    ? (await axios.put<Patient>(`${url}/${patient.id}`, { ...body, version: patient.version }, config(token))).data
    : (await axios.post<Patient>(url, body, config(token))).data;
}
export function patientError(error: unknown): { message: string; matches?: PatientSummary[] } {
  if (axios.isAxiosError(error)) {
    if (error.response?.status === 401) return { message: "Your session expired. Log out and sign in again." };
    if (error.response?.status === 403) return { message: "You do not have access to patient records." };
    const body = error.response?.data;
    if (body?.code === "POSSIBLE_DUPLICATE" && Array.isArray(body.matches)) {
      return { message: "Possible matching patients found. Review them before saving.", matches: body.matches };
    }
    if (body?.errors && typeof body.errors === "object") return {
      message: Object.entries(body.errors).map(([key, value]) => `${key.replace(/^details[.]/, "")}: ${String(value)}`).join(" "),
    };
    if (typeof body?.detail === "string") return { message: body.detail };
  }
  return { message: "The request could not be completed. Your entries are still here. Check your connection and try again." };
}
