# 数据源调研报告（v3 · 2026-05-13 更新）

> **2026-05-13 决策变更**：项目实际采用 **football-data.org**，不再使用 api-football.com。
> 原因：api-football dashboard 找 token 的流程对当前用户不顺畅；用户主动决定接受覆盖范围收窄，换取更简单的注册流程（注册后 token 直接邮件发送）。
>
> 代价（已知并接受）：
> - 中国队、巴西队、阿根廷队的比赛 → 拉不到
> - 国家队友谊赛、欧洲俱乐部杯赛（足总杯/国王杯等）→ 拉不到
> - 仍保留：五大联赛、欧冠、世界杯、欧洲杯
>
> 下文 v2 报告保留作历史参考，"最终选择"段落应理解为"曾考虑选择"。
> 新接入要点：
>   - 注册：https://www.football-data.org/client/register （邮件收 token）
>   - baseUrl：`https://api.football-data.org/`
>   - Header：`X-Auth-Token: <token>`
>   - 限速：10 req/min，无日上限
>   - 核心端点：`GET /v4/teams/{id}/matches?dateFrom=YYYY-MM-DD&dateTo=YYYY-MM-DD`

---

# v2 报告（保留作历史参考）

## 一、为什么换数据源

原方案选用 football-data.org，其免费档**只覆盖五大联赛+欧冠+世界杯+欧洲杯**。

但现在用户关注列表加入了：
- **中国队**：国家队主要比赛是亚预赛、亚洲杯——**football-data.org 完全不覆盖**
- **巴西队、阿根廷队**：南美洲赛事 (CONMEBOL) **未覆盖**
- **国家队友谊赛**：football-data.org 一律不提供
- **欧洲俱乐部的杯赛**（足总杯、国王杯、德国杯等）：未覆盖

→ 必须切换数据源。

---

## 二、最终选择：api-football.com

| 评估维度 | api-football.com | football-data.org | 抓取虎扑/懂球帝 |
|---------|------------------|-------------------|----------------|
| 免费额度 | **100 req/天** | 10 req/min（无日上限） | 无 |
| 国家队全覆盖 | **✓** 含中国队、含友谊赛 | 仅世界杯+欧洲杯 | 部分 |
| 联赛覆盖 | **1100+ 联赛/杯赛** | 12 个 | 主要联赛 |
| 杯赛覆盖 | 足总杯/国王杯/德国杯等全有 | 无 | 部分 |
| 中国大陆直连 | **✓** | ✓ | ✓ |
| 数据延迟 | < 30 分钟 | < 1 小时 | 人工抄录 |
| API 文档质量 | 详尽，附带 try-it | 简洁 | 无 |
| 稳定性 | 2018 年至今，付费用户多 | 2013 年至今，纯公益 | 反爬随时变 |
| 商业使用 | 免费档允许 | 免费档允许 | 灰色 |

**配额测算**：
- 17 队 × 1 次/周 = 17 req/周
- 加上首次启动时校验球队 = 一次性 17 req
- 节假日表 = 1 req/年
- **每天平均 < 5 req，远低于 100/天上限**

---

## 三、注册与 API Key

### 方法 A：直接从 api-football.com 注册（推荐）

1. 访问 https://dashboard.api-football.com/register
2. 邮箱+密码注册，无需信用卡
3. 登录后到 `My Account` 拿到 API Key（一串 32 位字符）
4. 接口地址：`https://v3.football.api-sports.io`
5. 请求时在 Header 加：`x-apisports-key: <你的 Key>`

### 方法 B：通过 RapidAPI 注册（备选）

走 RapidAPI 平台，接口地址变为 `https://api-football-v1.p.rapidapi.com/v3`，
Header 改为 `x-rapidapi-key` 和 `x-rapidapi-host`。
**不推荐**——多一层中转，限速更易触发。

### Token 配置

`local.properties`（不提交 git）：
```
API_FOOTBALL_KEY=你的key
```

`app/build.gradle.kts`：
```kotlin
android {
    defaultConfig {
        val props = Properties().apply {
            load(rootProject.file("local.properties").inputStream())
        }
        buildConfigField(
            "String",
            "API_FOOTBALL_KEY",
            "\"${props["API_FOOTBALL_KEY"]}\""
        )
    }
}
```

---

## 四、关键 API 端点

### 4.1 拉取指定球队的未来比赛（核心调用）

```
GET https://v3.football.api-sports.io/fixtures
    ?team={teamId}
    &from=2026-05-09
    &to=2026-05-16
    &timezone=Asia/Shanghai

Header: x-apisports-key: <你的key>
```

注意点：
- **不需要传 season**（按日期范围查会自动跨赛季）
- **timezone 直接用 Asia/Shanghai**——返回的时间字段已经是北京时间，省去客户端转换
- 一次最多返回该球队的所有比赛，无需翻页

