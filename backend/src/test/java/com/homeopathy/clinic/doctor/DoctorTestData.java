package com.homeopathy.clinic.doctor;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Arrays;

final class DoctorTestData {
    static DoctorDetails details(String email, String registration) {
        return new DoctorDetails("Meena", "", email, "9876543210", "BHMS", registration, "General practice",
            new BigDecimal("450.00"), 30, Arrays.stream(DayOfWeek.values()).map(day ->
                new DoctorDetails.WorkingDay(day, day == DayOfWeek.SUNDAY,
                    day == DayOfWeek.SUNDAY ? null : LocalTime.of(9, 0),
                    day == DayOfWeek.SUNDAY ? null : LocalTime.of(18, 0))).toList());
    }
}
