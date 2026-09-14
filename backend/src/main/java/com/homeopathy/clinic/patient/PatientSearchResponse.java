package com.homeopathy.clinic.patient;

import java.util.List;

public record PatientSearchResponse(List<PatientSummary> items, int page, int size, long totalElements, int totalPages) {}
