import glob
import json
import os
import tempfile
import unittest
from unittest import mock

import laptopScrapper


class SaveJsonTest(unittest.TestCase):
    def test_save_json_retries_transient_permission_error(self):
        with tempfile.TemporaryDirectory() as directory:
            target = os.path.join(directory, "products.json")
            expected = [{"name": "phone"}]
            real_replace = os.replace
            attempts = 0

            def replace_once_fails(source, destination):
                nonlocal attempts
                attempts += 1
                if attempts == 1:
                    raise PermissionError("temporarily locked")
                return real_replace(source, destination)

            with mock.patch.object(laptopScrapper.os, "replace", side_effect=replace_once_fails), \
                    mock.patch.object(laptopScrapper.time, "sleep"):
                laptopScrapper.save_json(target, expected)

            with open(target, encoding="utf-8") as saved_file:
                self.assertEqual(expected, json.load(saved_file))
            self.assertEqual(2, attempts)
            self.assertEqual([], glob.glob(os.path.join(directory, "products.json.*.tmp")))

    def test_save_json_preserves_complete_recovery_file_if_target_stays_locked(self):
        with tempfile.TemporaryDirectory() as directory:
            target = os.path.join(directory, "products.json")
            previous = [{"name": "previous"}]
            latest = [{"name": "latest"}]
            with open(target, "w", encoding="utf-8") as target_file:
                json.dump(previous, target_file)

            with mock.patch.object(laptopScrapper.os, "replace", side_effect=PermissionError("locked")), \
                    mock.patch.object(laptopScrapper.time, "sleep"):
                with self.assertRaisesRegex(PermissionError, "Fermez-la puis relancez"):
                    laptopScrapper.save_json(target, latest)

            with open(target, encoding="utf-8") as target_file:
                self.assertEqual(previous, json.load(target_file))
            recovery_files = glob.glob(os.path.join(directory, "products.json.*.tmp"))
            self.assertEqual(1, len(recovery_files))
            with open(recovery_files[0], encoding="utf-8") as recovery_file:
                self.assertEqual(latest, json.load(recovery_file))
            self.assertEqual(latest, laptopScrapper.load_json(target, []))

    def test_load_json_recovers_newer_legacy_temp_file(self):
        with tempfile.TemporaryDirectory() as directory:
            target = os.path.join(directory, "products.json")
            recovery = target + ".tmp"
            with open(target, "w", encoding="utf-8") as target_file:
                json.dump([{"name": "previous"}], target_file)
            with open(recovery, "w", encoding="utf-8") as recovery_file:
                json.dump([{"name": "latest"}], recovery_file)
            newer_time = os.path.getmtime(target) + 2
            os.utime(recovery, (newer_time, newer_time))

            self.assertEqual([{"name": "latest"}], laptopScrapper.load_json(target, []))

    def test_load_json_ignores_invalid_recovery_and_uses_target(self):
        with tempfile.TemporaryDirectory() as directory:
            target = os.path.join(directory, "products.json")
            recovery = target + ".tmp"
            expected = [{"name": "previous"}]
            with open(target, "w", encoding="utf-8") as target_file:
                json.dump(expected, target_file)
            with open(recovery, "w", encoding="utf-8") as recovery_file:
                recovery_file.write("{")
            newer_time = os.path.getmtime(target) + 2
            os.utime(recovery, (newer_time, newer_time))

            self.assertEqual(expected, laptopScrapper.load_json(target, []))


if __name__ == "__main__":
    unittest.main()
