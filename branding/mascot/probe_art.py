#!/usr/bin/env python3
"""探测 GLM 娘立绘：白底抠图（边缘 flood fill）→ 包围盒 → 采样点缀色。只打印，不写文件。"""
from PIL import Image, ImageDraw
from collections import Counter

SRC = "glm-adult-zipzippipe-pixiv-148186519-p06.jpg"

img = Image.open(SRC).convert("RGB")
w, h = img.size
print("size:", w, h)

# 四角+四边中点种子 flood fill（thresh 对 JPG 噪声留余量）
ff = img.copy()
seeds = [(0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1),
         (w // 2, 0), (w // 2, h - 1), (0, h // 2), (w - 1, h // 2)]
sentinel = (255, 0, 255)
for s in seeds:
    if ff.getpixel(s) != sentinel:
        ImageDraw.floodfill(ff, s, sentinel, thresh=32)

mask_bg = Image.new("1", ff.size, 1)
px_ff, px_m = ff.load(), mask_bg.load()
removed = 0
for y in range(h):
    for x in range(w):
        if px_ff[x, y] == sentinel:
            px_m[x, y] = 0
            removed += 1
print("bg removed px:", removed, "(%.1f%%)" % (100.0 * removed / (w * h)))

# 包围盒
bbox = mask_bg.getbbox()  # 非 bg 区域
print("character bbox:", bbox)

# 行/列占用来辅助裁切决策（每 5% 高度的非透明像素范围）
px = mask_bg.load()
for frac in range(0, 101, 5):
    y = min(h - 1, int(h * frac / 100))
    xs = [x for x in range(0, w, 4) if px[x, y]]
    if xs:
        print("y=%4d (%3d%%): x %d..%d" % (y, frac, min(xs), max(xs)))
    else:
        print("y=%4d (%3d%%): (empty)" % (y, frac))

# 采样蓝色点缀（全书低饱和蓝像素的均值）
blues = []
for y in range(0, h, 3):
    for x in range(0, w, 3):
        r, g, b = img.getpixel((x, y))
        if b > 120 and b - r > 40 and b - g > 25:
            blues.append((r, g, b))
if blues:
    n = len(blues)
    avg = tuple(sum(c[i] for c in blues) // n for i in range(3))
    print("blue accent avg: #%02X%02X%02X (n=%d)" % (avg + (n,)))

# 白底色采样（四角均值）
corners = [img.getpixel(p) for p in [(3, 3), (w - 4, 3), (3, h - 4), (w - 4, h - 4)]]
print("corner bg avg:", tuple(sum(c[i] for c in corners) // 4 for i in range(3)))
