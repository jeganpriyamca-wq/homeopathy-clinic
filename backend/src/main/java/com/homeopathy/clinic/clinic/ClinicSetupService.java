package com.homeopathy.clinic.clinic;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ClinicSetupService {
    private final ClinicProfileRepository repository;

    public ClinicSetupService(ClinicProfileRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public ClinicSettings get() {
        return repository.findById(1L).map(ClinicProfile::getSettings)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Clinic setup has not been completed"));
    }

    @Transactional
    public ClinicSettings save(ClinicSettings settings) {
        ClinicProfile profile = repository.findById(1L).orElseGet(() -> new ClinicProfile(settings));
        profile.setSettings(settings);
        return repository.saveAndFlush(profile).getSettings();
    }
}

