package com.homeopathy.clinic.doctor;

public record DoctorResponse(Long id, Long version, Long userId, boolean active, DoctorDetails details) {
    static DoctorResponse from(DoctorProfile doctor) {
        return new DoctorResponse(doctor.getId(), doctor.getVersion(), doctor.getUser().getId(),
            doctor.getUser().isActive(), doctor.details());
    }
}
