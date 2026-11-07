package com.stravamirror.repository;

import com.stravamirror.entity.ActivitySummary;
import java.util.List;

public interface ActivitySummaryRepositoryCustom {

    int bulkInsertOnConflictDoNothing(List<ActivitySummary> activities);
}
