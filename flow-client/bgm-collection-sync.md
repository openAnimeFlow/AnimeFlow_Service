# Bangumi 收藏同步

本文档描述 flow-client 当前的 Bangumi 收藏双向同步实现。同步任务和同步明细持久化在 MySQL，客户端通过 SSE 接收状态变化。SSE 断开不会取消后台任务，重新连接后以数据库状态为准。

## 1. 数据与职责

| 数据 | 表 | 作用 |
|------|------|------|
| 本地收藏 | user_bgm_collection | 保存本地收藏、Bangumi 状态和上传意图 |
| 同步任务 | user_bgm_collection_sync_task | 保存任务状态、阶段、进度、绑定快照和心跳 |
| 同步明细 | user_bgm_collection_sync_item | 保存条目快照、比较基线、写入意图和冲突 |

任务状态以 MySQL 为准。Redis 仅用于跨实例转发状态变化通知，不保存任务状态或收藏内容。

## 2. Token、绑定和本地优先

- Flow JWT 用于访问 AnimeFlow 接口，由 AuthorizationInterceptor、JwtTokenService 和客户端刷新机制处理。
- Bangumi OAuth Token 用于调用 Bangumi /p1/collections/subjects 和条目详情接口，由 BangumiOAuthExecutor 统一刷新并重试一次。
- 未绑定 Bangumi 时，修改收藏仍可只保存本地，状态为 LOCAL_ONLY。
- 已绑定 Bangumi 时，先保存本地，再尝试上传，状态通常为 PENDING 或 SYNCED。
- 提交完整同步任务必须已绑定 Bangumi。任务记录 oauth_id 和 Bangumi 用户 UID；绑定变化会取消任务并返回 SYNC_BINDING_CHANGED。

## 3. HTTP 接口

所有接口都需要 Flow JWT。

### 3.1 获取收藏

~~~http
GET /api/v1/users/collections?subjectType=2&type=2&keyword=关键词&limit=20&offset=0
~~~

数据来自本地表。排序为：

~~~sql
ORDER BY COALESCE(local_updated_at, FROM_UNIXTIME(bgm_updated_at)) DESC,
         id DESC
~~~

有 local_updated_at 时按本地修改时间排序；没有时按 Bangumi 更新时间排序。后台同步不会更新 local_updated_at，只有用户主动修改收藏时才更新。

### 3.2 修改收藏

~~~http
PUT /api/v1/users/collections/{subjectId}
~~~

支持收藏分类、评分、评论、标签和隐私设置。响应的 remoteSyncStatus：

| 状态 | 含义 |
|------|------|
| LOCAL_ONLY | 未绑定 Bangumi，只保存本地 |
| PENDING | 已保存本地，等待上传或重试 |
| SYNCED | 本地与 Bangumi 已确认一致 |
| AUTH_REQUIRED | 授权失效或绑定变化 |
| CONFLICT | 需要用户处理冲突 |

### 3.3 提交同步任务

~~~http
POST /api/v1/users/collections/sync?subjectType=2&requestId=<客户端幂等 ID>
~~~

subjectType 默认 2，可用值为 1、2、3、4、6。requestId 可选，最大长度 80；为空时服务端生成 UUID。相同用户和 requestId 会复用原任务；已有相同 Bangumi 绑定的活动任务也会复用。成功任务完成后有 1 小时用户级冷却，接口按 IP 限制为 60 秒最多 5 次。

接口只创建或复用任务并立即返回，不等待后台同步完成。

### 3.4 查询状态（兼容接口）

~~~http
GET /api/v1/users/collections/sync
~~~

该接口已标记 Deprecated，仍保留兼容旧客户端。新客户端应使用 SSE；状态来自 MySQL，不是 Redis 快照。

### 3.5 SSE 状态流

~~~http
GET /api/v1/users/collections/sync/events
Accept: text/event-stream
~~~

连接最长保持 5 分钟，客户端应在断开或超时后重连。建立连接时先发送当前状态；状态变化以 event=status 发送，事件 ID 为 taskId:statusVersion；每 20 秒发送 SSE 注释心跳。每次发送前校验 Flow JWT，过期会话自动关闭。关闭 SSE 不会取消任务，重连后会重新读取数据库状态。

Redis Pub/Sub 频道 animeflow:collection-sync:changed 只用于通知其他实例刷新 SSE。

### 3.6 查询和解决冲突

~~~http
GET /api/v1/users/collections/sync/{taskId}/conflicts?offset=0&limit=20
POST /api/v1/users/collections/sync/{taskId}/conflicts/resolve
~~~

冲突列表返回 conflictId、subjectId、subjectName、localType、remoteType、localVersion、conflictVersion 和 status。客户端分别展示 AnimeFlow 与 Bangumi 的收藏分类，让用户选择最终分类。

批量解决请求示例：

~~~json
{
  "items": [
    {
      "conflictId": 123,
      "conflictVersion": 2,
      "selectedType": 3
    }
  ]
}
~~~

selectedType 范围为 1~5。服务端校验任务归属、冲突版本、本地版本和当前 Bangumi 快照；通过后先持久化远端写入意图，再调度后台任务。相同版本和分类的重复提交是幂等的。

## 4. 任务状态、阶段和明细

任务状态：QUEUED、RUNNING、WAITING_CONFLICT、PARTIAL_FAILED、SUCCESS、FAILED、CANCELLED、IDLE。

执行阶段：

| 阶段 | 说明 |
|------|------|
| SCANNING | 拉取 Bangumi 分页并生成明细 |
| APPLYING | 执行导入、上传或无变化确认 |
| RESOLVING | 应用客户端已提交的冲突决议 |

