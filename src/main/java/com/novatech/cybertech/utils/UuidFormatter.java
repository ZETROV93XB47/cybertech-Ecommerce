package com.novatech.cybertech.utils;

public class UuidFormatter {

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

    public static void main(String[] args) {
        String unformatted = "C72E24D08AAF41D3ABF9BD1775E8DD16";
        String formatted = formatUuidString(unformatted);
        System.out.println("UUID non formaté : " + unformatted);
        System.out.println("UUID formaté     : " + formatted);

        // Tu peux aussi le convertir en objet UUID si besoin
        java.util.UUID uuidObject = java.util.UUID.fromString(formatted);
        System.out.println("Objet UUID       : " + uuidObject.toString());
    }
}
