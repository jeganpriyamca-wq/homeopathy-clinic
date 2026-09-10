# Clinic Setup API

One clinic profile per installation, shared by all doctors. Clinic consultation
duration and fee are defaults; individual doctor settings can be added later.

Both endpoints require an ADMIN bearer token:
- GET /api/admin/clinic returns settings, or 404 before first setup.
- PUT /api/admin/clinic creates or fully replaces settings and returns 200.

All fields match the existing India clinic form. Numeric form values are strings.
Optional fields use empty strings. Example:

```json
{
  "clinicName": "Sample Clinic",
  "displayName": "Sample Clinic",
  "mobile": "9876543210",
  "alternatePhone": "",
  "email": "clinic@example.com",
  "addressLine1": "Chennai",
  "addressLine2": "",
  "city": "Chennai",
  "district": "Chennai",
  "state": "Tamil Nadu",
  "pinCode": "600001",
  "country": "India",
  "timezone": "Asia/Kolkata",
  "currency": "INR",
  "openingTime": "09:00",
  "closingTime": "17:00",
  "weeklyClosedDay": "Sunday",
  "appointmentDuration": "30",
  "consultationFee": "500.00",
  "gstin": "",
  "registrationNumber": "",
  "prescriptionHeader": "",
  "prescriptionFooter": "Follow up as advised",
  "emergencyContact": "",
  "logo": ""
}
```

Validation matches the form's India phone, PIN, GSTIN format, state, hours,
duration (5–240 minutes), fee (0–1000000, up to two decimals), and length limits.
GSTIN validation checks format only. Blank weeklyClosedDay means open every day.
Country, timezone and currency are fixed to India, Asia/Kolkata and INR.

The optional logo is a PNG/JPEG/WebP data URL with a decoded limit of 1 MiB.
The API checks base64 and file signatures. This single clinic logo is stored
with settings in JSONB; this is not a design for patient photos or recordings.
Prescription header/footer are plain text, not HTML.

Invalid fields return 400 with an errors map; malformed JSON returns 400.
Missing/invalid tokens return 401; non-admin users receive 403.
Overlapping writes can return 409. Reload before retrying.
PUT fully replaces settings; sequential saves use the latest submission.

PostgreSQL persistence uses clinic_profile with fixed ID 1, JSONB settings,
an optimistic version, and creation/update timestamps. Existing Hibernate
ddl-auto=update creates the table at application startup.

The existing form now loads/saves through this API. On an initial 404 it restores
the previous browser draft, if present. Server failures preserve entries and do
not report success. Successful saves remove the old browser draft. Authentication
remains in memory as in the existing app; a page refresh requires login again.

Run `mvn test` in backend with Java 21 and Maven. Unit and MVC tests cover
authorization, validation, and service behavior. Tests have not been run locally:
Java/Maven are unavailable. A real PostgreSQL persistence round-trip and browser
save/reload should be verified before merging. No live database was modified.
