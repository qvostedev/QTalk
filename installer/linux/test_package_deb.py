import unittest
from package_deb import update_control, configure_desktop_integration
from pathlib import Path
from tempfile import TemporaryDirectory

CONTROL = "Package: qtalk\nVersion: 1.1.2-1\nArchitecture: amd64\nDepends: libc6 (>= 2.34), libx11-6\nDescription: QTalk\n SIP client\n"

class DebControlTest(unittest.TestCase):
    def test_declares_native_dependencies(self):
        result = update_control(CONTROL, "1.1.2")
        self.assertIn("libc6 (>= 2.39)", result)
        self.assertIn("libglew2.2", result)
        self.assertIn("pkexec", result)
        self.assertEqual(1, result.count("libx11-6"))
        self.assertIn("Description: QTalk\n SIP client", result)

    def test_installs_desktop_files_without_xdg_menu_scripts(self):
        with TemporaryDirectory() as temporary:
            stage = Path(temporary)
            control_directory = stage / "DEBIAN"
            control_directory.mkdir()
            (control_directory / "postinst").write_text("xdg-desktop-menu install old.desktop")
            (control_directory / "prerm").write_text("xdg-desktop-menu uninstall old.desktop")
            configure_desktop_integration(stage)
            self.assertFalse((control_directory / "prerm").exists())
            self.assertNotIn("xdg-desktop-menu", (control_directory / "postinst").read_text())
            self.assertIn("update-desktop-database", (control_directory / "postinst").read_text())

    def test_preserves_existing_dependencies(self):
        result = update_control(CONTROL.replace("libx11-6", "libx11-6, libzstd1"), "1.1.2")
        self.assertIn("libzstd1", result)

    def test_rejects_wrong_package_or_architecture(self):
        for source in [CONTROL.replace("Package: qtalk", "Package: other"), CONTROL.replace("amd64", "arm64")]:
            with self.assertRaises(ValueError):
                update_control(source, "1.1.2")

if __name__ == "__main__":
    unittest.main()
