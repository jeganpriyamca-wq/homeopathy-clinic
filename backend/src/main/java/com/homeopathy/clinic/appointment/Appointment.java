package com.homeopathy.clinic.appointment;

import com.homeopathy.clinic.doctor.DoctorProfile;
import com.homeopathy.clinic.patient.Patient;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "appointments", indexes = @Index(name = "idx_appointment_doctor_start", columnList = "doctor_id,starts_at"))
public class Appointment {
    public enum Status { BOOKED, ARRIVED, IN_CONSULTATION, COMPLETED, CANCELLED, NO_SHOW }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Version Long version;
    public Long getId() { return id; }
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "doctor_id", nullable = false)
    DoctorProfile doctor;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "patient_id", nullable = false)
    Patient patient;
    @Column(name = "starts_at", nullable = false) Instant startsAt;
    @Column(nullable = false) Instant endsAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false) Status status = Status.BOOKED;
    Instant checkedInAt;
    Instant consultationStartedAt;
    Instant checkedOutAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "follow_up_for_id", foreignKey = @ForeignKey(name = "fk_appointment_follow_up"))
    Appointment followUpFor;
    @Column(nullable = false, updatable = false) Instant createdAt;
    @Column(nullable = false) Instant updatedAt;
    @PrePersist void created() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate void updated() { updatedAt = Instant.now(); }
}
