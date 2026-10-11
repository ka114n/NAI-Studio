# RTX Video Super Resolution 静态图片 2× 放大

NAI Studio 的 2× 放大服务可在「NovelAI 官方」与「RTX」之间切换。默认保持官方服务。官方路径沿用 NovelAI `/ai/upscale`，按输入像素计费；RTX 路径使用 NVIDIA VFX SDK 的 `VideoSuperRes`，以 HIGH 档处理单张静态图片，不请求 NovelAI 放大接口。

电脑版 RTX 路径在本机处理；手机版通过「设置 → 电脑配对」里已有的 HTTPS 地址和配对码，调用电脑 `/v1/upscale`。共享服务沿用现有 Bearer 配对密钥、HTTPS 与单任务限制。请求限 16 MiB，输入图限 400 万像素，输出限 128 MiB。电脑处理完会删除上传临时文件；手机会把收到的 PNG 以 2× 关联图片保存在图库。自动放大开关也服从所选服务，RTX 路径不扣 Anlas。

电脑首次使用 RTX 时，NAI Studio 在 `%LOCALAPPDATA%/NAI Studio/local-tagger/` 建立隔离 Python/CUDA 环境，安装 PyTorch CUDA 与固定版本 `nvidia-vfx==0.2.0.0`，不安装 ComfyUI、不写系统 Python，也不把 NVIDIA VFX 运行库打进应用安装包。首次准备需要网络与数 GB 空间；需要 64 位 Windows、支持 Tensor Core 的 RTX GPU 和 NVIDIA 驱动 570.65 或更新版本。输入超过 400 万像素时需先缩小或改用官方放大。RTX VFX 输出会保留 PNG 透明度。

验证结果：已在 RTX 4060 Laptop GPU 上用 NVIDIA VFX 处理 640×512 PNG，得到 1280×1024 PNG 并保留透明通道；自动化集成测试也通过带配对鉴权的共享 HTTP 路由验证了输入、输出尺寸和临时文件清理。此前还通过真实 Funnel 公网入口和 USB 手机网络验证了反推、RTX PNG 回传，用户在手机应用内重试也成功。NVIDIA VFX SDK 为其专有组件，只在用户选择 RTX 后从 NVIDIA Python 包索引下载，不随项目发布。

## 常驻与显存释放

反推和 RTX 工作者在第一次使用后保持加载，复用 Python/CUDA；RTX 同尺寸请求也复用已加载的 VSR effect，尺寸改变时仅重建 effect。未使用时不预加载模型。取消原来的反推空闲 3 分钟自动退出策略。

电脑通过 NVIDIA 驱动自带的 NVML 每 500 ms 采样，不持续启动 nvidia-smi 子进程。有多个 NVIDIA 显卡时使用各卡已用显存的最大值，计入其他软件和驱动占用；严格大于 6 GiB（6144 MiB）并连续保持至少 3 秒时，释放本应用的反推与 RTX 工作者。短暂峰值、恰好 6 GiB 和缺失采样不会累计为连续超限。已经达到 3 秒的释放请求会保留到当前任务完成后处理，任务期间不会强制终止；共享 HTTPS 服务保持运行，下次请求重新加载模型。释放不影响磁盘上的模型、图片、配对设置或其他软件。

监测不可用持续 3 秒时也放弃空闲常驻，避免失去保护后永久保留显存。无模型驻留时停止 NVML 读取，关闭软件后停止监测并释放驱动引用。NVML 内存字段含义参见 [NVIDIA nvmlMemory_t](https://docs.nvidia.com/deploy/nvml-api/latest/api/structnvmlMemory__t.html)。
