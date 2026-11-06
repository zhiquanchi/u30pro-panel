# U30 Pro Panel

不 root 的中兴 U30 Pro（MU5358）本地管理 App。用原生 Android 客户端替代原厂后台的日常操作：看信号/速率/流量、管理已连设备。

**它不做什么**：不刷机、不改固件、不做「免流」、不绕过计费、不注入原厂页面。只调用设备自带的 goform HTTP API。

---

## 当前状态

| 项 | 状态 |
|---|---|
| 工程 | Kotlin 2.1 + Compose，单 module，minSdk 26 / target 35 |
| 构建 | `assembleDebug` 通过，约 10 MB APK |
| 测试 | 28 个 JVM 单测通过（hash、状态/设备/设置解析、APN/短信、HTTP、导出） |
| 真机 | MU5358 / `U30ProV1.0.0B24`：登录、状态、流量、在线设备、ACL、管理页读值、指示灯写入回读、诊断导出 |
| 读取会话 | 状态/设备列表/ACL/设置均可在**未登录**状态下读取，监控不占用设备唯一会话 |
| Phase 0 | `tools/probe.mjs` 已对本地 mock 端到端跑通 |

U30 Pro（MU5358 / UMS9632）与 U30 Air 同属 MyOS goform 家族，但基带不同，字段名有差异。因此 App 不硬编码字段，首次连接会做能力探测。

### 真机实测（2026-11-06）

| 项 | 结果 |
|---|---|
| 型号 / 固件 | `MU5358` / `U30ProV1.0.0B24`（`cr_version` 同名） |
| 端口 | 80 与 8080 都提供 goform，App 使用 80 |
| 登录 | `LD + 双 SHA-256` 成功，`loginScheme=ld-sha256` |
| 读取 | `LOGOUT` 后无 Cookie 仍可读状态、`station_list`、ACL |
| 信号 | `Z5g_rsrp=-71`、`network_rssi=3`；`network_information` 提供 `Nr_snr=17`、`nr_rsrq=-6`、`Nr_bands=41`、`Nr_pci=910`、`Nr_cell_id` |
| 管理读值 | 指示灯/电池保护/流量限额/APN/短信/OTA 状态均能读到；空字段项自动隐藏 |
| 写入验证 | 指示灯 `1 → 0 → 1`，App 登录 + AD + 写入 + 回读 + 登出全链路通过 |
| APN / 短信 | APN 当前 `China Mobile / CMIOT5G / IPv4v6 / auto`；短信子页可打开（当前无短信） |
| 流量 | 本月 94.0 GB 下行 / 7.9 GB 上行，速率字段正常；会话字节计数为 0 |
| 在线设备 | 1 台测试手机（`station_list`，示例地址已脱敏） |
| ACL | 扁平对象，`AclMode=2`，黑/白名单为空；支持拉黑 |
| 重命名 | 原厂 UI 含 `EDIT_HOSTNAME`，能力探测判定支持（未实际改名） |
| 温度 | 无可用字段 |

---

## 下载安装

