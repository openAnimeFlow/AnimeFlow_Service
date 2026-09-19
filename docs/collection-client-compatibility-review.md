# 收藏重构：旧客户端接口兼容性审查

审查日期：2026-09-19。范围：服务端 P1 提交、旧接口删除提交，以及当前未提交的 P2/P3 修复；对照客户端历史源码、标签和当前工作区。本文只记录核验结果与待办，不代表已经实现兼容适配。

## 1. 结论

**当前服务端不能视为完全兼容旧客户端。**

- 同步路径在客户端 7 月 2 日的提交中已迁移。删除 AccountController 旧路径不影响已经包含该迁移的客户端，但迁移前客户端仍会失去同步入口。
- 主要硬性不兼容是收藏 ID 可空：P1 之前客户端收到 `interest.id=null` 会发生类型解析错误，影响列表和详情。
- P1 客户端虽支持可空 ID 和单条保存状态，仍不支持 P2/P3 任务新状态与人工冲突处理。
- 收藏更新从“远端成功后返回成功”变成“本地保存成功即可返回业务成功”。旧客户端可解析响应，但可能误认为 Bangumi 已同步。
- 当前没有按客户端版本/能力分流、未知状态兼容映射、最低版本拦截等实现。不能把本文建议当作现有保护。

“已绑定用户安全”不成立：已绑定但远端上传失败，或者用户在新设备创建本地收藏后再使用旧设备，同样可能读到空远端 ID。

## 2. Git 证据与版本边界

| 仓库 / 基线 | 已核验事实 |
| --- | --- |
| 客户端 `89b62369` | 引入 Bangumi 登录与收藏同步功能，是早期旧路径的历史来源。 |
| 客户端 `be80560021f24f4e2df2556f2ffaa61925c68611`，2026-07-02 | `lib/http/api_path.dart` 将同步路径从 `/api/v1/account/oauth/bangumi/collections/sync` 改为 `/api/v1/users/collections/sync`。文件后来迁移到 `lib/core/network/api_path.dart`，不能仅搜索现有文件历史。 |
| 服务端 `69254b1`、`0a499a1`，2026-07-11 | 增加 CollectionController，并修正 POST 同步路由为类根路径下的 `/sync`。 |
| 服务端 `2ea463a`（`50257bf^`） | P1 前对照基线：PUT 返回 `Result.success()`，其实际 data 为字符串 `"success"`，不是因 Result<Void> 就返回 null。 |
| 服务端 `50257bf` | 引入本地优先保存、可空远端 ID、结构化保存结果与额外参数校验。 |
| 服务端 `2239ea6`，2026-09-19 | 删除 AccountController 中 POST/GET 旧同步接口。 |
| 客户端 `f6e2d726`（`a6ba4d02^`） | P1 前主要对照基线：ID 非空、更新方法返回 Future<void>、同步只认识四种状态。 |
| 客户端 `a6ba4d02`，2026-09-19 | 支持可空 ID、单条保存结果及同步提示；任务状态和冲突页面扩展尚在当前工作区。 |
| 客户端标签 `v2.5.2` | 实际读取确认仍包含 `int id` 和四状态同步模型，存在下述风险。 |
| 客户端标签包含关系 | `git tag --contains be805600` 返回 v2.1.2、v2.2.0、v2.2.1、v2.2.2、v2.3.0、v2.4.0、v2.5.0、v2.5.1、v2.5.2；`git tag --contains a6ba4d02` 本次未返回标签。 |

以上是本地 Git 证据，不能据此证明线上安装比例、实际发布包与标签一致，或所有历史设备都已升级。不要直接指定某个现有标签为“完整兼容新版收藏”的最低版本。

## 3. 逐接口检查

