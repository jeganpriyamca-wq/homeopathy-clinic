package com.homeopathy.clinic.appointment;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import static com.homeopathy.clinic.appointment.AppointmentDtos.*;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {
    private final AppointmentService service;
    public AppointmentController(AppointmentService service) { this.service = service; }
    @GetMapping public ResponseEntity<List<View>> day(@AuthenticationPrincipal Jwt actor,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @RequestParam(required = false) Long doctorId) {
        return reply(service.day(actor, date, doctorId));
    }
    @GetMapping("/doctors") public ResponseEntity<List<DoctorOption>> doctors(@AuthenticationPrincipal Jwt actor) {
        return reply(service.doctors(actor));
    }
    @GetMapping("/slots") public ResponseEntity<Slots> slots(@AuthenticationPrincipal Jwt actor,
        @RequestParam Long doctorId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @RequestParam(required = false) Long excludeId) {
        return reply(service.slots(actor, doctorId, date, excludeId));
    }
    @PostMapping public ResponseEntity<View> create(@AuthenticationPrincipal Jwt actor, @Valid @RequestBody Booking request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(service.create(actor, request));
    }
    @PutMapping("/{id}") public ResponseEntity<View> reschedule(@AuthenticationPrincipal Jwt actor,
        @PathVariable Long id, @Valid @RequestBody Reschedule request) {
        return reply(service.reschedule(actor, id, request));
    }
    @PatchMapping("/{id}/status") public ResponseEntity<View> status(@AuthenticationPrincipal Jwt actor,
        @PathVariable Long id, @Valid @RequestBody StatusChange request) {
        return reply(service.status(actor, id, request));
    }
    private <T> ResponseEntity<T> reply(T value) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value); }
}
