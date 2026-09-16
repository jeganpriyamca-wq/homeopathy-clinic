package com.homeopathy.clinic.appointment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.homeopathy.clinic.clinic.*;
import com.homeopathy.clinic.doctor.*;
import com.homeopathy.clinic.patient.*;
import com.homeopathy.clinic.user.*;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static com.homeopathy.clinic.appointment.AppointmentDtos.*;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:appointments;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import(AppointmentService.class)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class AppointmentPersistenceTest {
    @Autowired AppointmentService service;
    @Autowired AppointmentRepository appointments;
    @Autowired DoctorRepository doctors;
    @Autowired PatientRepository patients;
    @Autowired UserRepository users;
    @Autowired ClinicProfileRepository clinics;
    Long doctorId, otherDoctorId, patientId, doctorUserId;
    LocalDate date = LocalDate.now(AppointmentService.ZONE).plusDays(7).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    Jwt admin = actor("ADMIN", 999L);
    static Jwt actor(String role, Long id) {
        return Jwt.withTokenValue("test").header("alg","HS256").subject("test@example.com")
            .claim("role",role).claim("userId",id).build();
    }
    @BeforeEach void setup() throws Exception {
        var settings = new ObjectMapper().readValue("{}", ClinicSettings.class);
        var clinic = clinics.saveAndFlush(new ClinicProfile(settings));
        var one = makeDoctor(clinic,"one@example.com","TEST-1");
        doctorId = one.getId(); doctorUserId = one.getUser().getId();
        otherDoctorId = makeDoctor(clinic,"two@example.com","TEST-2").getId();
        Patient p = new Patient();p.setPatientNumber("PAT-TEST");p.setFirstName("Demo");p.setLastName("Patient");p.setPhone("1000000001");
        patientId = patients.saveAndFlush(p).getId();
    }
    DoctorProfile makeDoctor(ClinicProfile clinic,String email,String registration) {
        User user = new User();user.setFirstName("Test");user.setLastName("Doctor");user.setEmail(email);user.setPassword("unused-hash");user.setRole(Role.DOCTOR);
        users.saveAndFlush(user);
        DoctorProfile d = new DoctorProfile(clinic,user);
        d.apply(new DoctorDetails("Test","Doctor",email,"","BHMS",registration,"General",BigDecimal.valueOf(400),30,
            Arrays.stream(DayOfWeek.values()).map(day -> new DoctorDetails.WorkingDay(day,day==DayOfWeek.SUNDAY,
                day==DayOfWeek.SUNDAY?null:LocalTime.of(9,0),day==DayOfWeek.SUNDAY?null:LocalTime.of(11,0))).toList()));
        return doctors.saveAndFlush(d);
    }
    @AfterEach void cleanup() {
        // Follow-up rows reference earlier visits; remove those references first.
        appointments.findAll().forEach(a -> { a.followUpFor = null; appointments.saveAndFlush(a); });
        appointments.deleteAllInBatch();doctors.deleteAllInBatch();patients.deleteAllInBatch();users.deleteAllInBatch();clinics.deleteAllInBatch();
    }
    @Test void patientAppointmentsAreScopedSortedAndIncludeHistory() {
        assertTrue(service.patient(admin, patientId).isEmpty());
        var early = book(LocalTime.of(9, 0));
        var later = service.create(admin, new Booking(otherDoctorId, patientId, date, LocalTime.of(10, 0)));
        service.status(admin, early.id(), new StatusChange(early.version(), Appointment.Status.CANCELLED));
        assertEquals(List.of(later.id(), early.id()), service.patient(admin, patientId).stream().map(View::id).toList());
        assertEquals(2, service.patient(actor("RECEPTIONIST", 999L), patientId).size());
        var own = service.patient(actor("DOCTOR", doctorUserId), patientId);
        assertEquals(1, own.size());
        assertEquals(Appointment.Status.CANCELLED, own.getFirst().status());
        Patient other = new Patient(); other.setPatientNumber("PAT-OTHER"); other.setFirstName("Other"); other.setLastName("Patient"); other.setPhone("1000000002");
        other = patients.saveAndFlush(other);
        assertTrue(service.patient(admin, other.getId()).isEmpty());
        assertThrows(ResponseStatusException.class, () -> service.patient(admin, Long.MAX_VALUE));
    }
    View book(LocalTime time) { return service.create(admin,new Booking(doctorId,patientId,date,time)); }
    @Test void slotsRespectHoursPastDaysAndOverlaps() {
        assertEquals(4,service.slots(admin,doctorId,date,null).times().size());
        assertTrue(service.slots(admin,doctorId,date.with(TemporalAdjusters.next(DayOfWeek.SUNDAY)),null).times().isEmpty());
        assertTrue(service.slots(admin,doctorId,date.minusYears(2),null).times().isEmpty());
        var booked = book(LocalTime.of(9,0));
        assertEquals(LocalTime.of(9,30),booked.endTime());
        assertFalse(service.slots(admin,doctorId,date,null).times().contains(LocalTime.of(9,0)));
        assertThrows(ResponseStatusException.class,()->book(LocalTime.of(9,15)));
        assertThrows(ResponseStatusException.class,()->book(LocalTime.of(11,0)));
        assertThrows(ResponseStatusException.class,()->book(LocalTime.of(9,0)));
        assertEquals(1,service.day(admin,date,null).size());
        assertEquals(1,appointments.count());
    }
    @Test void slotGridIncludesUnavailableTimesAndExcludesTheBookingBeingMoved() {
        var booking = book(LocalTime.of(9,0));
        var grid = service.slots(admin,doctorId,date,null);
        assertEquals(List.of(new Slot(LocalTime.of(9,0),false), new Slot(LocalTime.of(9,30),true),
            new Slot(LocalTime.of(10,0),true), new Slot(LocalTime.of(10,30),true)), grid.slots());
        assertEquals(grid.times(), grid.slots().stream().filter(Slot::available).map(Slot::time).toList());
        assertTrue(service.slots(admin,doctorId,date,booking.id()).slots().getFirst().available());
        service.status(admin,booking.id(),new StatusChange(booking.version(),Appointment.Status.CANCELLED));
        assertTrue(service.slots(admin,doctorId,date,null).slots().stream().allMatch(Slot::available));
        var past = service.slots(admin,doctorId,date.minusWeeks(104),null);
        assertEquals(4,past.slots().size());
        assertTrue(past.slots().stream().noneMatch(Slot::available));
        assertTrue(service.slots(admin,doctorId,date.with(TemporalAdjusters.next(DayOfWeek.SUNDAY)),null).slots().isEmpty());
        User user = users.findById(doctorUserId).orElseThrow();user.setActive(false);users.saveAndFlush(user);
        assertTrue(service.slots(admin,doctorId,date,null).slots().stream().noneMatch(Slot::available));
    }
    @Test void rescheduleUsesVersionAndCancellationReleasesSlot() {
        var first = book(LocalTime.of(9,0));
        var moved = service.reschedule(admin,first.id(),new Reschedule(first.version(),date,LocalTime.of(10,0)));
        assertTrue(moved.version()>first.version());
        assertThrows(ResponseStatusException.class,()->service.reschedule(admin,first.id(),new Reschedule(first.version(),date,LocalTime.of(9,30))));
        assertTrue(service.slots(admin,doctorId,date,null).times().contains(LocalTime.of(9,0)));
        var cancelled = service.status(admin,moved.id(),new StatusChange(moved.version(),Appointment.Status.CANCELLED));
        assertTrue(service.slots(admin,doctorId,date,null).times().contains(LocalTime.of(10,0)));
        assertThrows(ResponseStatusException.class,()->service.reschedule(admin,cancelled.id(),new Reschedule(cancelled.version(),date,LocalTime.of(10,30))));
    }
    @Test void doctorsCannotBookOrSeeAnotherDoctorsPatients() {
        var own = actor("DOCTOR",doctorUserId);
        book(LocalTime.of(9,0));
        service.create(admin,new Booking(otherDoctorId,patientId,date,LocalTime.of(9,0)));
        assertEquals(1,service.day(own,date,null).size());
        assertEquals(1,service.doctors(own).size());
        assertEquals(403,assertThrows(ResponseStatusException.class,()->service.day(own,date,otherDoctorId)).getStatusCode().value());
        assertThrows(ResponseStatusException.class,()->service.create(own,new Booking(doctorId,patientId,date,LocalTime.of(10,0))));
        var other = service.day(admin,date,otherDoctorId).getFirst();
        assertEquals(403,assertThrows(ResponseStatusException.class,()->service.status(own,other.id(),new StatusChange(other.version(),Appointment.Status.CANCELLED))).getStatusCode().value());
    }
    @Test void inactiveDoctorOrPatientCannotBeBooked() {
        User user = users.findById(doctorUserId).orElseThrow();user.setActive(false);users.saveAndFlush(user);
        assertThrows(ResponseStatusException.class,()->book(LocalTime.of(9,0)));
        user.setActive(true);users.saveAndFlush(user);
        Patient p = patients.findById(patientId).orElseThrow();p.setActive(false);patients.saveAndFlush(p);
        assertThrows(ResponseStatusException.class,()->book(LocalTime.of(9,0)));
    }
    @Test void lifecycleRequiresArrivalBeforeCompletionAndRejectsStaleStatus() {
        var booked = book(LocalTime.of(9,0));
        assertThrows(ResponseStatusException.class,()->service.status(admin,booked.id(),new StatusChange(booked.version(),Appointment.Status.COMPLETED)));
        assertThrows(ResponseStatusException.class,()->service.status(admin,booked.id(),new StatusChange(booked.version(),Appointment.Status.ARRIVED)));
        // Simulate a previously booked visit whose date has now passed.
        var a = appointments.findById(booked.id()).orElseThrow();
        a.startsAt = Instant.now().minusSeconds(7200); a.endsAt = Instant.now().minusSeconds(5400);
        appointments.saveAndFlush(a);
        var current = service.day(admin,a.startsAt.atZone(AppointmentService.ZONE).toLocalDate(),doctorId).getFirst();
        var reception = actor("RECEPTIONIST",999L);
        var own = actor("DOCTOR",doctorUserId);
        assertEquals(403,assertThrows(ResponseStatusException.class,()->service.status(own,current.id(),new StatusChange(current.version(),Appointment.Status.ARRIVED))).getStatusCode().value());
        var arrived = service.status(reception,current.id(),new StatusChange(current.version(),Appointment.Status.ARRIVED));
        assertNotNull(arrived.checkedInAt());
        assertNull(arrived.consultationStartedAt());
        assertThrows(ResponseStatusException.class,()->service.status(reception,arrived.id(),new StatusChange(arrived.version(),Appointment.Status.COMPLETED)));
        assertThrows(ResponseStatusException.class,()->service.status(admin,current.id(),new StatusChange(current.version(),Appointment.Status.COMPLETED)));
        var consulting = service.status(own,arrived.id(),new StatusChange(arrived.version(),Appointment.Status.IN_CONSULTATION));
        assertEquals(Appointment.Status.IN_CONSULTATION,service.patient(reception,patientId).getFirst().status());
        assertNotNull(consulting.consultationStartedAt());
        assertEquals(arrived.checkedInAt().getEpochSecond(),consulting.checkedInAt().getEpochSecond());
        assertEquals(403,assertThrows(ResponseStatusException.class,()->service.status(own,consulting.id(),new StatusChange(consulting.version(),Appointment.Status.COMPLETED))).getStatusCode().value());
        var completed = service.status(reception,consulting.id(),new StatusChange(consulting.version(),Appointment.Status.COMPLETED));
        assertNotNull(completed.checkedOutAt());
        assertEquals(Appointment.Status.COMPLETED,completed.status());
        assertThrows(ResponseStatusException.class,()->service.status(admin,completed.id(),new StatusChange(completed.version(),Appointment.Status.BOOKED)));
    }
    @Test void followUpRequiresCheckedOutVisitAndPreservesThePatientAndDoctor() {
        var previous = book(LocalTime.of(10,0));
        var request = new Booking(doctorId,patientId,date.plusWeeks(1),LocalTime.of(10,0),previous.id());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.create(admin,request)).getStatusCode().value());
        var stored = appointments.findById(previous.id()).orElseThrow();
        stored.status = Appointment.Status.COMPLETED;
        appointments.saveAndFlush(stored);
        assertThrows(ResponseStatusException.class,()->service.create(admin,new Booking(otherDoctorId,patientId,date.plusWeeks(1),LocalTime.of(10,0),previous.id())));
        Patient other = new Patient();other.setPatientNumber("PAT-OTHER");other.setFirstName("Other");other.setLastName("Patient");other.setPhone("1000000002");
        Long otherId = patients.saveAndFlush(other).getId();
        assertThrows(ResponseStatusException.class,()->service.create(admin,new Booking(doctorId,otherId,date.plusWeeks(1),LocalTime.of(10,0),previous.id())));
        assertThrows(ResponseStatusException.class,()->service.create(admin,new Booking(doctorId,patientId,date,LocalTime.of(9,0),previous.id())));
        var followUp = service.create(actor("RECEPTIONIST",999L),request);
        assertEquals(previous.id(),followUp.followUpForId());
        assertEquals(previous.patientId(),followUp.patientId());
        assertEquals(previous.doctorId(),followUp.doctorId());
        assertEquals(Appointment.Status.BOOKED,followUp.status());
        assertEquals(previous.id(),service.patient(admin,patientId).getFirst().followUpForId());
        assertEquals(Appointment.Status.COMPLETED,service.day(admin,date,doctorId).getFirst().status());
        service.create(admin,new Booking(doctorId,patientId,date,LocalTime.of(10,30),previous.id()));
        assertEquals(List.of(LocalTime.of(9,0),LocalTime.of(9,30)),service.slots(admin,doctorId,date,null).times());
    }
    @Test void concurrentBookingsHaveExactlyOneWinner() throws Exception {
        var gate = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> attempt = () -> {
                gate.await();
                try {book(LocalTime.of(9,0));return true;}
                catch (ResponseStatusException conflict) {assertEquals(409,conflict.getStatusCode().value());return false;}
            };
            var first = pool.submit(attempt);var second = pool.submit(attempt);gate.countDown();
            int winners = (first.get(20,TimeUnit.SECONDS)?1:0)+(second.get(20,TimeUnit.SECONDS)?1:0);
            assertEquals(1,winners);assertEquals(1,appointments.count());
        } finally { pool.shutdownNow(); }
    }
}
