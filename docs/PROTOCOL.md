# 原厂 goform 协议参考

本文件记录 App 实际使用的协议，以及每个字段的验证来源。

**U30 Pro 真机已确认（2026-11-06，MU5358 / `U30ProV1.0.0B24`）**：

- `192.168.0.1:80` 与 `:8080` 都返回 `cmd=LD`，两端口同时可用；
- App 使用 **LD + 双 SHA-256** 成功登录（`loginScheme=ld-sha256`，端口 80）；
- 不带 `X-Requested-With` / `Referer` 时返回 `{"Error":"none secure connection"}`；
- **状态、`station_list`、ACL 的读取不需要登录**：执行 `LOGOUT` 之后，无 Cookie 的请求仍返回完整数据；
- `cmd=RD` 也无需登录即可返回盐值，因此 **RD 不能用来判断会话是否存活**；
- ACL 返回的是**扁平对象**，不是嵌套在命令名下的对象：
  `{"AclMode":"2","WhiteMacList":"","BlackMacList":"","WhiteNameList":"","BlackNameList":""}`；
- `station_list` 实测（示例已脱敏）：
  `{"station_list":[{"mac_addr":"aa:bb:cc:dd:ee:ff","hostname":"Test-Phone","ip_addr":"192.168.0.2"}]}`；
- 流量的 `flux_monthly_*`、`flux_realtime_*_thrpt` 有值；`flux_realtime_*_bytes` 为 `0`（设备行为）；
- 信号只返回 `Z5g_rsrp`、`network_rssi`（等级 0-5）、`network_signalbar`；
  `Z5g_snr`、`Z5g_SINR`、`Z5g_rsrq`、`network_ZCELLINFO_band`、`network_Z_PCI`、`network_cell_id` **返回空字符串**；
- 原厂 UI 的 `js/service.js` 包含 `EDIT_HOSTNAME` 与 `setDeviceAccessControlList`，因此**重命名和拉黑都支持**（设备配置 `STATION_BLOCK_SUPPORT: true`）。

## 1. 端点

| 用途 | 请求 |
|---|---|
| 读 | `GET /goform/goform_get_cmd_process?cmd=<csv>&multi_data=1&isTest=false&_=<ms>` |
| 写 | `POST /goform/goform_set_cmd_process`，`application/x-www-form-urlencoded` |

端口优先 `80`，失败回退 `8080`。UFI-TOOLS 文档称官方 Web 后台跑在 `:8080`；u30mon 与 f50-monitor 使用 `:80`。App 用 `cmd=LD` 是否返回 JSON 来判断。

## 2. 必需请求头

```
X-Requested-With: XMLHttpRequest
Referer: http://<gateway>/
```

缺任意一个（或 Referer 不匹配）时，设备返回：

```json
{"Error": "none secure connection"}
```

这一点在 U30 Air 上由 u30mon 在 HTTP/HTTPS 下都验证过，App 的 `GoformTransportTest` 也覆盖了错误映射。

## 3. 登录

```
1. GET  ...?cmd=LD&multi_data=1&isTest=false     -> {"LD":"<salt>"}
2. H1 = UPPER(hex(SHA256(password)))
3. H  = UPPER(hex(SHA256(H1 + LD)))
4. POST goformId=LOGIN&user=admin&password=H&isTest=false
   -> {"result":0}   成功
   -> {"result":3}   密码错误
   -> {"result":5}   会话被占用
5. POST goformId=LOGOUT&isTest=false
```

- 大写 hex 是硬要求（UFI-AXIS `GoformClient.kt` 与 u30mon 一致）。
- `user=admin` 在 AirDeck / UFI-AXIS 中存在，u30mon 省略也能成功，因此按可选字段处理。
- 老 MiFi 固件使用 `Base64(password)`，没有盐。App 在 LD 方案失败后回退。
- 会话 cookie 是 `JSESSIONID`，`HttpOnly`，`Path=/goform`。

测试向量（`GoformHashTest`）：

```
password = "admin123", LD = "abc-123"
login    = A0832D697A3C516C4A90F1D6F60FFDD14BF5FEB2CF5ED006BD657A752FB8DF95
```

## 4. 写操作签名 AD

除 `LOGIN` / `LOGIN_MULTI_USER` / `LOGOUT` 外，所有写操作都需要：

```
AD = UPPER(hex(SHA256( UPPER(hex(SHA256(wa_inner_version + cr_version))) + RD )))
```

`RD` 需要已登录的会话。`AD` 必须放在 form body 最后。App 的做法：

```
isTest=false
goformId=<id>
<业务参数...>
AD=<token>
```

## 5. 字段（按验证状态）

图例：✅ 有源码/实测验证 · 🟡 同家族推断 · ❓ U30 Pro 未验证

### 信号

