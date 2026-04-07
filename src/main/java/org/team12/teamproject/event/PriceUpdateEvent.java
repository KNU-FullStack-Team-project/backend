package org.team12.teamproject.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import java.math.BigDecimal;

@Getter
public class PriceUpdateEvent extends ApplicationEvent {
    private final String stockCode;
    private final BigDecimal currentPrice;

    public PriceUpdateEvent(Object source, String stockCode, BigDecimal currentPrice) {
        super(source);
        this.stockCode = stockCode;
        this.currentPrice = currentPrice;
    }
}
