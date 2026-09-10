package com.homeopathy.clinic.doctor;

import com.homeopathy.clinic.clinic.ClinicProfileRepository;
import com.homeopathy.clinic.user.*;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class DoctorService {
    private final DoctorRepository doctors;
    private final UserRepository users;
    private final ClinicProfileRepository clinics;
    private final PasswordEncoder passwords;

    public DoctorService(DoctorRepository doctors, UserRepository users,
                         ClinicProfileRepository clinics, PasswordEncoder passwords) {
        this.doctors = doctors;
        this.users = users;
        this.clinics = clinics;
        this.passwords = passwords;
    }

    @Transactional(readOnly = true)
    public List<DoctorResponse> list() {
        return doctors.findAllByOrderByIdAsc().stream().map(DoctorResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public DoctorResponse get(Long id) { return DoctorResponse.from(find(id)); }

    public DoctorResponse create(CreateDoctorRequest request) {
        var clinic = clinics.findById(1L).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.CONFLICT, "Complete Clinic Setup before adding doctors."));
        ensureUnique(request.details(), null, null);
        User user = new User();
        applyAccount(user, request.details());
        user.setRole(Role.DOCTOR);
        user.setActive(true);
        user.setPassword(passwords.encode(request.password()));
        users.saveAndFlush(user);
        DoctorProfile doctor = new DoctorProfile(clinic, user);
        doctor.apply(request.details());
        return DoctorResponse.from(doctors.saveAndFlush(doctor));
    }

    public DoctorResponse update(Long id, UpdateDoctorRequest request) {
        DoctorProfile doctor = find(id);
        checkVersion(doctor, request.version());
        ensureUnique(request.details(), doctor.getId(), doctor.getUser().getId());
        applyAccount(doctor.getUser(), request.details());
        doctor.apply(request.details());
        return DoctorResponse.from(doctors.saveAndFlush(doctor));
    }

    public DoctorResponse setActive(Long id, DoctorStatusRequest request) {
        DoctorProfile doctor = find(id);
        checkVersion(doctor, request.version());
        doctor.getUser().setActive(request.active());
        doctor.touch();
        return DoctorResponse.from(doctors.saveAndFlush(doctor));
    }

    private DoctorProfile find(Long id) {
        return doctors.findById(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found."));
    }

    private void checkVersion(DoctorProfile doctor, Long version) {
        if (!Objects.equals(doctor.getVersion(), version)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Doctor changed. Reload the list before editing again.");
        }
    }

    private void ensureUnique(DoctorDetails details, Long doctorId, Long userId) {
        users.findByEmailIgnoreCase(details.email().trim()).ifPresent(existing -> {
            if (userId == null || !Objects.equals(existing.getId(), userId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already used by another account.");
            }
        });
        String registration = details.registrationNumber().trim().toUpperCase(Locale.ROOT);
        boolean exists = doctorId == null ? doctors.existsByRegistrationNumber(registration)
            : doctors.existsByRegistrationNumberAndIdNot(registration, doctorId);
        if (exists) throw new ResponseStatusException(HttpStatus.CONFLICT, "Registration number is already used by another doctor.");
    }

    private void applyAccount(User user, DoctorDetails details) {
        user.setFirstName(details.firstName().trim());
        user.setLastName(details.lastName().trim());
        user.setEmail(details.email().trim().toLowerCase(Locale.ROOT));
    }
}
