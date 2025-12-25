package com.novatech.cybertech.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DateConverter {

    /**
     * Converts a date string in "MM/yyyy" format to a LocalDate representing the last day of that month.
     *
     * @param expiryDateString The date string, e.g., "12/2025".
     * @return An Optional containing the LocalDate if parsing is successful, otherwise an empty Optional.
     */
    public static LocalDate convertExpiryDateToLocalDate(final String expiryDateString) {
        if (expiryDateString == null || expiryDateString.trim().isEmpty()) {
            return null;
        }

        try {
            // Define the format of the input string
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/yyyy");

            // Parse the string into a YearMonth object
            YearMonth yearMonth = YearMonth.parse(expiryDateString, formatter);

            // Return the LocalDate corresponding to the last day of that month
            return yearMonth.atEndOfMonth();
        } catch (DateTimeParseException e) {
            // Handle cases where the string is not in the expected format
            System.err.println("Failed to parse date string: " + expiryDateString);
            return null;
        }
    }
}