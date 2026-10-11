"""PixAI Tagger worker. stdout is exclusively UTF-8 JSON lines; diagnostics use stderr."""
import contextlib
import json
import os
import sys
from pathlib import Path

PROTOCOL_OUT = sys.stdout

MODEL_ID = "pixai-labs/pixai-tagger-v1.0"
REVISION = "9fe10addf9326e292da8a85a98ea74cd91b41771"
MODEL_FILES = ("config.json", "preprocessor_config.json", "tagger_pipeline.py", "model.safetensors")
PROMPT_CATEGORIES = ("general", "character", "copyright", "style", "meta")


def emit(event, **values):
    print(json.dumps(dict(event=event, **values), ensure_ascii=False), file=PROTOCOL_OUT, flush=True)


def flatten_tags(results):
    """Use model-calibrated thresholds, preserve literal tag punctuation, omit rating labels."""
    seen = set()
    tags = []
    for category in PROMPT_CATEGORIES:
        entries = results.get(category, {})
        for tag, score in sorted(entries.items(), key=lambda item: (-float(item[1]), item[0])):
            tag = tag.strip()
            if tag and tag not in seen:
                tags.append(tag)
                seen.add(tag)
    return ", ".join(tags)


def prepare_model(directory):
    directory.mkdir(parents=True, exist_ok=True)
    # A marker is written only after every required file has been downloaded.
    marker = directory / "revision.txt"
    ready = marker.exists() and marker.read_text().strip() == REVISION
    ready = ready and all((directory / name).is_file() for name in MODEL_FILES)
    if not ready:
        emit("progress", stage="model")
        from huggingface_hub import snapshot_download
        snapshot_download(MODEL_ID, revision=REVISION, local_dir=str(directory),
                          allow_patterns=list(MODEL_FILES), max_workers=2)
        marker.write_text(REVISION)
    return str(directory)


def require_gpu(torch):
    if not torch.cuda.is_available():
        raise RuntimeError("未检测到可用的 NVIDIA GPU。请检查显卡驱动及 CUDA 版 PyTorch 环境；本地反推不会自动切换到 CPU。")
    # Trigger a real CUDA allocation so incompatible drivers fail before model download/loading.
    torch.zeros(1, device="cuda:0")
    return 0


def serve(directory):
    with contextlib.redirect_stdout(sys.stderr):
        import torch
        device = require_gpu(torch)
        path = prepare_model(directory)
        emit("progress", stage="loading")
        # Once cached, both weights and the pinned custom pipeline are loaded offline.
        os.environ["HF_HUB_OFFLINE"] = "1"
        os.environ["TRANSFORMERS_OFFLINE"] = "1"
        from PIL import Image, ImageOps
        from transformers import pipeline
        torch.set_num_threads(max(1, min(8, (os.cpu_count() or 2) - 1)))
        tagger = pipeline(model=path, image_processor=path, trust_remote_code=True,
                          device=device, dtype=torch.float16, local_files_only=True)
        if tagger.model.device.type != "cuda":
            raise RuntimeError("模型未加载到 GPU，已停止本地反推。")
    emit("ready", device=str(tagger.model.device), gpu=torch.cuda.get_device_name(device))
    for line in sys.stdin:
        try:
            request = json.loads(line)
            if request.get("action") == "close":
                break
            emit("progress", stage="infer")
            with contextlib.redirect_stdout(sys.stderr):
                with Image.open(request["image"]) as source:
                    image = ImageOps.exif_transpose(source).convert("RGBA")
                    # Treat transparent areas as white, consistent with normal tagger input.
                    rgb = Image.new("RGB", image.size, "white")
                    rgb.paste(image, mask=image.getchannel("A"))
                with torch.inference_mode():
                    results = tagger(rgb)["results"]
                text = flatten_tags(results)
            emit("result", text=text)
        except torch.cuda.OutOfMemoryError:
            torch.cuda.empty_cache()
            emit("error", message="GPU 显存不足，请关闭其他占用显存的软件后重试；不会自动切换到 CPU。")
        except Exception as error:
            emit("error", message=str(error))


if __name__ == "__main__":
    try:
        serve(Path(sys.argv[1]))
    except Exception as error:
        emit("error", message=str(error))
        sys.exit(1)
