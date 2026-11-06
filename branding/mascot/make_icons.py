#!/usr/bin/env python3
"""GLM 娘 → adaptive icon 资产生成。

输入：branding/mascot/glm-adult-zipzippipe-pixiv-148186519-p06.jpg（1152×2048 白底立绘，
ZipZipPipe 原设，经 shuaizenge/ai-girls 收录，sha256 b15ddcb0…）
输出：
  app/src/main/res/mipmap-*/zcodea_launcher_foreground.png （108/162/216/324/432 px，
  108dp 前景层，角色半身居中、内容收进 66% 安全区，白底已抠成透明）
  branding/mascot/mascot-bust-432.png（预览存档）
  branding/mascot/mascot-square-512.png（方头像，白底，关于页/引导页复用备选）
"""
from pathlib import Path
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
SRC = HERE / "glm-adult-zipzippipe-pixiv-148186519-p06.jpg"
RES = ROOT / "app" / "src" / "main" / "res"

# 半身裁切窗口（源图坐标，探测脚本 probe_art.py 定出）：
# 耳尖 y≈78 起，到书本下方 y≈1000；角色中轴 x≈625。
CROP = (145, 40, 1105, 1000)          # 960×960
CONTENT_H = 960 - 40                  # 裁切窗内内容高度
AXIS_X = 625.0                        # 角色中轴（源图坐标）
FILL = 0.70                           # 内容高度占 432 前景画布比例（66% 安全区+余量）
MASTER = 432                          # xxxhdpi 前景尺寸
DENSITIES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}


def cutout_rgba():
    """白底 → 透明（边缘 flood fill，thresh 覆盖 JPG 噪声）。"""
    img = Image.open(SRC).convert("RGB")
    w, h = img.size
    ff = img.copy()
    seeds = [(0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1),
             (w // 2, 0), (w // 2, h - 1), (0, h // 2), (w - 1, h // 2)]
    for s in seeds:
        ImageDraw.floodfill(ff, s, (255, 0, 255), thresh=32)
    rgba = img.convert("RGBA")
    px_ff, px_out = ff.load(), rgba.load()
    for y in range(h):
        for x in range(w):
            if px_ff[x, y] == (255, 0, 255):
                px_out[x, y] = (0, 0, 0, 0)
    return rgba


def build_foreground(cut):
    crop = cut.crop(CROP)  # 960×960
    target_h = int(MASTER * FILL)
    scale = target_h / CONTENT_H
    new_w = max(1, round(crop.width * scale))
    scaled = crop.resize((new_w, target_h), Image.LANCZOS)
    canvas = Image.new("RGBA", (MASTER, MASTER), (0, 0, 0, 0))
    x = int(round(MASTER / 2 - (AXIS_X - CROP[0]) * scale))
    y = int(round((MASTER - target_h) / 2))
    canvas.alpha_composite(scaled, (x, y))
    return canvas


def build_square(cut, size=512):
    """方头像：同裁切窗，内容占 88%，淡白底。"""
    crop = cut.crop(CROP)
    target_h = int(size * 0.88)
    scale = target_h / CONTENT_H
    scaled = crop.resize((max(1, round(crop.width * scale)), target_h), Image.LANCZOS)
    canvas = Image.new("RGBA", (size, size), (255, 255, 255, 255))
    x = int(round(size / 2 - (AXIS_X - CROP[0]) * scale))
    y = int(round((size - target_h) / 2))
    canvas.alpha_composite(scaled, (x, y))
    return canvas.convert("RGB")


def main():
    cut = cutout_rgba()
    master = build_foreground(cut)
    for density, px in DENSITIES.items():
        out = RES / ("mipmap-" + density)
        out.mkdir(parents=True, exist_ok=True)
        img = master if px == MASTER else master.resize((px, px), Image.LANCZOS)
        path = out / "zcodea_launcher_foreground.png"
        img.save(path, optimize=True)
        print("写", path.relative_to(ROOT), img.size)
    master.save(HERE / "mascot-bust-432.png", optimize=True)
    build_square(cut).save(HERE / "mascot-square-512.png", optimize=True)
    print("写 mascot-bust-432.png / mascot-square-512.png")


if __name__ == "__main__":
    main()
