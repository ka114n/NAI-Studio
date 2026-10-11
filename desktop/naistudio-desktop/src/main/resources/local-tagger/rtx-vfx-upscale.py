"""Resident NVIDIA VSR worker; stdout is exclusively the JSON line protocol."""
import contextlib
import json
import os
import sys

PROTOCOL_OUT = sys.stdout
with contextlib.redirect_stdout(sys.stderr):
    from PIL import Image, ImageOps
    import numpy as np
    import torch
    from nvvfx import VideoSuperRes


def emit(event, **values):
    print(json.dumps(dict(event=event, **values), ensure_ascii=False), file=PROTOCOL_OUT, flush=True)


class Upscaler:
    def __init__(self):
        self.stack = contextlib.ExitStack()
        self.effect = None
        self.shape = None

    def close(self):
        self.stack.close()
        self.effect = None
        self.shape = None
        torch.cuda.empty_cache()

    def upscale(self, source_path, output_path):
        with Image.open(source_path) as original:
            source = ImageOps.exif_transpose(original).convert("RGBA")
        width, height = source.size
        if width * height > 4_000_000:
            raise ValueError("RTX 2× 放大目前限制输入不超过 400 万像素")
        rgb = np.asarray(source.convert("RGB"), dtype=np.float32) / 255.0
        with torch.inference_mode():
            frame = torch.from_numpy(rgb).permute(2, 0, 1).contiguous().to(device="cuda")
            if self.shape != (width, height):
                self.close()
                self.effect = self.stack.enter_context(VideoSuperRes(quality=VideoSuperRes.QualityLevel.HIGH))
                self.effect.output_width = width * 2
                self.effect.output_height = height * 2
                self.effect.load()
                self.shape = (width, height)
            output = torch.from_dlpack(self.effect.run(frame).image).clone()
            torch.cuda.synchronize()
            pixels = (output.clamp(0, 1) * 255).round().byte().movedim(0, -1).cpu().numpy()
        upscaled = Image.fromarray(pixels).convert("RGBA")
        if source.getchannel("A").getextrema() != (255, 255):
            upscaled.putalpha(source.getchannel("A").resize(upscaled.size, Image.Resampling.LANCZOS))
        upscaled.save(output_path, format="PNG")
        return width * 2, height * 2


def serve():
    if not torch.cuda.is_available():
        raise RuntimeError("RTX 放大需要可用的 NVIDIA GPU")
    torch.set_num_threads(max(1, min(8, (os.cpu_count() or 2) - 1)))
    upscaler = Upscaler()
    emit("ready", device="cuda")
    try:
        for line in sys.stdin:
            try:
                request = json.loads(line)
                if request.get("action") == "close":
                    break
                with contextlib.redirect_stdout(sys.stderr):
                    width, height = upscaler.upscale(request["image"], request["output"])
                    # Drop transient tensors; retain only the reusable effect/context.
                    torch.cuda.empty_cache()
                emit("result", width=width, height=height)
            except Exception as error:
                upscaler.close()
                emit("error", message=str(error))
    finally:
        upscaler.close()


if __name__ == "__main__":
    try:
        if sys.argv[1:] == ["--worker"]:
            serve()
        else:
            upscaler = Upscaler()
            try:
                upscaler.upscale(sys.argv[1], sys.argv[2])
            finally:
                upscaler.close()
    except Exception as error:
        emit("error", message=str(error))
        sys.exit(1)
