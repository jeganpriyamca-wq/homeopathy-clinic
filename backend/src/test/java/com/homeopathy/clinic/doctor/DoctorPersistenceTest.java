package com.homeopathy.clinic.doctor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.homeopathy.clinic.clinic.*;
import com.homeopathy.clinic.auth.*;
import com.homeopathy.clinic.security.*;
import com.homeopathy.clinic.user.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:doctors;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({DoctorService.class, ActiveUserValidator.class, DoctorPersistenceTest.Config.class})
class DoctorPersistenceTest {
    @TestConfiguration static class Config {
        @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    }
    @Autowired DoctorService service;
    @Autowired ClinicProfileRepository clinics;
    @Autowired UserRepository users;
    @Autowired DoctorRepository doctors;
    @Autowired EntityManager entityManager;
    @Autowired PasswordEncoder passwords;
    @Autowired ActiveUserValidator validator;

    void setupClinic() throws Exception {
        ClinicSettings settings = new ObjectMapper().findAndRegisterModules().readValue("""
            {"clinicName":"Test Clinic","displayName":"Test Clinic","mobile":"9876543210",
            "alternatePhone":"","email":"clinic@example.com","addressLine1":"Test Street","addressLine2":"",
            "city":"Chennai","district":"Chennai","state":"Tamil Nadu","pinCode":"600001","country":"India",
            "timezone":"Asia/Kolkata","currency":"INR","openingTime":"09:00","closingTime":"18:00",
            "weeklyClosedDay":"Sunday","appointmentDuration":"30","consultationFee":"450","gstin":"",
            "registrationNumber":"","prescriptionHeader":"","prescriptionFooter":"","emergencyContact":"","logo":""}
            """, ClinicSettings.class);
        clinics.saveAndFlush(new ClinicProfile(settings));
    }

    @Test void setupIsRequiredBeforeAccountCreation() {
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.create(
            new CreateDoctorRequest(DoctorTestData.details("meena@example.com", "TN-123"), "test-password-123"))).getStatusCode().value());
        assertEquals(0, users.count());
    }

    @Test void createRoundTripUpdateAndDeactivateLoginAndToken() throws Exception {
        setupClinic();
        DoctorResponse created = service.create(new CreateDoctorRequest(
            DoctorTestData.details("MEENA@example.com", "tn-123"), "test-password-123"));
        entityManager.clear();
        DoctorResponse loaded = service.get(created.id());
        assertEquals("TN-123", loaded.details().registrationNumber());
        assertEquals("meena@example.com", loaded.details().email());
        assertEquals(7, loaded.details().workingHours().size());
        assertEquals(java.time.LocalTime.of(9, 0), loaded.details().workingHours().getFirst().opensAt());
        User account = users.findById(loaded.userId()).orElseThrow();
        assertEquals(Role.DOCTOR, account.getRole());
        assertTrue(passwords.matches("test-password-123", account.getPassword()));
        assertNotEquals("test-password-123", account.getPassword());

        JwtService jwtService = mock(JwtService.class);
        when(jwtService.generateToken(any())).thenReturn("test-token");
        AuthService auth = new AuthService(users, passwords, jwtService);
        assertEquals("DOCTOR", auth.login(new LoginRequest("meena@example.com", "test-password-123")).role());
        Jwt token = Jwt.withTokenValue("test-token").header("alg", "HS256").subject("meena@example.com")
            .claim("userId", account.getId()).claim("role", "DOCTOR").build();
        assertFalse(validator.validate(token).hasErrors());

        DoctorResponse updated = service.update(created.id(), new UpdateDoctorRequest(loaded.version(),
            DoctorTestData.details("meena@example.com", "TN-124")));
        assertTrue(updated.version() > loaded.version());
        assertEquals(409, assertThrows(ResponseStatusException.class, () ->
            service.update(created.id(), new UpdateDoctorRequest(loaded.version(), loaded.details()))).getStatusCode().value());
        DoctorResponse inactive = service.setActive(created.id(), new DoctorStatusRequest(updated.version(), false));
        entityManager.clear();
        assertFalse(service.get(created.id()).active());
        assertTrue(validator.validate(token).hasErrors());
        assertThrows(BadCredentialsException.class, () -> auth.login(new LoginRequest("meena@example.com", "test-password-123")));
        service.setActive(created.id(), new DoctorStatusRequest(inactive.version(), true));
        entityManager.clear();
        assertTrue(service.get(created.id()).active());
        assertEquals(1, doctors.count());
    }

    @Test void duplicateEmailAndRegistrationDoNotCreateExtraAccounts() throws Exception {
        setupClinic();
        service.create(new CreateDoctorRequest(DoctorTestData.details("meena@example.com", "TN-123"), "test-password-123"));
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.create(new CreateDoctorRequest(
            DoctorTestData.details("MEENA@example.com", "TN-456"), "test-password-123"))).getStatusCode().value());
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.create(new CreateDoctorRequest(
            DoctorTestData.details("other@example.com", "tn-123"), "test-password-123"))).getStatusCode().value());
        assertEquals(1, users.count());
        assertEquals(1, doctors.count());
    }

    @Test void missingDoctorReturns404() {
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.get(999L)).getStatusCode().value());
    }
}
