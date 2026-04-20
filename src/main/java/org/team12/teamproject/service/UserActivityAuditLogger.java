package org.team12.teamproject.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class UserActivityAuditLogger {

    private static final Logger auditLog = LoggerFactory.getLogger("USER_ACTIVITY");

    public void log(Long userId, String userEmail, String action, String targetType, String targetId, String detail) {
        auditLog.info(
                "userId={}, userEmail={}, action={}, targetType={}, targetId={}, detail={}",
                safe(userId),
                safe(userEmail),
                safe(action),
                safe(targetType),
                safe(targetId),
                safe(detail)
        );
    }

    private String safe(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }
}
