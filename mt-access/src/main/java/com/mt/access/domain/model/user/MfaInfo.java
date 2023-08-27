package com.mt.access.domain.model.user;

import javax.persistence.Embeddable;
import javax.persistence.Embedded;
import lombok.Getter;

@Embeddable
public class MfaInfo {
    @Getter
    @Embedded
    private MfaCode code;
    @Getter
    @Embedded
    private MfaId id;

    public static MfaInfo create() {
        MfaInfo mfaInfo = new MfaInfo();
        mfaInfo.code = new MfaCode();
        mfaInfo.id = new MfaId();
        return mfaInfo;
    }

    public static MfaInfo deserialize(MfaId id, MfaCode code) {
        MfaInfo mfaInfo = new MfaInfo();
        mfaInfo.id = id;
        mfaInfo.code = code;
        return mfaInfo;
    }

    public boolean validate(String mfaCode, String mfaId) {
        if (code == null || id == null) {
            return false;
        }
        return mfaCode.equals(code.getValue()) && mfaId.equals(id.getValue());
    }
}
