package com.jlxc.mikucarlauncher;

import android.service.notification.NotificationListenerService;

public class MusicNotificationListenerService extends NotificationListenerService {
    // 这里不需要主动处理通知内容。
    // MediaSessionManager.getActiveSessions() 只需要一个已授权的 NotificationListenerService 组件。
}
