# 站点配置指南（SITES_CONFIG）

本 App 是通用可配置签到器。所有签到逻辑都由「站点配置 JSON」驱动，无需改代码即可新增/调整任何站点的签到请求。

## 1. 站点配置 JSON 结构

```json
{
  "id": "jinman",
  "name": "禁漫天堂",
  "enabled": true,
  "builtin": true,
  "description": "站点说明，仅用于展示",
  "checkin": {
    "url": "https://example.com/api/checkin?lang=zh",
    "method": "POST",
    "headers": {
      "Cookie": "{{token}}",
      "User-Agent": "okhttp/4.9.2",
      "Content-Type": "application/json"
    },
    "body": "{}",
    "success": {
      "type": "contains",
      "value": "success"
    },
    "alreadySigned": ["已签到", "already"]
  }
}
```

### 字段说明

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `id` | 是 | 站点唯一标识（小写英文，如 `jinman`、`noyacg`），用于区分站点与关联账号 |
| `name` | 是 | 站点显示名称 |
| `enabled` | 否 | 是否启用该站点，默认 `true` |
| `builtin` | 否 | 是否内置站点。内置站点被编辑后以「覆盖」形式保存，可一键恢复默认 |
| `description` | 否 | 描述文字 |
| `checkin.url` | 是 | 签到接口完整 URL（含 query 参数） |
| `checkin.method` | 否 | `GET` / `POST` / `PUT`，默认 `GET` |
| `checkin.headers` | 否 | 请求头 map。`{{token}}` 会被替换为账号凭证 |
| `checkin.body` | 否 | 请求体（POST/PUT 时发送），同样支持 `{{token}}` |
| `checkin.success` | 否 | 成功判定规则，见下 |
| `checkin.alreadySigned` | 否 | 关键字数组：响应包含其中任意一个即视为「今日已签到」（同样算成功，日志标记重复签到） |

### success 判定规则

| type | 说明 | 示例 |
| --- | --- | --- |
| `contains`（默认） | 响应文本包含指定关键字即成功 | `{"type":"contains","value":"success"}` |
| `json` | 响应 JSON 中指定路径字段等于期望值即成功 | `{"type":"json","path":"code","value":"0"}` |
| `json_not` | 响应 JSON 中指定路径字段**不等于**期望值即成功 | `{"type":"json_not","path":"code","value":"1"}` |
| `none` | 只要 HTTP 2xx 就算成功 | `{"type":"none"}` |

`json` 的 `path` 支持点号路径与数组下标，例如 `data.day`、`data.list.0.status`。

## 2. 占位符

签到请求中的 URL、headers、body 支持以下占位符，发送时会按账号替换：

| 占位符 | 替换为 |
| --- | --- |
| `{{token}}` | 该账号填写的 Token / Cookie / 登录凭证 |
| `{{accountName}}` | 该账号名称 |

典型用法：站点配置里写 `"Cookie": "{{token}}"` 或 `"Authorization": "{{token}}"`，然后在「账号」页为每个账号填入各自凭证。

## 3. 内置站点与自定义站点

- **内置站点**（`app/src/main/assets/sites.json`）：禁漫天堂、NoyAcg 的**占位模板**。直接改 `assets/sites.json` 并重新编译可改默认值；在 App 内编辑内置站点会保存为「覆盖」，App 内提供「恢复默认」按钮还原。
- **自定义站点**：App「站点」页右下角 `+` 新增，粘贴上面的 JSON 结构即可。自定义站点直接保存在 App 本地。

## 4. 已抓到的真实接口怎么迁移到 assets

1. 先在 App 里把内置站点改成真实接口并测试通过；
2. 再把对应 JSON 复制回 `app/src/main/assets/sites.json` 的对应项，重新编译后所有用户开箱即用。

## 5. 重要提醒

- `assets/sites.json` 里是**占位模板**，URL/Headers 均为示例，必须按 `docs/PACKET_CAPTURE_GUIDE.md` 抓包后用真实值替换；
- 接口参数（尤其签名类参数）会变化，配置失效时日志会显示失败，请重新抓包更新。