| 字段 | 含义 | 状态 |
|---|---|---|
| `network_type` | `ENDC` / `NR5G-SA` / `LTE` | ✅ U30 Air |
| `ppp_status` | `ipv4_ipv6_connected` / `ppp_disconnected` | ✅ U30 Air |
| `network_provider`, `network_provider_fullname` | 运营商 | ✅ AirDeck |
| `Z5g_rsrp` | 5G RSRP | ✅ U30 Pro B24 |
| `Z5g_snr` | 5G SINR（U30 Pro 原厂 UI 用这个名字） | ❓ B24 返回空 |
| `Z5g_SINR` | 5G SINR（Air 命名） | ❓ B24 返回空 |
| `Z5g_rsrq` | 5G RSRQ | ❓ B24 返回空 |
| `network_rssi` | 信号等级（0-5），**不是 dBm** | ✅ U30 Pro B24 |
| `network_signalbar` | 信号格数 | ✅ U30 Pro B24 |
| `network_lte_rsrp`, `lte_rsrq` | LTE 指标 | ✅ U30 Air |
| `nr_rsrp`, `nr_rsrq`, `Nr_snr` | NR 命名（F50 风格） | ✅ F50 / ❓ Pro |
| `Lte_snr` | 注意大写 `L`，UFI-AXIS 有守卫测试 | ✅ F50 |
| `nr5g_action_band`, `network_ZCELLINFO_band`, `wan_active_band` | 频段 | ✅ Air；U30 Pro B24 返回空 |
| `network_Z_PCI`, `network_Z5g_PCI` | PCI | ✅ Air；U30 Pro B24 返回空 |
| `network_Z5g_CELL_ID`, `network_cell_id` | 小区 ID | U30 Pro B24 返回空 |
| `wan_lte_ca` | CA 状态 | ✅ U30 Air |

App 的取值优先级：`Z5g_*` → `nr_*` → `network_lte_*` → `lte_*`；缺失显示 `—`。

**补充（2026-11-06 真机）**：`cmd=network_information` 返回的是**扁平对象**，不是嵌套在 `network_information` 键下：

```json
{"Nr_bands":"41","Nr_pci":"910","Nr_cell_id":"13166960702",
 "Nr_signal_strength":"-72","Nr_snr":"16","nr_rsrp":"-72","nr_rsrq":"-6", ...}
```

所以 App 在读取状态时单独请求 `cmd=network_information`，把整个响应序列化成字符串交给 `StatusParser`，用它补齐 SINR/RSRQ/频段/PCI/小区。直接请求 `Z5g_snr`、`Z5g_rsrq`、`network_ZCELLINFO_band`、`network_Z_PCI` 仍返回空字符串。

### 流量

`flux_realtime_{rx,tx}_thrpt`（B/s）、`flux_realtime_{rx,tx}_bytes`、`flux_realtime_time`、`flux_monthly_{rx,tx}_bytes` 为 U30 Air 命名（✅）。

F50/UFI 工具使用不带 `flux_` 前缀的 `realtime_*` / `monthly_*`（✅ 其他固件）。App 两套都读，优先 `flux_*`。

### 设备

`imei`, `imsi`, `iccid`, `msisdn`, `wan_ipaddr`, `lan_ipaddr`, `model_name`, `hardware_version`, `wa_inner_version`, `cr_version`, `wa_version`, `web_version`, `battery_vol_percent`, `battery_charging`, `sms_unread_num`, `wifi_access_sta_num`, `usb_port_switch` 等。字段名来自 U30 Air / UFI-AXIS 批次表（✅ 字段存在，❓ Pro 是否全部返回）。

### 温度

候选：`temperature`, `cpu_temp`, `internal_temperature`, `ic_temp`。

**没有任何项目在 U30 系列上通过 goform 验证过温度**：
- UFI-AXIS 从本地 thermal zone 读；
- AirDeck 用自己的 `/api/status`；
- UFI-TOOLS 从本地文件读 millidegree。

App 只在探测命中且数值合理（0 < t < 150 ℃）时显示；`0` 视为「传感器不可用」。

### 客户端列表

| 字段 | 形状 | 状态 |
|---|---|---|
| `station_list` | JSON array **或** JSON 编码字符串 | ✅ U30 Air 数组；✅ F50 字符串 |
| `lan_station_list` | 同上 | ✅ 其他固件 |
| 条目 key | `hostname`, `mac_addr`, `ip_addr` | ✅ AirDeck |
| 其他可能 key | `connect_time`, `ssid_index`, `addr_type`, `signal` | ❓ |
| `wifi_access_sta_num` | 数量 | ✅ U30 Air |
| `queryDeviceAccessControlList` | `{AclMode, BlackMacList, WhiteMacList, ...}`，MAC 用 `;` 分隔 | ✅ F50 / Air |
| `hostNameList` | `[{mac, hostname}]` | ✅ F50 / ❓ Pro |

