package com.mt.access.domain.model.notification;

import com.mt.common.domain.model.restful.query.PageConfig;
import com.mt.common.domain.model.restful.query.QueryConfig;
import com.mt.common.domain.model.restful.query.QueryCriteria;
import com.mt.common.domain.model.restful.query.QueryUtility;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;

@Getter
public class NotificationQuery extends QueryCriteria {
    private static final String UN_ACK = "unAck";
    private final Sort sort;
    private Boolean isUnAck;

    public NotificationQuery(String queryParam, String pageParam, String skipCount) {
        updateQueryParam(queryParam);
        setPageConfig(PageConfig.limited(pageParam, 200));
        setQueryConfig(new QueryConfig(skipCount));
        this.sort = Sort.byLatestTimestamp();
    }

    private void updateQueryParam(String queryParam) {
        Map<String, String> stringStringMap = QueryUtility.parseQuery(queryParam,
            UN_ACK);
        Optional.ofNullable(stringStringMap.get(UN_ACK)).ifPresent(e -> {
            isUnAck = Boolean.TRUE;
        });
    }

    @Getter
    public static class Sort {
        private boolean isAsc;
        private boolean isTimestamp = false;

        public static Sort byLatestTimestamp() {
            Sort sort = new Sort();
            sort.isTimestamp = true;
            sort.isAsc = false;
            return sort;
        }
    }
}
