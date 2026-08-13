# 接口与数据模型

## SiteConfig

`SiteConfig` 定义站点的签到 URL、HTTP 方法、请求头、请求体、成功规则和已签到规则。`CheckinEngine` 在执行前将 `{{token}}` 替换为账号凭据。

## Account

`Account` 保存账号 ID、关联站点 ID、显示名称、凭据和启用状态。捕获导入确认后，固定请求内容保存为自定义站点，用户提供的会话凭据保存为账号。

## CaptureImporter

`CaptureImporter.parse(bytes, filename)` 支持 HAR、cURL、Postman Collection、Insomnia Export、OpenAPI JSON、PCAP 和 PCAPNG，输出 `CaptureParseResult`。

`CaptureImporter.classify(requests)` 以主域名分组，并按 URL、请求体和响应中的签到语义排序。`Authorization`、`Cookie`、`token`、`secret`、`password`、`session` 等字段在预览中显示为掩码。

PCAP 与 PCAPNG 仅提取文件中可见的 HTTP 会话；HTTPS 数据需在源工具完成 TLS 解密后导出。

小黑盒域名会显示为“小黑盒”，并根据请求中的签到、奖励、抽奖或任务语义保存为 `xiaoheihe-checkin`、`xiaoheihe-reward`、`xiaoheihe-lottery` 或 `xiaoheihe-task` 站点配置。首页为四类配置提供独立执行开关。