`station_list` 与其它命令合并读取时，部分固件会返回空。App 先合并读，缺失时单独重试。

### 写命令（App 实际使用）

| goformId | 参数 | 状态 |
|---|---|---|
| `setDeviceAccessControlList` | `AclMode=2`, `WhiteMacList`, `BlackMacList`, `WhiteNameList`, `BlackNameList` | ✅ U30 Air / F50 |
| `EDIT_HOSTNAME` | `mac`, `hostname` | ✅ F50 / ❓ Pro |
| `REBOOT_DEVICE` | — | ✅（App 未暴露） |

没有「拉黑单个设备」的独立命令：必须读出完整 ACL，修改 `BlackMacList` 后用 `setDeviceAccessControlList` 整体写回。App 会保留白名单和名称列表。

## 6. 会话约束

- 设备只允许一个**登录**会话，但 U30 Pro B24 的**读取不需要登录**。实测：`LOGOUT` 之后不带 Cookie 请求状态、`station_list`、ACL 仍然成功。
- 因此 App 的轮询走匿名读取，不占用唯一会话，可以和原厂网页同时使用；只有拉黑/解黑/重命名需要 `LOGIN` + `RD` 生成 AD，写完立即 `LOGOUT`。
- `cmd=RD` 在 U30 Pro 上匿名也可用，**不能作为会话存活探针**。App 用 `sessionActive` 标志跟踪自己是否登录，写操作失败时再强制重登。
- 若某个固件拒绝匿名读取，App 会自动回退到「保持登录会话」模式，并继续使用让位策略。
- 检测到 `result=session` / `5` / HTML 响应 / `none secure connection` 时，写入路径进入让位状态并暂停 30 秒。
- 手机切到后台时立即停止轮询。

## 7. 固件差异与陷阱

1. 结果码不统一：老 MiFi 用 `3` 表示成功，U30 Air 用 `0`。App 只接受 `0` / `"0"` / `"success"`。
2. 函数可能被裁剪：U30 Pro AI 版固件上 `USB_PORT_SETTING` 返回失败（社区反馈）。所以写入失败必须回读校验。
3. 浏览器 CORS：原厂端点没有 `Access-Control-Allow-Origin`，`JSESSIONID` 还是 HttpOnly + `Path=/goform`。这是选择原生 App 而不是网页的直接原因。
4. 端口可能是 80 或 8080，取决于固件。
5. `multi_data=1` 多字段读取需要；只读单字段时可省略，App 统一携带。
6. 所有标量都是字符串，空字符串表示不可用；`station_list` 的双形状必须兼容。
7. U30 Pro 的 `queryDeviceAccessControlList` 返回扁平对象；`station_list` 才是嵌套在命令名下的。
8. U30 Pro B24 的信号指标只填 `Z5g_rsrp` / `network_rssi` / `network_signalbar`，SINR/RSRQ/频段/PCI 均为空字符串——原厂主页也只显示信号格，不显示这些数值。

## 8. 参考实现

