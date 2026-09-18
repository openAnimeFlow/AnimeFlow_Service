package com.ligg.flowclient.service;

import com.ligg.flowclient.module.dto.PresenceHeartbeatDto;
import com.ligg.flowclient.module.vo.OnlineCountVo;
import com.ligg.flowclient.module.vo.WatchingSubjectVo;

import java.util.List;

public interface PresenceService {

    void heartbeat(String presenceId, PresenceHeartbeatDto dto,
                   String accessToken, boolean hasAuthorization);

    void offline(String presenceId);

    OnlineCountVo onlineCount();

    OnlineCountVo subjectOnlineCount(int subjectId);

    List<WatchingSubjectVo> watchingSubjects();
}
