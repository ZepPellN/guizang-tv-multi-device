# 壁纸原图（不入库）

把要打进 APK 的图片放在这个目录，然后运行 `guizang-tv/scripts/prepare_wallpapers.py`。

- 支持 JPEG / PNG / WebP，1–15 张
- 文件名就是电视上壁纸列表显示的名字，例如 `1-雪山公路.jpg`
- 图片本身被 `.gitignore` 排除，不会进入仓库
- 生成结果写到 `guizang-tv/app/src/main/assets/wallpapers/local/`，同样不入库

```sh
# 需要 Pillow
cd guizang-tv
python3 scripts/prepare_wallpapers.py ../wallpapers
```

脚本输出最长边 1920 的 JPEG（质量 88）和 320×180 缩略图；重复运行会把上一份移到 `guizang-tv/.wallpaper-backups/`。
