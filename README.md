# 童识汉字（TongshiHanzi）

面向儿童、家长和小学教师的离线汉字词典。项目使用原生 Android Java、XML、Room 与 Material 3 Views，支持汉字、拼音、笔画和五笔 86 查询，并提供系统中文语音朗读、收藏、生字本与设置。

开发者与主要维护者：**Chlna6666**

## 功能

- 汉字、拼音、数字声调、笔画数、笔画范围与五笔 86 查询
- 输入防抖、前缀匹配、模糊拼音和候选排序
- 汉字详情、多音字切换、基本释义、组词、笔顺与专业信息
- 主字、读音示例词与组词独立朗读
- Android TextToSpeech 中文朗读、语速、音调和具体语音选择
- 10,210 个汉字的真实离线矢量笔顺动画，缺失时明确降级
- 收藏、最近查询、跟随系统的深浅主题和 Material 3 动态配色
- Room 双数据库，字典内容与用户数据独立升级
- 无账号、广告、分析 SDK 和应用内联网请求

## 离线词库

项目保留 25 个经过人工审校的儿童释义作为高质量覆盖层，同时从固定版本的 `mapull/chinese-dictionary` 生成完整扩展词库。当前打包 **21,056 个汉字**，覆盖常用字和大量生僻字，并关联受限数量的词语候选。

CI 会把词库预先构建成 Room v4 SQLite 数据库。Android 首次运行通过 `Room.createFromAsset()` 复制并校验数据库，不再在设备上逐条解析 21,056 条 JSON 和执行大量插入，因此启动耗时主要变为一次顺序文件复制。收藏、历史等用户数据仍保存在独立数据库，词库升级不会清除用户数据。

gzip NDJSON 继续作为可审计、可重复生成的中间数据，使用 `.bin` 扩展名避免 AAPT 自动展开 `.gz`：

```bash
python3 tools/generate_full_dictionary_asset.py
gradle --rerun-tasks :app:compileDebugJavaWithJavac
python3 tools/build_prebuilt_dictionary.py --version 4
```

数据源固定到明确提交，许可证、修改说明和更新流程见 `DATA_LICENSES.md`。`pwxcoo/chinese-xinhua` 当前不直接打包，因为其 README 明确说明数据来自网站抓取，逐条来源和再分发权利仍需进一步核验。

## 笔顺数据

笔顺动画不是从字体轮廓推测。字体轮廓只描述最终外形，不包含起笔、收笔和先后顺序，也不能可靠区分中国大陆、台湾、日本等地区规范。

项目构建自定义随机访问格式 `TSHS`：

- Make Me a Hanzi：9,574 个中国大陆规范优先的笔顺记录；
- AnimCJK `ZhHans`：补充 636 个主数据集缺失的简体和生僻字；
- 合计：**10,210 个汉字**；
- 每个汉字的 SVG 路径和中线独立 zlib 压缩；
- Android 只内存映射固定索引，并按需解压当前详情页汉字；
- 最近 64 个汉字保留在 LRU 缓存；
- 25 个审校字的笔画数在 CI 中逐字与矢量数据核对，任何冲突都会使构建失败；
- 审校笔画名称优先于第三方通用名称。

```bash
python3 tools/generate_stroke_pack.py --strict-curated
```

对于仍无可靠矢量记录的汉字，应用只显示字形参考和“暂无可验证笔顺”，不会把字体部件分析伪装成标准笔顺。

## 可继续接入的数据源

项目的数据层已按来源拆分，后续可审查接入：

- Unicode Unihan：部首、总笔画、读音、异体和地区属性；
- OpenCC：简繁及地区异体映射；
- CJKVI IDS：汉字部件与表意描述序列，用于结构分析，不用于伪造笔顺；
- Rime Wubi：五笔编码；
- CC-CEDICT、Chinese Wiktionary/Kaikki：词语和释义补充，但必须保留各自许可证与署名。

## 构建

要求：

- JDK 17
- Gradle 9.1.0
- Android SDK 36
- Android Build Tools 36.0.0
- Python 3

```bash
python3 tools/generate_full_dictionary_asset.py
python3 tools/generate_stroke_vectors.py --strict
python3 tools/generate_stroke_pack.py --strict-curated
gradle --rerun-tasks :app:compileDebugJavaWithJavac
python3 tools/build_prebuilt_dictionary.py --version 4
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
2. 生成并逐条校验 21,056 字完整离线词库；
3. 生成 10,210 字多来源矢量笔顺包；
4. 对 25 个审校字执行笔画数一致性强校验；
5. 导出 Room v4 schema 并生成预填充 SQLite 数据库；
6. 执行 SQLite 完整性、外键和字符计数检查；
7. 运行单元测试并构建 Debug、Release/R8 APK；
8. 直接检查 APK 内的词库、Room 数据库、未压缩 TSHS 索引和实际记录数；
9. 上传 APK、词库清单、笔顺清单与 Room schema。

生成的二进制资产和清单会提交回对应代码分支，固定上游提交未变化时可重复得到同一结果。

## License

应用源代码采用 GPL-3.0-or-later。第三方软件和字典数据保持原许可证，详见 `THIRD_PARTY_NOTICES.md`、`DATA_LICENSES.md` 与 `licenses/`。
