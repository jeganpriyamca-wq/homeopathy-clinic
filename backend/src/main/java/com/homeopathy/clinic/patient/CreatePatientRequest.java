package com.homeopathy.clinic.patient;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CreatePatientRequest(@NotNull @Valid PatientDetails details, boolean duplicateAcknowledged) {}