| 接口 | 变化与旧客户端行为 | 结论 |
| --- | --- | --- |
| GET `/api/v1/users/collections` | 查询参数和 Result 包装保留；本地收藏的 interest.id 可以为 null，旧列表模型直接赋值给 int。一个条目解析失败即可导致整页请求失败。 | **不兼容：高优先级** |
| GET `/api/v1/bangumi/subjects/{id}` | 已登录时，本地收藏详情映射也可能返回空 interest.id，旧 InterestItem 同样直接赋值给 int。 | **不兼容：高优先级** |
| PUT `/api/v1/users/collections/{subjectId}` 响应 | data 从字符串 success 变成 localSaved/remoteSyncStatus/localVersion 对象。旧 FlowClient 接受 dynamic data，旧 updateCollectionService 仅 await 请求而不读取 data。 | **结构可兼容，语义不完全兼容** |
| 同一 PUT 的成功语义 | PENDING、AUTH_REQUIRED、CONFLICT、LOCAL_ONLY 仍可返回业务 code=200。旧页面据此显示保存成功，无法提示远端待办。 | **功能不完整：高优先级** |
| 同一 PUT 的首次评价 | 新建本地收藏必须有 type；旧 evaluate_dialog 提交 rate/tags/comment/subjectType，不传 type。若只有远端收藏而尚未导入本地，也会被判为首次本地收藏。 | **条件性不兼容：首次评价可能失败** |
| 同一 PUT 的 progress | 现在任何非 null 的 progress 都明确拒绝，包括 false。旧 API 包装允许传入，但在 f6e2d726 的实际收藏调用点未发现传入该参数。 | **协议收紧；不能断言当前旧客户端必受影响** |
| 同一 PUT 的 subjectType | 限定 1/2/3/4/6，且必须与本地元数据一致；不能再依赖默认写死动画。元数据、已有收藏及传入值都缺失时拒绝新建。 | **条件性不兼容；错误请求被拒绝** |
| POST `/api/v1/users/collections/sync` | subjectType 参数保留，requestId 为可选查询参数，缺省时服务端生成。新任务可返回 QUEUED。旧模型将其解析为 IDLE。 | **请求兼容，状态不兼容** |
| GET 同一路径 | 新增 WAITING_CONFLICT/PARTIAL_FAILED/CANCELLED 等状态及 taskId/phase/计数字段。额外字段会被旧模型忽略，但未知状态回退 IDLE。 | **任务追踪及冲突处理不兼容** |
| POST 同一路径的执行语义 | 从单向导入变为双方并集合并，本地独有记录会上传；分类冲突持久等待。旧 UI 对“同步”的预期和说明没有包含上传与人工选择。 | **行为变化，需能力门控或明确升级** |
| GET/POST `/api/v1/account/oauth/bangumi/collections/sync` | 路由已删除。未包含 be805600 的历史客户端仍请求该地址，正常鉴权后无法命中处理器。 | **早期客户端不兼容；已获用户授权删除** |
| GET `/sync/{taskId}/conflicts`、POST `/sync/{taskId}/conflicts/resolve`（收藏根路径下） | 纯新增接口，旧客户端不调用，不会单独造成解析错误；但旧客户端没有决议入口。 | **接口增量兼容，冲突功能缺失** |

### 3.1 空 ID 的源码依据与复现条件

客户端 f6e2d726：
- `lib/shared/models/bangumi/interest_item.dart`：`int id;`、`id = json['id']`。
- `lib/shared/models/bangumi/user_collections_item.dart`：`final int id;`、`id = json['id']`。
- 列表解析是逐项 map 后 toList，未为单条空 ID 提供容错。

触发数据：一个合法本地收藏返回 `{"interest":{"id":null,"type":3,...}}`。Dart 不能把 null 赋给非空 int；即便服务端省略 id 字段，json['id'] 仍为 null，不能规避。

不仅未绑定用户受影响；跨版本混用设备以及已绑定上传失败的新收藏也需要覆盖。不能靠“旧客户端暂时不能新增未绑定收藏”保护它读取其他设备已创建的本地收藏。

### 3.2 新任务状态会停止旧客户端轮询

f6e2d726 的 `bgm_collection_sync_status_item.dart`：
- 只认识 IDLE/RUNNING/SUCCESS/FAILED，default 返回 idle。
- isRunning 仅判断 RUNNING。

`bgm_collection_sync_provider.dart` 的 _ensurePolling 先停止定时器，再在 !isRunning 时直接返回。因此新 POST 返回 QUEUED 后，旧客户端通常立即停止轮询，不能自动观察后台结果；并非只是标签文字不准确。

WAITING_CONFLICT 被解析为 IDLE 后，旧客户端也无法发现或完成人工处理。重复点同步会返回原活动任务，不会自行消除冲突。

### 3.3 返回对象本身不会令已核验旧客户端崩溃

旧 `FlowApi.updateCollectionService` 为 Future<void>，没有将 data 强转 String；旧 `FlowClient._parseEnvelope` 读取 code/message，并将 data 作为 dynamic 保存。因此“字符串换对象必然导致旧客户端报错”不符合历史源码。

真正的问题是语义：本地保存成功不等于远端成功。不能把所有 code=200 都解释为 Bangumi 已更新，也不能为提示远端失败而简单把已保存请求改成失败，导致旧端盲目重试。

### 3.4 不应误报为本次新增问题的行为

- 旧评价页面把空评论、0 分和空标签转成不发送，这属于旧客户端原有清空能力限制；服务端支持明确空值后也无法自动修复旧 UI。
- taskId 等新增字段不被旧模型读取，字段追加本身不会使本次核验的旧模型崩溃。
- progress 在旧包装中存在，不等于实际页面正在使用；更早历史版本或第三方调用需另行核验。
- 本次没有发现已实现的客户端能力请求头识别或版本分支。之前讨论的 X-AnimeFlow-* 请求头只是建议。

## 4. 按客户端代际判断

