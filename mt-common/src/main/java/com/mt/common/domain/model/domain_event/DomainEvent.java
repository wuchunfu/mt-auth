package com.mt.common.domain.model.domain_event;

import com.mt.common.domain.CommonDomainRegistry;
import com.mt.common.domain.model.validate.Validator;
import java.io.Serializable;
import java.util.Date;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter(AccessLevel.PUBLIC)
//cannot be abstract due to serialization issue
public class DomainEvent implements Serializable {

    private Long id;

    private Long timestamp;

    private String name;

    private DomainId domainId;

    private Set<DomainId> domainIds;
    private boolean internal = true;
    private String topic;

    public void setDomainId(DomainId domainId) {
        Validator.notNull(domainId);
        Validator.notNull(domainId.getDomainId());
        this.domainId = domainId;
    }

    public DomainEvent(DomainId domainId) {
        setId(CommonDomainRegistry.getUniqueIdGeneratorService().id());
        setTimestamp(new Date().getTime());
        setDomainId(domainId);
    }

    public DomainEvent(Set<DomainId> domainIds) {
        setId(CommonDomainRegistry.getUniqueIdGeneratorService().id());
        setTimestamp(new Date().getTime());
        setDomainIds(domainIds);
        setDomainId(new AnyDomainId());//add dummy domain id so it can be deserialized
    }

    public DomainEvent() {
        setId(CommonDomainRegistry.getUniqueIdGeneratorService().id());
        setTimestamp(new Date().getTime());
    }

    @Override
    public String toString() {
        return "DomainEvent{" +
            "id=" + id +
            ", timestamp=" + timestamp +
            ", name='" + name + '\'' +
            ", domainId=" + domainId +
            ", domainIds=" + domainIds +
            ", internal=" + internal +
            ", topic='" + topic + '\'' +
            '}';
    }
}
