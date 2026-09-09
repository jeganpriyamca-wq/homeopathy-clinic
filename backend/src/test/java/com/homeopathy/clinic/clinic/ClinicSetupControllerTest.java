package com.homeopathy.clinic.clinic;

import com.homeopathy.clinic.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@WebMvcTest(ClinicSetupController.class)
@Import(SecurityConfig.class)
class ClinicSetupControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ClinicSetupService service;
    @MockitoBean JwtDecoder decoder;

    static final String BODY = """
{
  "clinicName": "Sample Clinic",
  "displayName": "Sample Clinic",
  "mobile": "9876543210",
  "alternatePhone": "",
  "email": "clinic@example.com",
  "addressLine1": "Chennai",
  "addressLine2": "",
  "city": "Chennai",
  "district": "Chennai",
  "state": "Tamil Nadu",
  "pinCode": "600001",
  "country": "India",
  "timezone": "Asia/Kolkata",
  "currency": "INR",
  "openingTime": "09:00",
  "closingTime": "17:00",
  "weeklyClosedDay": "Sunday",
  "appointmentDuration": "30",
  "consultationFee": "500.00",
  "gstin": "",
  "registrationNumber": "",
  "prescriptionHeader": "",
  "prescriptionFooter": "Follow up as advised",
  "emergencyContact": "",
  "logo": ""
}
        """;

    @Test void anonymousCannotReadOrWrite() throws Exception {
        mvc.perform(get("/api/admin/clinic")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/admin/clinic").contentType("application/json").content(BODY))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test void staffCannotReadOrWrite() throws Exception {
        for (String role : new String[]{"DOCTOR", "RECEPTIONIST"}) {
            var token = jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
            mvc.perform(get("/api/admin/clinic").with(token)).andExpect(status().isForbidden());
            mvc.perform(put("/api/admin/clinic").with(token).contentType("application/json").content(BODY))
                .andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }

    @Test void adminCanSaveAndRead() throws Exception {
        when(service.save(any())).thenAnswer(call -> call.getArgument(0));
        var token = jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
        mvc.perform(put("/api/admin/clinic").with(token).contentType("application/json").content(BODY))
            .andExpect(status().isOk()).andExpect(jsonPath("$.clinicName").value("Sample Clinic"));
        mvc.perform(get("/api/admin/clinic").with(token)).andExpect(status().isOk());
        verify(service).get();
    }

    @Test void rejectsInvalidFieldsAndSchedules() throws Exception {
        for (String body : new String[]{
            BODY.replace("Sample Clinic", " "),
            BODY.replace("clinic@example.com", "invalid"),
            BODY.replace("Asia/Kolkata", "Invalid/Zone"),
            BODY.replace("INR", "ZZZ"),
            BODY.replace("Sunday", "InvalidDay"),
            BODY.replace("17:00", "08:00"),
            BODY.replace("500.00", "-1"),
            BODY.replace("Tamil Nadu", "Unknown State"),
            BODY.replace("9876543210", "123"),
            BODY.replace("\"30\"", "\"241\""),
            BODY.replace("\"logo\": \"\"", "\"logo\": \"invalid\"")
        }) {
            mvc.perform(put("/api/admin/clinic")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors").isMap());
        }
        verifyNoInteractions(service);
    }

    @Test void malformedTimeReturnsBadRequest() throws Exception {
        mvc.perform(put("/api/admin/clinic")
            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
            .contentType("application/json").content(BODY.replace("09:00", "invalid")))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
