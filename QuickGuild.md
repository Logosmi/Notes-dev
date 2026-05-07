# Gradle && ADB
## 构建
```bash
./gradlew clean assembledebug
```
## 列出可用设备
```bash
adb devices
```
## 安装(需要卸载原来的包)
```bash
./gradlew installDebug
```
## 重新安装
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
## 调试错误
```bash
adb logcat -d *:E | grep -E "net.micode.notes|AndroidRuntime|FATAL EXCEPTION" | grep "^05-07" > crash.log
```

# Git
branch_name 替换为实际分支名称
## 查看当前所在分支以及同步状态
```bash
git status
```
## 切换到一分支
```bash
git checkout branch_name
```
## 拉取最新代码(开发前执行一次，从dev分支获取最新代码)
```bash
git pull origin branch_name
```
## 暂存所有更改
```bash
git add .
```
## 提交
```bash
git commit -m "描述信息"
```
## 推送到github
```bash
git push origin branch_name
```