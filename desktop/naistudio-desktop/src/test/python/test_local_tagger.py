import importlib.util
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.dont_write_bytecode = True

WORKER = Path(__file__).resolve().parents[2] / "main/resources/local-tagger/worker.py"
spec = importlib.util.spec_from_file_location("worker", WORKER)
worker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(worker)


class LocalTaggerTest(unittest.TestCase):
    def test_flatten_preserves_punctuation_deduplicates_and_excludes_rating(self):
        results = {"general": {"blue_hair": .9, "solo": .8},
                   "character": {"character_(series)": .92, "solo": .7},
                   "rating": {"safe": 1.0}, "style": {"artist": .9}}
        self.assertEqual(worker.flatten_tags(results), "blue_hair, solo, character_(series), artist")

    def test_missing_categories_and_empty_results_are_handled(self):
        self.assertEqual(worker.flatten_tags({}), "")
        self.assertEqual(worker.flatten_tags({"meta": {"highres": .7}}), "highres")

    def test_progress_stays_on_json_channel_even_when_library_stdout_is_redirected(self):
        stream = io.StringIO()
        old = worker.PROTOCOL_OUT
        worker.PROTOCOL_OUT = stream
        try:
            with worker.contextlib.redirect_stdout(io.StringIO()):
                worker.emit("result", text="角色_(作品), smile")
            self.assertEqual(json.loads(stream.getvalue())["text"], "角色_(作品), smile")
        finally:
            worker.PROTOCOL_OUT = old

    def test_gpu_unavailable_is_an_error_not_cpu_fallback(self):
        class FakeTorch:
            class cuda:
                @staticmethod
                def is_available():
                    return False
        with self.assertRaisesRegex(RuntimeError, "不会自动切换到 CPU"):
            worker.require_gpu(FakeTorch)

    def test_gpu_selection_checks_actual_cuda_allocation(self):
        class FakeTorch:
            class cuda:
                @staticmethod
                def is_available():
                    return True
            @staticmethod
            def zeros(size, device):
                self.assertEqual(device, "cuda:0")
        self.assertEqual(worker.require_gpu(FakeTorch), 0)

    def test_valid_pinned_cache_needs_no_network(self):
        with tempfile.TemporaryDirectory() as name:
            directory = Path(name)
            (directory / "revision.txt").write_text(worker.REVISION)
            for file in worker.MODEL_FILES:
                (directory / file).write_text("cached")
            self.assertEqual(worker.prepare_model(directory), str(directory))


if __name__ == "__main__":
    unittest.main()
