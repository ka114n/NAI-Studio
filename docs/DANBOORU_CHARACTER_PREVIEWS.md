# Danbooru 安全版角色参考图抓取脚本

`tools/fetch_danbooru_previews.py` 使用 Python 3.10+ 标准库，无需 pip 安装依赖。
固定使用 `https://safebooru.donmai.us` 官方安全版 JSON API，为每个精确角色标签下载
一张按指定排序最靠前、可以访问的静态原图。只允许一般评级 `g`，下载前再次复核评级。
不提供切换普通站点或其他评级的选项，也不在安全版失败时回退普通站点。
脚本独立运行，不改变手机/电脑应用，也未自动运行全库下载。

## 本机快捷使用

交付入口：`C:/Users/kallan/Desktop/NAI-Studio/tools/Fetch-Danbooru-Previews.cmd`。
双击后输入英文角色标签，例如 `amiya_(arknights)`（留空可按作品批量抓取），
随后输入作品英文 tag，以及必含/排除 tag（逗号分隔，留空跳过）。
下载完成后显示保存结果。
支持把空格形式 `amiya (arknights)` 转成 Danbooru 的下划线形式。

默认输出：`C:/Users/kallan/Desktop/NAI-Studio/datasets/danbooru-character-previews/`。
图片按站点、排序、评级、是否单人图、作品及 tag 条件分目录，文件名有角色标识及帖子 ID。
每张图片旁的 `.json` 保存角色中文名（从词库/列表输入时）、评分、赞数、收藏数、作者标签、
作品标签、原始出处、帖子地址、下载时间、文件哈希和跳过的候选记录。
状态保存在 `download-state.sqlite`，不要删掉它，否则无法利用已有断点记录。
图片、状态和配对/账户信息不进入 Git 备份，默认数据目录位于源码目录之外。

PowerShell 单人角色图：

```powershell
& 'C:/Users/kallan/Desktop/NAI-Studio/tools/Fetch-Danbooru-Previews.ps1' -CharacterTag 'amiya_(arknights)' -Solo
```

使用现有词库，先抓图片数量最多的 100 个角色标签：

```powershell
& 'C:/Users/kallan/Desktop/NAI-Studio/tools/Fetch-Danbooru-Previews.ps1' -Dictionary 'C:/Users/kallan/Downloads/tag.sqlite' -MaxCharacters 100 -Solo
```

把 `-MaxCharacters 100` 改为 `-MaxCharacters 0` 可处理词库全部角色。
重新执行相同命令会继续未完成的条目；`-Refresh` 可重新检查已完成角色的排名。
PS 包装器批量默认限 100 条，Python 命令行的 `--max-characters` 默认 0（全部）。

## 只抓指定作品

`-Copyright`（别名 `-Work`）/ `--copyright`（别名 `--work`）支持一个或多个
精确英文版权 tag，空格会转成下划线，多个作品按“任意一个匹配”处理。
例如只抓《明日方舟》，或者《明日方舟》和《碧蓝档案》：

```powershell
& 'C:/Users/kallan/Desktop/NAI-Studio/tools/Fetch-Danbooru-Previews.ps1' -Copyright 'arknights' -MaxCharacters 100
& 'C:/Users/kallan/Desktop/NAI-Studio/tools/Fetch-Danbooru-Previews.ps1' -Copyright 'arknights','blue_archive' -IncludeTag 'solo' -ExcludeTag 'comic','text' -FilterMode local -MaxCharacters 100
```

Python 用法：

```powershell
python tools/fetch_danbooru_previews.py --copyright arknights --max-characters 100
python tools/fetch_danbooru_previews.py --copyright arknights --copyright blue_archive --include-tag solo --filter-mode local --max-characters 100
python tools/fetch_danbooru_previews.py --tag 'amiya_(arknights)' --copyright arknights
```

只填作品时，先读取安全站该作品的帖子元数据，收集真实角色 tag，再逐角色查询排名。
无需提供词库；这种用法不填角色中文名。若同时提供 `--dictionary`，则把发现的角色与
词库角色取交集，保留中文名和词库热度顺序，并在筛选后应用数量上限。
只提供显式角色 `--tag` / `--tags-file` 时直接查询指定角色，作品用于限制候选图片；
同时提供词库时，词库部分仍按作品发现结果筛选，再合并显式角色，避免遍历全库逐一请求。
这些操作会联网读取元数据；脚本不会靠角色名的括号后缀推断作品，避免漏掉无后缀角色。

