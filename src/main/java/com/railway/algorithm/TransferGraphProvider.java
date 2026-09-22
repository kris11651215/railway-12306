package com.railway.algorithm;

import java.time.LocalDate;

public interface TransferGraphProvider {

    TimeExpandedGraph graph(LocalDate travelDate);
}