| 项目 | 用途 |
|---|---|
| [u30mon](https://github.com/noname22/u30mon) | U30 Air 登录/字段实测 |
| [UFI-AXIS](https://github.com/Asunano/UFI-AXIS) | MIT；会话策略、字段分批、双形状解析 |
| [AirDeck](https://github.com/leehe-byte/AirDeck) | U30 Air 行为参考（无 license，仅参考，未复制代码） |
| [UFI-TOOLS API_Doc.md](https://github.com/kanoqwq/UFI-TOOLS/blob/http-server-version/API_Doc.md) | 写命令与端口整理 |
| [ZTE_API_and_Hack](https://github.com/paulo-correia/ZTE_API_and_Hack) | 老 MiFi goform 结果码 |

## 9. 真机首次验证清单

1. 电脑在设备 WiFi 上：`U30_PASSWORD=... node tools/probe.mjs`；不在的话用 App「诊断 → 导出 JSON」+ `adb pull`。
2. 记录 `login.scheme` 与 `port`，确认 App 探测一致。
3. 对比 App 与原厂网页：网络类型、RSRP/SINR、本月流量、在线设备数。
4. 打开原厂网页，确认 App 出现「让位中」而不是互踢死循环。
5. 拉黑一台测试设备 → 掉线；解黑 → 可重连；原厂 ACL 页面同步变化。
6. 观察 `diagnostics → 原始字段`，把缺失但原厂网页有值的字段名补进 `GoformFields`。
7. 导出的 `status.raw` 建议整份留存：它包含 App 尚未解析的固件字段。

## 10. 设备管理命令（App 已实现）

所有命令都在 `POST /goform/goform_set_cmd_process`，由 App 的 `withWrite` 自动完成 `LOGIN → RD → AD → 写入 → LOGOUT`，并在写入后回读校验。参数名从本机原厂页面提取。

| 功能 | goformId | 关键参数 | 读值字段 | 验证状态 |
|---|---|---|---|---|
| 网络模式 | `SET_BEARER_PREFERENCE` | `BearerPreference=NETWORK_auto/Only_5G/Only_LTE` | `network_net_select_mode` | 未写入 |
| 漫游 | `SET_CONNECTION_MODE` | `ConnectionMode`, `dial_roam_setting_option=on/off` | `cmd=ConnectionMode` → `connectionMode`,`autoConnectWhenRoaming` | 未写入 |
| 数据连接 | `CONNECT_NETWORK` / `DISCONNECT_NETWORK` | — | `ppp_status` | 未写入 |
| 解除锁定 | `UNLOCK_ALL_CELL` | — | `locked_cell_info` | 未写入 |
| WiFi 开关 | `switchWiFiModule` | `SwitchOption=0/1` | `queryWiFiModuleSwitch` → `WiFiModuleSwitch` | 未写入 |
| WiFi SSID/密码/隐藏/最大连接数/AP 隔离 | `setAccessPointInfo` | `ChipIndex`, `AccessPointIndex`, `SSID`, `AuthMode`, `EncrypType`, `Password`(Base64), `ApBroadcastDisabled`, `ApIsolate`, `ApMaxStationNumber`, `AccessPointSwitchStatus`, `wifi_syncparas_flag` | `queryAccessPointInfo` → `ResponseList[]` | 未写入 |
| 指示灯 | `INDICATOR_LIGHT_SETTING` | `indicator_light_switch=0/1` | `indicator_light_switch` | ✅ 真机 `1→0→1` |
| USB 端口 | `USB_PORT_SETTING` | `usb_port_switch=0/1` | `usb_port_switch` | 未写入 |
| USB 协议 | `SET_USB_NETWORK_PROTOCAL` | `usb_network_protocal=0/1/2`（自动/RNDIS/CDC-ECM） | `usb_network_protocal` | 未写入 |
| Samba | `SAMBA_SETTING` | `samba_switch`, `samba_password` | `samba_switch,samba_username` | 未写入 |
| 性能模式 | `PERFORMANCE_MODE_SETTING` | `performance_mode=0/1` | `performance_mode` | 未写入 |
| 电池保护 | `BATTERY_PROTECTION_SETTING` | `battery_protection=0/1` | `battery_protection` | 未写入 |
| SIM 槽 | `SET_SIM_SLOT` | `sim_slot=0/1` | `sim_slot`, `dual_sim_support` | 未写入 |
| 流量限额 | `DATA_LIMIT_SETTING` | `flux_data_volume_limit_switch/unit/size`, `flux_data_volume_alert_percent`, `flux_auto_clear_flow_data_switch`, `flux_clear_date`, `flux_limited_disconnect` | 同名 `flux_*` 字段 | 未写入 |
| 流量清零 | `RESET_DATA_COUNTER` | — | `flux_*` | 未写入 |
| APN | `APN_PROC_EX` | `apn_action=save/set_default/delete`, `index`, `profile_name`, `apn_wan_apn`, `apn_pdp_type`, `apn_ppp_auth_mode`, `apn_ppp_username`, `apn_ppp_passwd`, `apn_wan_dial` | `APN_config0..19`（`($)` 分隔） | 未写入 |
| 短信发送 | `SEND_SMS` | `Number`, `sms_time`, `MessageBody`(UTF-16 hex), `encode_type=GSM7_default/UNICODE` | `sms_status_rpt_data` | 未发送 |
| 短信删除/已读 | `DELETE_SMS` / `SET_MSG_READ` | `msg_id="id;id;"` | `sms_status_rpt_data` | 未写入 |
| OTA 检查 | `IF_UPGRADE` | `select_op=check`, `ota_manual_check_roam_state=1` | `new_version_state`, `dm_new_version` | 未执行 |
| 重启 | `REBOOT_DEVICE` | — | — | 未执行 |

**读取字段的注意点**：

- `queryAccessPointInfo` 返回 `ResponseList[]`，每个芯片一项，包含 `SSID/AuthMode/Password(Base64)/ApBroadcastDisabled/ApMaxStationNumber`；这是 WiFi 编辑的数据来源。
- `APN_configN` 的格式是 `name($)apn($)mode($)dial($)auth($)username($)password($)pdp`。U30 Pro 的 `APN_config0` 常是 `Default($)($)...`（APN 为空），App 会跳过空 APN，改用 `apn_auto_config`/`apn_wan_apn` 展示当前自动配置。
- 读取时用 `null` 表示「设备没返回该键」，`""` 表示「返回了空值」；管理页按此隐藏不支持项。
- 写失败或回读不一致会直接展示；`USB_PORT_SETTING` 在部分 U30 固件上返回成功但值不变，必须依赖回读。