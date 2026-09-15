import LogoutButton from "../components/LogoutButton";
import { useEffect, useRef, useState } from "react";
import type { ChangeEvent, FormEvent, ReactNode } from "react";
import "./ClinicSetupPage.css";
import axios from "axios";
import { Link, Navigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

const clinicUrl = `${import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080"}/api/admin/clinic`;

const states = ["Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar", "Chhattisgarh", "Goa", "Gujarat", "Haryana", "Himachal Pradesh", "Jharkhand", "Karnataka", "Kerala", "Madhya Pradesh", "Maharashtra", "Manipur", "Meghalaya", "Mizoram", "Nagaland", "Odisha", "Punjab", "Rajasthan", "Sikkim", "Tamil Nadu", "Telangana", "Tripura", "Uttar Pradesh", "Uttarakhand", "West Bengal"];
const territories = ["Andaman and Nicobar Islands", "Chandigarh", "Dadra and Nagar Haveli and Daman and Diu", "Delhi", "Jammu and Kashmir", "Ladakh", "Lakshadweep", "Puducherry"];
const days = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"];
const defaults = {
  clinicName: "", displayName: "", mobile: "", alternatePhone: "", email: "",
  addressLine1: "", addressLine2: "", city: "", district: "", state: "", pinCode: "",
  country: "India", timezone: "Asia/Kolkata", currency: "INR",
  openingTime: "09:00", closingTime: "18:00", weeklyClosedDay: "Sunday",
  appointmentDuration: "30", consultationFee: "", gstin: "", registrationNumber: "",
  prescriptionHeader: "", prescriptionFooter: "", emergencyContact: "", logo: "",
};
type Settings = typeof defaults;
type Key = keyof Settings;
const storageKey = "homeopathy-clinic:setup-draft:v1";

function Section({ id, number, title, description, children }: { id: string; number: string; title: string; description: string; children: ReactNode }) {
  return <section className="setup-section" id={id} aria-labelledby={`${id}-title`}>
    <header className="setup-section-heading"><span aria-hidden="true">{number}</span><div><h2 id={`${id}-title`}>{title}</h2><p>{description}</p></div></header>
    <div className="setup-grid">{children}</div>
  </section>;
}

export default function ClinicSetupPage() {
  const { user } = useAuth();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [loadFailed, setLoadFailed] = useState(false);
  const [values, setValues] = useState<Settings>({ ...defaults });
  const [status, setStatus] = useState("");
  const [storageError, setStorageError] = useState("");
  const [logoError, setLogoError] = useState("");
  const [readingLogo, setReadingLogo] = useState(false);
  const [dirty, setDirty] = useState(false);
  const reader = useRef<FileReader | null>(null);
  const logoInput = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!user || user.role !== "ADMIN") return;
    let cancelled = false;
    const controller = new AbortController();
    async function load() {
      setLoading(true);
      setLoadFailed(false);
      setStorageError("");
      try {
        const response = await axios.get<Settings>(clinicUrl, {
          headers: { Authorization: `Bearer ${user!.accessToken}` },
          signal: controller.signal,
        });
        if (cancelled) return;
        setValues(response.data);
        setStatus("Clinic settings loaded from the server.");
      } catch (error) {
        if (cancelled) return;
        if (axios.isAxiosError(error) && error.response?.status === 404) {
    try {
      const raw = localStorage.getItem(storageKey);
      if (raw) {
        const parsed: unknown = JSON.parse(raw);
        if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) throw new Error("Invalid draft");
        const next = { ...defaults };
        for (const key of Object.keys(defaults) as Key[]) {
          const value = (parsed as Record<string, unknown>)[key];
          if (typeof value === "string") next[key] = value;
        }
        if (!/^(data:image\/(png|jpeg|webp);base64,)/.test(next.logo) || next.logo.length > 1500000) next.logo = "";
        if (![...states, ...territories].includes(next.state)) next.state = "";
        if (!["", ...days].includes(next.weeklyClosedDay)) next.weeklyClosedDay = "Sunday";
        setValues({ ...next, country: "India", timezone: "Asia/Kolkata", currency: "INR" });
        setStatus("Your saved draft has been restored from this browser.");
      }
    } catch { setStorageError("The browser draft could not be restored. You can still fill in the form."); }

        } else {
          setLoadFailed(true);
          setStorageError("Clinic settings could not be loaded. Check your connection or sign in again, then reload this page.");
        }
      } finally { if (!cancelled) setLoading(false); }
    }
    void load();
    return () => { cancelled = true; controller.abort(); reader.current?.abort(); };
  }, [user]);

  useEffect(() => {
    if (!dirty) return;
    const warn = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = ""; };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty]);

  function update(key: Key, value: string) {
    setValues(current => ({ ...current, [key]: value }));
    setDirty(true);
    setStatus("");
  }

  function selectLogo(event: ChangeEvent<HTMLInputElement>) {
    reader.current?.abort();
    setReadingLogo(false);
    setLogoError("");
    const file = event.target.files?.[0];
    if (!file) return;
    if (!["image/png", "image/jpeg", "image/webp"].includes(file.type) || file.size > 1024 * 1024) {
      setLogoError("Choose a PNG, JPEG or WebP image no larger than 1 MB.");
      event.target.value = "";
      return;
    }
    const nextReader = new FileReader();
    reader.current = nextReader;
    setReadingLogo(true);
    nextReader.onload = () => {
      update("logo", String(nextReader.result));
      setReadingLogo(false);
    };
    nextReader.onerror = () => { setLogoError("This image could not be read. Please choose another file."); setReadingLogo(false); };
    nextReader.readAsDataURL(file);
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (readingLogo || saving || loading || loadFailed || !user) return;
    setStorageError("");
    setSaving(true);
    try {
      const clean = { ...values };
      for (const key of Object.keys(clean) as Key[]) clean[key] = clean[key].trim();
      const response = await axios.put<Settings>(clinicUrl, clean, {
        headers: { Authorization: `Bearer ${user.accessToken}` },
      });
      setValues(response.data);
      setDirty(false);
      setStatus("Clinic settings saved to the server.");
      try { localStorage.removeItem(storageKey); } catch { /* Server save succeeded. */ }
    } catch (error) {
      const data = axios.isAxiosError(error) ? error.response?.data : undefined;
      const fields = data?.errors && typeof data.errors === "object"
        ? Object.values(data.errors).filter((value): value is string => typeof value === "string").join(" ")
        : "";
      setStorageError(fields || data?.detail || "Clinic settings could not be saved. Your entries are still here. Check your connection or sign in again.");
    } finally { setSaving(false); }
  }

  function field(key: Key, label: string, options: { type?: string; required?: boolean; hint?: string; prefix?: string; pattern?: string; min?: number; max?: number; step?: string; maxLength?: number; autoComplete?: string; readOnly?: boolean } = {}) {
    const { hint, prefix, ...inputOptions } = options;
    return <div className="setup-field">
      <label htmlFor={key}>{label}{options.required && <span aria-hidden="true"> *</span>}</label>
      <div className={prefix ? "setup-prefixed" : undefined}>
        {prefix && <span aria-hidden="true">{prefix}</span>}
        <input id={key} name={key} value={values[key]} {...inputOptions}
          aria-label={prefix === "+91" ? `${label}, India country code +91` : undefined}
          aria-describedby={hint ? `${key}-hint` : undefined}
          inputMode={options.type === "tel" || key === "pinCode" ? "numeric" : undefined}
          title={hint} onChange={event => update(key, key === "gstin" ? event.target.value.toUpperCase() : event.target.value)} />
      </div>
      {hint && <small id={`${key}-hint`}>{hint}</small>}
    </div>;
  }

  if (!user) return <Navigate to="/login" replace />;
  if (user.role !== "ADMIN") return <Navigate to={user.role === "DOCTOR" ? "/doctor" : "/reception"} replace />;

  return <main className="clinic-setup">
    <div className="setup-shell">
      <div className="clinic-topbar"><nav className="clinic-admin-nav" aria-label="Administration"><span aria-current="page">Clinic Setup</span><Link to="/manage-doctors" onClick={event => { if (saving || (dirty && !window.confirm("Discard unsaved clinic changes?"))) event.preventDefault(); }}>Manage Doctors</Link><Link to="/patients" onClick={event => { if (saving || (dirty && !window.confirm("Discard unsaved changes?"))) event.preventDefault(); }}>Patients</Link><Link to="/appointments" onClick={event => { if (saving || (dirty && !window.confirm("Discard unsaved changes?"))) event.preventDefault(); }}>Appointments</Link></nav><LogoutButton disabled={saving || readingLogo} hasUnsavedChanges={dirty} /></div>
      <header className="setup-page-heading"><div><p className="setup-eyebrow">CLINIC WORKSPACE</p><h1>Make it your clinic.</h1><p>Set up the details your team and patients will see.</p></div><span className="setup-badge">India · INR · IST</span></header>
      <div className="setup-layout">
        <aside className="setup-sidebar"><nav aria-label="Clinic setup sections">
          {[["information", "Clinic Information"], ["contact", "Contact Details"], ["address", "Address"], ["timings", "Clinic Timings"], ["consultation", "Consultation Settings"], ["prescription", "Prescription Settings"]].map(([id, title], index) => <a href={`#${id}`} key={id}><span>0{index + 1}</span>{title}</a>)}
        </nav><p>Settings are saved securely to your clinic server and shared across devices.</p></aside>
        <form onSubmit={save} className="setup-form">
          <fieldset disabled={loading || saving || loadFailed} style={{ display: "contents" }}>
          <p className="setup-required">Fields marked * are required. All clinic times use Asia/Kolkata.</p>
          <Section id="information" number="01" title="Clinic Information" description="Your clinic’s identity, on screen and on paper.">
            {field("clinicName", "Clinic Name", { required: true, pattern: ".*\\S.*", maxLength: 150 })}
            {field("displayName", "Doctor / Clinic Display Name", { required: true, pattern: ".*\\S.*", maxLength: 150 })}
            <div className="setup-logo-row setup-wide"><div className="setup-logo-preview">{values.logo ? <img src={values.logo} alt="Clinic logo preview" onError={() => { update("logo", ""); setLogoError("This image cannot be displayed. Please choose another image."); }} /> : <span aria-hidden="true">✚</span>}</div><div className="setup-field"><label htmlFor="logo">Clinic Logo (optional)</label><input ref={logoInput} id="logo" type="file" accept="image/png,image/jpeg,image/webp" onChange={selectLogo} aria-describedby="logo-hint" /><small id="logo-hint">PNG, JPEG or WebP · maximum 1 MB</small>{values.logo && <button type="button" className="setup-text-button" onClick={() => { reader.current?.abort(); setReadingLogo(false); update("logo", ""); if (logoInput.current) logoInput.current.value = ""; }}>Remove logo</button>}{logoError && <p role="alert" className="setup-error">{logoError}</p>}</div></div>
            {field("registrationNumber", "Clinic Registration Number (optional)", { maxLength: 100 })}
          </Section>
          <Section id="contact" number="02" title="Contact Details" description="Help patients reach the right person.">
            {field("mobile", "Mobile Number", { required: true, type: "tel", prefix: "+91", pattern: "[6-9][0-9]{9}", maxLength: 10, hint: "Enter 10 digits starting with 6, 7, 8 or 9, without +91.", autoComplete: "tel-national" })}
            {field("alternatePhone", "Alternate Phone (optional)", { type: "tel", prefix: "+91", pattern: "[1-9][0-9]{9}", maxLength: 10, hint: "10 digits; for a landline include the STD code without the leading 0." })}
            {field("email", "Email", { type: "email", required: true, maxLength: 254, autoComplete: "email" })}
            {field("emergencyContact", "Emergency Contact (optional)", { type: "tel", prefix: "+91", pattern: "[1-9][0-9]{9}", maxLength: 10, hint: "Clinic emergency contact: 10 digits without +91 or a leading 0." })}
          </Section>
          <Section id="address" number="03" title="Address" description="A complete address makes your clinic easier to find.">
            {field("addressLine1", "Address Line 1", { required: true, pattern: ".*\\S.*", maxLength: 200, autoComplete: "address-line1" })}
            {field("addressLine2", "Address Line 2 / Landmark (optional)", { maxLength: 200, autoComplete: "address-line2" })}
            {field("city", "City / Town", { required: true, pattern: ".*\\S.*", maxLength: 100, autoComplete: "address-level2" })}
            {field("district", "District", { required: true, pattern: ".*\\S.*", maxLength: 100 })}
            <div className="setup-field"><label htmlFor="state">State / Union Territory *</label><select id="state" name="state" required autoComplete="address-level1" value={values.state} onChange={event => update("state", event.target.value)}><option value="">Select state or union territory</option><optgroup label="States">{states.map(state => <option key={state}>{state}</option>)}</optgroup><optgroup label="Union Territories">{territories.map(state => <option key={state}>{state}</option>)}</optgroup></select></div>
            {field("pinCode", "PIN Code", { required: true, pattern: "[0-9]{6}", maxLength: 6, hint: "Exactly 6 digits.", autoComplete: "postal-code" })}
            {field("country", "Country", { readOnly: true })}
          </Section>
          <Section id="timings" number="04" title="Clinic Timings" description="Set your regular daily hours and weekly day off.">
            {field("openingTime", "Clinic Opening Time", { type: "time", required: true })}
            <div className="setup-field"><label htmlFor="closingTime">Clinic Closing Time *</label><input id="closingTime" name="closingTime" type="time" required value={values.closingTime} aria-describedby="closing-hint" onChange={event => update("closingTime", event.target.value)} ref={node => { node?.setCustomValidity(values.closingTime <= values.openingTime ? "Closing time must be after opening time on the same day." : ""); }} /><small id="closing-hint">Closing time must be later on the same day.</small></div>
            <div className="setup-field"><label htmlFor="weeklyClosedDay">Weekly Closed Day</label><select id="weeklyClosedDay" name="weeklyClosedDay" value={values.weeklyClosedDay} onChange={event => update("weeklyClosedDay", event.target.value)}><option value="">Open every day</option>{days.map(day => <option key={day}>{day}</option>)}</select></div>
            {field("timezone", "Timezone", { readOnly: true })}
          </Section>
          <Section id="consultation" number="05" title="Consultation Settings" description="Keep your appointment schedule and fees consistent.">
            {field("appointmentDuration", "Appointment Duration (minutes)", { type: "number", required: true, min: 5, max: 240, step: "1" })}
            {field("consultationFee", "Consultation Fee", { type: "number", required: true, prefix: "₹", min: 0, max: 1000000, step: "0.01", hint: "Enter 0 for a free consultation." })}
            {field("currency", "Currency (₹)", { readOnly: true })}
            {field("gstin", "GSTIN (optional)", { maxLength: 15, pattern: "[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]", hint: "15 characters. Format check only; registration is not verified." })}
          </Section>
          <Section id="prescription" number="06" title="Prescription Settings" description="Add the wording you would like on your printed prescriptions.">
            {([ ["prescriptionHeader", "Prescription Header"], ["prescriptionFooter", "Prescription Footer"] ] as const).map(([key, label]) => <div className="setup-field setup-wide" key={key}><label htmlFor={key}>{label} (optional)</label><textarea id={key} name={key} rows={3} maxLength={1000} value={values[key]} onChange={event => update(key, event.target.value)} /></div>)}
            <div className="setup-prescription setup-wide" aria-label="Prescription header and footer preview"><small>HEADER & FOOTER PREVIEW</small><h3>{values.displayName || values.clinicName || "Your clinic name"}</h3><p>{[values.addressLine1, values.addressLine2, values.city, values.district, values.state, values.pinCode].filter(Boolean).join(", ")}</p>{values.mobile && <p>+91 {values.mobile}</p>}<p>{values.prescriptionHeader}</p><div className="setup-prescription-space">Prescription content will appear here.</div><p>{values.prescriptionFooter || "Your prescription footer"}</p></div>
          </Section>
          <footer className="setup-actions"><div><strong>{loading ? "Loading settings…" : dirty ? "Unsaved changes" : "Clinic settings"}</strong><p>Save your settings to make them available across devices.</p></div><button type="submit" disabled={readingLogo || saving || loading}>{saving ? "Saving…" : readingLogo ? "Reading logo…" : "Save clinic settings"}</button></footer>
          </fieldset>
          <p className="setup-status" role="status">{status}</p>
          {storageError && <p className="setup-error" role="alert">{storageError}</p>}
        </form>
      </div>
    </div>
  </main>;
}
