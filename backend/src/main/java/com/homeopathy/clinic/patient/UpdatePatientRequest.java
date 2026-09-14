package com.homeopathy.clinic.patient;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record UpdatePatientRequest(
    @NotNull @Min(0) Long version, @NotNull @Valid PatientDetails details, boolean duplicateAcknowledged
) {}