默认每部作品发现最多 50 页，每页 200 条（最新可访问帖子），因此可能漏掉较早出现的角色。
需要完整扫描安全站当前可访问帖子时设置 `-DiscoveryPages 0` / `--discovery-pages 0`；
这会增加元数据请求和等待时间，不意味着作品里所有角色都有一般评级图片。
角色发现采用帖子 ID 游标翻页；再次运行会重新发现角色，已下载且校验通过的角色仍按断点跳过。
仅修改发现页数或候选窗口，不会建立新配置；扩大候选窗口后可用 `--retry-missing`。

多部作品分别查询后合并、去重并按选定评分重新排序，每个角色仍只保存一张；
`--pages` 指每角色、每作品的候选页数。下载前核对真实 `tag_string_copyright`，
候选必须包含至少一个所选作品，所有必含 tag 都要满足，任一排除 tag 都不能存在。
跨作品同框图可以入选；若不希望出现另一作品，可把它加入排除 tag。
作品筛选使用正式版权标签，不自动解析中文作品名、系列别名或包含所有衍生作品。

## Python 命令行

```powershell
python tools/fetch_danbooru_previews.py --tag 'amiya_(arknights)' --solo
python tools/fetch_danbooru_previews.py --dictionary 'C:/Users/kallan/Downloads/tag.sqlite' --max-characters 100 --solo
python tools/fetch_danbooru_previews.py --tags-file characters.txt --sort upvotes --output 'D:/character-previews'
```

`--tag` 可以重复。`--tags-file` 使用 UTF-8，一行一个精确英文标签，也可以每行
`英文标签<TAB>中文名称`。以 `#` 开头的行会被忽略。`--dictionary` 支持原始词库和
本项目索引化 SQLite，只读取 `category=4`，不会修改词库。合并不同输入会去除重复标签。
若同时指定词库和单个标签，会合并输入，而不是仅在词库中查询这个标签。
限量、无作品筛选的批次直接从词库按热度取指定数量，不解析后续整库条目。
词库中带当前脚本不支持的查询语法字符的标签会跳过并计数；显式输入仍会提示格式错误。

## 排序与筛选

- `--include-tag` / `-IncludeTag`：图片必须包含所有填写的 tag。
- `--exclude-tag` / `-ExcludeTag`：图片不能包含任意一个填写的 tag。
  使用精确英文标签，下划线/空格形式均可；重复参数或逗号分隔都支持。
  同一个 tag 同时必含与排除时立即提示冲突，不发请求。`-Solo` 也属于必含 `solo` 条件。
- `--filter-mode server` / `-FilterMode server`：默认，把必含 tag 与 `-排除tag` 加入服务端搜索，
  下载前再次根据返回的完整标签检查。使用当前正式标签，不用旧别名作为本地复核条件。
