# 童识汉字（TongshiHanzi）

面向儿童、家长和小学教师的离线汉字词典。项目使用原生 Android Java、XML、Room 与 Material 3 Views，支持汉字、拼音、笔画和五笔 86 查询，并提供系统中文语音朗读、收藏、生字本与设置。

开发者与主要维护者：**Chlna6666**

## 功能

- 汉字、拼音、数字声调、笔画数、笔画范围与五笔 86 查询
- 输入防抖、前缀匹配、模糊拼音和候选排序
- 汉字详情、多音字切换、基本释义、组词、笔顺与专业信息
- 主字、读音示例词与组词独立朗读
- Android TextToSpeech 中文朗读、语速、音调和具体语音选择
- 真实离线矢量笔顺动画，缺失数据时明确降级
- 收藏、最近查询、跟随系统的深浅主题和 Material 3 动态配色
- Room 双数据库，字典内容与用户数据独立升级
- 无账号、广告、分析 SDK 和应用内联网请求

## 离线词库

项目保留 25 个经过人工审校的儿童释义作为高质量覆盖层，同时从固定版本的 `mapull/chinese-dictionary` 生成完整扩展词库。上游提供约 2 万个汉字、常用及生僻字信息和大规模词语数据。

生成文件采用 gzip 压缩的 NDJSON。应用首次建立字典数据库时按 256 个汉字一批导入，避免一次性加载完整数据造成明显内存峰值。

```bash
python3 tools/sync_mapull_dictionary.py
```

数据源固定到明确提交，许可证、修改说明和更新流程见 `DATA_LICENSES.md`。`pwxcoo/chinese-xinhua` 当前不直接打包，因为其 README 明确说明数据来自网站抓取，逐条来源和再分发权利仍需进一步核验。

## 构建

要求：

- JDK 17
- Gradle 9.1.0
- Android SDK 36
- Android Build Tools 36.0.0

```bash
python3 tools/sync_mapull_dictionary.py
gradle clean test assembleDebug assembleRelease
```

Debug APK 位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

Release 构建默认输出未签名 APK。正式分发应在 GitHub Actions Secrets 或本地安全配置中使用固定发布密钥签名，不能每次生成新的随机密钥。

## CI

Android CI 会执行：

1. 校验 25 字审校种子；
2. 生成或复用固定版本的完整离线词库；
3. 校验压缩词库中的每条 JSON 与字符计数；
4. 生成审校字符的离线笔顺矢量；
5. 运行单元测试并构建 Debug、Release APK；
6. 上传 APK 与词库清单。

首次生成完整词库时，工作流会把确定性的压缩数据文件提交回当前分支；后续构建在固定上游提交未变化时不会重复下载或改写文件。

## License

应用源代码采用 GPL-3.0-or-later。第三方软件和字典数据保持原许可证，详见 `THIRD_PARTY_NOTICES.md`、`DATA_LICENSES.md` 与 `licenses/`。
