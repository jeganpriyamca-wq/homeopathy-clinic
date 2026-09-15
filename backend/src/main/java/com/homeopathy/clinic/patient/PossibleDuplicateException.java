package com.homeopathy.clinic.patient;

import java.util.List;

public class PossibleDuplicateException extends RuntimeException {
    private final List<PatientSummary> matches;
    public PossibleDuplicateException(List<PatientSummary> matches) {
        super("Possible matching patients found. Review them before saving a separate patient.");
        this.matches = List.copyOf(matches);
    }
    public List<PatientSummary> getMatches() { return matches; }
}
