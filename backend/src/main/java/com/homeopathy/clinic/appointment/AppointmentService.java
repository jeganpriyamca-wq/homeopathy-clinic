package com.homeopathy.clinic.appointment;

import com.homeopathy.clinic.doctor.*;
import com.homeopathy.clinic.patient.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static com.homeopathy.clinic.appointment.AppointmentDtos.*;

@Service
@Transactional
public class AppointmentService {
    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private final AppointmentRepository appointments;
    private final DoctorRepository doctors;
    private final PatientRepository patients;
    public AppointmentService(AppointmentRepository appointments, DoctorRepository doctors, PatientRepository patients) {
        this.appointments = appointments; this.doctors = doctors; this.patients = patients;
    }
    private boolean manager(Jwt actor) {
        return Set.of("ADMIN", "RECEPTIONIST").contains(actor.getClaimAsString("role"));
    }
    private void requireManager(Jwt actor) {
        if (!manager(actor)) throw problem(HttpStatus.FORBIDDEN, "Only admins and receptionists can manage bookings.");
    }
    private void access(Jwt actor, DoctorProfile doctor) {
        if (!manager(actor) && (!"DOCTOR".equals(actor.getClaimAsString("role")) ||
            !Objects.equals(doctor.getUser().getId(), ((Number) actor.getClaim("userId")).longValue())))
            throw problem(HttpStatus.FORBIDDEN, "Doctors can access only their own appointments.");
    }
    @Transactional(readOnly = true)
    public List<View> patient(Jwt actor, Long patientId) {
        if (!manager(actor) && !"DOCTOR".equals(actor.getClaimAsString("role")))
            throw problem(HttpStatus.FORBIDDEN, "Staff access required.");
        if (!patients.existsById(patientId)) throw problem(HttpStatus.NOT_FOUND, "Patient not found.");
        Long userId = manager(actor) ? null : ((Number) actor.getClaim("userId")).longValue();
        return appointments.forPatient(patientId, userId).stream().map(View::from).toList();
    }
    @Transactional(readOnly = true)
    public List<DoctorOption> doctors(Jwt actor) {
        return doctors.findAllByOrderByIdAsc().stream()
            .filter(d -> manager(actor) || Objects.equals(d.getUser().getId(), ((Number) actor.getClaim("userId")).longValue()))
            .map(d -> new DoctorOption(d.getId(), d.getUser().getFirstName() + " " + d.getUser().getLastName(), d.getUser().isActive())).toList();
    }
    @Transactional(readOnly = true)
    public List<View> day(Jwt actor, LocalDate date, Long doctorId) {
        if (!manager(actor) && doctorId == null) {
            var own = doctors(actor);
            if (own.isEmpty()) return List.of();
            doctorId = own.getFirst().id();
        }
        if (doctorId != null) access(actor, doctor(doctorId));
        var start = date.atStartOfDay(ZONE).toInstant();
        var end = date.plusDays(1).atStartOfDay(ZONE).toInstant();
        return (doctorId == null ? appointments.day(start, end) : appointments.doctorDay(doctorId, start, end))
            .stream().map(View::from).toList();
    }
    @Transactional(readOnly = true)
    public Slots slots(Jwt actor, Long doctorId, LocalDate date, Long excludeId) {
        requireManager(actor);
        var doctor = doctor(doctorId);
        if (excludeId != null) {
            var excluded = find(excludeId);
            if (!excluded.doctor.getId().equals(doctorId) || excluded.status != Appointment.Status.BOOKED)
                throw problem(HttpStatus.BAD_REQUEST, "Only a booked appointment for this doctor can be rescheduled.");
        }
        return new Slots(doctorId, date, ZONE.getId(), available(doctor, date, excludeId));
    }
    public View create(Jwt actor, Booking request) {
        requireManager(actor);
        // All appointment mutations for a doctor serialize on this persistent row, including across app instances.
        var doctor = lockDoctor(request.doctorId());
        var patient = patients.findById(request.patientId()).orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "Patient not found."));
        if (!patient.isActive()) throw problem(HttpStatus.CONFLICT, "This patient is inactive.");
        validateSlot(doctor, request.date(), request.time(), null);
        var a = new Appointment(); a.doctor = doctor; a.patient = patient;
        setTime(a, request.date(), request.time());
        return View.from(appointments.saveAndFlush(a));
    }
    public View reschedule(Jwt actor, Long id, Reschedule request) {
        requireManager(actor);
        var a = lockedAppointment(id);
        version(a, request.version());
        if (a.status != Appointment.Status.BOOKED)
            throw problem(HttpStatus.CONFLICT, "Only booked appointments can be rescheduled.");
        if (!a.patient.isActive()) throw problem(HttpStatus.CONFLICT, "This patient is inactive.");
        validateSlot(a.doctor, request.date(), request.time(), id);
        setTime(a, request.date(), request.time());
        return View.from(appointments.saveAndFlush(a));
    }
    public View status(Jwt actor, Long id, StatusChange request) {
        var a = lockedAppointment(id);
        access(actor, a.doctor);
        version(a, request.version());
        boolean allowed = switch(a.status) {
            case BOOKED -> Set.of(Appointment.Status.ARRIVED, Appointment.Status.CANCELLED, Appointment.Status.NO_SHOW).contains(request.status());
            case ARRIVED -> Set.of(Appointment.Status.COMPLETED, Appointment.Status.CANCELLED).contains(request.status());
            default -> false;
        };
        if (!allowed) throw problem(HttpStatus.CONFLICT, "That status change is not allowed. Reload the appointment.");
        if (!manager(actor) && request.status() == Appointment.Status.CANCELLED)
            throw problem(HttpStatus.FORBIDDEN, "Ask reception to cancel this booking.");
        if (request.status() != Appointment.Status.CANCELLED && a.startsAt.atZone(ZONE).toLocalDate().isAfter(LocalDate.now(ZONE)))
            throw problem(HttpStatus.CONFLICT, "A future appointment cannot be marked arrived, completed or no-show.");
        if (request.status() == Appointment.Status.NO_SHOW && Instant.now().isBefore(a.endsAt))
            throw problem(HttpStatus.CONFLICT, "Mark no-show only after the appointment end time.");
        a.status = request.status();
        return View.from(appointments.saveAndFlush(a));
    }
    private List<LocalTime> available(DoctorProfile doctor, LocalDate date, Long excludeId) {
        if (!doctor.getUser().isActive() || date.isBefore(LocalDate.now(ZONE))) return List.of();
        var settings = doctor.details();
        var hours = settings.workingHours().stream().filter(d -> d.day() == date.getDayOfWeek()).findFirst().orElse(null);
        if (hours == null || hours.closed()) return List.of();
        var booked = appointments.doctorDay(doctor.getId(), date.atStartOfDay(ZONE).toInstant(), date.plusDays(1).atStartOfDay(ZONE).toInstant());
        var result = new ArrayList<LocalTime>();
        var now = Instant.now();
        // Date-time arithmetic avoids LocalTime wrapping at midnight.
        for (var start = date.atTime(hours.opensAt()); !start.plusMinutes(settings.appointmentDuration()).isAfter(date.atTime(hours.closesAt()));
             start = start.plusMinutes(settings.appointmentDuration())) {
            var from = start.atZone(ZONE).toInstant();
            var to = start.plusMinutes(settings.appointmentDuration()).atZone(ZONE).toInstant();
            if (from.isBefore(now)) continue;
            boolean overlaps = booked.stream().anyMatch(a -> !Objects.equals(a.id, excludeId)
                && a.status != Appointment.Status.CANCELLED && a.status != Appointment.Status.NO_SHOW
                && a.startsAt.isBefore(to) && a.endsAt.isAfter(from));
            if (!overlaps) result.add(start.toLocalTime());
        }
        return result;
    }
    private void validateSlot(DoctorProfile doctor, LocalDate date, LocalTime time, Long excludeId) {
        if (!available(doctor, date, excludeId).contains(time))
            throw problem(HttpStatus.CONFLICT, "This slot is no longer available. Choose another time.");
    }
    private void setTime(Appointment a, LocalDate date, LocalTime time) {
        a.startsAt = date.atTime(time).atZone(ZONE).toInstant();
        a.endsAt = a.startsAt.plusSeconds(a.doctor.details().appointmentDuration() * 60L);
    }
    private Appointment lockedAppointment(Long id) {
        Long doctorId = appointments.doctorId(id).orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "Appointment not found."));
        lockDoctor(doctorId);
        return find(id);
    }
    private void version(Appointment a, Long version) {
        if (!Objects.equals(a.version, version)) throw problem(HttpStatus.CONFLICT, "Appointment changed. Reload before trying again.");
    }
    private Appointment find(Long id) { return appointments.findById(id).orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "Appointment not found.")); }
    private DoctorProfile doctor(Long id) { return doctors.findById(id).orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "Doctor not found.")); }
    private DoctorProfile lockDoctor(Long id) { return doctors.lockForAppointment(id).orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "Doctor not found.")); }
    private ResponseStatusException problem(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
}
