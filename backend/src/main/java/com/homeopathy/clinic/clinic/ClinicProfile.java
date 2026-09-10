package com.homeopathy.clinic.clinic;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "clinic_profile")
public class ClinicProfile {
    @Id
    private Long id = 1L;

    @Version
    private Long version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private ClinicSettings settings;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ClinicProfile() {}

    public ClinicProfile(ClinicSettings settings) {
        this.settings = settings;
    }

    public ClinicSettings getSettings() { return settings; }
    public void setSettings(ClinicSettings settings) { this.settings = settings; }

    @PrePersist
    void onCreate() { createdAt = updatedAt = Instant.now(); }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }
}

