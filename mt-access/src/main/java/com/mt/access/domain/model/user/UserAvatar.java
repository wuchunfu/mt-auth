package com.mt.access.domain.model.user;

import com.mt.access.domain.model.image.ImageId;
import javax.persistence.Column;
import javax.persistence.Embeddable;
import lombok.Data;

@Data
public class UserAvatar {
    private String value;

    private UserAvatar() {
    }

    public UserAvatar(ImageId imageId) {
        this.value = imageId.getDomainId();
    }
}
