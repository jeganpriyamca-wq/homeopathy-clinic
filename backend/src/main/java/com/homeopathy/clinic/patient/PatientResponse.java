package com.homeopathy.clinic.patient;

public record PatientResponse(Long id, Long version, String patientNumber, boolean active, PatientDetails details) {
    static PatientResponse from(Patient patient) {
        return new PatientResponse(patient.getId(), patient.getVersion(), patient.getPatientNumber(), patient.isActive(),
            new PatientDetails(patient.getFirstName(), patient.getLastName(), patient.getDateOfBirth(),
                value(patient.getPhone()), value(patient.getEmail()), value(patient.getAddress())));
    }
    private static String value(String text) { return text == null ? "" : text; }
}
