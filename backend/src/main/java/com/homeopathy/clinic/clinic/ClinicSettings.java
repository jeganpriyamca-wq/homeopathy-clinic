package com.homeopathy.clinic.clinic;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;
import java.time.LocalTime;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.Set;

// String values match the existing form, including numeric HTML input values.
public record ClinicSettings(
    @NotBlank @Size(max = 150) String clinicName,
    @NotBlank @Size(max = 150) String displayName,
    @NotNull @Pattern(regexp = "[6-9][0-9]{9}") String mobile,
    @NotNull @Pattern(regexp = "|[1-9][0-9]{9}") String alternatePhone,
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @Size(max = 200) String addressLine1,
    @NotNull @Size(max = 200) String addressLine2,
    @NotBlank @Size(max = 100) String city,
    @NotBlank @Size(max = 100) String district,
    @NotBlank @Size(max = 100) String state,
    @NotNull @Pattern(regexp = "[0-9]{6}") String pinCode,
    @NotNull @Pattern(regexp = "India") String country,
    @NotNull @Pattern(regexp = "Asia/Kolkata") String timezone,
    @NotNull @Pattern(regexp = "INR") String currency,
    @NotNull @Pattern(regexp = "([01][0-9]|2[0-3]):[0-5][0-9]") String openingTime,
    @NotNull @Pattern(regexp = "([01][0-9]|2[0-3]):[0-5][0-9]") String closingTime,
    @NotNull @Pattern(regexp = "|Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday") String weeklyClosedDay,
    @NotNull @Pattern(regexp = "[0-9]{1,3}") String appointmentDuration,
    @NotNull @Pattern(regexp = "[0-9]{1,7}([.][0-9]{1,2})?") String consultationFee,
    @NotNull @Pattern(regexp = "|[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]") String gstin,
    @NotNull @Size(max = 100) String registrationNumber,
    @NotNull @Size(max = 1000) String prescriptionHeader,
    @NotNull @Size(max = 1000) String prescriptionFooter,
    @NotNull @Pattern(regexp = "|[1-9][0-9]{9}") String emergencyContact,
    @NotNull @Size(max = 1400000) String logo
) {
    private static final Set<String> STATES = Set.of("Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar", "Chhattisgarh", "Goa", "Gujarat", "Haryana", "Himachal Pradesh", "Jharkhand", "Karnataka", "Kerala", "Madhya Pradesh", "Maharashtra", "Manipur", "Meghalaya", "Mizoram", "Nagaland", "Odisha", "Punjab", "Rajasthan", "Sikkim", "Tamil Nadu", "Telangana", "Tripura", "Uttar Pradesh", "Uttarakhand", "West Bengal", "Andaman and Nicobar Islands", "Chandigarh", "Dadra and Nagar Haveli and Daman and Diu", "Delhi", "Jammu and Kashmir", "Ladakh", "Lakshadweep", "Puducherry");

    @JsonIgnore
    @AssertTrue(message = "Choose a valid Indian state or union territory")
    public boolean isStateValid() { return state != null && STATES.contains(state); }

    @JsonIgnore
    @AssertTrue(message = "Closing time must be after opening time on the same day")
    public boolean isTimeRangeValid() {
        try { return LocalTime.parse(openingTime).isBefore(LocalTime.parse(closingTime)); }
        catch (RuntimeException e) { return false; }
    }

    @JsonIgnore
    @AssertTrue(message = "Appointment duration must be 5 to 240 minutes")
    public boolean isDurationValid() {
        try { int minutes = Integer.parseInt(appointmentDuration); return minutes >= 5 && minutes <= 240; }
        catch (RuntimeException e) { return false; }
    }

    @JsonIgnore
    @AssertTrue(message = "Consultation fee must be between 0 and 1000000 INR")
    public boolean isFeeValid() {
        try { var fee = new BigDecimal(consultationFee); return fee.signum() >= 0 && fee.compareTo(new BigDecimal("1000000")) <= 0; }
        catch (RuntimeException e) { return false; }
    }

    @JsonIgnore
    @AssertTrue(message = "Logo must be a PNG, JPEG or WebP data URL no larger than 1 MB")
    public boolean isLogoValid() {
        if (logo == null) return false;
        if (logo.isEmpty()) return true;
        if (logo.length() > 1400000) return false;
        String[] parts = logo.split(",", 2);
        if (parts.length != 2) return false;
        try {
            byte[] bytes = Base64.getDecoder().decode(parts[1]);
            if (bytes.length < 12 || bytes.length > 1024 * 1024) return false;
            return switch (parts[0]) {
                case "data:image/png;base64" -> bytes[0] == (byte) 0x89 && bytes[1] == 'P'
                    && bytes[2] == 'N' && bytes[3] == 'G' && bytes[4] == 13 && bytes[5] == 10
                    && bytes[6] == 26 && bytes[7] == 10;
                case "data:image/jpeg;base64" -> bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8 && bytes[2] == (byte) 0xff;
                case "data:image/webp;base64" -> bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F'
                    && bytes[3] == 'F' && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
                default -> false;
            };
        } catch (IllegalArgumentException e) { return false; }
    }
}
