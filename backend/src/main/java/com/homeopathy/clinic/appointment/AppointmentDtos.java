package com.homeopathy.clinic.appointment;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.List;

public final class AppointmentDtos {
    private AppointmentDtos() {}
    public record Booking(@NotNull @Positive Long doctorId, @NotNull @Positive Long patientId,
                          @NotNull LocalDate date, @NotNull LocalTime time, @Positive Long followUpForId) {
        public Booking(Long doctorId, Long patientId, LocalDate date, LocalTime time) {
            this(doctorId, patientId, date, time, null);
        }
    }
    public record Reschedule(@NotNull @Min(0) Long version, @NotNull LocalDate date, @NotNull LocalTime time) {}
    public record StatusChange(@NotNull @Min(0) Long version, @NotNull Appointment.Status status) {}
    public record DoctorOption(Long id, String name, boolean active) {}
    public record Slot(LocalTime time, boolean available) {}
    public record Slots(Long doctorId, LocalDate date, String timezone, List<LocalTime> times, List<Slot> slots) {}
    public record View(Long id, Long version, Long doctorId, String doctorName, Long patientId,
                       String patientNumber, String patientName, LocalDate date, LocalTime time,
                       LocalTime endTime, Appointment.Status status, Instant checkedInAt,
                       Instant consultationStartedAt, Instant checkedOutAt, Long followUpForId) {
        static View from(Appointment a) {
            // A prior visit may already be present as a lazy follow-up reference.
            a = org.hibernate.Hibernate.unproxy(a, Appointment.class);
            var start = a.startsAt.atZone(AppointmentService.ZONE);
            return new View(a.id, a.version, a.doctor.getId(),
                a.doctor.getUser().getFirstName() + " " + a.doctor.getUser().getLastName(),
                a.patient.getId(), a.patient.getPatientNumber(),
                a.patient.getFirstName() + " " + a.patient.getLastName(), start.toLocalDate(),
                start.toLocalTime(), a.endsAt.atZone(AppointmentService.ZONE).toLocalTime(), a.status,
                a.checkedInAt, a.consultationStartedAt, a.checkedOutAt,
                a.followUpFor == null ? null : a.followUpFor.getId());
        }
    }
}
