package com.homeopathy.clinic.doctor;

import jakarta.validation.constraints.*;

public record DoctorStatusRequest(@NotNull @Min(0) Long version, @NotNull Boolean active) {}
