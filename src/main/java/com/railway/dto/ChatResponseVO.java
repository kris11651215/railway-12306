package com.railway.dto;

import java.util.List;

public class ChatResponseVO {

    private String reply;
    private AiIntentVO intent;
    private List<ToolCallTraceVO> toolCalls;
    private List<TrainVO> trains;
    private TransferPlanVO transferPlan;
    private RagAnswerVO knowledge;
    private boolean degraded;
    private String provider;

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }

    public AiIntentVO getIntent() {
        return intent;
    }

    public void setIntent(AiIntentVO intent) {
        this.intent = intent;
    }

    public List<ToolCallTraceVO> getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(List<ToolCallTraceVO> toolCalls) {
        this.toolCalls = toolCalls;
    }

    public List<TrainVO> getTrains() {
        return trains;
    }

    public void setTrains(List<TrainVO> trains) {
        this.trains = trains;
    }

    public TransferPlanVO getTransferPlan() {
        return transferPlan;
    }

    public void setTransferPlan(TransferPlanVO transferPlan) {
        this.transferPlan = transferPlan;
    }

    public RagAnswerVO getKnowledge() {
        return knowledge;
    }

    public void setKnowledge(RagAnswerVO knowledge) {
        this.knowledge = knowledge;
    }

    public boolean isDegraded() {
        return degraded;
    }

    public void setDegraded(boolean degraded) {
        this.degraded = degraded;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }
}
