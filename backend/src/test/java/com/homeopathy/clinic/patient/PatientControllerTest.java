package com.homeopathy.clinic.patient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.homeopathy.clinic.config.SecurityConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@WebMvcTest(PatientController.class)
@Import(SecurityConfig.class)
class PatientControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean PatientService service;
    @MockitoBean JwtDecoder decoder;
    final PatientDetails details = PatientTestData.details("Arun", "9876543210");
    final PatientResponse response = new PatientResponse(1L, 1L, "PAT-000001", true, details);

    String body() throws Exception { return mapper.writeValueAsString(new CreatePatientRequest(details, false)); }

    @Test void anonymousAndUnknownRolesCannotAccessAnyPatientEndpoint() throws Exception {
        for (String role : new String[]{"ANONYMOUS", "OTHER"}) {
            for (MockHttpServletRequestBuilder request : List.of(get("/api/patients"), get("/api/patients/1"),
                post("/api/patients").content(body()), put("/api/patients/1").content("{}"))) {
                request.contentType("application/json");
                if (role.equals("OTHER")) request.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OTHER")));
                mvc.perform(request).andExpect(status().is(role.equals("ANONYMOUS") ? 401 : 403));
            }
        }
        verifyNoInteractions(service);
    }

    @Test void allThreeStaffRolesCanRegisterSearchViewAndEdit() throws Exception {
        when(service.create(any())).thenReturn(response);
        when(service.get(1L)).thenReturn(response);
        when(service.update(eq(1L), any())).thenReturn(response);
        when(service.search("", 0, 20)).thenReturn(new PatientSearchResponse(List.of(), 0, 20, 0, 0));
        for (String role : new String[]{"ADMIN", "DOCTOR", "RECEPTIONIST"}) {
            var staff = jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
            mvc.perform(post("/api/patients").with(staff).contentType("application/json").content(body()))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/patients/1"))
                .andExpect(jsonPath("$.patientNumber").value("PAT-000001"));
            mvc.perform(get("/api/patients").with(staff)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
            mvc.perform(get("/api/patients/1").with(staff)).andExpect(status().isOk()).andExpect(jsonPath("$.details.phone").value("9876543210"));
            mvc.perform(put("/api/patients/1").with(staff).contentType("application/json")
                .content(mapper.writeValueAsString(new UpdatePatientRequest(1L, details, false)))).andExpect(status().isOk());
        }
    }

    @Test void invalidFieldsAndFutureDateAreRejected() throws Exception {
        ObjectNode valid = (ObjectNode) mapper.readTree(body());
        for (String[] entry : new String[][]{{"firstName", " "}, {"phone", "123"}, {"email", "invalid"}, {"address", " "}, {"dateOfBirth", "2999-01-01"}}) {
            ObjectNode invalid = valid.deepCopy();
            ((ObjectNode) invalid.get("details")).put(entry[0], entry[1]);
            mvc.perform(post("/api/patients").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType("application/json").content(mapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors").isMap());
        }
        verifyNoInteractions(service);
    }

    @Test void updateRequiresVersionAndInvalidDateFormatReturns400() throws Exception {
        var staff = jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
        mvc.perform(put("/api/patients/1").with(staff).contentType("application/json")
            .content(mapper.writeValueAsString(new UpdatePatientRequest(null, details, false)))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/patients").with(staff).contentType("application/json")
            .content(body().replace("1990-05-15", "not-a-date"))).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void possibleMatchesReturnReviewableConflict() throws Exception {
        when(service.create(any())).thenThrow(new PossibleDuplicateException(List.of(
            new PatientSummary(1L, "PAT-000001", "Arun", "Kumar", details.dateOfBirth(), details.phone(), true))));
        mvc.perform(post("/api/patients").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_RECEPTIONIST")))
            .contentType("application/json").content(body())).andExpect(status().isConflict())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.code").value("POSSIBLE_DUPLICATE"))
            .andExpect(jsonPath("$.matches[0].patientNumber").value("PAT-000001"));
    }
}
