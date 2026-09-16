package com.homeopathy.clinic.patient;

import java.time.LocalDate;

public record PatientSummary(Long id, String patientNumber, String firstName, String lastName,
                             LocalDate dateOfBirth, String phone, boolean active) {
    static PatientSummary from(Patient patient) {
        return new PatientSummary(patient.getId(), patient.getPatientNumber(), patient.getFirstName(),
            patient.getLastName(), patient.getDateOfBirth(), patient.getPhone(), patient.isActive());
    }
}
