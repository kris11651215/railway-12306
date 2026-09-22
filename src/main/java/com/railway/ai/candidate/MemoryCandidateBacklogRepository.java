package com.railway.ai.candidate;

import com.railway.dto.CandidateBacklogVO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class MemoryCandidateBacklogRepository implements CandidateBacklogRepository {

    private static final LocalDate SAMPLE_DATE = LocalDate.of(2026, 4, 15);

    private static final List<OdSeed> SAMPLE_ODS = List.of(
            new OdSeed("广州南", "武汉", "二等座", 157),
            new OdSeed("北京南", "上海虹桥", "二等座", 154),
            new OdSeed("广州南", "深圳北", "二等座", 153),
            new OdSeed("北京南", "杭州东", "二等座", 46),
            new OdSeed("长沙南", "广州南", "一等座", 38));

    @Override
    public List<CandidateBacklogVO> aggregate(LocalDate travelDate) {
        List<CandidateBacklogVO> result = new ArrayList<>();
        int index = 0;
        for (OdSeed seed : SAMPLE_ODS) {
            int waitingCount = travelDate.equals(SAMPLE_DATE)
                    ? seed.waitingCount
                    : generatedCount(travelDate, seed, index);
            CandidateBacklogVO backlog = new CandidateBacklogVO();
            backlog.setFromStation(seed.fromStation);
            backlog.setToStation(seed.toStation);
            backlog.setSeatType(seed.seatType);
            backlog.setWaitingCount(waitingCount);
            backlog.setFirstCandidateTime(firstCandidateTime(travelDate, index));
            result.add(backlog);
            index++;
        }
        result.sort((left, right) -> Integer.compare(right.getWaitingCount(), left.getWaitingCount()));
        return result;
    }

    private int generatedCount(LocalDate travelDate, OdSeed seed, int index) {
        long seedValue = travelDate.toEpochDay() * 31
                + seed.fromStation.hashCode()
                + seed.toStation.hashCode()
                + seed.seatType.hashCode() * 7L
                + index;
        return 60 + (int) Math.floorMod(seedValue, 200);
    }

    private LocalDateTime firstCandidateTime(LocalDate travelDate, int index) {
        return travelDate.minusDays(7).atTime(8, 15).plusMinutes(index * 7L);
    }

    private static class OdSeed {

        private final String fromStation;
        private final String toStation;
        private final String seatType;
        private final int waitingCount;

        OdSeed(String fromStation, String toStation, String seatType, int waitingCount) {
            this.fromStation = fromStation;
            this.toStation = toStation;
            this.seatType = seatType;
            this.waitingCount = waitingCount;
        }
    }
}