预编译 APK 见 [GitHub Releases](https://github.com/zhiquanchi/u30pro-panel/releases)。

- `app-release.apk`，Android 8.0+，侧载安装。
- 该包使用独立的 release keystore 签名（keystore 不入库）。如果你之前用 `adb install` 装过 debug 包，**覆盖安装会报签名冲突**：需要先卸载 debug 版（会清除已保存的密码/设备档案），或在另一台手机安装。
- 自己构建：`./gradlew assembleRelease`。仓库不含签名密钥，未配置 `keystore.properties` 时产出的是 unsigned APK。

---

## 功能

- **状态总览**：NR/ENDC/LTE、运营商、RSRP/SINR/RSRQ、频段/PCI/小区、实时上下行 + 60 秒曲线、本月/本次流量、在线时长、WiFi 客户端数、电量、IMEI/ICCID/号码、温度（仅在设备真的返回时显示）。SINR/RSRQ/频段/PCI 从 `network_information` 的 `Nr_*` 字段读取。
- **设备管理**：移动网络（网络模式、漫游、数据连接、频段锁定状态/解除、APN 编辑）、WiFi（开关、SSID/密码、隐藏、最大连接数、AP 隔离）、系统硬件（指示灯、USB 端口/协议、Samba、性能模式、电池保护、SIM 槽、OTA 检查、重启）、流量（限额、提醒、清零日、统计清零）。
- **风险分级 + 回读校验**：安全项直接写；普通项确认；重启/改 WiFi/频段/USB/SIM 等需输入「确认」。每次写入后重新读值，不一致会明确提示。
- **短信**：列表、详情、发送、删除、标记已读。
- **已连设备**：合并 `station_list` + `lan_station_list`，标注 ACL 黑名单；拉黑/解黑；重命名走 `EDIT_HOSTNAME`。
- **诊断/自检**：端口、登录算法、固件、可用字段数、每类字段命中率、缺失字段、原始键值、复制设备档案 JSON、重新探测。
- **导出诊断 JSON**：写入 `/sdcard/Android/data/com.u30pro.panel/files/diagnostics/latest.json`，电脑 USB 即可 `adb pull`，无需连接随身 WiFi。
- **设置**：轮询间隔 1/2/5 秒、断开、清除本地数据。

---

## 快速开始

### 1. 构建

需要 JDK 17 与 Android SDK 35：

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./gradlew assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```

`local.properties` 里的 `sdk.dir` 指向本机 SDK。仓库中的 Gradle wrapper 使用腾讯镜像（`services.gradle.org` 会 302 到 GitHub，在当前网络不可达）；如你的网络正常，可把 `gradle/wrapper/gradle-wrapper.properties` 的 `distributionUrl` 换回官方地址。

### 2. 安装并连接

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

手机先连上 U30 Pro 的 WiFi → 打开 App → 输入网关（默认 `192.168.0.1`）和管理密码 → 「连接并探测」。

### 3.（建议）先用 Phase 0 脚本确认协议

Mac 连上设备 WiFi 后：

```bash
U30_PASSWORD='你的管理密码' node tools/probe.mjs
```

脚本**只做 LOGIN / 读取 / LOGOUT，不写任何配置**。它会：

1. 探测 80 / 8080 上的 `cmd=LD`；
2. 尝试 LD + 双 SHA-256 登录，失败回退 Base64（老固件）；
3. 分批读取所有候选字段，整批失败时二分拆开重试；
4. 单独重试 `station_list` / `lan_station_list` / ACL；
5. 输出 `docs/probe-u30pro.json`。

如果登录算法不是 `ld-sha256`，或者关键字段与预期不同，先改 App 常量再联调。

### 4. 电脑不连随身 WiFi 时

手机 USB 连电脑、手机连设备 WiFi，电脑保持原来的网络即可。`adb install`、`adb logcat`、截图都走 USB；App 的请求由手机发往 `192.168.0.1`。

`tools/probe.mjs` 是电脑直连设备的脚本，此时跑不了（`adb forward` 只能到手机 localhost，到不了 `192.168.0.1`）。改用 App 导出：

1. App 连上设备，等状态页刷新几秒。
2. 打开「诊断」→「导出 JSON」。
3. 电脑执行：

```bash
adb pull /sdcard/Android/data/com.u30pro.panel/files/diagnostics/latest.json ./docs/probe-u30pro.json
jq '.probe.profile, (.status.raw | keys)' ./docs/probe-u30pro.json
```

导出内容包含 `probe`（登录算法、端口、固件、每类字段命中率/缺失字段）、`status.raw`（设备返回的全部原始键值）、`stations`（客户端与 ACL）。`latest.json` 固定覆盖，另有最多 10 份时间戳留档。

若个别 OEM 限制读取 `Android/data`，debug 版可用：

```bash
adb exec-out run-as com.u30pro.panel cat files/diagnostics/latest.json > ./docs/probe-u30pro.json
```

### 5. 本地 mock（无设备时开发）

```bash
node tools/mock-goform.mjs --port 8787 --password test123
U30_PASSWORD=test123 node tools/probe.mjs --gateway 127.0.0.1 --port 8787 --out /tmp/probe-mock.json
```

mock 模拟的是一台 U30 Air 风格的设备，用来验证协议实现，不是设备仿真器。

---

## 结构

```
app/src/main/java/com/u30pro/panel/
  U30App.kt / AppContainer.kt              手动 DI
  MainActivity.kt                          生命周期 → 前台轮询 / 后台停止
  data/goform/                             HTTP、cookie、hash、宽松 JSON
  data/net/                                WiFi 网络绑定 socket/DNS
  data/parse/                              Status/Station/Settings/APN/SMS 解析（纯函数，可测）
  data/profile/                            DeviceProfile、候选字段表
  data/repo/PanelRepository.kt             会话 + 探测 + 读写 + APN/SMS
  data/settings/                           设置模型、命令目录、风险分级
  data/store/                              DataStore、Keystore 加密密码
  ui/                                      ViewModel、Shell、状态/设备/管理/诊断/应用
  ui/screens/ManageScreen.kt               通用设置渲染
  ui/screens/ApnScreen.kt / SmsScreen.kt   APN 与短信子页
tools/probe.mjs                            Phase 0 协议探测
tools/mock-goform.mjs                      开发用 mock
docs/PROTOCOL.md                           协议与字段验证情况
```

## 协议要点

- 读：`GET /goform/goform_get_cmd_process?cmd=<csv>&multi_data=1&isTest=false`
- 写：`POST /goform/goform_set_cmd_process`
- 登录：`cmd=LD` 取盐 → `UPPER(hex(SHA256(UPPER(hex(SHA256(pw))) + LD)))`
- 必须带 `X-Requested-With: XMLHttpRequest` 和 `Referer: http://<gateway>/`，否则返回 `{"Error":"none secure connection"}`
- 会话：`JSESSIONID`（HttpOnly，`Path=/goform`），由 OkHttp CookieJar 处理
- 写操作需 `AD = UPPER(hex(SHA256(UPPER(hex(SHA256(wa_inner_version+cr_version)))+RD)))`
- 结果码：`0` 成功，`3` 密码错，`5` / `session` 会话被占用
- 端口：优先 80，回退 8080

细节与字段验证状态见 [docs/PROTOCOL.md](docs/PROTOCOL.md)。

---

## 已知限制

1. **写操作会短暂占用会话**：读取不需要登录，监控可以和原厂网页同时使用；只有写入会 `LOGIN → 写入 → LOGOUT`，期间如果原厂后台正持有会话，App 会提示让位。
2. **字段差异**：U30 Pro 的 `Z5g_snr`/`network_ZCELLINFO_band` 等直接字段为空；SINR/RSRQ/频段/PCI 实际来自 `network_information` 的 `Nr_*` 扁平字段。温度不经过 goform，仍不可用。
3. **写操作验证范围**：真机只实际验证了「指示灯」开关（可逆）。WiFi SSID/密码、APN、SMS、USB、频段、SIM 槽等写入路径已实现并有回读校验，但**未在你的设备上执行**，首次使用请自行确认。
4. **部分功能按固件隐藏**：访客网络、信道/带宽、夜间模式、定时开关机、自动省电等字段在当前 B24 返回空，界面自动隐藏；修改管理密码因哈希模式未确认，未暴露。
5. **明文 HTTP**：仅用于局域网直连设备，未引入证书或代理。
6. **不做 root 级功能**：AT 指令、深度限速、系统级踢人等不在范围内；恢复出厂/OTA 安装/运营商解锁/`SET_NV`/PUK 明确不提供。

---

## 安全

- 密码仅在「记住密码」时保存，使用 Android Keystore 的 AES-GCM 加密。
- 拉黑/解黑、重命名都会二次确认；探测阶段只读。
- 仅用于你自己的设备。

## 参考

- [noname22/u30mon](https://github.com/noname22/u30mon) — U30 Air 登录/字段实测
- [Asunano/UFI-AXIS](https://github.com/Asunano/UFI-AXIS) — MIT，会话保活/让位策略与字段分批
- [leehe-byte/AirDeck](https://github.com/leehe-byte/AirDeck) — U30 Air 行为参考（仅参考行为，未复制代码）
- [kanoqwq/UFI-TOOLS](https://github.com/kanoqwq/UFI-TOOLS) — 社区设备侧面板（需 root，本方案未采用）