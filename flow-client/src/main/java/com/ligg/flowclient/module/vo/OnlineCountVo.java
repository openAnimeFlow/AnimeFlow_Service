package com.ligg.flowclient.module.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OnlineCountVo {
    private long onlineUsers;
    private long onlineDevices;
    private long anonymousUsers;
    private long loggedInUsers;
}
