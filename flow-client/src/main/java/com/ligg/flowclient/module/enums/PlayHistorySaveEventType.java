package com.ligg.flowclient.module.enums;

/**
 * 播放记录保存事件类型。
 */
public enum PlayHistorySaveEventType {

    /**
     * 普通保存，不允许旧进度覆盖更新的进度。
     */
    DEFAULT,

    /**
     * 强制使用本次请求中的播放进度覆盖已有进度。
     */
    FORCE_OVERWRITE
}
