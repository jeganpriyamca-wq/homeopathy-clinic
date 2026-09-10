package com.homeopathy.clinic.doctor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.homeopathy.clinic.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@WebMvcTest(DoctorController.class)
@Import(SecurityConfig.class)
class DoctorControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean DoctorService service;
    @MockitoBean JwtDecoder decoder;
    final DoctorDetails details = DoctorTestData.details("meena@example.com", "TN-123");
    final DoctorResponse response = new DoctorResponse(1L, 0L, 2L, true, details);

    String body() throws Exception {
        return mapper.writeValueAsString(new CreateDoctorRequest(details, "test-password-123"));
    }

    @Test void allEndpointsRequireAdmin() throws Exception {
        for (String role : new String[]{"ANONYMOUS", "DOCTOR", "RECEPTIONIST"}) {
            for (MockHttpServletRequestBuilder request : List.of(
                get("/api/admin/doctors"), get("/api/admin/doctors/1"),
                post("/api/admin/doctors").content(body()),
                put("/api/admin/doctors/1").content("{}"),
                patch("/api/admin/doctors/1/status").content("{}"))) {
                request.contentType("application/json");
                if (!role.equals("ANONYMOUS")) request.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role)));
                mvc.perform(request).andExpect(status().is(role.equals("ANONYMOUS") ? 401 : 403));
            }
        }
        verifyNoInteractions(service);
    }

    @Test void adminCanCreateListEditAndDeactivateWithoutPasswordInResponses() throws Exception {
        when(service.create(any())).thenReturn(response);
        when(service.list()).thenReturn(List.of(response));
        when(service.get(1L)).thenReturn(response);
        when(service.update(eq(1L), any())).thenReturn(response);
        when(service.setActive(eq(1L), any())).thenReturn(new DoctorResponse(1L, 1L, 2L, false, details));
        var admin = jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
        mvc.perform(post("/api/admin/doctors").with(admin).contentType("application/json").content(body()))
            .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/admin/doctors/1"))
            .andExpect(jsonPath("$.password").doesNotExist()).andExpect(jsonPath("$.details.password").doesNotExist());
        mvc.perform(get("/api/admin/doctors").with(admin)).andExpect(status().isOk()).andExpect(jsonPath("$[0].details.firstName").value("Meena"));
        mvc.perform(get("/api/admin/doctors/1").with(admin)).andExpect(status().isOk());
        mvc.perform(put("/api/admin/doctors/1").with(admin).contentType("application/json")
            .content(mapper.writeValueAsString(new UpdateDoctorRequest(0L, details)))).andExpect(status().isOk());
        mvc.perform(patch("/api/admin/doctors/1/status").with(admin).contentType("application/json")
            .content("{\"version\":0,\"active\":false}")).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
    }

    @Test void invalidDetailsPasswordsAndSchedulesNeverReachService() throws Exception {
        ObjectNode valid = (ObjectNode) mapper.readTree(body());
        for (String field : new String[]{"email", "firstName", "registrationNumber", "qualification"}) {
            ObjectNode invalid = valid.deepCopy();
            ((ObjectNode) invalid.get("details")).put(field, " ");
            assertInvalid(invalid);
        }
        ObjectNode invalid = valid.deepCopy();
        ((ObjectNode) invalid.get("details")).put("consultationFee", -1);
        assertInvalid(invalid);
        invalid = valid.deepCopy();
        invalid.put("password", "short");
        assertInvalid(invalid);
        invalid = valid.deepCopy();
        invalid.put("password", "é".repeat(40));
        assertInvalid(invalid);
        invalid = valid.deepCopy();
        ((ObjectNode) invalid.at("/details/workingHours/1")).put("day", "MONDAY");
        assertInvalid(invalid);
        invalid = valid.deepCopy();
        ((ObjectNode) invalid.at("/details/workingHours/0")).put("closesAt", "08:00");
        assertInvalid(invalid);
        verifyNoInteractions(service);
    }

    void assertInvalid(ObjectNode body) throws Exception {
        mvc.perform(post("/api/admin/doctors").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
            .contentType("application/json").content(mapper.writeValueAsString(body)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors").isMap());
    }

    @Test void updateRequiresVersion() throws Exception {
        mvc.perform(put("/api/admin/doctors/1").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
            .contentType("application/json").content(mapper.writeValueAsString(new UpdateDoctorRequest(null, details))))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
