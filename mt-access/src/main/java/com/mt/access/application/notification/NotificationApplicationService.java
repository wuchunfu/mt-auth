package com.mt.access.application.notification;

import com.mt.access.domain.DomainRegistry;
import com.mt.access.domain.model.CrossDomainValidationService;
import com.mt.access.domain.model.cross_domain_validation.event.CrossDomainValidationFailureCheck;
import com.mt.access.domain.model.notification.Notification;
import com.mt.access.domain.model.notification.NotificationId;
import com.mt.access.domain.model.notification.NotificationQuery;
import com.mt.access.domain.model.notification.NotificationType;
import com.mt.access.domain.model.notification.event.SendBellNotificationEvent;
import com.mt.access.domain.model.notification.event.SendEmailNotificationEvent;
import com.mt.access.domain.model.notification.event.SendSmsNotificationEvent;
import com.mt.access.domain.model.pending_user.event.PendingUserActivationCodeUpdated;
import com.mt.access.domain.model.pending_user.event.PendingUserCreated;
import com.mt.access.domain.model.proxy.event.ProxyCacheCheckFailedEvent;
import com.mt.access.domain.model.report.event.RawAccessRecordProcessingWarning;
import com.mt.access.domain.model.sub_request.event.SubscriberEndpointExpireEvent;
import com.mt.access.domain.model.user.event.NewUserRegistered;
import com.mt.access.domain.model.user.event.ProjectOnboardingComplete;
import com.mt.access.domain.model.user.event.UserMfaNotificationEvent;
import com.mt.access.domain.model.user.event.UserPwdResetCodeUpdated;
import com.mt.common.application.CommonApplicationServiceRegistry;
import com.mt.common.domain.CommonDomainRegistry;
import com.mt.common.domain.model.domain_event.event.RejectedMsgReceivedEvent;
import com.mt.common.domain.model.domain_event.event.UnrountableMsgReceivedEvent;
import com.mt.common.domain.model.idempotent.event.HangingTxDetected;
import com.mt.common.domain.model.job.event.JobNotFoundEvent;
import com.mt.common.domain.model.job.event.JobPausedEvent;
import com.mt.common.domain.model.job.event.JobStarvingEvent;
import com.mt.common.domain.model.job.event.JobThreadStarvingEvent;
import com.mt.common.domain.model.restful.SumPagedRep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class NotificationApplicationService {
    private static final String NOTIFICATION = "Notification";
    @Value("${instanceId}")
    private Long instanceId;

    public SumPagedRep<Notification> queryBell(String queryParam, String pageParam,
                                               String skipCount) {
        NotificationQuery notificationQuery =
            new NotificationQuery(NotificationType.BELL, queryParam, pageParam, skipCount);
        return DomainRegistry.getNotificationRepository()
            .notificationsOfQuery(notificationQuery);
    }

    public SumPagedRep<Notification> mgmtQuery(String queryParam, String pageParam,
                                               String skipCount) {
        NotificationQuery notificationQuery =
            new NotificationQuery(queryParam, pageParam, skipCount);
        return DomainRegistry.getNotificationRepository()
            .notificationsOfQuery(notificationQuery);
    }

    public SumPagedRep<Notification> userQuery(String queryParam, String pageParam,
                                               String skipCount) {
        NotificationQuery notificationQuery =
            NotificationQuery.queryForUser(queryParam, pageParam, skipCount,
                DomainRegistry.getCurrentUserService().getUserId());
        return DomainRegistry.getNotificationRepository()
            .notificationsOfQuery(notificationQuery);
    }

    @Transactional
    public void userAckBell(String id) {
        DomainRegistry.getNotificationRepository()
            .acknowledgeForUser(new NotificationId(id),
                DomainRegistry.getCurrentUserService().getUserId());
    }

    /**
     * acknowledge notification,
     * no idempotent wrapper required bcz itself is native idempotent.
     *
     * @param id notification id
     */
    @Transactional
    public void ackBell(String id) {
        DomainRegistry.getNotificationRepository()
            .acknowledge(new NotificationId(id));
    }

    public void handle(HangingTxDetected event) {
        Notification notification = new Notification(event);
        sendBellNotification(event.getId(), notification);
    }

    public void handle(NewUserRegistered event) {
        Notification notification = new Notification(event);
        sendBellNotification(event.getId(), notification);
    }

    public void handle(ProjectOnboardingComplete event) {
        Notification notification = new Notification(event);
        sendBellNotification(event.getId(), notification);
    }

    public void handle(ProxyCacheCheckFailedEvent event) {
        Notification notification = new Notification(event);
        sendBellNotification(event.getId(), notification);
    }

    public void handle(CrossDomainValidationService.ValidationFailedEvent event) {
        Notification notification = new Notification(event);
        sendBellNotification(event.getId(), notification);
    }

    public void handle(UserMfaNotificationEvent event) {
        CommonApplicationServiceRegistry.getIdempotentService()
            .idempotent(event.getId().toString(), (command) -> {
                Notification notification = new Notification(event);
                DomainRegistry.getNotificationRepository().add(notification);
                CommonDomainRegistry.getDomainEventRepository()
                    .append(new SendSmsNotificationEvent(event, notification));
                return null;
            }, NOTIFICATION);
    }

    public void handle(UserPwdResetCodeUpdated event) {
        Notification notification = new Notification(event);
        SendEmailNotificationEvent sendEmailNotificationEvent =
            new SendEmailNotificationEvent(event, notification);
        sendEmailNotification(event.getId(), notification, sendEmailNotificationEvent);
    }

    public void handle(PendingUserActivationCodeUpdated event) {
        Notification notification = new Notification(event);
        SendEmailNotificationEvent sendEmailNotificationEvent =
            new SendEmailNotificationEvent(event, notification);
        sendEmailNotification(event.getId(), notification, sendEmailNotificationEvent);
    }

    public void handle(CrossDomainValidationFailureCheck event) {
        Notification notification = new Notification(event);
        SendEmailNotificationEvent sendEmailNotificationEvent =
            new SendEmailNotificationEvent(event, notification);
        sendEmailNotification(event.getId(), notification, sendEmailNotificationEvent);
    }

    /**
     * send bell notification to stored ws session
     * idempotent is for each instance
     *
     * @param event send bell notification event
     */
    public void handle(SendBellNotificationEvent event) {
        try {
            CommonApplicationServiceRegistry.getIdempotentService()
                .idempotent(event.getId().toString(), (ignored) -> {
                    log.debug("sending bell notifications with {}", event.getTitle());
                    if (event.getUserId() != null) {
                        DomainRegistry.getWsPushNotificationService()
                            .notifyUser(event.value(), event.getUserId());
                    } else {
                        DomainRegistry.getWsPushNotificationService()
                            .notifyMgmt(event.value());
                    }
                    Notification notification = DomainRegistry.getNotificationRepository()
                        .get(new NotificationId(event.getDomainId().getDomainId()));
                    notification.markAsDelivered();
                    return null;
                }, NOTIFICATION + "_" + instanceId);

        } catch (ObjectOptimisticLockingFailureException exception) {
            log.warn(
                "ignore optimistic lock exception when sending bell notification on purpose");
        }

    }

    /**
     * send sms to mobile
     *
     * @param event send bell notification event
     */
    public void handle(SendSmsNotificationEvent event) {
        CommonApplicationServiceRegistry.getIdempotentService()
            .idempotent(event.getId().toString(), (ignored) -> {
                DomainRegistry.getSmsNotificationService()
                    .notify(event.getMobile(), event.getCode());
                Notification notification = DomainRegistry.getNotificationRepository()
                    .get(new NotificationId(event.getDomainId().getDomainId()));
                notification.markAsDelivered();
                return null;
            }, NOTIFICATION);

    }

    /**
     * send email notification
     *
     * @param event send email notification event
     */
    public void handle(SendEmailNotificationEvent event) {
        CommonApplicationServiceRegistry.getIdempotentService()
            .idempotent(event.getId().toString(), (ignored) -> {
                DomainRegistry.getEmailNotificationService()
                    .notify(event.getEmail(), event.getTemplateUrl(), event.getSubject(),
                        event.getParams());
                Notification notification = DomainRegistry.getNotificationRepository()
                    .get(new NotificationId(event.getDomainId().getDomainId()));
                notification.markAsDelivered();
                return null;
            }, NOTIFICATION);
    }

    public void handle(UnrountableMsgReceivedEvent event) {
        Notification notification = new Notification(event);
        sendBellNotification(event.getId(), notification);
    }

    /**
     * send bell notification to subscriber
     *
     * @param event SubscriberEndpointExpireEvent
     */
    public void handle(SubscriberEndpointExpireEvent event) {
        event.getDomainIds().forEach(e -> {
            Notification notification = new Notification(event, e);
            sendBellNotification(event.getId(), notification);
        });
    }

    public void handle(JobPausedEvent event) {
        Notification notification = new Notification(event);
        sendBellNotification(event.getId(), notification);
    }

    public void handle(JobNotFoundEvent event) {
        Notification notification = new Notification(event);
        sendBellNotification(event.getId(), notification);
    }

    public void handle(JobThreadStarvingEvent event) {
        Notification notification = new Notification(event);
        sendBellNotification(event.getId(), notification);
    }

    public void handle(JobStarvingEvent event) {
        Notification notification = new Notification(event);
        sendBellNotification(event.getId(), notification);
    }

    public void handle(PendingUserCreated event) {
        Notification notification = new Notification(event);
        sendBellNotification(event.getId(), notification);
    }

    public void handle(RejectedMsgReceivedEvent event) {
        if (!event.getSourceName().equalsIgnoreCase(SendBellNotificationEvent.name)) {
            //avoid infinite loop when send bell notification got rejected
            Notification notification = new Notification(event);
            sendBellNotification(event.getId(), notification);
        }
    }

    public void handle(RawAccessRecordProcessingWarning event) {
        Notification notification = new Notification(event);
        sendBellNotification(event.getId(), notification);
    }

    private void sendBellNotification(Long eventId, Notification notification1) {
        CommonApplicationServiceRegistry.getIdempotentService()
            .idempotent(eventId.toString(), (command) -> {
                DomainRegistry.getNotificationRepository().add(notification1);
                CommonDomainRegistry.getDomainEventRepository()
                    .append(new SendBellNotificationEvent(notification1));
                return null;
            }, NOTIFICATION);
    }

    private void sendEmailNotification(Long id, Notification notification,
                                       SendEmailNotificationEvent sendEmailNotificationEvent) {
        CommonApplicationServiceRegistry.getIdempotentService()
            .idempotent(id.toString(), (command) -> {
                DomainRegistry.getNotificationRepository().add(notification);
                CommonDomainRegistry.getDomainEventRepository()
                    .append(sendEmailNotificationEvent);
                return null;
            }, NOTIFICATION);
    }

}
