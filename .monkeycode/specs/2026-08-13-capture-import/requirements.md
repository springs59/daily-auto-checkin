# Requirements Document

## Introduction

“抓包导入配置”让用户从已获得授权的网络请求导出文件中选择签到请求，生成可编辑的站点与账号配置草稿。功能面向 Android 本地文件导入，不采集设备网络流量。

## Glossary

- **捕获文件**: 包含已记录 HTTP 请求或响应的数据文件。
- **候选请求**: 解析器从捕获文件中提取，供用户选择的单条 HTTP 请求。
- **签到草稿**: 由候选请求生成且尚未保存的站点和账号配置。
- **平台**: 由请求所属主域名、路径或导出工具元数据识别出的服务来源。
- **可读 HTTPS 内容**: 已在导出前由浏览器开发者工具、代理工具或抓包工具完成 TLS 解密的 HTTP 数据。

## Requirements

### Requirement 1: 导入入口和格式说明

**User Story:** 作为用户，我希望在导入页面看到支持的文件格式和准备方法，以便生成可用的签到草稿。

#### Acceptance Criteria

1. WHEN 用户打开捕获文件导入页面，系统 SHALL 显示支持的 HAR、HAR JSON、cURL 文本、Postman Collection JSON、Insomnia Export JSON、OpenAPI JSON、PCAP 和 PCAPNG 格式。
2. WHEN 用户查看 PCAP 或 PCAPNG 说明，系统 SHALL 显示 HTTPS 请求需要在源工具中完成 TLS 解密后再导出，并说明加密原始包仅包含连接元数据。
3. WHEN 用户查看浏览器导出说明，系统 SHALL 指引用户从网络面板导出 HAR 或复制单条请求为 cURL。
4. WHEN 用户查看代理工具导出说明，系统 SHALL 指引用户在授权范围内使用已解密的 HTTP 会话导出 HAR、cURL 或原始捕获文件。
5. WHEN 用户选择文件，系统 SHALL 显示支持格式对应的文件类型筛选条件。

### Requirement 2: 文件识别和解析

**User Story:** 作为用户，我希望系统自动识别导入文件格式并读取请求，以便减少手工转录。

#### Acceptance Criteria

1. WHEN 用户选择捕获文件，系统 SHALL 结合文件签名、扩展名和内容结构选择解析器。
2. WHEN 输入文件为 HAR 或 HAR JSON，系统 SHALL 读取每条 entry 的请求 URL、方法、请求头、Cookie、查询参数、请求体、响应状态和响应体。
3. WHEN 输入文件为 cURL 文本，系统 SHALL 读取 URL、方法、请求头、Cookie 和请求体。
4. WHEN 输入文件为 Postman Collection、Insomnia Export 或 OpenAPI JSON，系统 SHALL 将可还原为 HTTP 请求的条目转换为候选请求。
5. WHEN 输入文件为 PCAP 或 PCAPNG，系统 SHALL 读取可见 HTTP 会话中的请求 URL、方法、请求头和请求体，并显示无法解析的会话数量。
6. IF 输入内容缺少可读 HTTP 请求，系统 SHALL 显示原因和对应的导出准备说明。
7. WHEN 解析完成，系统 SHALL 显示解析器名称、候选请求数量和未解析项目数量。

### Requirement 3: 平台识别和请求筛选

**User Story:** 作为用户，我希望系统识别候选请求所属平台并推荐签到请求，以便快速选择正确配置。

#### Acceptance Criteria

1. WHEN 系统生成候选请求，系统 SHALL 使用主域名、URL 路径、HTTP 方法、请求体字段、响应特征和导出工具元数据计算平台候选项。
2. WHEN 候选请求属于同一注册域名，系统 SHALL 将候选请求归入同一平台分组。
3. WHEN 平台识别结果存在多个候选项，系统 SHALL 展示候选平台名称、匹配依据和置信等级。
4. WHEN 系统发现包含 sign、checkin、attendance、daily、reward 或任务完成语义的路径、参数或响应内容，系统 SHALL 提高对应请求的签到推荐等级。
5. WHEN 用户选择平台分组，系统 SHALL 展示该分组内的候选请求及 URL、方法、状态码、推荐等级和敏感字段掩码。
6. WHEN 用户选择候选请求，系统 SHALL 允许用户编辑站点名称、请求 URL、方法、请求头、请求体、成功规则和账号凭据后保存。

### Requirement 4: 凭据处理和配置生成

**User Story:** 作为用户，我希望安全地将已选择请求转换为签到配置，以便应用能执行签到。

#### Acceptance Criteria

1. WHEN 系统展示候选请求，系统 SHALL 对 Authorization、Cookie、Set-Cookie、token、secret、password 和 session 名称匹配的字段显示掩码。
2. WHEN 用户进入编辑草稿，系统 SHALL 提供查看和修改凭据字段的显式操作。
3. WHEN 用户确认保存草稿，系统 SHALL 将固定请求信息保存为自定义站点配置，并将用户选定凭据保存为对应账号。
4. WHEN 请求头或请求体包含账号凭据，系统 SHALL 使用应用现有的 `{{token}}` 模板变量替换保存到站点配置的凭据值。
5. WHEN 响应体可用，系统 SHALL 允许用户从响应文本或 JSON 字段配置成功和重复签到判断规则。
6. IF 用户未选择候选请求或站点名称为空，系统 SHALL 显示字段校验信息并保留编辑内容。

### Requirement 5: 导入结果和可追溯性

**User Story:** 作为用户，我希望了解导入结论和人工确认范围，以便判断配置是否可靠。

#### Acceptance Criteria

1. WHEN 系统完成解析，系统 SHALL 显示文件格式、解析时间、候选请求数量、平台分组数量和解析警告。
2. WHEN 系统完成平台识别，系统 SHALL 显示每个推荐的匹配依据。
3. WHEN 用户保存签到草稿，系统 SHALL 显示保存的站点名称和账号名称。
4. WHEN 系统无法从响应中确定成功规则，系统 SHALL 创建待编辑的成功规则并提示用户填写匹配条件。
