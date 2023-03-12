package com.mt.access.domain.model.user;

import com.google.common.base.Objects;
import com.mt.access.domain.DomainRegistry;
import com.mt.access.domain.model.user.event.UserGetLocked;
import com.mt.access.domain.model.user.event.UserPwdResetCodeUpdated;
import com.mt.common.domain.CommonDomainRegistry;
import com.mt.common.domain.model.audit.Auditable;
import com.mt.common.domain.model.audit.NextAuditable;
import com.mt.common.domain.model.exception.DefinedRuntimeException;
import com.mt.common.domain.model.exception.ExceptionCatalog;
import com.mt.common.domain.model.exception.HttpResponseCode;
import com.mt.common.domain.model.validate.ValidationNotificationHandler;
import com.mt.common.infrastructure.HttpValidationNotificationHandler;
import java.util.Arrays;
import javax.annotation.Nullable;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Where;

/**
 * user aggregate.
 */
@Entity
@Table(name = "user_")
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = "userRegion")
public class User extends NextAuditable {
    private static final String[] ROOT_ACCOUNTS = {"0U8AZTODP4H0"};
    @Setter(AccessLevel.PRIVATE)
    @Embedded
    @Getter
    private UserEmail email;
    @Embedded
    @Getter
    @Setter
    private UserPassword password;
    @Embedded
    @Getter
    @Setter
    private UserMobile mobile;
    @Embedded
    @Getter
    @Setter
    @Nullable
    private UserAvatar userAvatar;
    @Embedded
    @Getter
    @Setter
    private UserName userName;
    @Getter
    @Setter
    @Convert(converter = Language.DbConverter.class)
    private Language language;
    @Embedded
    @Getter
    @Setter(AccessLevel.PRIVATE)
    private UserId userId;
    @Column
    @Setter(AccessLevel.PRIVATE)
    @Getter
    private boolean locked = false;

    @Getter
    @Embedded
    private PasswordResetCode pwdResetToken;
    @Getter
    @Setter
    @Embedded
    private MfaInfo mfaInfo;

    private User(UserEmail userEmail, UserPassword password, UserId userId, UserMobile mobile) {
        super();
        setEmail(userEmail);
        setPassword(password);
        setUserId(userId);
        setLocked(false);
        setMobile(mobile);
        setId(CommonDomainRegistry.getUniqueIdGeneratorService().id());
        DomainRegistry.getUserValidationService()
            .validate(this, new HttpValidationNotificationHandler());
    }

    private User() {
    }

    public static User newUser(UserEmail userEmail, UserPassword password, UserId userId,
                               UserMobile mobile) {
        return new User(userEmail, password, userId, mobile);
    }

    public String getDisplayName() {
        if (userName != null) {
            return userName.getValue();
        }
        return email.getEmail();
    }

    @Override
    public void validate(@NotNull ValidationNotificationHandler handler) {
        (new UserValidator(this, handler)).validate();
    }

    public void setPwdResetToken(PasswordResetCode pwdResetToken) {
        this.pwdResetToken = pwdResetToken;
        CommonDomainRegistry.getDomainEventRepository()
            .append(new UserPwdResetCodeUpdated(getUserId(), getEmail(), getPwdResetToken()));
    }


    public void lockUser(boolean locked) {
        if (Arrays.stream(ROOT_ACCOUNTS)
            .anyMatch(e -> e.equalsIgnoreCase(this.userId.getDomainId()))) {
            throw new DefinedRuntimeException("root account cannot be locked", "0062",
                HttpResponseCode.BAD_REQUEST,
                ExceptionCatalog.ILLEGAL_ARGUMENT);
        }
        CommonDomainRegistry.getDomainEventRepository().append(new UserGetLocked(userId));
        setLocked(locked);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof User)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        User user = (User) o;
        return Objects.equal(userId, user.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(super.hashCode(), userId);
    }

    public void update(UserMobile mobile,
                       @Nullable UserName userName,
                       @Nullable Language language) {
        if (userName != null) {
            if (this.userName != null && this.userName.getValue() != null
                && !this.userName.equals(userName)) {
                throw new DefinedRuntimeException("username can only be set once", "0063",
                    HttpResponseCode.BAD_REQUEST,
                    ExceptionCatalog.ILLEGAL_ARGUMENT);
            }
            this.userName = userName;
        }
        if (language != null) {
            this.language = language;
        }
        if (!this.mobile.equals(mobile)) {
            this.mobile = mobile;
        }
    }
}
