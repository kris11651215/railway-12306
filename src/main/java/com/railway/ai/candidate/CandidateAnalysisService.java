package com.railway.ai.candidate;

import com.railway.ai.AiProperties;
import com.railway.dto.CandidateBacklogVO;
import com.railway.dto.CandidateSuggestionVO;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class CandidateAnalysisService {

    private static final int REPORT_READY_MINUTES = 30;
    private static final int DECISION_WINDOW_MINUTES = 120;
    private static final double ADD_TRAIN_INDEX = 150.0;

    private final CandidateBacklogRepository backlogRepository;
    private final AiProperties properties;

    public CandidateAnalysisService(CandidateBacklogRepository backlogRepository, AiProperties properties) {
        this.backlogRepository = backlogRepository;
        this.properties = properties;
    }

    public CandidateSuggestionVO analyze(LocalDate travelDate) {
        List<CandidateBacklogVO> backlogs = backlogRepository.aggregate(travelDate);
        List<CandidateSuggestionVO.Item> items = new ArrayList<>();
        for (CandidateBacklogVO backlog : backlogs) {
            double index = backlogIndex(backlog, travelDate);
            if (index >= properties.getBacklogThreshold()) {
                items.add(toItem(backlog, index));
            }
        }
        int overThreshold = items.size();
        items.sort(Comparator.comparingDouble(CandidateSuggestionVO.Item::getBacklogIndex).reversed());
        if (items.size() > properties.getSuggestTopN()) {
            items = new ArrayList<>(items.subList(0, properties.getSuggestTopN()));
        }

        LocalDateTime now = LocalDateTime.now();
        CandidateSuggestionVO suggestion = new CandidateSuggestionVO();
        suggestion.setDate(travelDate.toString());
        suggestion.setGeneratedAt(now);
        suggestion.setReportDeadline(now.plusMinutes(REPORT_READY_MINUTES));
        suggestion.setDecisionDeadline(now.plusMinutes(DECISION_WINDOW_MINUTES));
        suggestion.setReportReadyMinutes(REPORT_READY_MINUTES);
        suggestion.setDecisionWindowMinutes(DECISION_WINDOW_MINUTES);
        suggestion.setThreshold(properties.getBacklogThreshold());
        suggestion.setScannedOdCount(backlogs.size());
        suggestion.setOverThresholdCount(overThreshold);
        suggestion.setItems(items);
        suggestion.setSummary(buildSummary(travelDate, backlogs.size(), overThreshold, items));
        return suggestion;
    }

    public double backlogIndex(CandidateBacklogVO backlog, LocalDate travelDate) {
        int waitingCount = backlog.getWaitingCount() == null ? 0 : backlog.getWaitingCount();
        double seatWeight = seatWeight(backlog.getSeatType());
        double urgency = urgencyFactor(travelDate);
        return round(waitingCount * seatWeight * urgency, 2);
    }

    private CandidateSuggestionVO.Item toItem(CandidateBacklogVO backlog, double index) {
        int waitingCount = backlog.getWaitingCount() == null ? 0 : backlog.getWaitingCount();
        boolean addTrain = index >= ADD_TRAIN_INDEX;
        CandidateSuggestionVO.Item item = new CandidateSuggestionVO.Item();
        item.setFromStation(backlog.getFromStation());
        item.setToStation(backlog.getToStation());
        item.setSeatType(backlog.getSeatType());
        item.setWaitingCount(waitingCount);
        item.setBacklogIndex(index);
        item.setActionType(addTrain ? "ADD_TRAIN" : "REALLOCATE_QUOTA");
        item.setActionName(addTrain ? "加开列车" : "票额调配");
        item.setSuggestedFormation(formation(waitingCount));
        item.setEstimatedLoadFactor(round(Math.min(0.98, 0.55 + index / 1000.0), 3));
        item.setConfidence(round(Math.min(0.97, 0.60 + Math.log10(waitingCount + 1) / 10.0), 3));
        item.setReason(addTrain
                ? "候补积压指数 " + index + " 超过加开阈值 " + ADD_TRAIN_INDEX
                + "，建议加开 " + backlog.getFromStation() + "→" + backlog.getToStation() + " 方向列车"
                : "候补积压中等，建议优先调整票额分配（长途票额调配短途或加挂车厢）");
        return item;
    }

    private String buildSummary(LocalDate travelDate, int scanned, int overThreshold,
                                List<CandidateSuggestionVO.Item> items) {
        if (overThreshold == 0) {
            return travelDate + " 共扫描 " + scanned + " 个候补OD方向，未发现超过积压阈值的方向，维持现有运力。";
        }
        StringBuilder summary = new StringBuilder();
        summary.append(travelDate).append(" 共扫描 ").append(scanned).append(" 个候补OD方向，")
                .append(overThreshold).append(" 个方向超过积压阈值，建议优先处理：");
        int limit = Math.min(items.size(), 3);
        for (int index = 0; index < limit; index++) {
            CandidateSuggestionVO.Item item = items.get(index);
            if (index > 0) {
                summary.append("；");
            }
            summary.append(item.getFromStation()).append("→").append(item.getToStation())
                    .append("（").append(item.getSeatType()).append("候补")
                    .append(item.getWaitingCount()).append("人，").append(item.getActionName()).append("）");
        }
        summary.append("。报告已生成，30分钟内可流转，2小时内可完成加开决策。");
        return summary.toString();
    }

    private String formation(int waitingCount) {
        if (waitingCount >= 300) {
            return "重联16节（2组）";
        }
        if (waitingCount >= 150) {
            return "16节";
        }
        return "8节";
    }

    private double seatWeight(String seatType) {
        if (seatType == null) {
            return 1.0;
        }
        return switch (seatType) {
            case "一等座" -> 0.6;
            case "商务座" -> 0.4;
            case "硬座" -> 0.9;
            case "硬卧" -> 0.8;
            case "软卧" -> 0.7;
            default -> 1.0;
        };
    }

    private double urgencyFactor(LocalDate travelDate) {
        long days = ChronoUnit.DAYS.between(LocalDate.now(), travelDate);
        if (days < 0) {
            return 1.0;
        }
        if (days <= 3) {
            return 1.3;
        }
        if (days <= 7) {
            return 1.15;
        }
        if (days <= 15) {
            return 1.05;
        }
        return 1.0;
    }

    private double round(double value, int scale) {
        double factor = Math.pow(10, scale);
        return Math.round(value * factor) / factor;
    }
}
