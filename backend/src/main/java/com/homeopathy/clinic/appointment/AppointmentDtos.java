package com.homeopathy.clinic.appointment;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.List;

public final class AppointmentDtos {
    private AppointmentDtos() {}
    public record Booking(@NotNull @Positive Long doctorId, @NotNull @Positive Long patientId,
                          @NotNull LocalDate date, @NotNull LocalTime time) {}
    public record Reschedule(@NotNull @Min(0) Long version, @NotNull LocalDate date, @NotNull LocalTime time) {}
    public record StatusChange(@NotNull @Min(0) Long version, @NotNull Appointment.Status status) {}
    public record DoctorOption(Long id, String name, boolean active) {}
    public record Slots(Long doctorId, LocalDate date, String timezone, List<LocalTime> times) {}
    public record View(Long id, Long version, Long doctorId, String doctorName, Long patientId,
                       String patientNumber, String patientName, LocalDate date, LocalTime time,
                       LocalTime endTime, Appointment.Status status) {
        static View from(Appointment a) {
            var start = a.startsAt.atZone(AppointmentService.ZONE);
            return new View(a.id, a.version, a.doctor.getId(),
                a.doctor.getUser().getFirstName() + " " + a.doctor.getUser().getLastName(),
                a.patient.getId(), a.patient.getPatientNumber(),
                a.patient.getFirstName() + " " + a.patient.getLastName(), start.toLocalDate(),
                start.toLocalTime(), a.endsAt.atZone(AppointmentService.ZONE).toLocalTime(), a.status);
        }
    }
}
