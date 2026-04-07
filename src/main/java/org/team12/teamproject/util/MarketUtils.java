package org.team12.teamproject.util;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.ZoneId;

public class MarketUtils {

    private static final ZoneId KST_ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalTime MARKET_OPEN = LocalTime.of(9, 0);
    private static final LocalTime MARKET_CLOSE = LocalTime.of(15, 30);

    /**
     * 현재 한국 시간(KST)이 주식 시장 운영 시간(평일 09:00 ~ 15:30)인지 확인합니다.
     */
    public static boolean isMarketOpen() {
        ZonedDateTime nowKst = ZonedDateTime.now(KST_ZONE);
        DayOfWeek day = nowKst.getDayOfWeek();
        LocalTime time = nowKst.toLocalTime();

        // 토요일, 일요일 제외
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }

        // 09:00 ~ 15:30 사이인지 확인
        return !time.isBefore(MARKET_OPEN) && !time.isAfter(MARKET_CLOSE);
    }
}
