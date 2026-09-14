package com.homeopathy.clinic.doctor;

import com.homeopathy.clinic.clinic.ClinicProfile;
import com.homeopathy.clinic.user.User;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "doctors")
public class DoctorProfile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Version
    private Long version;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinic_id", nullable = false)
    private ClinicProfile clinic;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;
    @Column(nullable = false, length = 10)
    private String mobile;
    @Column(nullable = false, length = 200)
    private String qualification;
    @Column(nullable = false, unique = true, length = 100)
    private String registrationNumber;
    @Column(nullable = false, length = 200)
    private String specialization;
    @Column(nullable = false, precision = 9, scale = 2)
    private BigDecimal consultationFee;
    @Column(nullable = false)
    private Integer appointmentDuration;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<DoctorDetails.WorkingDay> workingHours;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    protected DoctorProfile() {}
    public DoctorProfile(ClinicProfile clinic, User user) { this.clinic = clinic; this.user = user; }

    public void apply(DoctorDetails details) {
        mobile = details.mobile().trim();
        qualification = details.qualification().trim();
        registrationNumber = details.registrationNumber().trim().toUpperCase(java.util.Locale.ROOT);
        specialization = details.specialization().trim();
        consultationFee = details.consultationFee();
        appointmentDuration = details.appointmentDuration();
        workingHours = List.copyOf(details.workingHours());
        touch();
    }

    // A change to linked account fields must also advance the doctor's version.
    public void touch() { updatedAt = Instant.now(); }
    @PrePersist
    void created() { createdAt = updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getVersion() { return version; }
    public User getUser() { return user; }
    public DoctorDetails details() {
        return new DoctorDetails(user.getFirstName(), user.getLastName(), user.getEmail(), mobile,
            qualification, registrationNumber, specialization, consultationFee, appointmentDuration, workingHours);
    }
}
