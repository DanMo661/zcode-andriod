# 吉祥物来源与授权记录（2026-11-04，R1 阶段 4）

## 选定形象：GLM 娘（智谱 GLM 社区拟人）

- **角色设计**：ZipZipPipe（[Pixiv](https://www.pixiv.net/users/18604994) / [Bilibili](https://space.bilibili.com/4168597)）
- **原始立绘**：Pixiv《[AI娘化](https://www.pixiv.net/artworks/148186519)》第 7 页（下标 p6），1152×2048，白底
- **获取渠道**：[shuaizenge/ai-girls](https://github.com/shuaizenge/ai-girls)（AI 娘素材收录仓库）`characters/glm/adult/`
  - 本目录 `glm-adult-zipzippipe-pixiv-148186519-p06.jpg`，sha256 `b15ddcb0cc9308087182da75e65e77b3ed14b8d20278b36f76e4712d2df965b8`（与上游 catalog.json 一致）
- **Q 版截帧**（备用，引导页/关于页候选）：Bilibili《[大AI和小AI们](https://www.bilibili.com/video/BV1tE9XBbErS/)》35 秒处，720×1280
  - 本目录 `glm-chibi-zipzippipe-BV1tE9XBbErS-35s.png`，sha256 `9088698d394f6a358be564300bb29d793c90ed328e91b51af56c453d0db3fea9`
- **检索路径**：image search 关键词 "z.ai Zhipu GLM anime mascot girl" → 命中梗鲸站（[aigengtu.com](https://aigengtu.com)）"智谱 GLM" 角色卡（本目录 `search-cache/cand2-glm-catgirl.png`，角色卡明确标注"智谱 GLM"）→ 追溯到 ZipZipPipe 原设与 ai-girls 收录仓库。另一次中文检索（"智谱 GLM 动漫吉祥物 娘化"）确认该形象与 DeepSeek 鲸鱼娘无混淆。
- **授权依据**：ZipZipPipe 在《[“亿万”身家的她们](https://www.bilibili.com/video/BV1bARXBYEZz/)》简介声明其图片开放分享、允许再创作；其 DeepSeek 二次设计明确 CC BY-NC-SA 4.0（署名-非商业-相同方式共享）。GLM 娘为其原创设计，按"开放分享允许再创作"条款使用；**本项目为非营利开源（Apache-2.0，无商业化），署名保留于本文件与 THIRD-PARTY-NOTICE.md**。若作者日后收紧条款，以下架处理。
- **注意**：该形象为社区拟人创作，不代表智谱/Z.ai 官方角色——与本项目"与智谱/Z.ai 官方无关"的独立第三方声明一致。

## 派生物（本项目生成）

| 文件 | 说明 |
|---|---|
| `mascot-bust-432.png` | 半身抠像预览（432×432，透明底） |
| `mascot-square-512.png` | 方头像（512×512，白底，关于页/引导页复用候选） |
| `app/src/main/res/mipmap-*/zcodea_launcher_foreground.png` | adaptive icon 前景（108/162/216/324/432 px，抠像半身居中，内容 70% 收进安全区） |
| `make_icons.py` | 派生脚本（PIL：边缘 flood-fill 抠白底 → 裁切 → 缩放合成） |
| `probe_art.py` | 抠图探测/取参脚本（只读） |
| `preview-light-blue.png` | 背景色对比预览（最终选深底 #0D0E0F，未采用浅蓝） |

## 弃用路径存档（search-cache/）

- 前置运行曾准备 3 份 AI 生图 prompt（`_prompt-a/b/c.txt`）：违背用户"网上找、不自己生成"的要求，未执行，文件移入 `search-cache/` 存档。
- `search-cache/cand1-zai-wolf.jpg`（900×1600 检索镜像版）与正式采用的 ai-girls 收录版同源，前者分辨率较低、出处链不完整，弃用。
