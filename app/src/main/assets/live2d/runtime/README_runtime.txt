这个目录用于存放 APK 内置的 Live2D 离线运行库。

GitHub Actions 编译时会由 app/build.gradle 的 prepareOfflineLive2DRuntime 任务自动下载：
- pixi.min.js
- pixi-live2d-display.min.js
- live2dcubismcore.min.js
- live2d.min.js

如果你的编译环境无法访问外网，也可以手动下载以上 4 个文件并放到这个目录。
只要这些文件存在且大于 1KB，Gradle 任务就不会重复下载。

车机运行时加载顺序：
1. file:///android_asset/live2d/runtime/  APK 内置离线运行库
2. file:///sdcard/MikuCarLauncher/live2d/runtime/  手动放置的运行库
3. 在线 CDN 兜底
