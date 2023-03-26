package com.mt.access.port.adapter.persistence.organization;

import com.mt.access.domain.model.organization.Organization;
import com.mt.access.domain.model.organization.OrganizationId;
import com.mt.access.domain.model.organization.OrganizationQuery;
import com.mt.access.domain.model.organization.OrganizationRepository;
import com.mt.access.domain.model.organization.Organization_;
import com.mt.access.port.adapter.persistence.QueryBuilderRegistry;
import com.mt.common.domain.model.domain_event.DomainId;
import com.mt.common.domain.model.restful.SumPagedRep;
import com.mt.common.domain.model.restful.query.QueryUtility;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.persistence.criteria.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

public interface SpringDataJpaOrganizationRepository
    extends OrganizationRepository, JpaRepository<Organization, Long> {
    default Optional<Organization> getById(OrganizationId id) {
        return getByQuery(new OrganizationQuery(id)).findFirst();
    }

    default void add(Organization organization) {
        save(organization);
    }

    default void remove(Organization organization) {
        delete(organization);
    }

    default SumPagedRep<Organization> getByQuery(OrganizationQuery organizationQuery) {
        return QueryBuilderRegistry.getOrganizationAdaptor().execute(organizationQuery);
    }

    @Component
    class JpaCriteriaApiOrganizationAdaptor {
        public SumPagedRep<Organization> execute(OrganizationQuery query) {
            QueryUtility.QueryContext<Organization> queryContext =
                QueryUtility.prepareContext(Organization.class, query);
            Optional.ofNullable(query.getIds()).ifPresent(e -> QueryUtility
                .addDomainIdInPredicate(
                    e.stream().map(DomainId::getDomainId).collect(Collectors.toSet()),
                    Organization_.ORGANIZATION_ID, queryContext));
            Order order = null;
            if (query.getSort().isById()) {
                order = QueryUtility.getDomainIdOrder(Organization_.ORGANIZATION_ID, queryContext,
                    query.getSort().isAsc());
            }
            queryContext.setOrder(order);
            return QueryUtility.nativePagedQuery(query, queryContext);
        }
    }
}