- `--filter-mode local` / `-FilterMode local`：只按角色/作品/评级向 API 请求，再对返回的候选元数据
  检查全部必含/排除条件，通过后才下载。该模式在有限候选窗口内找最高排名匹配图，
  稀有条件可能查不到，需要扩大 `--pages` / `--candidates`，不保证全站最高匹配图。
  根据[官方账号权限说明](https://safebooru.donmai.us/wiki_pages/help:users)，匿名/普通账户最多同时搜索
  2 个 tag，Gold 为 6 个；条件较多时可使用本地模式。请求仍受原有权限与访问限制约束。
  API 返回无效查询或超出限制时停止批次，不把同一错误重复请求到整个角色库。
- `--sort score` / `-Sort score`：默认，Danbooru 评分降序，即赞减踩。
- `--sort upvotes` / `-Sort upvotes`：正向赞数降序。
- `--sort favcount` / `-Sort favcount`：收藏数降序。
- `--rating general` / `-Rating general`：仅保留此兼容参数，只查询一般评级 `g`。
  其他评级和 `all` 会报参数错误。“最高”指当前作品/标签条件与候选窗口下的排名。
- `--solo` / `-Solo`：只查询带 `solo` 标签的图片，避免最高分是群像。默认不限制单人图。
- 默认查最多 2 页，每页 50 个候选。跳过动画、视频、无原图链接、已删除、禁用的帖子。
  第一名不能下载时会尝试下一张；JSON 的 `candidate_rank` 明确记录候选排序，
  不会把第 2 名当成全站第 1 名。查完没有可下载图片则记录 `missing`。
- `--retry-missing`：重新查找之前没有图片的角色；更改候选窗口后应带上此项。
- `--refresh`：重新查询所有条目。重新选择的图片会保留之前下载的文件，避免破坏人工挑选；
  以状态数据库的最新 `ok` 记录为准。

例如，要求单人且有蓝色眼睛，排除漫画和文字：

```powershell
& 'C:/Users/kallan/Desktop/NAI-Studio/tools/Fetch-Danbooru-Previews.ps1' -CharacterTag 'amiya_(arknights)' -IncludeTag 'solo','blue_eyes' -ExcludeTag 'comic','text' -FilterMode local
```

Python 同等参数：

```powershell
python tools/fetch_danbooru_previews.py --tag 'amiya_(arknights)' --include-tag 'solo,blue_eyes' --exclude-tag 'comic,text' --filter-mode local
```

条件去重、排序后计算独立配置标识。更换作品、必含/排除 tag 或筛选模式，不会错误跳过之前
其他条件下已完成的角色，也不会混用 `missing` 记录。仅改大小写、标签顺序不会重新建立重复配置。
安全版使用新的 `safebooru-` 配置前缀，旧普通站点的断点记录不混用；旧图片不会自动删除。
侧车 JSON 同时保存 `required_tags`、`excluded_tags`、`filter_mode`、
`selected_copyrights`、实际各作品查询 `queries` 以及 `api_base_url`。
配套网站允许打开这些记录中的安全站来源链接。

单图默认最多 50 MB，可通过 `--max-mb` 调整。原图 MD5（服务器提供时）和本地 SHA-256
会校验。中断下载使用 `.part`，完成后才替换文件；恢复时检查现有文件和配套 JSON。
多人图片可能同时被多个角色选中，目前保留独立文件和独立来源记录。
下载得到的是现有插画，不能据此保证生成模型识别角色，也不直接推导角色固定外观。

## 访问与速度

单线程串行请求，默认请求间隔至少 1.2 秒；不可调到 1 秒以下。
超时、429 和暂时性服务错误会有限重试；全局网络或权限失败时停止批次，避免连续失败请求。
401/403 不绕过登录、会员权限或网络验证；图片无权限时会跳到下一候选。
Python `urllib` 使用操作系统可识别的代理或 `HTTPS_PROXY` 环境变量。

匿名可访问的图片无需 Key。若需要使用自己的 Danbooru 账号，通过进程环境变量设置
`DANBOORU_LOGIN` 和 `DANBOORU_API_KEY`，不在命令行或文件里保存 Key。
仅 API 主机收到 Basic Authorization，图片 CDN 请求不带账号授权。
`--user-agent` 可设置带本人 Danbooru 用户 ID 的明确客户端标识。

整库约十万个角色标签，还包含服装等变体，可能需要数天和较多磁盘空间；
实际可下载数量取决于评级、单人限制、候选窗口、网络与账号权限。
这份脚本不会自动把抓到的图片导入 NAI Studio/AnimaDex，也不会自动公开发布图片。

参考：[官方 API 文档](https://safebooru.donmai.us/wiki_pages/help:api)、
[排序参数源码](https://github.com/danbooru/danbooru/blob/master/app/logical/post_query_builder.rb)。

## 本次验证

检查 Python 编译、命令行帮助、PowerShell 语法和 Git 差异。
开发前读取公开 API，确认 `order:score` 返回评分降序及原图链接。
遵照用户要求未运行双端测试；未下载示例图片、未执行全库抓取、未修改应用安装包。

2026-10-07：增加必含/排除 tag、独立断点配置、本地候选筛选与查询限制错误停止。
Python 编译、命令行帮助、PowerShell 语法以及离线条件/配置标识检查；没有联网抓图。

2026-10-07：新增单作品/多作品筛选、真实帖子角色发现和合并排序，固定官方安全版与 g 评级。
检查 Python 编译、命令行帮助、PowerShell/JavaScript 语法、离线元数据筛选/发现/排序和配置隔离。
未进行双端测试，未启动元数据抓取或图片下载；联网端到端抓取尚未验证。
