package com.railway.dto;

import java.time.LocalDateTime;
import java.util.List;

public class CandidateSuggestionVO {

    private String date;
    private LocalDateTime generatedAt;
    private LocalDateTime reportDeadline;
    private LocalDateTime decisionDeadline;
    private int reportReadyMinutes;
    private int decisionWindowMinutes;
    private double threshold;
    private int scannedOdCount;
    private int overThresholdCount;
    private String summary;
    private List<Item> items;

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }

    public LocalDateTime getReportDeadline() {
        return reportDeadline;
    }

    public void setReportDeadline(LocalDateTime reportDeadline) {
        this.reportDeadline = reportDeadline;
    }

    public LocalDateTime getDecisionDeadline() {
        return decisionDeadline;
    }

    public void setDecisionDeadline(LocalDateTime decisionDeadline) {
        this.decisionDeadline = decisionDeadline;
    }

    public int getReportReadyMinutes() {
        return reportReadyMinutes;
    }

    public void setReportReadyMinutes(int reportReadyMinutes) {
        this.reportReadyMinutes = reportReadyMinutes;
    }

    public int getDecisionWindowMinutes() {
        return decisionWindowMinutes;
    }

    public void setDecisionWindowMinutes(int decisionWindowMinutes) {
        this.decisionWindowMinutes = decisionWindowMinutes;
    }

    public double getThreshold() {
        return threshold;
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }

    public int getScannedOdCount() {
        return scannedOdCount;
    }

    public void setScannedOdCount(int scannedOdCount) {
        this.scannedOdCount = scannedOdCount;
    }

    public int getOverThresholdCount() {
        return overThresholdCount;
    }

    public void setOverThresholdCount(int overThresholdCount) {
        this.overThresholdCount = overThresholdCount;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items;
    }

    public static class Item {

        private String fromStation;
        private String toStation;
        private String seatType;
        private int waitingCount;
        private double backlogIndex;
        private String actionType;
        private String actionName;
        private String suggestedFormation;
        private double estimatedLoadFactor;
        private double confidence;
        private String reason;

        public String getFromStation() {
            return fromStation;
        }

        public void setFromStation(String fromStation) {
            this.fromStation = fromStation;
        }

        public String getToStation() {
            return toStation;
        }

        public void setToStation(String toStation) {
            this.toStation = toStation;
        }

        public String getSeatType() {
            return seatType;
        }

        public void setSeatType(String seatType) {
            this.seatType = seatType;
        }

        public int getWaitingCount() {
            return waitingCount;
        }

        public void setWaitingCount(int waitingCount) {
            this.waitingCount = waitingCount;
        }

        public double getBacklogIndex() {
            return backlogIndex;
        }

        public void setBacklogIndex(double backlogIndex) {
            this.backlogIndex = backlogIndex;
        }

        public String getActionType() {
            return actionType;
        }

        public void setActionType(String actionType) {
            this.actionType = actionType;
        }

        public String getActionName() {
            return actionName;
        }

        public void setActionName(String actionName) {
            this.actionName = actionName;
        }

        public String getSuggestedFormation() {
            return suggestedFormation;
        }

        public void setSuggestedFormation(String suggestedFormation) {
            this.suggestedFormation = suggestedFormation;
        }

        public double getEstimatedLoadFactor() {
            return estimatedLoadFactor;
        }

        public void setEstimatedLoadFactor(double estimatedLoadFactor) {
            this.estimatedLoadFactor = estimatedLoadFactor;
        }

        public double getConfidence() {
            return confidence;
        }

        public void setConfidence(double confidence) {
            this.confidence = confidence;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }
}