返回示例（节选）：
```json
{
  "errors": [],
  "results": 1,
  "response": [{
    "fixture": {
      "id": 868872,
      "date": "2026-05-09T22:00:00+08:00",
      "timestamp": 1747318800,
      "status": { "long": "Not Started", "short": "NS" }
    },
    "league": {
      "id": 39,
      "name": "Premier League",
      "country": "England",
      "season": 2025,
      "round": "Regular Season - 37"
    },
    "teams": {
      "home": { "id": 40, "name": "Liverpool", "logo": "...", "winner": null },
      "away": { "id": 33, "name": "Manchester United", "logo": "...", "winner": null }
    },
    "goals": { "home": null, "away": null }
  }]
}
```

### 4.2 比赛类型识别（用于过滤）

`league.id` 决定比赛性质，关键 ID：

| 联赛/杯赛 | league.id | 类型 |
|----------|-----------|------|
| 英超 | 39 | OFFICIAL_CUP |
| 西甲 | 140 | OFFICIAL_CUP |
| 德甲 | 78 | OFFICIAL_CUP |
| 法甲 | 61 | OFFICIAL_CUP |
| 欧冠 | 2 | OFFICIAL_CUP |
| 欧联 | 3 | OFFICIAL_CUP |
| 足总杯 | 45 | OFFICIAL_CUP |
| 国王杯 | 143 | OFFICIAL_CUP |
| 德国杯 | 81 | OFFICIAL_CUP |
| 世界杯 | 1 | OFFICIAL_CUP |
| 欧洲杯 | 4 | OFFICIAL_CUP |
| 美洲杯 | 9 | OFFICIAL_CUP |
| 亚洲杯 | 17 | OFFICIAL_CUP |
| 世预赛-亚 | 33 | QUALIFIERS |
| 世预赛-欧 | 32 | QUALIFIERS |
| 世预赛-南美 | 34 | QUALIFIERS |
| 欧预赛 | 960 | QUALIFIERS |
| 国际友谊赛 | 10 | FRIENDLY |
| 俱乐部友谊赛 | 667 | FRIENDLY |

代码里用 Set 维护这三类的 league.id 集合，匹配后给 match 打标签。
若 league.id 不在任何已知集合中，默认归为 `OFFICIAL_CUP`（保守策略，宁可多通知也不漏）。

### 4.3 配额管理

每两次请求之间 sleep 1 秒（api-football 的限速是 10 req/秒，没必要等 7 秒）：
```kotlin
followedTeams.forEach { team ->
    fetchTeamFixtures(team.id)
    delay(1000)
}
```

### 4.4 错误处理

| HTTP 状态 | 含义 | 处理 |
|----------|------|------|
| 200 | 成功，但需检查 `errors` 数组 | 解析 `response` |
| 401 | Key 错误 | 提示用户检查 |
| 429 | 当日配额耗尽 | 等到次日 UTC 0 点（北京 8 点）重试 |
| 499 | 客户端超时 | 重试 1 次 |
| 5xx | 服务端故障 | 30 分钟后重试 |

`errors` 数组示例（200 状态码下也可能有警告）：
```json
{ "errors": { "rateLimit": "Too many requests..." } }
```
代码必须每次都检查这个字段。

---

## 五、数据准确性保障

api-football.com 数据来自多家官方+体育数据公司，更新频率较高。仍可能遇到的问题：

1. **比赛改期** → 增加"赛前 4 小时小校验"机制（已在原设计 §4 提及）
2. **中国队赛事临时取消**（如外交因素） → 状态会变 `CANC` (Canceled) 或 `PST` (Postponed)，代码需处理这两种状态
3. **国家队集训名单变化** → 不影响赛程数据本身

---

## 六、备用数据源（plan B）

若 api-football.com 出现长时间故障：

1. **football-data.org**：欧洲俱乐部部分仍可用（10 req/min 无日上限）
2. **抓取**：兜底站点
   - 中国队赛程：足协官网 https://www.thecfa.cn/
   - 国家队赛程：FIFA 官网 https://www.fifa.com/
   - 俱乐部赛程：https://www.dongqiudi.com/data/team/{id}

代码做接口隔离（`MatchDataSource` interface），切换实现类即可。

---

## 七、合规与免责

- api-football.com 免费档允许个人用途和小型商业项目
- 不要把 API Key 公开在 GitHub 公共仓库（用 `.gitignore` 忽略 `local.properties`）
- App 内"关于"页注明：`Match data provided by API-Football (api-football.com)`
- 队徽图片缓存到本地，避免每次请求都打他们 CDN

---

## 八、参考链接

- 官网：https://www.api-football.com/
- 文档：https://www.api-football.com/documentation-v3
- Dashboard：https://dashboard.api-football.com/
- 价格说明：https://www.api-football.com/pricing
- 球队 ID 检索：`GET /v3/teams?search={teamName}` 或 `?country={country}`
