package com.homeopathy.clinic.patient;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.ZoneId;

public record PatientDetails(
    @NotBlank @Size(max = 100) String firstName,
    @NotNull @Size(max = 100) String lastName,
    LocalDate dateOfBirth,
    @NotNull @Pattern(regexp = "[1-9][0-9]{9}", message = "Enter 10 digits without +91 or a leading zero") String phone,
    @NotNull @Email @Size(max = 254) String email,
    @NotBlank @Size(max = 1000) String address
) {
    @JsonIgnore
    @AssertTrue(message = "Date of birth cannot be in the future")
    public boolean isDateOfBirthValid() {
        return dateOfBirth == null || !dateOfBirth.isAfter(LocalDate.now(ZoneId.of("Asia/Kolkata")));
    }
}
