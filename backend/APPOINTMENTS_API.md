# Appointment booking and daily schedules

This feature builds on the patient registration branch. Admins and receptionists open Appointments from clinic navigation; doctor and receptionist dashboards show the daily schedule directly.

## Roles and workflow
- Admins/receptionists can book an active patient with an active doctor, reschedule a BOOKED appointment, cancel BOOKED/ARRIVED appointments, and update attendance.
- Doctors see only their own schedule and can mark their own visits arrived, completed, or no-show. They cannot book, reschedule, cancel, or inspect another doctor's schedule.
- BOOKED -> ARRIVED, CANCELLED, NO_SHOW. ARRIVED -> COMPLETED, CANCELLED.
- Terminal statuses cannot be reopened. Attendance cannot be recorded for future dates; no-show is allowed only after the end time.
- Rescheduling retains the doctor and patient. To switch doctor, cancel and make a new booking.
- Schedule refresh is manual. API conflicts refresh availability and preserve the form, allowing another slot selection.
- No notifications, prescriptions, billing or recurring bookings are included.

## Time and availability
All input dates/times are Asia/Kolkata. The database stores start/end instants; API views return clinic-local dates and times.
Slots use the doctor's working days and appointment duration, aligned from opening time. The booking and rescheduling forms show a time grid: available slots are green, unavailable slots are grey and disabled, and the selected slot is dark green with a Selected label. Past slots, inactive doctors and overlapping occupied slots are unavailable. Closed days return an empty grid.
Cancelled/no-show visits do not occupy slots. Existing booking end times remain fixed if a doctor's duration later changes.
Doctor hours are independent of the clinic's default hours. This version prevents doctor overlaps; it does not prevent the same patient booking different doctors at overlapping times.

## API
Every endpoint requires a staff bearer token. All successful responses have Cache-Control: no-store.

| Method | URL | Purpose |
| --- | --- | --- |
| GET | /api/appointments?date=YYYY-MM-DD&doctorId=1 | Daily schedule; doctorId optional, doctors automatically scoped to themselves |
| GET | /api/appointments/doctors | Staff doctor selector; doctors receive only their own profile |
| GET | /api/appointments/slots?doctorId=1&date=YYYY-MM-DD | Available times; admin/reception only |
| POST | /api/appointments | Book, returns 201 |
| PUT | /api/appointments/{id} | Reschedule |
| PATCH | /api/appointments/{id}/status | Update status |

For rescheduling, slots accepts excludeId for the BOOKED appointment of that doctor.
The response retains `times` (available times only) and adds `slots`, containing every scheduled time and its availability, for example `[{"time":"09:00:00","available":true},{"time":"09:30:00","available":false}]`. Both lists use the same availability rules as booking validation. No patient details are included in slot availability.

POST:
```json
{"doctorId":1,"patientId":1,"date":"2030-01-07","time":"09:00"}
```

PUT:
```json
{"version":0,"date":"2030-01-07","time":"09:30"}
```

PATCH:
```json
{"version":1,"status":"CANCELLED"}
```

Views include id/version, doctorId/name, patientId/number/name, date/time/endTime and status. Use the version from the latest response.
Invalid input returns 400; missing/invalid authentication 401; forbidden access 403; missing entities 404; unavailable slots, stale versions or invalid transitions 409.

## Persistence and concurrency
Hibernate ddl-auto=update creates appointments with doctor/patient foreign keys, version, status, timestamps and a doctor/start index.
All booking, rescheduling and status writes acquire a PESSIMISTIC_WRITE lock on the doctor row before reading appointments or checking availability. This serializes writes for that doctor across app instances. Version checks prevent overwriting stale appointment changes.
Direct database inserts bypass this service-level overlap protection. There is no PostgreSQL exclusion constraint in this version.
No live clinic database was changed during implementation.

## Testing
- Time-grid update: TypeScript check and four component rendering tests passed (`cd frontend && node tests/appointment-slot-picker.test.cjs`). New backend tests cover slot flags, rescheduling exclusions, cancellation, past/closed days and inactive doctors, plus the JSON response. These new backend tests have not been run here because Java/Maven are unavailable; downloading them failed. The Vite build is blocked by a local subprocess `spawn EPERM` error. Browser interaction and a live PostgreSQL check remain unverified for this update. Restart the backend with the updated API before using the new frontend.
- Backend: cd backend && mvn test (Java 21). 38 tests passed, including real parallel transactions with exactly one successful booking of a shared slot, lifecycle/version checks, doctor ownership, inactive accounts, working hours and existing patient/doctor tests.
- Tests use H2 in PostgreSQL compatibility mode. PostgreSQL locking/schema behavior still needs confirmation in the development database.
- Frontend TypeScript check passed.
- Headless Chrome with mocked APIs passed: anonymous redirect, admin navigation, patient selection, availability, 409 recovery, booking, rescheduling, cancellation, doctor ownership UI, arrival/completion, reception dashboard, logout and desktop/mobile layouts.
- Vite production build requires rechecking: its temporary-file write was denied, and escalation hit an internal approval-review compatibility error.

Manual development check:
1. Pull the feature branch, restart backend, start frontend.
2. Log in as admin, open Appointments, choose a future working day and an active doctor.
3. Search an existing demo patient and select a slot. Confirm booking.
4. Refresh the schedule; verify patient, doctor, start/end and Booked status.
5. Try the same slot from another browser session; the server must reject the conflicting booking.
6. Reschedule; the old slot becomes available. Cancel; the new slot becomes available.
7. Book a visit for today and log in as its doctor. Mark Arrived then Completed.
8. Verify another doctor's appointments are absent and forbidden through the API.
9. Check no-show after a visit ends; terminal statuses cannot be changed.

Patient profile: GET /api/appointments/patient/{patientId} returns that patient's appointments (including history), newest first. Admins and receptionists see all doctors; doctors see only their own appointments. Missing patients return 404. Responses use Cache-Control: no-store. The profile displays No appointments scheduled for an empty result, with separate loading and retry states.
