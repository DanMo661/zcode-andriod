# branding/ — ZcodeAndriod 品牌资产

本目录归品牌代理所有，纯资源、不含逻辑。

| 子目录/文件 | 内容 |
|---|---|
| `mascot/` | 吉祥物源图、出处与授权记录（`SOURCES.md`）、派生脚本与预览。R1 起采用社区画师 ZipZipPipe 的 "GLM 娘"（智谱 GLM 社区拟人，非官方形象） |

实际派生物落位（不在本目录，避免双份维护）：

- 启动图标 adaptive 前景：`app/src/main/res/mipmap-*/zcodea_launcher_foreground.png`（深底 `@color/dsha_launcher_background` 沿用，资源名保留零引用风险；monochrome 沿用 Z 标）
- 引导页 hero / 关于页 mark：`app/src/main/res/drawable-nodpi/zcodea_mascot.png`（welcome_page1.xml 与 AboutFragment 引用）
- 引导页三屏文案 / 关于页文本：`app/src/main/res/values*/strings.xml` 与 `ui/AboutFragment.java`（R1 品牌清洗时落地）
- 署名（法律义务）：根 `THIRD-PARTY-NOTICE.md` "启动图标吉祥物"一节 + `mascot/SOURCES.md`
