# Patient Registration and Search

Clinic staff (ADMIN, DOCTOR and RECEPTIONIST) can register, search, view and edit
patient demographics. Admins open **Patients** from the administration navigation;
doctors and receptionists open **Patient records → Open Patients** on their dashboards.

This builds on the existing patients table. Patient records belong to the single
clinic installation. Patient login accounts, appointments, clinical notes,
prescriptions, file uploads and deletion are outside this feature.

## HTTP contract

All endpoints require a valid staff bearer token. Inactive accounts are rejected
by the existing JWT account validator.

| Method | Path | Result |
| --- | --- | --- |
| GET | /api/patients?q=&page=0&size=20 | Paginated summaries |
| GET | /api/patients/{id} | Full patient profile |
| POST | /api/patients | Register a patient (201) |
| PUT | /api/patients/{id} | Update demographics (200) |

Search matches patient number, first/last name together, or phone, ignoring name
and ID case. Partial matches are supported. SQL wildcard characters are treated
literally. Results are newest first, using a stable ID sort. Page starts at 0;
size is 1–100 (default 20); q is at most 100 characters.

Search response:
```json
{
  "items": [{
    "id": 1, "patientNumber": "PAT-000001", "firstName": "Arun", "lastName": "Kumar",
    "dateOfBirth": "1990-05-15", "phone": "9876543210", "active": true
  }],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
}
```

POST body:
```json
{
  "details": {
    "firstName": "Arun",
    "lastName": "Kumar",
    "dateOfBirth": "1990-05-15",
    "phone": "9876543210",
    "email": "",
    "address": "12 Test Street, Chennai"
  },
  "duplicateAcknowledged": false
}
```

First name, phone and address are required. Last name/email use empty strings when
unknown. Date of birth may be null; it cannot be after today's date in Asia/Kolkata.
Phone is 10 digits without +91 or a leading zero; shared family numbers are allowed.
Names are trimmed with internal whitespace collapsed, email is normalized to
lowercase, and address is trimmed. Max lengths: names 100, email 254, address 1000.

A full response is `{id, version, patientNumber, active, details}`. PUT accepts
`{version, details, duplicateAcknowledged}`; use the version returned by the latest
GET/save. Editing cannot change the patient number, ID or active status.
Missing records return 404; stale versions return 409. Reload the profile before
reapplying a stale edit. Invalid requests return 400, missing authentication 401,
and unauthorized roles 403.

## Possible duplicate review

Before creating or editing, the server checks for:
- An exact normalized phone match; or
- The same first name, last name and date of birth when a birth date is provided.

An edit excludes the patient itself. A match returns 409 with
`code: "POSSIBLE_DUPLICATE"` and up to five patient summaries in `matches`.
The UI allows opening the existing profile or explicitly acknowledging a separate
patient and resubmitting with `duplicateAcknowledged: true`.
Changing any form field resets that acknowledgement. These are advisory matches,
not proof that records refer to the same person. Simultaneous registrations can
still require later duplicate review; phone numbers are deliberately not unique.

## Persistence and privacy

Patient numbers use PAT- plus the generated database ID padded to at least six
digits. Registration inserts with an internal temporary unique number, obtains the
ID and assigns the final patient number inside a single transaction. The temporary
number is never returned or committed. Existing patient numbers remain unchanged.
A conflicting legacy number triggers 409 and rolls back the registration.

Hibernate's existing ddl-auto=update adds:
- nullable address (up to 1000 characters), preserving legacy records;
- version with default 0 for optimistic locking on legacy and new records.

Existing missing address/phone/email values render as not recorded and must meet
current validation when editing. The existing active flag is preserved.
Search and detail responses use Cache-Control: no-store. The UI holds patient
details in component memory only; it does not persist them to localStorage.
Logout clears authentication and removes patient screens.

## Running checks

Use Java 21 and Maven:
```sh
cd backend
mvn clean test
```

Frontend:
```sh
cd frontend
npm ci
npm run build
npm run dev
```

Database tests use H2 in PostgreSQL compatibility mode. Verify schema update and
save/reload against a development PostgreSQL database before deployment; no live
clinic database is modified by these tests.

Manual checks with fake data:
1. Log in as admin, open Patients and register a patient. Note the patient number.
2. Edit contact details; reload and search by name, patient number and phone.
3. Register another patient with the same phone. Review the warning, then
   acknowledge a separate family member and save.
4. Open the same patient in two sessions. Save one edit, then confirm the stale
   edit in the other session is rejected until the profile is reloaded.
5. Verify doctors and receptionists can use the same patient screens.
6. Log out and confirm patient pages require login.

Implementation verification: 28 backend tests passed; TypeScript and Vite build
passed. Headless Chrome checks with fake/mocked API data passed for staff
navigation, registration, editing, ID/name/phone search, pagination, empty results,
duplicate review, save-error recovery, stale-edit handling, logout and responsive
layout. Desktop and mobile screenshots were visually reviewed.
