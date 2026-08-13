# Requirements Document

## Introduction

本功能按米游社功能约定增加小黑盒平台接入。功能支持用户本人通过官方页面获得会话，导入小黑盒 HAR/cURL 请求，识别签到、每日任务、奖励和活动状态请求，并通过独立平台开关纳入自动执行与结果日志。

## Requirements

### Requirement 1: 平台接入

1. WHEN 用户导入包含小黑盒域名的 HAR、cURL 或其他支持格式，系统 SHALL 将请求分组为小黑盒平台。
2. WHEN 系统展示小黑盒请求，系统 SHALL 显示域名、用途、方法、URL、匹配依据和脱敏凭据。
3. WHEN 用户通过官方网页登录或导入官方会话，系统 SHALL 允许用户确认并保存小黑盒账号。
4. IF 会话失效或需要人工验证，系统 SHALL 跳过当前请求并标记异常。

### Requirement 2: 自动执行和开关

1. WHEN 用户开启小黑盒签到开关，系统 SHALL 自动执行已确认的小黑盒签到请求。
2. WHEN 用户开启小黑盒每日任务开关，系统 SHALL 自动执行已确认且可识别的每日任务请求。
3. WHEN 用户关闭对应开关，系统 SHALL 将对应请求标记为未执行。
4. WHEN 所有请求完成，系统 SHALL 汇总正常完成、异常和未执行三类状态。
5. WHEN 用户更新会话或任务信息，系统 SHALL 提供选择性重试异常任务或取消重试。

### Requirement 3: 日志与安全

1. WHEN 小黑盒请求执行结束，系统 SHALL 记录平台、账号、任务名称、状态、时间和脱敏响应摘要。
2. WHEN 系统展示 Cookie、Authorization、Token、密码或会话字段，系统 SHALL 使用掩码。
3. WHEN 用户删除小黑盒账号，系统 SHALL 清理关联配置和执行入口。
