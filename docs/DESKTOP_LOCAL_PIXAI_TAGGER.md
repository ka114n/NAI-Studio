# Windows local PixAI tagging

The reverse-prompt tool now offers **Online / Local tagging**. Online vision-LLM rules, API settings, copying results and importing into the positive prompt remain available. Local tagging returns tags, not natural-language descriptions; positive-prompt locks still apply to importing.

## First use and storage

Select an image, choose Local tagging, and start. This action bootstraps an isolated Python environment and downloads the pinned official PixAI Tagger v1.0 model. No ComfyUI, system Python installation, API key or paid image-generation call is required. Default inference uses an NVIDIA GPU through CUDA on 64-bit Windows. GPU availability is checked with a real CUDA allocation, and model readiness is accepted only if its device is `cuda`. There is no automatic CPU fallback. GPU unavailability or insufficient VRAM produces an explicit error. The model runs in FP16 to reduce VRAM consumption. Model-calibrated thresholds are retained; general, character, copyright, style and meta tags are ordered by confidence within each category and deduplicated. Rating classifications are not added to the prompt.

The UI estimates approximately **5 GB download**: the model is 1,945,425,796 bytes (~1.95 GB), plus a 2,915,474,854-byte CUDA PyTorch wheel, torchvision and the remaining Python/runtime dependencies. This is an estimate, not a fixed transfer quota; transitive dependencies can vary. Allow at least 16 GB of disk space for installed files and caches. A working NVIDIA graphics driver and sufficient available VRAM are required. The CUDA runtime is supplied by PyTorch; a separate ComfyUI or CUDA Toolkit installation is not required.

Cache: `%LOCALAPPDATA%/NAI Studio/local-tagger/`. CUDA dependencies are isolated in `env-cuda126/`, with a separate `environment-cuda126.txt` success marker and `worker-cuda126.py` runtime script. Model files stay in the existing `model/` directory; upgrading the former CPU version does not download the weights again. Installation uses copy mode to avoid Windows directory/reparse problems during in-place environment upgrades. It is separate from the EXE, application settings/backup directory, source checkout and signing keys. Preserve this cache to avoid another download. Deleting it requires another first-use download. Partial model downloads can resume when retried; a cancelled environment setup is retried until dependency imports succeed. No image contents, API secrets or inference diagnostics are stored in this cache.

Images are read locally by a child Python worker. After the pinned files have been downloaded, model loading is forced offline. Repeated requests reuse the loaded model; three minutes idle releases it. Cancellation, worker exit and application shutdown terminate the child process. First use contacts GitHub (runtime), PyPI (dependencies) and Hugging Face (model); network access to these services is required only for provisioning.

## Recovery and dependencies

- Runtime manager: uv 0.8.22, Windows x86_64 archive, SHA-256 `5049375aa2a5162f132b2c1cb992e25d42d47d934cab8c174dbe6f60973dcc12`.
- Managed Python: 3.11.13, isolated venv; no global PATH changes.
- GPU wheels: torch 2.8.0+cu126 and torchvision 0.23.0+cu126 from the official PyTorch CDN. Wheel SHA-256 hashes are pinned in the desktop backend. Other dependencies use PyPI: transformers 4.57.1, timm 1.0.20, huggingface-hub 0.36.0, numpy 2.2.6, Pillow 11.3.0, safetensors 0.6.2.
- Official install/version reference: https://pytorch.org/get-started/previous-versions/#v2-8-0
- Model: https://huggingface.co/pixai-labs/pixai-tagger-v1.0
- Pinned revision: `9fe10addf9326e292da8a85a98ea74cd91b41771`; only config.json, preprocessor_config.json, tagger_pipeline.py and model.safetensors are fetched. The repository's custom Python pipeline is executed from this pinned snapshot, not arbitrary user-supplied model code.
- JSON-line protocol worker is a small bundled resource, `local-tagger/worker.py`. Model weights and Python environment are **not** bundled with the EXE or committed to Git.

If setup fails, the dialog reports the error. Retry after restoring network access. CUDA wheel URLs use the official main CDN because its alternative R2 endpoint returned HTTP 403 on the verification machine. No third-party mirror or system-wide Python change is used. Cold startup still needs library/model loading; subsequent requests reuse the worker.

## Verification (2026-10-04)

- Desktop compilation, package input and full JVM suite: 565 tests, 6 skipped, zero failures/errors, including the opt-in real GPU model test.
- Real GPU bootstrap was verified on an NVIDIA GeForce RTX 4060 Laptop GPU (8 GB VRAM, driver 610.88). It installed the isolated CUDA environment, reused the cached official model, confirmed the CUDA model device, produced nonempty tags from a synthetic portrait, and returned identical results on a second request without download or model reload.
- Python worker unit tests cover punctuation preservation, tag deduplication, rating exclusion, empty categories, UTF-8 JSON output under redirected library stdout, cached operation without network access, GPU selection with actual CUDA allocation, and GPU unavailability as an error rather than CPU fallback.
- Routine test runs skip multi-GB downloads. Set `NAI_TEST_LOCAL_TAGGER=1` only for explicit integration verification. Run `python desktop/naistudio-desktop/src/test/python/test_local_tagger.py` for lightweight worker checks.
- No Android changes, public repository updates or Releases are included in this task.
