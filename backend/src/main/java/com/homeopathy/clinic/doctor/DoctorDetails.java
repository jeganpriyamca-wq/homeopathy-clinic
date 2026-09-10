package com.homeopathy.clinic.doctor;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

public record DoctorDetails(
    @NotBlank @Size(max = 100) String firstName,
    @NotNull @Size(max = 100) String lastName,
    @NotBlank @Email @Size(max = 254) String email,
    @NotNull @Pattern(regexp = "|[6-9][0-9]{9}") String mobile,
    @NotBlank @Size(max = 200) String qualification,
    @NotBlank @Size(max = 100) String registrationNumber,
    @NotBlank @Size(max = 200) String specialization,
    @NotNull @DecimalMin("0") @DecimalMax("1000000") @Digits(integer = 7, fraction = 2) BigDecimal consultationFee,
    @NotNull @Min(5) @Max(240) Integer appointmentDuration,
    @NotNull @Size(min = 7, max = 7) List<@NotNull @Valid WorkingDay> workingHours
) {
    @JsonIgnore
    @AssertTrue(message = "Include each weekday exactly once")
    public boolean isWeekValid() {
        return workingHours != null && workingHours.size() == 7
            && workingHours.stream().allMatch(day -> day != null && day.day() != null)
            && workingHours.stream().map(WorkingDay::day).distinct().count() == 7;
    }

    public record WorkingDay(
        @NotNull DayOfWeek day,
        @NotNull Boolean closed,
        LocalTime opensAt,
        LocalTime closesAt
    ) {
        @JsonIgnore
        @AssertTrue(message = "Closed days require null times; closing must follow opening on an open day")
        public boolean isTimeRangeValid() {
            if (closed == null) return false;
            if (closed) return opensAt == null && closesAt == null;
            return opensAt != null && closesAt != null && opensAt.isBefore(closesAt)
                && opensAt.getSecond() == 0 && closesAt.getSecond() == 0
                && opensAt.getNano() == 0 && closesAt.getNano() == 0;
        }
    }
}
