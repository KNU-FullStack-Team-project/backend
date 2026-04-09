package org.team12.teamproject.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.team12.teamproject.entity.NotificationType;
import org.team12.teamproject.entity.PriceAlert;
import org.team12.teamproject.event.PriceUpdateEvent;
import org.team12.teamproject.repository.PriceAlertRepository;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceAlertService {

    private final PriceAlertRepository priceAlertRepository;
    private final NotificationService notificationService;

    @EventListener
    @Transactional
    public void handlePriceUpdate(PriceUpdateEvent event) {
        String stockCode = event.getStockCode();
        BigDecimal currentPrice = event.getCurrentPrice();

        List<PriceAlert> activeAlerts = priceAlertRepository.findByIsActiveAndStockStockCode(1, stockCode);

        for (PriceAlert alert : activeAlerts) {
            boolean isTriggered = false;

            if (alert.getDirection() == PriceAlert.AlertDirection.ABOVE) {
                if (currentPrice.compareTo(alert.getTargetPrice()) >= 0) {
                    isTriggered = true;
                }
            } else if (alert.getDirection() == PriceAlert.AlertDirection.BELOW) {
                if (currentPrice.compareTo(alert.getTargetPrice()) <= 0) {
                    isTriggered = true;
                }
            }

            if (isTriggered) {
                triggerAlert(alert, currentPrice);
            }
        }
    }

    private void triggerAlert(PriceAlert alert, BigDecimal currentPrice) {
        String directionStr = alert.getDirection() == PriceAlert.AlertDirection.ABOVE ? "이상" : "이하";
        String title = "목표가 도달 알림";
        String message = String.format("[%s] 목표가 %s원 %s 도달! (현재가: %s원)",
                alert.getStock().getStockName(),
                alert.getTargetPrice().setScale(0).toString(),
                directionStr,
                currentPrice.setScale(0).toString());

        notificationService.sendNotification(alert.getUser(), title, message, NotificationType.PRICE_ALERT);

        alert.deactivate(); // Ensure this method exists or update it
        priceAlertRepository.save(alert);

        log.info("목표가 도달 알림 발송: UserId={}, Stock={}, Target={}, Current={}",
                alert.getUser().getId(), alert.getStock().getStockName(), alert.getTargetPrice(), currentPrice);
    }

    @Transactional
    public PriceAlert createPriceAlert(PriceAlert alert) {
        alert.activate(); // Ensure this method exists
        return priceAlertRepository.save(alert);
    }

    @Transactional(readOnly = true)
    public List<PriceAlert> getActiveAlerts(Long userId) {
        return priceAlertRepository.findByUserIdAndIsActiveTrue(userId);
    }

    @Transactional
    public void deleteAlert(Long alertId, Long userId) {
        priceAlertRepository.findById(alertId).ifPresent(alert -> {
            if (alert.getUser().getId().equals(userId)) {
                priceAlertRepository.delete(alert);
            }
        });
    }
}
