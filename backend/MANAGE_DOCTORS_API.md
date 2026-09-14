# Manage Doctors

Admins can open **Manage Doctors** from Clinic Setup to create doctor accounts,
edit professional details and schedules, and deactivate/reactivate doctors.
Complete Clinic Setup before adding the first doctor.

Each doctor belongs to the installation's clinic (ID 1) and has a unique linked
user with role DOCTOR. Clinic data stays shared. Doctor fees and schedules are
stored separately. This feature does not yet book appointments or generate prescriptions.

## Endpoints

All endpoints require an ADMIN bearer token.

| Method | URL | Behavior |
| --- | --- | --- |
| GET | /api/admin/doctors | List active and inactive doctors |
| GET | /api/admin/doctors/{id} | Get a doctor |
| POST | /api/admin/doctors | Create profile and login atomically (201) |
| PUT | /api/admin/doctors/{id} | Replace editable details |
| PATCH | /api/admin/doctors/{id}/status | Activate/deactivate the linked account |

POST example:

```json
{
  "password": "use-a-unique-password",
  "details": {
    "firstName": "Meena",
    "lastName": "",
    "email": "meena@example.com",
    "mobile": "9876543210",
    "qualification": "BHMS",
    "registrationNumber": "TN-123",
    "specialization": "General practice",
    "consultationFee": 450.00,
    "appointmentDuration": 30,
    "workingHours": [
      {"day": "MONDAY", "closed": false, "opensAt": "09:00", "closesAt": "18:00"},
      {"day": "TUESDAY", "closed": false, "opensAt": "09:00", "closesAt": "18:00"},
      {"day": "WEDNESDAY", "closed": false, "opensAt": "09:00", "closesAt": "18:00"},
      {"day": "THURSDAY", "closed": false, "opensAt": "09:00", "closesAt": "18:00"},
      {"day": "FRIDAY", "closed": false, "opensAt": "09:00", "closesAt": "18:00"},
      {"day": "SATURDAY", "closed": false, "opensAt": "09:00", "closesAt": "13:00"},
      {"day": "SUNDAY", "closed": true, "opensAt": null, "closesAt": null}
    ]
  }
}
```

Response: `{id, version, userId, active, details}`. No password or password hash
is returned. PUT accepts `{version, details}`; status PATCH accepts
`{version, active}`. Use the latest response version. Stale changes return 409;
cancel the edit and reload the list before reapplying changes.

Fees use INR (0–1000000, at most two decimals). Duration is an integer from 5 to
240 minutes. Include each uppercase weekday exactly once, with one same-day
interval per open day. Closed days have null times. Times use Asia/Kolkata.
Schedules are stored independently from clinic hours; scheduling constraints
against clinic hours will be handled when appointment booking is implemented.

Email is normalized to lowercase and must be unique across all accounts.
Registration is normalized to uppercase and unique across doctor profiles.
Last name and mobile are optional empty strings. Other text fields are required.
Passwords are required only on creation: 12–72 characters and at most 72 UTF-8
bytes for BCrypt. This UI does not yet reset existing account passwords.

Validation errors return 400 with field errors. Unauthorized/forbidden requests
return 401/403, missing doctors return 404, duplicate values, incomplete clinic
setup and stale updates return 409.

Deactivation preserves the profile and user. It blocks login, and the JWT decoder
checks the account's active state, email, and role against the database on every
authenticated request, blocking already-issued tokens while inactive.
Changing the login email also invalidates tokens with the old email. Reactivation
allows login again (and any still-unexpired token matching the account).
No email notifications or credentials are sent automatically.

The existing Hibernate ddl-auto=update creates the new doctors table and its
foreign keys at startup. Use a development database first.

## Verification

- Java 21 / Maven: `cd backend && mvn clean test` — 15 tests passed.
- TypeScript and Vite production build passed.
- Headless browser smoke test passed for anonymous redirect, admin navigation,
  create/edit, invalid working hours, failed-save recovery, deactivate/reactivate,
  reload, and desktop/mobile layout, using mocked HTTP responses.
- Database tests use H2 in PostgreSQL compatibility mode and exercise JSON
  schedule round-trips, account/password creation, version conflicts, duplicate
  rejection, and inactive login/token rejection.
- An actual PostgreSQL run remains a deployment-environment check; no live clinic
  database was modified.

Manual check: log in as admin, add a test doctor, edit and reload it, then log in
as that doctor in a separate browser session. Deactivate the doctor as admin and
confirm new logins and authenticated API calls are rejected. Reactivate and
confirm login works again.
