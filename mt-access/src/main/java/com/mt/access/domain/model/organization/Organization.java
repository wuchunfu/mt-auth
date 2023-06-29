package com.mt.access.domain.model.organization;

import com.mt.access.domain.model.project.ProjectId;
import com.mt.common.domain.CommonDomainRegistry;
import com.mt.common.domain.model.audit.Auditable;
import javax.persistence.AttributeOverride;
import javax.persistence.AttributeOverrides;
import javax.persistence.Cacheable;
import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CacheConcurrencyStrategy;

@Table
@Entity
@NoArgsConstructor
@Getter
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE,
    region = "organizationRegion")
@EqualsAndHashCode(callSuper = true)
public class Organization extends Auditable {
    private String name;

    @Embedded
    private OrganizationId organizationId;
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "domainId", column = @Column(name = "projectId"))
    })
    private ProjectId projectId;

    public Organization(OrganizationId organizationId, String name) {
        super();
        this.id = CommonDomainRegistry.getUniqueIdGeneratorService().id();
        this.organizationId = organizationId;
        this.name = name;
    }

    public void replace(String name) {
        this.name = name;
    }

}
