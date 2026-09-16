package com.homeopathy.clinic.patient;

import java.time.LocalDate;

final class PatientTestData {
    static PatientDetails details(String firstName, String phone) {
        return new PatientDetails(firstName, "Kumar", LocalDate.of(1990, 5, 15), phone,
            "patient@example.com", "12 Test Street, Chennai");
    }
}
