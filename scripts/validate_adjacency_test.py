import os
import unittest

from scripts.validate_adjacency_vs_knit import validate_knit_file


class TestValidateDemoJvm(unittest.TestCase):
    def test_demo_jvm_knit_json(self):
        json_path = os.path.join(os.path.dirname(__file__), "..", "demo-jvm", "build", "knit.json")
        json_path = os.path.abspath(json_path)
        ok, report = validate_knit_file(json_path)
        # Useful on failure to see details in test output
        if not ok:
            print("\n" + report)
        self.assertTrue(ok, msg=report)


if __name__ == "__main__":
    unittest.main()
