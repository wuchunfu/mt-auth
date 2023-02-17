package com.mt.access.resource;

import static com.mt.common.CommonConstant.HTTP_HEADER_AUTHORIZATION;
import static com.mt.common.CommonConstant.HTTP_PARAM_PAGE;
import static com.mt.common.CommonConstant.HTTP_PARAM_QUERY;
import static com.mt.common.CommonConstant.HTTP_PARAM_SKIP_COUNT;

import com.mt.access.application.ApplicationServiceRegistry;
import com.mt.access.application.notification.representation.BellNotificationRepresentation;
import com.mt.access.application.notification.representation.NotificationRepresentation;
import com.mt.access.domain.DomainRegistry;
import com.mt.access.domain.model.notification.Notification;
import com.mt.common.domain.model.restful.SumPagedRep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping(produces = "application/json")
public class NotificationResource {
    /**
     * get system bell notification
     *
     * @param queryParam query param
     * @param pageParam  page param
     * @param skipCount  skip count
     * @return paginated bell notification
     */
    @GetMapping(path = "mngmt/notifications/bell")
    public ResponseEntity<SumPagedRep<BellNotificationRepresentation>> getBellNotifications(
        @RequestParam(value = HTTP_PARAM_QUERY, required = false) String queryParam,
        @RequestParam(value = HTTP_PARAM_PAGE, required = false) String pageParam,
        @RequestParam(value = HTTP_PARAM_SKIP_COUNT, required = false) String skipCount) {
        SumPagedRep<Notification> notificationsOf =
            ApplicationServiceRegistry.getNotificationApplicationService()
                .bellNotificationsOf(queryParam, pageParam, skipCount);
        return ResponseEntity
            .ok(new SumPagedRep<>(notificationsOf, BellNotificationRepresentation::new));
    }

    @GetMapping(path = "mngmt/notifications")
    public ResponseEntity<SumPagedRep<NotificationRepresentation>> getNotifications(
        @RequestParam(value = HTTP_PARAM_QUERY, required = false) String queryParam,
        @RequestParam(value = HTTP_PARAM_PAGE, required = false) String pageParam,
        @RequestParam(value = HTTP_PARAM_SKIP_COUNT, required = false) String skipCount) {
        SumPagedRep<Notification> notificationsOf =
            ApplicationServiceRegistry.getNotificationApplicationService()
                .notificationsOf(queryParam, pageParam, skipCount);
        return ResponseEntity
            .ok(new SumPagedRep<>(notificationsOf, NotificationRepresentation::new));
    }

    @GetMapping(path = "user/notifications/bell")
    public ResponseEntity<SumPagedRep<BellNotificationRepresentation>> getUserBellNotifications(
        @RequestParam(value = HTTP_PARAM_QUERY, required = false) String queryParam,
        @RequestParam(value = HTTP_PARAM_PAGE, required = false) String pageParam,
        @RequestParam(value = HTTP_PARAM_SKIP_COUNT, required = false) String skipCount,
        @RequestHeader(HTTP_HEADER_AUTHORIZATION) String jwt) {
        DomainRegistry.getCurrentUserService().setUser(jwt);
        SumPagedRep<Notification> notificationsOf =
            ApplicationServiceRegistry.getNotificationApplicationService()
                .notificationsForUser(queryParam, pageParam, skipCount);
        return ResponseEntity
            .ok(new SumPagedRep<>(notificationsOf, BellNotificationRepresentation::new));
    }

    @PostMapping(path = "user/notifications/bell/{id}/ack")
    public ResponseEntity<SumPagedRep<Void>> ackNotificationsForUser(
        @PathVariable(name = "id") String id,
        @RequestHeader(HTTP_HEADER_AUTHORIZATION) String jwt
        ) {
        DomainRegistry.getCurrentUserService().setUser(jwt);
        ApplicationServiceRegistry.getNotificationApplicationService()
            .ackBellNotificationForUser(id);
        return ResponseEntity
            .ok().build();
    }

    @PostMapping(path = "mngmt/notifications/bell/{id}/ack")
    public ResponseEntity<SumPagedRep<Void>> ackNotifications(
        @PathVariable(name = "id") String id) {
        ApplicationServiceRegistry.getNotificationApplicationService()
            .ackBellNotification(id);
        return ResponseEntity
            .ok().build();
    }
}
