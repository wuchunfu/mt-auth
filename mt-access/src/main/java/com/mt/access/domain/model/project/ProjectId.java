package com.mt.access.domain.model.project;

import com.mt.common.domain.CommonDomainRegistry;
import com.mt.common.domain.model.domain_id.DomainId;
import com.mt.common.domain.model.domain_id.GeneratedDomainId;
import java.io.Serializable;

public class ProjectId extends GeneratedDomainId implements Serializable {
    public ProjectId() {
        super();
    }

    public ProjectId(String domainId) {
        super(domainId);
    }

    @Override
    protected String getPrefix() {
        return "0P";
    }
}