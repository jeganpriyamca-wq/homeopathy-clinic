package com.homeopathy.clinic.doctor;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/doctors")
public class DoctorController {
    private final DoctorService service;
    public DoctorController(DoctorService service) { this.service = service; }

    @GetMapping
    public List<DoctorResponse> list() { return service.list(); }

    @GetMapping("/{id}")
    public DoctorResponse get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    public ResponseEntity<DoctorResponse> create(@Valid @RequestBody CreateDoctorRequest request) {
        DoctorResponse doctor = service.create(request);
        return ResponseEntity.created(URI.create("/api/admin/doctors/" + doctor.id())).body(doctor);
    }

    @PutMapping("/{id}")
    public DoctorResponse update(@PathVariable Long id, @Valid @RequestBody UpdateDoctorRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public DoctorResponse setActive(@PathVariable Long id, @Valid @RequestBody DoctorStatusRequest request) {
        return service.setActive(id, request);
    }
}
