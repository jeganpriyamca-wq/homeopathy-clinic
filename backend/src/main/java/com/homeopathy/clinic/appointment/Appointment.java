package com.homeopathy.clinic.appointment;

import com.homeopathy.clinic.doctor.DoctorProfile;
import com.homeopathy.clinic.patient.Patient;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "appointments", indexes = @Index(name = "idx_appointment_doctor_start", columnList = "doctor_id,starts_at"))
public class Appointment {
    public enum Status { BOOKED, ARRIVED, COMPLETED, CANCELLED, NO_SHOW }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Version Long version;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "doctor_id", nullable = false)
    DoctorProfile doctor;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "patient_id", nullable = false)
    Patient patient;
    @Column(name = "starts_at", nullable = false) Instant startsAt;
    @Column(nullable = false) Instant endsAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false) Status status = Status.BOOKED;
    @Column(nullable = false, updatable = false) Instant createdAt;
    @Column(nullable = false) Instant updatedAt;
    @PrePersist void created() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate void updated() { updatedAt = Instant.now(); }
}
