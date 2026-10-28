# lume órbita — 简体中文指南

QA: [0.4.0](../../qa/i18n-validation.md) · [0.4.1](../../qa/konka-041-validation.md) · [0.4.2](../../qa/clock-locale-042.md)

lume órbita是面向Android TV和Google TV 11及以上版本的原创启动器，使用Kotlin和原生Views，无需root。功能包括应用库、可排序的收藏、继续观看和推荐、透明玻璃风格、影院模式、30种氛围背景及屏幕保护程序。兼容性仍取决于电视厂商，不能保证所有型号都支持相同功能。

## 语言

在设置中打开“语言”。自动模式会按电视的系统语言偏好选择第一个支持的语言；没有匹配项时使用英语（美国）。手动选择会保存，并且只改变此启动器，不会修改电视系统语言。支持21种语言及地区变体。应用名称、平台提供的标题和简介可能保留原语言；它们不是启动器自己的译文。

## 遥控器与内容

左右键在菜单、“继续观看”、“推荐”和“影院模式”之间移动。焦点在标题上时，会预览上次选择的卡片和详情，但焦点仍在标题上。按下方向键或OK进入卡片，按上方向键返回标题。收藏区按OK打开应用，长按OK显示操作。应用库中按OK添加或移除收藏；移动操作用左右键调整，OK保存，返回取消。

内容和进度来自平台向Android共享的数据。可选的READ_TV_LISTINGS权限需在电视上授权；没有权限或没有共享数据时，相关内容可能为空。启动器不访问流媒体账户。应用库显示可启动的应用，不会把后台服务或已停用组件当作应用导入。

## 安装与主屏幕

先确认电视系统、官方调试方式及当前主屏幕组件。下载APK后核对发行版提供的SHA-256。下面的`SERIAL`和`LUME.apk`是占位符，请替换成自己的设备标识和文件名：

执行 `adb install -r` 前后，请记录并核对默认主屏幕应用以及 Home 键打开的界面。某些设备在更新后可能改变主屏幕选择，请勿假定更新一定会保留它。保留另一个可用的启动器；如有变化，请通过 Android 的主屏幕选择界面恢复。

```sh
adb devices
adb -s SERIAL install -r LUME.apk
adb -s SERIAL shell am start -n br.com.lume.launcher/.LauncherActivity
```

先作为普通应用试用，再通过启动器设置或Android的默认应用设置选择主屏幕。选择项取决于固件。试用期间保留另一个兼容启动器，并分别测试Home、返回和待机恢复。Home默认应用与待机唤醒后的行为不是同一设置。

恢复时先选择另一个可正常工作的启动器，再考虑卸载Lume。不要卸载唯一可用的主屏幕或系统组件。更新需要相同包名和签名；本地编译的签名可能不同，不要为了绕过签名错误而直接清除数据。

## 外观、影院模式和保护程序

透明度、反光、动画、轻量模式和背景都可自定义。影院模式仅调暗启动器、改变其色调并暂停视觉效果，不改变电视的画面设置。可选定时为本地时间20:00–06:00；手动切换会关闭定时。

屏幕保护程序支持免费在线风景和离线程序生成图像。照片只保存在内存中，不写入电视存储；进程结束后需要重新加载。可调整启动延迟、切换间隔、调暗程度和时钟。启动器内的闲置保护与Android在其他应用或休眠时管理的保护程序是独立的。方向键、OK或返回可退出。图片署名包含作者、来源、许可证和显示调整说明。

Google Play、网络设置和HDMI入口依赖设备提供的系统功能。商店负责兼容性与安装确认；启动器不会自行下载或安装APK。

## 编译与验证

需要JDK 17、Android SDK Platform 35和Build Tools 36.0.0。配置自己的`JAVA_HOME`、`ANDROID_HOME`或未提交的`local.properties`，并接受SDK许可。Gradle由wrapper提供：

修改翻译 JSON 后，请在仓库根目录重新生成 XML 资源，再运行验证和 Gradle 构建：

```sh
python tools/build_locale_resources.py
cd src/android
./gradlew --no-daemon --max-workers=1 testDebugUnitTest verifyRulesCoverage assembleRelease lintRelease
```

Windows PowerShell使用`.\gradlew.bat`代替`./gradlew`。APK位于`src/android/app/build/outputs/apk/release/app-release.apk`。在仓库根目录可运行：

```sh
python tools/verify_locales.py
python tools/verify_locales.py --self-check
```

静态检查验证资源和格式结构；self-check只测试检查器本身。它们不代表21种语言已通过真实界面或母语校审。代码覆盖率仅针对所测逻辑，不等于所有电视界面都无误。参见[国际化测试计划](../../qa/i18n-test-plan.md)。

## 隐私与许可

没有自建后端或遥测。收藏、外观和语言等选项保存在应用私有设置中。在线图片需要HTTPS请求；内存缓存上限不代表应用总内存占用。不要公开电视的观看记录、调试文件或签名密钥。

原创代码采用[MIT许可证](../../../LICENSE)。照片、依赖库、工具及商标保留各自权利，详见[第三方声明](../../../THIRD_PARTY_NOTICES.md)。译文不替代原始法律文本。

## 通过 GitHub 更新（0.4.3+）

在 **关于** 中选择 **检查更新**：仅在您点击时查询 GitHub，不会定期检查。如果有兼容的新版本，请选择 **下载更新**。下载验证完成后，再选择 **安装更新**，然后在 Android 中确认安装。如果 Android 要求授权，请打开 **允许此来源**，自行在 Android 设置中允许 Lume 安装，然后返回确认。Lume 不会自动授予此权限。使用相同包名和签名证书更新会保留应用设置；请勿通过卸载 Lume 来更新。更新后请检查 Home。如果电视更改了主屏幕选择，可通过 **检查主屏幕选择** 打开系统设置并手动选择。
