package com.homeopathy.clinic.doctor;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoctorRepository extends JpaRepository<DoctorProfile, Long> {
    @EntityGraph(attributePaths = "user")
    List<DoctorProfile> findAllByOrderByIdAsc();

    @EntityGraph(attributePaths = "user")
    Optional<DoctorProfile> findById(Long id);

    boolean existsByRegistrationNumber(String registrationNumber);
    boolean existsByRegistrationNumberAndIdNot(String registrationNumber, Long id);
}
