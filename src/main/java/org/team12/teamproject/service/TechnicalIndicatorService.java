package org.team12.teamproject.service;

import org.springframework.stereotype.Service;
import org.team12.teamproject.dto.StockCandleDto;
import org.team12.teamproject.dto.StockDiagnosisDto;
import org.team12.teamproject.dto.StockDiagnosisDto.DiagnosisDetail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class TechnicalIndicatorService {

    public StockDiagnosisDto diagnose(String symbol, List<StockCandleDto> candles) {
        if (candles == null || candles.size() < 60) {
            return StockDiagnosisDto.builder().symbol(symbol).signal("INSUFFICIENT_DATA").build();
        }

        List<StockCandleDto> sorted = new ArrayList<>(candles);
        if (sorted.get(0).getDate().compareTo(sorted.get(sorted.size() - 1).getDate()) > 0) {
            Collections.reverse(sorted);
        }

        double currentPrice = parseDouble(sorted.get(sorted.size() - 1).getClose());
        List<DiagnosisDetail> details = new ArrayList<>();
        double totalScore = 0;

        // 1. RSI (14) & Divergence
        double[] rsiValues = calculateRSI(sorted, 14);
        double currentRsi = rsiValues[rsiValues.length - 1];
        
        // RSI 기본 점수
        if (currentRsi <= 30) { totalScore += 2; details.add(new DiagnosisDetail("RSI", "과매도권 (" + Math.round(currentRsi) + ")", 2)); }
        else if (currentRsi <= 40) { totalScore += 1; details.add(new DiagnosisDetail("RSI", "약한 매수권 (" + Math.round(currentRsi) + ")", 1)); }
        else if (currentRsi >= 70) { totalScore -= 2; details.add(new DiagnosisDetail("RSI", "과매수권 (" + Math.round(currentRsi) + ")", -2)); }
        else if (currentRsi >= 60) { totalScore -= 1; details.add(new DiagnosisDetail("RSI", "약한 매도권 (" + Math.round(currentRsi) + ")", -1)); }

        // RSI 다이버전스 (최근 10일 기준)
        double divScore = calculateRsiDivergence(sorted, rsiValues);
        if (divScore > 0) { totalScore += 2; details.add(new DiagnosisDetail("RSI", "상승 다이버전스 포착", 2)); }
        else if (divScore < 0) { totalScore -= 2; details.add(new DiagnosisDetail("RSI", "하락 다이버전스 포착", -2)); }

        // 2. MACD (12, 26, 9) 고도화
        MacdResult macd = calculateMACD(sorted, 12, 26, 9);
        int lastIdx = macd.histogram.length - 1;
        double curHist = macd.histogram[lastIdx];
        double prevHist = macd.histogram[lastIdx - 1];
        
        // 크로스 및 지속 상태 반영
        if (prevHist < 0 && curHist >= 0) {
            totalScore += 2; details.add(new DiagnosisDetail("MACD", "골든크로스 발생 당일", 2));
        } else if (prevHist >= 0 && curHist < 0) {
            totalScore -= 2; details.add(new DiagnosisDetail("MACD", "데드크로스 발생 당일", -2));
        } else {
            // 최근 5일 이내 크로스 여부 확인
            boolean recentGolden = false;
            boolean recentDead = false;
            for (int i = 1; i <= 5; i++) {
                if (macd.histogram[lastIdx - i - 1] < 0 && macd.histogram[lastIdx - i] >= 0) recentGolden = true;
                if (macd.histogram[lastIdx - i - 1] >= 0 && macd.histogram[lastIdx - i] < 0) recentDead = true;
            }
            if (recentGolden) { totalScore += 1; details.add(new DiagnosisDetail("MACD", "골든크로스 이후 추세 유효 (1~5일)", 1)); }
            else if (recentDead) { totalScore -= 1; details.add(new DiagnosisDetail("MACD", "데드크로스 이후 추세 유효 (1~5일)", -1)); }
        }
        // 0선 위아래 (중기 구조)
        if (macd.macdLine[lastIdx] > 0) { totalScore += 1; details.add(new DiagnosisDetail("MACD", "0선 위 (중기 상승 구조)", 1)); }
        else { totalScore -= 1; details.add(new DiagnosisDetail("MACD", "0선 아래 (중기 하락 구조)", -1)); }

        // 3. MA (5, 20, 60)
        double[] ma5 = calculateSMA(sorted, 5);
        double[] ma20 = calculateSMA(sorted, 20);
        double[] ma60 = calculateSMA(sorted, 60);
        double curMa5 = ma5[ma5.length - 1];
        double curMa20 = ma20[ma20.length - 1];
        double curMa60 = ma60[ma60.length - 1];

        if (curMa5 > curMa20 && curMa20 > curMa60) { totalScore += 3; details.add(new DiagnosisDetail("이평선", "완전 정배열", 3)); }
        else if (curMa5 > curMa20) { totalScore += 1; details.add(new DiagnosisDetail("이평선", "단기 정배열", 1)); }
        else if (curMa5 < curMa20 && curMa20 < curMa60) { totalScore -= 3; details.add(new DiagnosisDetail("이평선", "완전 역배열", -3)); }
        else if (curMa5 < curMa20) { totalScore -= 1; details.add(new DiagnosisDetail("이평선", "단기 역배열", -1)); }

        // 4. 볼린저 밴드 %B 도입
        BollingerResult bb = calculateBollingerBands(sorted, 20, 2);
        double upper = bb.upper[bb.upper.length - 1];
        double lower = bb.lower[bb.lower.length - 1];
        double middle = bb.middle[bb.middle.length - 1];
        double percentB = (currentPrice - lower) / (upper - lower);

        if (percentB < 0) { totalScore += 2; details.add(new DiagnosisDetail("볼린저(%B)", "%B 하단 이탈 (" + String.format("%.2f", percentB) + ")", 2)); }
        else if (percentB < 0.2) { totalScore += 1; details.add(new DiagnosisDetail("볼린저(%B)", "%B 하단 근접", 1)); }
        else if (percentB > 1.0) { totalScore -= 2; details.add(new DiagnosisDetail("볼린저(%B)", "%B 상단 이탈 (" + String.format("%.2f", percentB) + ")", -2)); }
        else if (percentB > 0.8) { totalScore -= 1; details.add(new DiagnosisDetail("볼린저(%B)", "%B 상단 근접", -1)); }

        if (currentPrice > middle) { totalScore += 1; details.add(new DiagnosisDetail("볼린저(추세)", "중심선 위 지지", 1)); }
        else { totalScore -= 1; details.add(new DiagnosisDetail("볼린저(추세)", "중심선 아래 저항", -1)); }

        // 5. 거래량 양방향 가중치
        double[] volMa20 = calculateVolumeSMA(sorted, 20);
        double curVol = parseDouble(sorted.get(sorted.size() - 1).getVolume());
        double avgVol = volMa20[volMa20.length - 2];
        double volRatio = curVol / avgVol;
        double volMultiplier = 1.0;

        if (volRatio > 2.0) { volMultiplier = 1.3; details.add(new DiagnosisDetail("거래량", "폭발적 거래량 (200%↑)", 0)); }
        else if (volRatio > 1.5) { volMultiplier = 1.2; details.add(new DiagnosisDetail("거래량", "강한 거래량 (150%↑)", 0)); }
        else if (volRatio < 0.5) { volMultiplier = 0.7; details.add(new DiagnosisDetail("거래량", "낮은 신뢰도 (50%↓)", 0)); }

        totalScore *= volMultiplier;

        // 최종 판정 기준 재조정
        String signal = "HOLD";
        if (totalScore >= 9) signal = "STRONG_BUY";
        else if (totalScore >= 5) signal = "BUY";
        else if (totalScore <= -9) signal = "STRONG_SELL";
        else if (totalScore <= -5) signal = "SELL";

        return StockDiagnosisDto.builder()
                .symbol(symbol)
                .totalScore(Math.round(totalScore * 10) / 10.0)
                .signal(signal)
                .rsi(currentRsi)
                .currentPrice(currentPrice)
                .ma60(curMa60)
                .bollingerMiddle(middle)
                .details(details)
                .build();
    }

    private double calculateRsiDivergence(List<StockCandleDto> sorted, double[] rsi) {
        int len = sorted.size();
        if (len < 15) return 0;

        // 최근 10일간의 가격 및 RSI 고점/저점 비교
        double curPrice = parseDouble(sorted.get(len-1).getClose());
        double prevPrice = parseDouble(sorted.get(len-10).getClose());
        double curRsi = rsi[len-1];
        double prevRsi = rsi[len-10];

        // 상승 다이버전스: 가격은 신저점인데 RSI는 올라감
        if (curPrice < prevPrice && curRsi > prevRsi && curRsi < 40) return 2.0;
        // 하락 다이버전스: 가격은 신고점인데 RSI는 내려감
        if (curPrice > prevPrice && curRsi < prevRsi && curRsi > 60) return -2.0;

        return 0;
    }

    private double parseDouble(String val) {
        if (val == null || val.isEmpty()) return 0.0;
        try {
            return Double.parseDouble(val.replaceAll("[^0-9.-]", ""));
        } catch (Exception e) {
            return 0.0;
        }
    }

    private double[] calculateSMA(List<StockCandleDto> data, int period) {
        double[] result = new double[data.size()];
        for (int i = period - 1; i < data.size(); i++) {
            double sum = 0;
            for (int j = 0; j < period; j++) {
                sum += parseDouble(data.get(i - j).getClose());
            }
            result[i] = sum / period;
        }
        return result;
    }

    private double[] calculateVolumeSMA(List<StockCandleDto> data, int period) {
        double[] result = new double[data.size()];
        for (int i = period - 1; i < data.size(); i++) {
            double sum = 0;
            for (int j = 0; j < period; j++) {
                sum += parseDouble(data.get(i - j).getVolume());
            }
            result[i] = sum / period;
        }
        return result;
    }

    private double[] calculateEMA(List<StockCandleDto> data, int period) {
        double[] result = new double[data.size()];
        if (data.size() < period) return result;

        double k = 2.0 / (period + 1);
        double smaSum = 0;
        for (int i = 0; i < period; i++) {
            smaSum += parseDouble(data.get(i).getClose());
        }
        double prevEMA = smaSum / period;
        result[period - 1] = prevEMA;

        for (int i = period; i < data.size(); i++) {
            double close = parseDouble(data.get(i).getClose());
            double ema = close * k + prevEMA * (1 - k);
            result[i] = ema;
            prevEMA = ema;
        }
        return result;
    }

    private double[] calculateEMAFromArray(double[] values, int period) {
        double[] result = new double[values.length];
        
        int firstValid = -1;
        for (int i=0; i<values.length; i++) {
            if (values[i] != 0.0) { // simplified check
                firstValid = i;
                break;
            }
        }
        if (firstValid == -1 || values.length - firstValid < period) return result;

        double k = 2.0 / (period + 1);
        double smaSum = 0;
        for (int i = firstValid; i < firstValid + period; i++) {
            smaSum += values[i];
        }
        double prevEMA = smaSum / period;
        result[firstValid + period - 1] = prevEMA;

        for (int i = firstValid + period; i < values.length; i++) {
            double val = values[i];
            double ema = val * k + prevEMA * (1 - k);
            result[i] = ema;
            prevEMA = ema;
        }
        return result;
    }

    private double[] calculateRSI(List<StockCandleDto> data, int period) {
        double[] rsi = new double[data.size()];
        if (data.size() <= period) return rsi;

        double gains = 0;
        double losses = 0;

        for (int i = 1; i <= period; i++) {
            double diff = parseDouble(data.get(i).getClose()) - parseDouble(data.get(i - 1).getClose());
            if (diff >= 0) gains += diff;
            else losses -= diff;
        }

        double avgGain = gains / period;
        double avgLoss = losses / period;

        rsi[period] = getRsiValue(avgGain, avgLoss);

        for (int i = period + 1; i < data.size(); i++) {
            double diff = parseDouble(data.get(i).getClose()) - parseDouble(data.get(i - 1).getClose());
            double gain = diff >= 0 ? diff : 0;
            double loss = diff < 0 ? -diff : 0;

            avgGain = (avgGain * (period - 1) + gain) / period;
            avgLoss = (avgLoss * (period - 1) + loss) / period;

            rsi[i] = getRsiValue(avgGain, avgLoss);
        }

        return rsi;
    }

    private double getRsiValue(double avgGain, double avgLoss) {
        if (avgLoss == 0) return 100;
        double rs = avgGain / avgLoss;
        return 100 - (100 / (1 + rs));
    }

    private BollingerResult calculateBollingerBands(List<StockCandleDto> data, int period, double stdDevMult) {
        BollingerResult res = new BollingerResult(data.size());
        for (int i = period - 1; i < data.size(); i++) {
            double sum = 0;
            double[] slice = new double[period];
            for (int j = 0; j < period; j++) {
                double val = parseDouble(data.get(i - j).getClose());
                sum += val;
                slice[j] = val;
            }
            double avg = sum / period;
            res.middle[i] = avg;

            double varianceSum = 0;
            for (double v : slice) {
                varianceSum += Math.pow(v - avg, 2);
            }
            double stdDev = Math.sqrt(varianceSum / period);

            res.upper[i] = avg + (stdDev * stdDevMult);
            res.lower[i] = avg - (stdDev * stdDevMult);
        }
        return res;
    }

    private MacdResult calculateMACD(List<StockCandleDto> data, int shortPeriod, int longPeriod, int signalPeriod) {
        MacdResult res = new MacdResult(data.size());
        double[] emaShort = calculateEMA(data, shortPeriod);
        double[] emaLong = calculateEMA(data, longPeriod);

        for (int i = 0; i < data.size(); i++) {
            if (i >= longPeriod - 1) {
                res.macdLine[i] = emaShort[i] - emaLong[i];
            }
        }

        res.signalLine = calculateEMAFromArray(res.macdLine, signalPeriod);

        for (int i = 0; i < data.size(); i++) {
            if (i >= longPeriod + signalPeriod - 2) {
                res.histogram[i] = res.macdLine[i] - res.signalLine[i];
            }
        }

        return res;
    }

    private static class BollingerResult {
        double[] middle;
        double[] upper;
        double[] lower;

        BollingerResult(int size) {
            middle = new double[size];
            upper = new double[size];
            lower = new double[size];
        }
    }

    private static class MacdResult {
        double[] macdLine;
        double[] signalLine;
        double[] histogram;

        MacdResult(int size) {
            macdLine = new double[size];
            signalLine = new double[size];
            histogram = new double[size];
        }
    }
}
