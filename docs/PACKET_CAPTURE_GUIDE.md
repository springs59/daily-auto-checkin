# 抓包教学文档（以禁漫天堂、NoyAcg 为例）

本 App 是**通用可配置签到器**：签到请求完全由「站点配置 JSON」描述，接口地址、请求头、请求体都是可配置的。你只需要**抓包一次**拿到真实的签到请求，把它填进 App 的站点配置里，App 就能每天自动帮你发送。

> 前置说明：不同 App / 版本 / 域名的签到接口路径和参数各不相同，且可能随时变更。所以本项目**不写死任何站点接口**，而是提供通用引擎 + 抓包方法。下面教你从零抓出「禁漫天堂」和「NoyAcg」的签到请求。

---

## 1. 准备工具（三选一）

| 工具 | 适用场景 | 获取方式 |
| --- | --- | --- |
| **抓包 App（手机本地）** | 不依赖电脑，最常用 | Reqable / HttpCanary / 小黄鸟(HttpCanary) / Packet Capture，网上可直接搜到 APK |
| **Charles + 电脑代理** | 精确、可重放、可改包 | https://www.charlesproxy.com/ |
| **mitmproxy + 电脑代理** | 免费、命令行 | `pip install mitmproxy` |

下面以「手机本地抓包 App」为例（免电脑），Charles 的原理完全一样，只是把手机流量代理到电脑。

---

## 2. 通用抓包步骤（禁漫 / NoyAcg 通用）

1. **安装抓包工具**，如 HttpCanary（小黄鸟）。首次打开它会申请「创建 VPN」权限，**必须允许**——它就是用本地 VPN 实现无 root 抓包。
2. **安装它的 CA 证书**（用于解密 HTTPS）：
   - 打开 HttpCanary → 侧边栏「设置」→「SSL 证书设置」→「安装证书」→ 按提示下载证书文件并手动安装到系统「CA 证书」；
   - 各 App 提示的「信任用户凭据」步骤照做即可。
3. **回到抓包工具首页**，点击右下角「开始抓包」，状态栏会出现 VPN 图标。
4. **打开目标 App（禁漫 / NoyAcg）**：
   - 先登录自己的账号（保证已登录状态）；
   - 进入「每日签到 / 签到」页面；
   - **点击一次「签到」按钮**。
5. **停止抓包**，在抓包工具里按域名/时间排序，找签到那一下产生的请求：
   - 特征：**紧跟在点击签到之后**、方法多为 `POST`、URL 里常带 `sign`、`checkin`、`signin`、`attendance` 等关键字；
   - 如果找不到，就搜 `POST` 请求，逐个看响应体——签到成功的响应里会出现「签到成功」「已签到」「连续签到 x 天」「积分 +xx」等字样，**以响应内容为准反推请求**。
6. **把这个请求完整抄下来**，包括：
   - **URL**（含 query 参数）
   - **Method**（GET/POST/PUT）
   - **Request Headers**（重点是 `Cookie`、`Authorization`、`token`、`User-Agent`、`Content-Type`）
   - **Request Body**（POST/PUT 的原始 body，通常是一段 JSON）

抄下来的内容就是要填进「站点配置 JSON」的东西。

---

## 3. 拿到请求后怎么填

站点配置 JSON 的字段说明见 `docs/SITES_CONFIG.md`。核心逻辑：

- URL、请求头、请求体里凡是**属于某个账号自己的凭证**（`Cookie` 值、`Authorization` token、`token` 参数），一律写成占位符 `{{token}}`；
- 站点配置里只保留所有账号**共用**的固定头（如 `User-Agent`、`Content-Type`）；
- 每个账号在「账号」页填入自己的真实凭证，App 发送时会把 `{{token}}` 替换成该账号的凭证。

**示例**：抓到禁漫签到的请求是

```
POST https://18comic.vip/api/checkin?lang=zh
Cookie: _ga=xxxx; rememberme=xxxx; token=abc123
User-Agent: okhttp/4.9.2
Content-Type: application/json

{}
```

那么站点配置应写成：

```json
{
  "id": "jinman",
  "name": "禁漫天堂",
  "enabled": true,
  "builtin": true,
  "description": "禁漫天堂每日签到",
  "checkin": {
    "url": "https://18comic.vip/api/checkin?lang=zh",
    "method": "POST",
    "headers": {
      "Cookie": "{{token}}",
      "User-Agent": "okhttp/4.9.2",
      "Content-Type": "application/json"
    },
    "body": "{}",
    "success": { "type": "contains", "value": "success" },
    "alreadySigned": ["已签到", "already"]
  }
}
```

然后在「账号」页添加账号，token 字段粘贴 `_ga=xxxx; rememberme=xxxx; token=abc123`（即你抓到的 Cookie 完整值）。

---

## 4. 禁漫天堂（18comic）抓包要点

- 域名为**动态/多线路**（`18comic.vip`、`18comic.ink` 等），抓包时**以实际抓到的域名和请求为准**，不要把 web 页面的域名硬套到 App 接口上；
- App 接口通常带 `token`、`device` 等参数，直接照抄；
- 签到成功后服务端返回里一般含「已签到」或连续签到天数，把成功关键字填进 `success.value` 或 `alreadySigned` 数组；
- **签到接口的成功判定以真实响应为准**，宁可先在「立即签到」里试一次、再在日志页看结果。

## 5. NoyAcg 抓包要点

- 包名 `asia.noy.th`，接口域名请在抓包里直接看；
- 登录态一般通过请求头 `Authorization` / `token` 或 `Cookie` 传递，抓包后**逐头对照**，把属于账号的凭证挪到 `{{token}}`；
- 签到请求可能与「签到中心」页面相关，点一次签到后看响应里「签到成功 / 已领取」关键字即可定位。

---

## 6. 常见问题

### Q1：抓不到 HTTPS 明文？
多半是 CA 证书没装 / 没信任。用电脑端 Charles 时，记得 `Proxy → SSL Proxying Settings` 里勾选你要解密的域名。

### Q2：接口要计算签名（signature）怎么办？
如果请求里含 `sign`、`ts`、`nonce` 这类**每次动态计算**的参数，本 App 的静态配置无法自动生成。这种情况该站点签到的自动化成功率低，可以：
- 改走该站 Web 端签到（往往无签名）；
- 或跳过该站，只签其他可配置的站点。

### Q3：凭证过期怎么办？
Cookie/token 一般几天到几周有效，失效后 App 日志会显示失败，去目标 App 重新登录后再抓一次，更新账号 token 即可。

### Q4：同一个账号会不会重复签到？
不会。App 内置「当日去重」：同一天同一账号已签过就自动跳过，定时触发与手动触发共用该去重（手动「立即签到」会忽略去重，强制执行一次）。

---

## 7. 安全与合规提醒

- 本工具仅供**自己的账号**做个人定时签到使用，请勿用于批量注册、刷量、伪造互动等任何灰产用途；
- 抓包只在你自己的设备、自己的账号上进行；
- 目标站点接口随时可能变更，本项目不保证任何特定站点长期可用。
