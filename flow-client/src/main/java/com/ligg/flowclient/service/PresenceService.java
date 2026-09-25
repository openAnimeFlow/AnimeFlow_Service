package com.ligg.flowclient.service;

import com.ligg.flowclient.module.dto.PresenceHeartbeatDto;
import com.ligg.flowclient.module.vo.OnlineCountVo;
import com.ligg.flowclient.module.vo.WatchingSubjectVo;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface PresenceService {

    void heartbeat(String presenceId, PresenceHeartbeatDto dto,
                   String accessToken, boolean hasAuthorization);

    void offline(String presenceId);

    OnlineCountVo onlineCount();

    OnlineCountVo subjectOnlineCount(int subjectId, String excludePresenceId);

    SubjectOnlineCounts subjectOnlineCounts(int subjectId, Set<String> excludePresenceIds);

    List<WatchingSubjectVo> watchingSubjects();

    record SubjectOnlineCounts(OnlineCountVo total, Map<String, OnlineCountVo> excluded) {
        public OnlineCountVo forPresence(String presenceId) {
            return presenceId == null ? total : excluded.getOrDefault(presenceId, total);
        }
    }
}
