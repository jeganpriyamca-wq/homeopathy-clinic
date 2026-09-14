package com.homeopathy.clinic.patient;

import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/patients")
public class PatientController {
    private final PatientService service;
    public PatientController(PatientService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<PatientSearchResponse> search(@RequestParam(defaultValue = "") String q,
        @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.search(q, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PatientResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(id));
    }

    @PostMapping
    public ResponseEntity<PatientResponse> create(@Valid @RequestBody CreatePatientRequest request) {
        PatientResponse patient = service.create(request);
        return ResponseEntity.created(URI.create("/api/patients/" + patient.id())).cacheControl(CacheControl.noStore()).body(patient);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PatientResponse> update(@PathVariable Long id, @Valid @RequestBody UpdatePatientRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.update(id, request));
    }
}