明细状态：PLANNED、CONFLICT、RESOLUTION_PENDING、APPLY_PENDING、DONE、FAILED。

完成操作：IMPORT、UPLOAD、UNCHANGED、RESOLVE。statusVersion 每次任务状态更新都会递增，客户端可用来过滤重复 SSE 事件。

## 5. 扫描和应用流程

~~~http
GET /p1/collections/subjects?subjectType={subjectType}&type={1|2|3|4|5}&limit=50&offset=0
~~~

分页返回的 interest 已包含分类、评分、评论、标签、隐私和更新时间。服务端把条目和 interest 保存为 scan_snapshot，不会为每条收藏再次请求详情。

只有以下情况需要请求条目详情：

- 本地有收藏但扫描结果没有远端收藏，需要确认远端实时状态；
- 本地收藏需要上传；
- 任务恢复后验证远端基线；
- PUT 后确认远端结果。

五种分类扫描完成后，服务端把本地对应 subjectType 的收藏加入明细，形成双方完整并集。仅存在本地的收藏进入上传判断，不会被同步任务自动删除。

每个条目的处理规则：

| 本地 | Bangumi | 操作 |
|------|---------|------|
| 无 | 有 | IMPORT，写入本地 |
| 有 | 无 | UPLOAD，上传本地 |
| 有 | 有且分类相同 | UNCHANGED 或 UPLOAD |
| 有 | 有但分类不同 | CONFLICT，等待用户选择 |
| 无 | 无 | UNCHANGED，不创建虚假收藏 |

远端 PUT 前会持久化 local_version、local_snapshot、remote_snapshot、desired_payload、operation 和 APPLY_PENDING。进程退出后，恢复任务可根据基线确认 PUT 是否已经成功，避免重复写入或覆盖远端新编辑。

同步完成写回本地时更新 bgm_updated_at、sync_time 和远端绑定信息，但保留已有 local_updated_at。新导入记录的 local_updated_at 为空，列表使用 Bangumi 更新时间排序。

## 6. 冲突、离开页面和恢复

冲突主要是本地与 Bangumi 收藏分类不同，也可能由本地版本变化、远端字段变化或不确定 PUT 造成。客户端关闭页面或应用不会取消任务：

- 任务和明细已经持久化在 MySQL；
- SSE 断开只移除连接；
- recover() 默认每 60 秒恢复 QUEUED、RUNNING、WAITING_CONFLICT、PARTIAL_FAILED 任务；
- MySQL 连接锁在进程退出后自动释放；
- 恢复时跳过 DONE 和 CONFLICT 明细，只继续未完成或已持久化意图的明细；
- 重新进入账户设置页面时重新建立 SSE，并再次查询冲突列表。

WAITING_CONFLICT 不会自动替用户选择分类；用户可以稍后继续处理原任务。

## 7. 锁、事务和远端请求

CollectionWriteLock 使用 MySQL GET_LOCK：

- 任务级锁使用约定的负数条目 ID；
- 条目级锁使用真实 subjectId；
- 调用方先获取任务锁，再获取条目锁；
- 获取和释放必须使用同一个物理数据库连接，因此不能拆成普通 MyBatis Mapper 调用；
- 数据库短事务只持久化意图或最终结果，远端 HTTP 请求不放在数据库事务中。

## 8. 状态响应字段

UserBgmCollectionSyncStatusVo 包含：

| 字段 | 说明 |
|------|------|
| taskId、status、phase | 任务标识、状态和阶段 |
| scannedCount | 扫描阶段已发现的明细数 |
| totalCount | 去重后的任务条目总数 |
| syncedCount | 导入、上传、无变化和已解决冲突的合计 |
| importedCount、uploadedCount、unchangedCount | 各类完成数量 |
| pendingConflictCount | 待处理冲突数 |
| failedCount | 可重试失败数 |
| statusVersion | 状态版本 |
| message | 任务级错误码或提示 |
| startedAt、finishedAt | 开始和结束时间，毫秒时间戳 |

扫描阶段显示 scannedCount；应用阶段显示各类完成数量和 syncedCount。

## 9. 错误和兼容性

| 场景 | 处理 |
|------|------|
| Flow JWT 无效 | 返回 401，由客户端刷新 Flow Token |
| 未绑定 Bangumi 提交同步 | 创建任务时拒绝 |
| Bangumi Token 过期 | 服务端刷新并重试一次 |
| Bangumi 换绑 | 任务取消，返回 SYNC_BINDING_CHANGED |
| 网络或临时数据库错误 | 明细 FAILED 或任务 PARTIAL_FAILED，等待恢复 |
| 冲突版本过期 | 返回 STALE_CONFLICT，刷新后重新选择 |
| 重复提交 | 按 requestId 或活动任务幂等返回 |
| 旧客户端轮询 | GET /sync 仍可用，但已弃用 |
| 旧 AccountController 同步接口 | 已移除，迁移到 /api/v1/users/collections/sync |

任务表和明细表的数据不能在任务仍可能恢复或存在未解决冲突时删除。后续清理应按终态、完成时间和保留期执行。

## 10. 线程池和调度

同步线程池 bgmCollectionSyncExecutor：核心线程 1、最大线程 2、队列容量 50。

任务恢复默认每 60 秒执行一次，可配置：

~~~properties
anime-flow.collection-sync.initial-delay-ms=60000
anime-flow.collection-sync.retry-delay-ms=60000
~~~

SSE 使用独立调度器发送状态和心跳，避免阻塞同步执行器。
