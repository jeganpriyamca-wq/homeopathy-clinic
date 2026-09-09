package com.homeopathy.clinic.clinic;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/clinic")
public class ClinicSetupController {
    private final ClinicSetupService service;

    public ClinicSetupController(ClinicSetupService service) { this.service = service; }

    @GetMapping
    public ClinicSettings get() { return service.get(); }

    @PutMapping
    public ClinicSettings save(@Valid @RequestBody ClinicSettings settings) {
        return service.save(settings);
    }
}

