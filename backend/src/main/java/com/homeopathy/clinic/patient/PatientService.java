package com.homeopathy.clinic.patient;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class PatientService {
    private final PatientRepository patients;
    public PatientService(PatientRepository patients) { this.patients = patients; }

    @Transactional(readOnly = true)
    public PatientSearchResponse search(String query, int page, int size) {
        if (query.length() > 100 || page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a search of at most 100 characters, page >= 0 and size 1–100.");
        }
        String normalized = cleanName(query).toLowerCase(Locale.ROOT);
        // Treat SQL LIKE metacharacters as literal user input.
        String pattern = "%" + normalized.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        LocalDate birthDate = parseBirthDate(normalized);
        Specification<Patient> filter = (root, q, cb) -> normalized.isEmpty() ? cb.conjunction() :
            cb.or(cb.like(cb.lower(root.get("patientNumber")), pattern, '\\'),
                  cb.like(cb.lower(cb.concat(cb.concat(root.get("firstName"), " "), root.get("lastName"))), pattern, '\\'),
                  cb.like(root.get("phone"), pattern, '\\'),
                  birthDate == null ? cb.disjunction() : cb.equal(root.get("dateOfBirth"), birthDate));
        var result = patients.findAll(filter, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
        return new PatientSearchResponse(result.getContent().stream().map(PatientSummary::from).toList(),
            page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PatientResponse get(Long id) { return PatientResponse.from(find(id)); }

    public PatientResponse create(CreatePatientRequest request) {
        PatientDetails details = normalize(request.details());
        checkDuplicates(details, null, request.duplicateAcknowledged());
        Patient patient = new Patient();
        apply(patient, details);
        // Both flushes are in one transaction: the temporary number is never returned or committed.
        patient.setPatientNumber("NEW-" + UUID.randomUUID().toString().replace("-", "").substring(0, 26));
        patients.saveAndFlush(patient);
        patient.setPatientNumber(String.format(Locale.ROOT, "PAT-%06d", patient.getId()));
        return PatientResponse.from(patients.saveAndFlush(patient));
    }

    public PatientResponse update(Long id, UpdatePatientRequest request) {
        Patient patient = find(id);
        if (!Objects.equals(patient.getVersion(), request.version())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This patient changed. Reload the profile before editing again.");
        }
        PatientDetails details = normalize(request.details());
        checkDuplicates(details, id, request.duplicateAcknowledged());
        apply(patient, details);
        return PatientResponse.from(patients.saveAndFlush(patient));
    }

    private void checkDuplicates(PatientDetails details, Long excludeId, boolean acknowledged) {
        if (acknowledged) return;
        Specification<Patient> filter = (root, query, cb) -> {
            Predicate match = cb.equal(root.get("phone"), details.phone());
            if (details.dateOfBirth() != null) {
                match = cb.or(match, cb.and(
                    cb.equal(cb.lower(root.get("firstName")), details.firstName().toLowerCase(Locale.ROOT)),
                    cb.equal(cb.lower(root.get("lastName")), details.lastName().toLowerCase(Locale.ROOT)),
                    cb.equal(root.get("dateOfBirth"), details.dateOfBirth())));
            }
            return excludeId == null ? match : cb.and(match, cb.notEqual(root.get("id"), excludeId));
        };
        List<PatientSummary> matches = patients.findAll(filter, PageRequest.of(0, 5, Sort.by("id")))
            .stream().map(PatientSummary::from).toList();
        if (!matches.isEmpty()) throw new PossibleDuplicateException(matches);
    }

    private LocalDate parseBirthDate(String query) {
        for (String format : List.of("uuuu-MM-dd", "dd/MM/uuuu")) {
            try {
                return LocalDate.parse(query, DateTimeFormatter.ofPattern(format, Locale.ROOT)
                    .withResolverStyle(ResolverStyle.STRICT));
            } catch (DateTimeParseException ignored) {
                // Ordinary text and invalid dates still use the existing text search.
            }
        }
        return null;
    }

    private Patient find(Long id) {
        return patients.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found."));
    }

    private PatientDetails normalize(PatientDetails details) {
        return new PatientDetails(cleanName(details.firstName()), cleanName(details.lastName()), details.dateOfBirth(),
            details.phone().trim(), details.email().trim().toLowerCase(Locale.ROOT), details.address().trim());
    }
    private String cleanName(String value) { return value.trim().replaceAll("\\s+", " "); }
    private void apply(Patient patient, PatientDetails details) {
        patient.setFirstName(details.firstName());
        patient.setLastName(details.lastName());
        patient.setDateOfBirth(details.dateOfBirth());
        patient.setPhone(details.phone());
        patient.setEmail(details.email());
        patient.setAddress(details.address());
    }
}
