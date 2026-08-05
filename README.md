# 童识汉字（TongshiHanzi）

面向儿童、家长和小学教师的离线汉字词典。项目使用原生 Android Java、XML、Room 与 Material 3 Views，支持汉字、拼音、笔画和五笔 86 查询，并提供系统中文语音朗读、收藏、生字本与设置。

## 功能

- 汉字、拼音、数字声调、笔画数、笔画范围与五笔 86 查询
- 输入防抖、前缀匹配、模糊拼音和候选排序
- 汉字详情、多音字切换、基本释义、组词、笔顺与专业信息
- Android TextToSpeech 中文朗读、语速、音调和具体语音选择
- 收藏、最近查询、深色模式和 Material 3 动态配色
- Room 双数据库，字典内容与用户数据独立升级
- 无账号、广告、分析 SDK 和不必要权限

仓库内置 25 个审校开发字条。完整公开数据集必须通过 `dict-builder` 按 `docs/DATA_PIPELINE.md` 导入，并保留来源及许可证。

## 构建

要求 JDK 17、Gradle 9.5、Android SDK 37 与 Build Tools 36.0.0。

```bash
gradle clean test assembleDebug
```

APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

## License

应用源代码采用 GPL-3.0-or-later。第三方软件和字典数据保持原许可证，详见 `THIRD_PARTY_NOTICES.md` 与 `DATA_LICENSES.md`。
