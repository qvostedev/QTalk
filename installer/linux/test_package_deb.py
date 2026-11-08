import unittest
from package_deb import update_control

CONTROL = "Package: qtalk\nVersion: 1.1.2-1\nArchitecture: amd64\nDepends: libc6 (>= 2.34), libx11-6\nDescription: QTalk\n SIP client\n"

class DebControlTest(unittest.TestCase):
    def test_declares_native_dependencies(self):
        result = update_control(CONTROL, "1.1.2")
        self.assertIn("libc6 (>= 2.39)", result)
        self.assertIn("libglew2.2", result)
        self.assertIn("pkexec", result)
        self.assertEqual(1, result.count("libx11-6"))
        self.assertIn("Description: QTalk\n SIP client", result)

    def test_preserves_existing_dependencies(self):
        result = update_control(CONTROL.replace("libx11-6", "libx11-6, libzstd1"), "1.1.2")
        self.assertIn("libzstd1", result)

    def test_rejects_wrong_package_or_architecture(self):
        for source in [CONTROL.replace("Package: qtalk", "Package: other"), CONTROL.replace("amd64", "arm64")]:
            with self.assertRaises(ValueError):
                update_control(source, "1.1.2")

if __name__ == "__main__":
    unittest.main()
