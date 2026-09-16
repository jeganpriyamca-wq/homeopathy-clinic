package com.homeopathy.clinic.appointment;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@WebMvcTest(AppointmentController.class)
@Import(SecurityConfig.class)
class AppointmentControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AppointmentService service;
    @MockitoBean JwtDecoder decoder;
    @Test void slotResponseIncludesAvailabilityAndRetainsAvailableTimes() throws Exception {
        var date = java.time.LocalDate.of(2030,1,7);
        var time = java.time.LocalTime.of(9,0);
        when(service.slots(any(),eq(1L),eq(date),isNull())).thenReturn(new AppointmentDtos.Slots(
            1L,date,"Asia/Kolkata",List.of(time),List.of(new AppointmentDtos.Slot(time,true),
            new AppointmentDtos.Slot(time.plusMinutes(30),false))));
        mvc.perform(get("/api/appointments/slots?doctorId=1&date=2030-01-07")
            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_RECEPTIONIST"))))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.times[0]").value("09:00:00"))
            .andExpect(jsonPath("$.slots[0].available").value(true))
            .andExpect(jsonPath("$.slots[1].time").value("09:30:00"))
            .andExpect(jsonPath("$.slots[1].available").value(false));
    }
    @Test void anonymousAndUnknownRolesAreDenied() throws Exception {
        mvc.perform(get("/api/appointments?date=2030-01-01")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/appointments?date=2030-01-01").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OTHER")))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void doctorCannotCreateRescheduleOrQuerySlots() throws Exception {
        var doctor = jwt().authorities(new SimpleGrantedAuthority("ROLE_DOCTOR"));
        mvc.perform(post("/api/appointments").with(doctor).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/appointments/1").with(doctor).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/appointments/slots?doctorId=1&date=2030-01-01").with(doctor)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void invalidRequestsAreRejectedAndDailyResultsAreNotCached() throws Exception {
        var admin = jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
        mvc.perform(post("/api/appointments").with(admin).contentType("application/json").content("{}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/appointments?date=invalid").with(admin)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/appointments").with(admin)).andExpect(status().isBadRequest());
        when(service.day(any(),any(),isNull())).thenReturn(List.of());
        mvc.perform(get("/api/appointments?date=2030-01-01").with(admin)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
    }
}
