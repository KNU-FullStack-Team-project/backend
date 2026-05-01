package org.team12.teamproject.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AdminActionAuditLogger {

    private static final Logger auditLog = LoggerFactory.getLogger("ADMIN_ACTION");

    public void log(
            Long adminUserId,
            String adminEmail,
            String action,
            String targetType,
            String targetId,
            String detail
    ) {
        auditLog.info(
                "adminUserId={}, adminEmail={}, action={}, targetType={}, targetId={}, detail={}",
                safe(adminUserId),
                safe(adminEmail),
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
