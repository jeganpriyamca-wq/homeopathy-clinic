# Appointment booking and daily schedules

This feature builds on the patient registration branch. Admins and receptionists open Appointments from clinic navigation; doctor and receptionist dashboards show the daily schedule directly.

## Roles and workflow
- Admins/receptionists can book an active patient with an active doctor, reschedule a BOOKED appointment, cancel BOOKED/ARRIVED appointments, and update attendance.
- Doctors see only their own schedule and can start consultations for their checked-in patients. Reception/admin manages check-in, check-out, cancellation, no-show and follow-up booking.
- BOOKED -> ARRIVED, CANCELLED, NO_SHOW. ARRIVED -> IN_CONSULTATION, CANCELLED. IN_CONSULTATION -> COMPLETED. Display labels: ARRIVED = Checked in · Waiting; IN_CONSULTATION = Consultation in progress; COMPLETED = Checked out.
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

Views include id/version, doctorId/name, patientId/number/name, date/time/endTime, status, checkedInAt, consultationStartedAt, checkedOutAt and followUpForId. Timestamps are UTC instants; the UI displays IST. Older visits may have null workflow timestamps. Use the version from the latest response.
Invalid input returns 400; missing/invalid authentication 401; forbidden access 403; missing entities 404; unavailable slots, stale versions or invalid transitions 409.

## Persistence and concurrency
Hibernate ddl-auto=update creates appointments with doctor/patient foreign keys, version, status, timestamps and a doctor/start index.
All booking, rescheduling and status writes acquire a PESSIMISTIC_WRITE lock on the doctor row before reading appointments or checking availability. This serializes writes for that doctor across app instances. Version checks prevent overwriting stale appointment changes.
Direct database inserts bypass this service-level overlap protection. There is no PostgreSQL exclusion constraint in this version.
No live clinic database was changed during implementation.

## Testing
- Java 21 / Maven: 42 backend tests passed, including visit transitions, role restrictions, timestamps, follow-up links, slot availability, version conflicts and concurrent booking protection.
- TypeScript checking passed. Eight frontend tests cover visit actions, role-specific controls, follow-up dispatch, grid selection, unavailable slots and loading/error states.
- Backend database tests use H2 in PostgreSQL compatibility mode. The PostgreSQL migration and a live database round-trip still require verification in the development environment.
- The Vite production build is blocked locally by an esbuild subprocess `spawn EPERM` error. Browser interaction and visual layout for the new dashboard have not been verified here. Run `npm run build` and the manual workflow check in Codespaces.

Patient profile: GET /api/appointments/patient/{patientId} returns that patient's appointments (including history), newest first. Admins and receptionists see all doctors; doctors see only their own appointments. Missing patients return 404. Responses use Cache-Control: no-store. The profile displays No appointments scheduled for an empty result, with separate loading and retry states.

## Dashboard and visit workflow

Dashboard navigation is available from Patients, Appointments and the admin pages.
The dashboard shows All visits, Booked, Checked in · Waiting, Consultation in
progress and Checked out counts. Select a stage to filter the selected date and
doctor. Use Refresh schedule to see changes made by another staff member.
Reception/admin can start a consultation when the patient enters the doctor's room;
the assigned doctor can also start it. Checkout is done by reception/admin after
consultation. This change records workflow status, not clinical case notes or billing.

After checkout, Book follow-up opens a new booking with the same patient and doctor.
Choose a date and an available green slot. POST accepts optional followUpForId;
the server requires that the source visit is checked out, belongs to the same patient
and doctor, and precedes the new booking. Normal active-patient/doctor, availability
and concurrency checks still apply. The original visit remains checked out.
Follow-up bookings can be rescheduled/cancelled through the existing actions.

### Updating an existing PostgreSQL database

Before starting the updated backend, stop the old backend and run
`backend/db/visit-workflow.sql` in your development database's SQL editor (or with
`psql -v ON_ERROR_STOP=1 -f backend/db/visit-workflow.sql` using your existing secure
connection settings). It updates the status CHECK constraint and adds nullable visit
timestamps and the follow-up reference. The script is transactional and safe to rerun.
Existing records and their status names are preserved; old COMPLETED records display
as Checked out, and no timestamps are invented for historical visits. Fresh databases
are created with the new schema by the existing Hibernate configuration.

Then restart backend and frontend together. Do not test the new UI against an old
backend. No live database has been changed by this implementation.

### Manual workflow check

1. Log in as reception/admin and open Dashboard. Confirm Dashboard is the active tab.
2. Select today's test appointment, click Check in; it moves to Checked in · Waiting.
3. Click Start consultation (or do so as its doctor); it moves to Consultation in progress.
4. Click Check out as reception/admin; it moves to Checked out.
5. Click Book follow-up. Confirm the patient and doctor are prefilled and choose a date.
6. Pick a green slot and confirm. The new visit is Booked and marked Follow-up visit.
7. Refresh and inspect patient history: both original and follow-up visits remain.
8. Confirm doctors cannot check in/out, book follow-ups or update another doctor's visit.
9. Confirm skipping from Booked/Waiting to Checked out and stale updates are rejected.

Frontend checks: `node tests/appointment-actions.test.cjs`,
`node tests/appointment-slot-picker.test.cjs`, and `npx tsc -p tsconfig.app.json --noEmit`.
Backend checks: `mvn test` with Java 21.
