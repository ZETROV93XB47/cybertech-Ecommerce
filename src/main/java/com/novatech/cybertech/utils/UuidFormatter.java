package com.novatech.cybertech.utils;

/**
 * Utility for converting a packed 32-character hexadecimal string into the
 * canonical RFC-4122 UUID textual form ({@code 8-4-4-4-12}).
 * <p>
 * Mostly used when consuming UUIDs from data sources that strip the dashes
 * (Oracle {@code RAW(16)} hex dumps, certain external identity providers, etc.).
 * </p>
 */
public class UuidFormatter {

    /**
     * Convert a 32-character hex string into its dash-separated UUID textual form.
     *
     * @param unformattedUuid the packed 32-character hexadecimal string.
     * @return the UUID in canonical {@code 8-4-4-4-12} form (case preserved).
     * @throws IllegalArgumentException if the input is {@code null}, not exactly
     *         32 characters long, or contains non-hexadecimal characters.
     */
    public static String formatUuidString(String unformattedUuid) {
        if (unformattedUuid == null || unformattedUuid.length() != 32) {
            throw new IllegalArgumentException("La chaîne UUID non formatée doit comporter 32 caractères.");
        }
        // Vérifie si la chaîne contient uniquement des caractères hexadécimaux (optionnel mais recommandé)
        if (!unformattedUuid.matches("[0-9a-fA-F]+")) {
            throw new IllegalArgumentException("La chaîne UUID contient des caractères non hexadécimaux.");
        }

        return String.format("%s-%s-%s-%s-%s",
                unformattedUuid.substring(0, 8),
                unformattedUuid.substring(8, 12),
                unformattedUuid.substring(12, 16),
                unformattedUuid.substring(16, 20),
                unformattedUuid.substring(20, 32)
        );
    }

    /**
     * Inert demonstration entry-point. Intentionally not named {@code main} so the
     * JVM does not pick it up as an executable; preserved for historical reference.
     *
     * @param args ignored.
     */
    public static void maind(String[] args) {
        String unformatted = "C72E24D08AAF41D3ABF9BD1775E8DD16";
        String formatted = formatUuidString(unformatted);
        System.out.println("UUID non formaté : " + unformatted);
        System.out.println("UUID formaté     : " + formatted);

        // Tu peux aussi le convertir en objet UUID si besoin
        java.util.UUID uuidObject = java.util.UUID.fromString(formatted);
        System.out.println("Objet UUID       : " + uuidObject.toString());
    }
}
