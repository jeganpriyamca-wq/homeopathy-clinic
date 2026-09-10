package com.homeopathy.clinic.clinic;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class ClinicSetupServiceTest {
    final ClinicProfileRepository repository = mock(ClinicProfileRepository.class);
    final ClinicSetupService service = new ClinicSetupService(repository);

    @Test void missingSetupReturns404() {
        when(repository.findById(1L)).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(ResponseStatusException.class, service::get).getStatusCode().value());
    }

    @Test void createsThenUpdatesTheSameProfile() throws Exception {
        ClinicSettings settings = new ObjectMapper().findAndRegisterModules()
            .readValue(ClinicSetupControllerTest.BODY, ClinicSettings.class);
        when(repository.findById(1L)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        assertEquals(settings, service.save(settings));

        ClinicProfile existing = new ClinicProfile(settings);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        assertEquals(settings, service.save(settings));
        verify(repository).saveAndFlush(existing);
        assertEquals(settings, service.get());
    }
}
