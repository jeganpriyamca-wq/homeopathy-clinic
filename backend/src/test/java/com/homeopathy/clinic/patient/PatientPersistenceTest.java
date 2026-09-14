package com.homeopathy.clinic.patient;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:patients;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PatientService.class)
class PatientPersistenceTest {
    @Autowired PatientService service;
    @Autowired PatientRepository patients;
    @Autowired EntityManager entityManager;

    PatientResponse create(String firstName, String phone) {
        return service.create(new CreatePatientRequest(PatientTestData.details(firstName, phone), false));
    }

    @Test void uniqueNumbersPersistAndStayUnchangedAfterEditing() {
        PatientResponse one = create("Arun", "9876543210");
        PatientResponse two = create("Meena", "9876543211");
        assertNotEquals(one.patientNumber(), two.patientNumber());
        assertTrue(one.patientNumber().matches("PAT-[0-9]{6,}"));
        entityManager.clear();
        PatientResponse loaded = service.get(one.id());
        assertEquals("12 Test Street, Chennai", loaded.details().address());
        assertEquals(one.details(), loaded.details());
        PatientResponse updated = service.update(one.id(), new UpdatePatientRequest(loaded.version(),
            PatientTestData.details("Arun Updated", "9876543210"), false));
        entityManager.clear();
        assertEquals(one.patientNumber(), service.get(one.id()).patientNumber());
        assertTrue(updated.version() > loaded.version());
        assertEquals(409, assertThrows(ResponseStatusException.class, () ->
            service.update(one.id(), new UpdatePatientRequest(loaded.version(), loaded.details(), false))).getStatusCode().value());
    }

    @Test void sharedPhoneWarnsButAcknowledgementAllowsFamilyMembers() {
        PatientResponse first = create("Arun", "9876543210");
        PossibleDuplicateException warning = assertThrows(PossibleDuplicateException.class, () -> create("Meena", "9876543210"));
        assertEquals(first.id(), warning.getMatches().getFirst().id());
        assertEquals(1, patients.count());
        PatientResponse family = service.create(new CreatePatientRequest(PatientTestData.details("Meena", "9876543210"), true));
        assertNotEquals(first.patientNumber(), family.patientNumber());
        assertEquals(2, patients.count());
    }

    @Test void sameNameAndDateWarnsEvenWithDifferentPhone() {
        create("Arun", "9876543210");
        assertThrows(PossibleDuplicateException.class, () -> create(" arun ", "9876543211"));
    }

    @Test void updateExcludesSelfButChecksOtherPatients() {
        PatientResponse one = create("Arun", "9876543210");
        create("Meena", "9876543211");
        assertDoesNotThrow(() -> service.update(one.id(), new UpdatePatientRequest(one.version(), one.details(), false)));
        PatientResponse current = service.get(one.id());
        assertThrows(PossibleDuplicateException.class, () -> service.update(one.id(), new UpdatePatientRequest(
            current.version(), PatientTestData.details("Arun", "9876543211"), false)));
    }

    @Test void searchByNameIdPhoneAndPagesDoesNotInterpretWildcards() {
        PatientResponse one = create("Arun", "9876543210");
        create("Meena", "9876543211");
        assertEquals(one.id(), service.search("arun kumar", 0, 20).items().getFirst().id());
        assertEquals(one.id(), service.search(one.patientNumber().toLowerCase(), 0, 20).items().getFirst().id());
        assertEquals(one.id(), service.search("543210", 0, 20).items().getFirst().id());
        assertEquals(2, service.search("", 0, 1).totalPages());
        assertEquals(1, service.search("", 1, 1).items().size());
        assertEquals(0, service.search("%", 0, 20).totalElements());
        assertEquals(0, service.search("_", 0, 20).totalElements());
        assertEquals(0, service.search("' OR 1=1", 0, 20).totalElements());
    }

    @Test void unknownBirthDateAndOptionalFieldsRoundTrip() {
        var details = new PatientDetails("  Arun  Kumar ", "", null, "9876543210", "", " Test Address ");
        PatientResponse created = service.create(new CreatePatientRequest(details, false));
        entityManager.clear();
        var saved = service.get(created.id());
        assertNull(saved.details().dateOfBirth());
        assertEquals("", saved.details().lastName());
        assertEquals("Arun Kumar", saved.details().firstName());
        assertEquals("Test Address", saved.details().address());
    }

    @Test void legacyPatientNumbersAndMissingAddressArePreserved() {
        Patient legacy = new Patient();
        legacy.setPatientNumber("OLD-123");
        legacy.setFirstName("Legacy");
        legacy.setLastName("");
        patients.saveAndFlush(legacy);
        entityManager.clear();
        var loaded = service.get(legacy.getId());
        assertEquals("OLD-123", loaded.patientNumber());
        assertEquals("", loaded.details().address());
        assertEquals("", loaded.details().phone());
    }

    @Test void missingPatientsAndInvalidPaginationAreRejected() {
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.get(99999L)).getStatusCode().value());
        for (int[] limits : new int[][]{{-1,20},{0,0},{0,101}}) {
            assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.search("", limits[0], limits[1])).getStatusCode().value());
        }
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.search("x".repeat(101), 0, 20)).getStatusCode().value());
    }
}
