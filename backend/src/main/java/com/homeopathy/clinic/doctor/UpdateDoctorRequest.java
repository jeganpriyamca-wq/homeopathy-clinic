package com.homeopathy.clinic.doctor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record UpdateDoctorRequest(@NotNull @Min(0) Long version, @NotNull @Valid DoctorDetails details) {}