| 客户端范围 | 新同步路径 | 空 ID / 保存状态 | P2/P3 任务与冲突 |
| --- | --- | --- | --- |
| 尚未包含 be805600 | 否，旧路径已删除 | 不保证 | 不支持 |
| 包含路径迁移、但早于 a6ba4d02；已核验 v2.5.2 / f6e2d726 | 是 | 不支持空 ID；忽略保存状态 | 不支持 |
| a6ba4d02 的 P1 客户端 | 是 | 支持 | 仍不支持新增任务状态及决议页面 |
| 当前未提交客户端工作区 | 是 | 支持 | 已有状态与冲突入口；完整交互仍需联调验收 |

## 5. 兼容处理待办与发布条件

以下均为待实施/待验收项，本次只写文档：

1. **确定支持边界。** 若要求所有历史客户端继续使用，则需要旧协议适配层；若采用最低版本策略，必须先在真实旧客户端验证升级提示可见。服务端返回升级消息并不能自动让所有旧 UI 都展示它。
2. **保护所有读入口。** 能力门控必须覆盖收藏列表、详情和写入，且考虑跨设备产生的空 ID。不能只禁用旧客户端的“未绑定新增”；不要伪造 Bangumi ID，也不要隐瞒本地记录造成列表与计数不一致。
3. **识别任务能力。** P1 客户端也不支持新任务状态，不能仅用“支持空 ID”判断可启用双向同步。对不支持冲突决议的客户端，在创建新任务前明确要求升级或保留旧版安全同步实现。
4. **如果保留旧任务状态映射，明确边界。** QUEUED 可映射 RUNNING 以保留轮询；WAITING_CONFLICT/PARTIAL_FAILED 应返回旧端能展示的说明。不能映射 SUCCESS 冒充完成，也不能一直映射 RUNNING 令客户端无期限空轮询。映射无法补出人工决议 UI。
5. **处理首次评价与进度参数。** 新建要求明确分类应保留；旧评价界面需升级或经过明确的旧协议兼容策略，不应偷偷猜测分类。progress 只能在实现语义等价支持后开放。
6. **旧路径删除维持已确认决策。** 不因本审查擅自恢复；若未来要支持早期设备，再单独决定是否恢复代理别名。别名只能修复路径，不能修复空 ID 与新状态问题。
7. **数据库先迁移。** 按 P1、任务表、恢复迁移顺序部署。数据库结构兼容和客户端 JSON 兼容是两件事；表存在不表示旧客户端可用。
8. **先验证，再开放。** 在旧客户端与新版服务端的组合上验证下列场景，确定能力识别/最低版本策略真正生效，再开放本地收藏和双向同步。当前没有可据本地标签直接认定的完整兼容最低版本。

## 6. 验收清单

- v2.5.2 / f6e2d726：普通已同步收藏列表与详情；夹杂一个 id=null 的列表；本地独有条目详情。
- 同账号新旧两台设备：新设备创建本地收藏，旧设备读取；已绑定但新收藏上传失败。
- 旧 PUT：返回对象；分别返回 LOCAL_ONLY/PENDING/AUTH_REQUIRED/CONFLICT；核对界面是否误报远端成功。
- 旧评价：本地存在/不存在、仅远端存在三种情况；只评价不传 type；有无条目元数据。
- 旧同步：POST QUEUED 后轮询、WAITING_CONFLICT、PARTIAL_FAILED、重复提交。
- P1 客户端 a6ba4d02：专门验证新增任务状态，不能用空 ID 测试通过替代。
- 旧路径：迁移前/后客户端分别验证，确认支持范围与删除决策一致。
- 请求：未传 requestId、合法 subjectType、progress=true/false、空标签/评论/评分。
- 需要回滚服务时保留本地收藏，不能恢复删除本地独有数据的旧同步逻辑。

## 7. 复核方法与限制

主要使用 git show、git log -S、git grep、git tag --contains，比对历史路由、Dart 模型、API 包装、页面提交参数和当前服务端实现。可复核命令示例：

```text
# 客户端仓库
git show be805600 -- lib/http/api_path.dart
git show f6e2d726:lib/core/network/api/flow_api.dart
git show f6e2d726:lib/shared/models/bangumi/interest_item.dart
git show f6e2d726:lib/shared/models/bangumi/user_collections_item.dart
git show f6e2d726:lib/shared/models/flow/bgm_collection_sync_status_item.dart
git show f6e2d726:lib/features/user/application/bgm_collection_sync_provider.dart
git show v2.5.2:lib/shared/models/bangumi/interest_item.dart
git tag --contains a6ba4d02

# 服务端仓库
git show 50257bf^:flow-client/src/main/java/com/ligg/flowclient/controller/CollectionController.java
git show 50257bf^:common/src/main/java/com/ligg/common/response/Result.java
git show 2239ea6
```

本次为历史源码兼容审查，没有运行已发布客户端安装包，也没有读取线上访问日志或做真实 Bangumi 写入测试。结论中的确定项来自具体解析/调用代码；版本覆盖率与线上实际影响范围仍需发布记录和日志验证。
