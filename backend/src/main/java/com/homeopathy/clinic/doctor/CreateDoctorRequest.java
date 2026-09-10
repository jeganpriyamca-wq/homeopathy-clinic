package com.homeopathy.clinic.doctor;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;

public record CreateDoctorRequest(
    @NotNull @Valid DoctorDetails details,
    @NotBlank @Size(min = 12, max = 72) String password
) {
    @JsonIgnore
    @AssertTrue(message = "Password must be at most 72 UTF-8 bytes")
    public boolean isPasswordLengthValid() {
        return password != null && password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
