package com.homeopathy.clinic.appointment;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    @Query("select a.doctor.id from Appointment a where a.id = :id")
    java.util.Optional<Long> doctorId(@Param("id") Long id);
    @EntityGraph(attributePaths = {"doctor.user", "patient"})
    @Query("select a from Appointment a where a.startsAt >= :start and a.startsAt < :end order by a.startsAt, a.id")
    List<Appointment> day(@Param("start") Instant start, @Param("end") Instant end);

    @EntityGraph(attributePaths = {"doctor.user", "patient"})
    @Query("select a from Appointment a where a.doctor.id = :doctor and a.startsAt >= :start and a.startsAt < :end order by a.startsAt, a.id")
    List<Appointment> doctorDay(@Param("doctor") Long doctor, @Param("start") Instant start, @Param("end") Instant end);
}
