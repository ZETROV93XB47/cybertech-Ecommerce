import unittest

import dataLoader


class DataLoaderAttributeMappingTest(unittest.TestCase):
    def test_compact_preserves_false_boolean_values(self):
        self.assertEqual({"enabled": False, "size": 4}, dataLoader.compact({
            "enabled": False,
            "missing": None,
            "empty": "",
            "zero": 0,
            "size": 4,
        }))

    def test_extract_measure_converts_units(self):
        self.assertEqual(3.08, dataLoader.extract_measure("3.08 kg", "kg"))
        self.assertEqual(0.254, dataLoader.extract_measure("254 g", "kg"))
        self.assertEqual(1646, dataLoader.extract_measure("164.6 cm", "mm"))
        self.assertIsNone(dataLoader.extract_measure("Unique", "mm"))

    def test_monitor_maps_display_features_and_false_flags(self):
        result = dataLoader.map_attributes_ldlc_to_monitor({
            "Résolution Max": "2560 x 1440 pixels",
            "Fréquence verticale maxi": "180 Hz",
            "Taille de l'écran": "27 pouces",
            "Format de l'écran": "16/9",
            "Type de dalle": "Dalle IPS",
            "Temps de réponse": "1 ms",
            "Luminosité": "250 cd/m²",
            "Contraste": "1000 /1",
            "HDR (High Dynamique Range)": "Non",
            "Écran incurvé": "Non",
            "NVIDIA G-SYNC": "Oui",
            "Entrées vidéo": "1 X HDMI Femelle",
            "Poids": "3.08 kg",
            "Largeur": "542.04 mm",
            "Hauteur": "407.9 mm",
            "Profondeur": "182.82 mm",
        })

        self.assertEqual("2560 x 1440", result["resolution"])
        self.assertEqual(27.0, result["screenSizeInches"])
        self.assertEqual(1.0, result["responseTimeMs"])
        self.assertEqual(250.0, result["brightnessNits"])
        self.assertIs(result["hdr"], False)
        self.assertIs(result["curved"], False)
        self.assertEqual("NVIDIA G-SYNC", result["adaptiveSync"])
        self.assertEqual(3.08, result["weightKg"])
        self.assertEqual(542.04, result["widthMm"])

    def test_computer_maps_screen_and_hardware_attributes(self):
        result = dataLoader.map_attributes_ldlc_to_computer({
            "Processeur": "Intel Core Ultra 7",
            "Chipset graphique": "Intel Arc",
            "Taille de la mémoire": "16 Go",
            "Système d'exploitation": "Windows 11",
            "Norme(s) réseau sans-fil": "Wi-Fi 6 AX",
            "Type d'écran": "LED",
            "Configuration disque(s)": "SSD 512 Go",
            "Taille de l'écran": "14 pouces",
            "Résolution Max": "1920 x 1080 pixels",
            "Taux de rafraîchissement": "60 Hz",
            "Type de dalle": "Dalle IPS",
            "Poids": "1.5 kg",
            "Capacité de la batterie": "45 Wh",
            "Ecran tactile": "Non",
            "Hybride / convertible": "Non",
            "Nombre de core": "6",
            "Type de disque": "SSD",
        })

        self.assertEqual(14.0, result["screenSizeInches"])
        self.assertEqual("1920 x 1080 pixels", result["screenResolution"])
        self.assertEqual(1.5, result["weightKg"])
        self.assertIs(result["touchscreen"], False)
        self.assertIs(result["convertible"], False)
        self.assertEqual(6, result["cpuCores"])
        self.assertEqual("SSD", result["storageType"])

    def test_desktop_maps_storage_connectivity_and_dimensions(self):
        result = dataLoader.map_attributes_ldlc_to_desktop({
            "Processeur": "AMD Ryzen 5",
            "Taille de la mémoire": "16 Go",
            "Configuration disque(s)": "SSD 1 To",
            "Type de disque système": "SSD",
            "Interface avec l'ordinateur disque dur système": "M.2 - PCI-E 4.0 4x",
            "Puissance": "500 W",
            "Taille mémoire vidéo": "8 Go",
            "Norme(s) réseau sans-fil": "Wi-Fi 6 AX",
            "Nombre de ports/contrôleur ethernet": "Realtek RTL8111H",
            "Lecteur optique": "Aucun",
            "Largeur": "168 mm",
            "Hauteur": "353 mm",
            "Profondeur": "265 mm",
            "Poids": "8.2 kg",
        })

        self.assertEqual("SSD", result["storageType"])
        self.assertEqual(500, result["powerSupplyW"])
        self.assertEqual("Realtek RTL8111H", result["ethernetController"])
        self.assertEqual(168.0, result["widthMm"])
        self.assertEqual(8.2, result["weightKg"])

    def test_smartphone_maps_radio_body_and_boolean_attributes(self):
        result = dataLoader.map_attributes_ldlc_to_smartphone({
            "Système d'exploitation": "Android",
            "Processeur": "Snapdragon",
            "Stockage": "256 Go",
            "Taille d'écran": "6.7 pouces",
            "Fréquence": "5G",
            "Définition écran": "1878 x 2670 pixels",
            "Taille mémoire RAM": "12 Go",
            "Capacité de la batterie": "4950 mAh",
            "Poids": "254 g",
            "Hauteur": "164.6 mm",
            "Largeur": "75.2 mm",
            "Epaisseur": "5.2 mm",
            "Dual SIM": "Oui",
            "Format(s) SIM": "eSIM",
            "NFC": "Non",
            "Charge rapide": "Oui",
            "Compatible chargeur à induction": "Non",
            "Étanche": "Oui",
            "Pliable": "Non",
            "Nombre de core": "8",
        })

        self.assertEqual("1878 x 2670 pixels", result["screenResolution"])
        self.assertEqual(12, result["ramGB"])
        self.assertEqual(254.0, result["weightG"])
        self.assertIs(result["dualSim"], True)
        self.assertIs(result["nfc"], False)
        self.assertIs(result["wirelessCharging"], False)
        self.assertIs(result["foldable"], False)
        self.assertEqual(8, result["coreCount"])

    def test_smartwatch_maps_optional_features_and_false_flags(self):
        result = dataLoader.map_attributes_ldlc_to_smartwatch({
            "Système d'exploitation": "watchOS",
            "Taille du boîtier": "42 mm",
            "Matière du boîtier": "Aluminium",
            "Autonomie": "18 heures",
            "Données cellulaires": "Non",
            "Ecran tactile": "Oui",
            "Ecran couleur": "Oui",
            "Bluetooth": "Oui",
            "Wi-Fi": "Non",
            "Compatible iPhone": "Oui",
            "Taille du bracelet": "Unique",
            "Couleur du bracelet": "Noir",
            "NFC": "Non",
            "Capacité de la batterie": "300 mAh",
        })

        self.assertIs(result["touchscreen"], True)
        self.assertIs(result["wifi"], False)
        self.assertIs(result["nfc"], False)
        self.assertEqual("Unique", result["strapSize"])
        self.assertEqual(300, result["batteryCapacityMah"])


if __name__ == "__main__":
    unittest.main()
