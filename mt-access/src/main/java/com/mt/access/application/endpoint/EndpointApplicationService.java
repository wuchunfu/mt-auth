package com.mt.access.application.endpoint;

import static com.mt.access.domain.model.permission.Permission.CREATE_API;
import static com.mt.access.domain.model.permission.Permission.EDIT_API;
import static com.mt.access.domain.model.permission.Permission.VIEW_API;

import com.github.fge.jsonpatch.JsonPatch;
import com.mt.access.application.endpoint.command.EndpointCreateCommand;
import com.mt.access.application.endpoint.command.EndpointExpireCommand;
import com.mt.access.application.endpoint.command.EndpointPatchCommand;
import com.mt.access.application.endpoint.command.EndpointUpdateCommand;
import com.mt.access.application.endpoint.representation.EndpointProxyCacheRepresentation;
import com.mt.access.domain.DomainRegistry;
import com.mt.access.domain.model.audit.AuditLog;
import com.mt.access.domain.model.cache_profile.CacheProfileId;
import com.mt.access.domain.model.cache_profile.event.CacheProfileRemoved;
import com.mt.access.domain.model.cache_profile.event.CacheProfileUpdated;
import com.mt.access.domain.model.client.Client;
import com.mt.access.domain.model.client.ClientId;
import com.mt.access.domain.model.client.event.ClientDeleted;
import com.mt.access.domain.model.cors_profile.CorsProfileId;
import com.mt.access.domain.model.cors_profile.event.CorsProfileRemoved;
import com.mt.access.domain.model.cors_profile.event.CorsProfileUpdated;
import com.mt.access.domain.model.endpoint.Endpoint;
import com.mt.access.domain.model.endpoint.EndpointId;
import com.mt.access.domain.model.endpoint.EndpointQuery;
import com.mt.access.domain.model.endpoint.event.EndpointCollectionModified;
import com.mt.access.domain.model.project.ProjectId;
import com.mt.common.application.CommonApplicationServiceRegistry;
import com.mt.common.domain.CommonDomainRegistry;
import com.mt.common.domain.model.domain_event.event.ApplicationStartedEvent;
import com.mt.common.domain.model.exception.DefinedRuntimeException;
import com.mt.common.domain.model.exception.ExceptionCatalog;
import com.mt.common.domain.model.exception.HttpResponseCode;
import com.mt.common.domain.model.restful.SumPagedRep;
import com.mt.common.domain.model.restful.query.QueryUtility;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class EndpointApplicationService {
    private static final String ENDPOINT = "Endpoint";
    @Value("${proxy.reload}")
    private boolean reloadOnAppStart;
    @Value("${spring.application.name}")
    private String appName;

    /**
     * send app started event with a delay of 60s to wait for registry complete.
     */
    @EventListener(ApplicationReadyEvent.class)
    protected void reloadProxy() {
        if (reloadOnAppStart) {
            try {
                Thread.sleep(90 * 1000);
            } catch (InterruptedException e) {
                log.error("wait is interrupted due to", e);
            }
            log.debug("sending reload proxy endpoint message");
            CommonDomainRegistry.getEventStreamService()
                .next(appName, false, "started_access", ApplicationStartedEvent.create());
        }
    }

    @Transactional
    @AuditLog(actionName = "create api")
    public String create(String rawProjectId, EndpointCreateCommand command, String changeId) {
        EndpointId endpointId = new EndpointId();
        ProjectId projectId = new ProjectId(rawProjectId);
        DomainRegistry.getPermissionCheckService().canAccess(projectId, CREATE_API);
        return CommonApplicationServiceRegistry.getIdempotentService()
            .idempotent(changeId, (change) -> {
                String clientId = command.getResourceId();
                Optional<Client> optional =
                    DomainRegistry.getClientRepository().clientOfId(new ClientId(clientId));
                if (optional.isPresent()) {
                    Client client = optional.get();
                    if (!client.getProjectId().equals(projectId)) {
                        throw new DefinedRuntimeException("project id mismatch", "0010",
                            HttpResponseCode.BAD_REQUEST,
                            ExceptionCatalog.ILLEGAL_ARGUMENT);
                    }
                    Endpoint endpoint = client.addNewEndpoint(
                        command.getCacheProfileId() != null
                            ?
                            new CacheProfileId(command.getCacheProfileId()) : null,
                        command.getName(),
                        command.getDescription(),
                        command.getPath(),
                        endpointId,
                        command.getMethod(),
                        command.isSecured(),
                        command.isWebsocket(),
                        command.isCsrfEnabled(),
                        command.getCorsProfileId() != null
                            ?
                            new CorsProfileId(command.getCorsProfileId()) : null,
                        command.isShared(),
                        command.isExternal(),
                        command.getReplenishRate(),
                        command.getBurstCapacity()
                    );
                    DomainRegistry.getEndpointRepository().add(endpoint);
                    return endpointId.getDomainId();
                } else {
                    throw new DefinedRuntimeException("invalid client id", "0011",
                        HttpResponseCode.BAD_REQUEST,
                        ExceptionCatalog.ILLEGAL_ARGUMENT);
                }
            }, ENDPOINT);
    }

    public SumPagedRep<Endpoint> tenantQuery(String queryParam, String pageParam, String config) {
        EndpointQuery endpointQuery = new EndpointQuery(queryParam, pageParam, config);
        DomainRegistry.getPermissionCheckService()
            .canAccess(endpointQuery.getProjectIds(), VIEW_API);
        return DomainRegistry.getEndpointRepository().endpointsOfQuery(endpointQuery);
    }

    public SumPagedRep<Endpoint> getShared(String queryParam, String pageParam, String config) {
        EndpointQuery endpointQuery = EndpointQuery.sharedQuery(queryParam, pageParam, config);
        return DomainRegistry.getEndpointRepository().endpointsOfQuery(endpointQuery);
    }

    public SumPagedRep<Endpoint> adminQuery(String queryParam, String pageParam, String config) {
        EndpointQuery endpointQuery = new EndpointQuery(queryParam, pageParam, config);
        return DomainRegistry.getEndpointRepository().endpointsOfQuery(endpointQuery);
    }

    public Optional<Endpoint> tenantEndpoint(String projectId, String id) {
        EndpointQuery endpointQuery =
            new EndpointQuery(new EndpointId(id), new ProjectId(projectId));
        DomainRegistry.getPermissionCheckService()
            .canAccess(endpointQuery.getProjectIds(), VIEW_API);
        return DomainRegistry.getEndpointRepository().endpointsOfQuery(endpointQuery).findFirst();
    }

    public Optional<Endpoint> adminEndpoint(String id) {
        EndpointQuery endpointQuery = new EndpointQuery(new EndpointId(id));
        return DomainRegistry.getEndpointRepository().endpointsOfQuery(endpointQuery).findFirst();
    }

    @Transactional
    @AuditLog(actionName = "update api")
    public void update(String id, EndpointUpdateCommand command, String changeId) {
        log.debug("start of update endpoint");
        EndpointQuery endpointQuery =
            new EndpointQuery(new EndpointId(id), new ProjectId(command.getProjectId()));
        DomainRegistry.getPermissionCheckService()
            .canAccess(endpointQuery.getProjectIds(), EDIT_API);
        EndpointId endpointId = new EndpointId(id);
        CommonApplicationServiceRegistry.getIdempotentService()
            .idempotent(changeId, (ignored) -> {
                Optional<Endpoint> endpoint =
                    DomainRegistry.getEndpointRepository().endpointOfId(endpointId);
                if (endpoint.isPresent()) {
                    Endpoint endpoint1 = endpoint.get();
                    endpoint1.update(
                        command.getCacheProfileId() != null
                            ?
                            new CacheProfileId(command.getCacheProfileId()) : null,
                        command.getName(),
                        command.getDescription(),
                        command.getPath(),
                        command.getMethod(),
                        command.isWebsocket(),
                        command.isCsrfEnabled(),
                        command.getCorsProfileId() != null
                            ?
                            new CorsProfileId(command.getCorsProfileId()) : null,
                        command.getReplenishRate(),
                        command.getBurstCapacity()
                    );
                    CommonDomainRegistry.getDomainEventRepository()
                        .append(new EndpointCollectionModified());
                    DomainRegistry.getEndpointRepository().add(endpoint1);
                }
                return null;
            }, ENDPOINT);
        log.debug("end of update endpoint");
    }

    @Transactional
    @AuditLog(actionName = "remove api")
    public void removeEndpoint(String projectId, String id, String changeId) {
        EndpointQuery endpointQuery =
            new EndpointQuery(new EndpointId(id), new ProjectId(projectId));
        DomainRegistry.getPermissionCheckService()
            .canAccess(endpointQuery.getProjectIds(), EDIT_API);
        CommonApplicationServiceRegistry.getIdempotentService()
            .idempotent(changeId, (ignored) -> {
                Optional<Endpoint> endpoint =
                    DomainRegistry.getEndpointRepository().endpointsOfQuery(endpointQuery)
                        .findFirst();
                if (endpoint.isPresent()) {
                    Endpoint endpoint1 = endpoint.get();
                    endpoint1.remove();
                }
                return null;
            }, ENDPOINT);
    }

    @Transactional
    @AuditLog(actionName = "remove api with query")
    public void removeEndpoints(String projectId, String queryParam, String changeId) {
        ProjectId projectId1 = new ProjectId(projectId);
        DomainRegistry.getPermissionCheckService().canAccess(projectId1, EDIT_API);
        CommonApplicationServiceRegistry.getIdempotentService()
            .idempotent(changeId, (change) -> {
                Set<Endpoint> allByQuery = QueryUtility.getAllByQuery(
                    (query) -> DomainRegistry.getEndpointRepository().endpointsOfQuery(query),
                    new EndpointQuery(queryParam, projectId1));
                Set<ProjectId> collect =
                    allByQuery.stream().map(Endpoint::getProjectId).collect(Collectors.toSet());
                if (collect.size() != 1) {
                    throw new DefinedRuntimeException("project count not 1", "0012",
                        HttpResponseCode.BAD_REQUEST,
                        ExceptionCatalog.ILLEGAL_ARGUMENT);
                }
                ProjectId[] a = new ProjectId[1];
                if (!collect.toArray(a)[0].equals(projectId1)) {
                    throw new DefinedRuntimeException("project count mismatch", "0013",
                        HttpResponseCode.BAD_REQUEST,
                        ExceptionCatalog.ILLEGAL_ARGUMENT);
                }
                Endpoint.remove(allByQuery);
                return null;
            }, ENDPOINT);
    }

    @Transactional
    @AuditLog(actionName = "patch api")
    public void patchEndpoint(String projectId, String id, JsonPatch command, String changeId) {
        ProjectId projectId1 = new ProjectId(projectId);
        DomainRegistry.getPermissionCheckService().canAccess(projectId1, EDIT_API);
        EndpointId endpointId = new EndpointId(id);
        CommonApplicationServiceRegistry.getIdempotentService()
            .idempotent(changeId, (ignored) -> {
                Optional<Endpoint> endpoint = DomainRegistry.getEndpointRepository()
                    .endpointsOfQuery(new EndpointQuery(endpointId, projectId1)).findFirst();
                if (endpoint.isPresent()) {
                    Endpoint endpoint1 = endpoint.get();
                    EndpointPatchCommand beforePatch = new EndpointPatchCommand(endpoint1);
                    EndpointPatchCommand afterPatch =
                        CommonDomainRegistry.getCustomObjectSerializer()
                            .applyJsonPatch(command, beforePatch, EndpointPatchCommand.class);
                    endpoint1.update(
                        endpoint1.getCacheProfileId(),
                        afterPatch.getName(),
                        afterPatch.getDescription(),
                        afterPatch.getPath(),
                        afterPatch.getMethod(),
                        endpoint1.isWebsocket(),
                        endpoint1.isCsrfEnabled(),
                        endpoint1.getCorsProfileId(),
                        endpoint1.getReplenishRate(),
                        endpoint1.getBurstCapacity()
                    );
                    CommonDomainRegistry.getDomainEventRepository()
                        .append(new EndpointCollectionModified());
                }
                return null;
            }, ENDPOINT);
    }

    @Transactional
    public void reloadEndpointCache(String changeId) {
        CommonApplicationServiceRegistry.getIdempotentService()
            .idempotent(changeId, (ignored) -> {
                DomainRegistry.getEndpointService().reloadEndpointCache();
                return null;
            }, ENDPOINT);
    }

    @Transactional
    public void handle(ClientDeleted deserialize) {
        CommonApplicationServiceRegistry.getIdempotentService()
            .idempotent(deserialize.getId().toString(), (ignored) -> {
                log.debug("handle delete client event");
                Set<Endpoint> allByQuery = QueryUtility.getAllByQuery(
                    (query) -> DomainRegistry.getEndpointRepository().endpointsOfQuery(query),
                    new EndpointQuery(new ClientId(deserialize.getDomainId().getDomainId())));
                if (!allByQuery.isEmpty()) {
                    DomainRegistry.getEndpointRepository().remove(allByQuery);
                    CommonDomainRegistry.getDomainEventRepository()
                        .append(new EndpointCollectionModified());
                }
                return null;
            }, ENDPOINT);
    }

    @Transactional
    public void handle(CorsProfileRemoved deserialize) {
        CommonApplicationServiceRegistry.getIdempotentService()
            .idempotent(deserialize.getId().toString(), (ignored) -> {
                log.debug("handle cors profile removed");
                Set<Endpoint> allByQuery = QueryUtility.getAllByQuery(
                    (query) -> DomainRegistry.getEndpointRepository().endpointsOfQuery(query),
                    new EndpointQuery(new CorsProfileId(deserialize.getDomainId().getDomainId())));
                if (!allByQuery.isEmpty()) {
                    allByQuery.forEach(e -> e.setCorsProfileId(null));
                    CommonDomainRegistry.getDomainEventRepository()
                        .append(new EndpointCollectionModified());
                }
                return null;
            }, ENDPOINT);
    }

    /**
     * refresh proxy when referred cors profile is updated.
     *
     * @param event cors profile updated event
     */
    @Transactional
    public void handle(CorsProfileUpdated event) {
        CommonApplicationServiceRegistry.getIdempotentService()
            .idempotent(event.getId().toString(), (ignored) -> {
                log.debug("handle cors profile updated");
                CorsProfileId corsProfileId =
                    new CorsProfileId(event.getDomainId().getDomainId());
                SumPagedRep<Endpoint> endpointSumPagedRep = DomainRegistry.getEndpointRepository()
                    .endpointsOfQuery(new EndpointQuery(corsProfileId));
                if (endpointSumPagedRep.findFirst().isPresent()) {
                    CommonDomainRegistry.getDomainEventRepository()
                        .append(new EndpointCollectionModified());
                }
                return null;
            }, ENDPOINT);
    }

    @Transactional
    public void handle(CacheProfileRemoved deserialize) {
        CommonApplicationServiceRegistry.getIdempotentService()
            .idempotent(deserialize.getId().toString(), (ignored) -> {
                log.debug("handle cache profile removed");
                CacheProfileId profileId =
                    new CacheProfileId(deserialize.getDomainId().getDomainId());
                Set<Endpoint> allByQuery = QueryUtility.getAllByQuery(
                    (query) -> DomainRegistry.getEndpointRepository().endpointsOfQuery(query),
                    new EndpointQuery(profileId));
                if (!allByQuery.isEmpty()) {
                    CommonDomainRegistry.getDomainEventRepository()
                        .append(new EndpointCollectionModified());
                    allByQuery.forEach(e -> e.setCacheProfileId(null));
                }
                return null;
            }, ENDPOINT);
    }

    @Transactional
    public void handle(CacheProfileUpdated deserialize) {
        CommonApplicationServiceRegistry.getIdempotentService()
            .idempotent(deserialize.getId().toString(), (ignored) -> {
                log.debug("handle cache profile updated");
                CacheProfileId profileId =
                    new CacheProfileId(deserialize.getDomainId().getDomainId());
                SumPagedRep<Endpoint> firstPage = DomainRegistry.getEndpointRepository()
                    .endpointsOfQuery(new EndpointQuery(profileId));
                if (firstPage.findFirst().isPresent()) {
                    CommonDomainRegistry.getDomainEventRepository()
                        .append(new EndpointCollectionModified());
                } else {
                    log.debug("cache profile is not used");
                }
                return null;
            }, ENDPOINT);
    }

    public SumPagedRep<EndpointProxyCacheRepresentation> proxyQuery(String pageParam) {
        SumPagedRep<Endpoint> endpoints = DomainRegistry.getEndpointRepository()
            .endpointsOfQuery(new EndpointQuery(pageParam));
        List<EndpointProxyCacheRepresentation> collect =
            endpoints.getData().stream().map(EndpointProxyCacheRepresentation::new)
                .collect(Collectors.toList());
        EndpointProxyCacheRepresentation.updateDetail(collect);
        return new SumPagedRep<>(collect, endpoints.getTotalItemCount());
    }

    /**
     * internal query endpoints
     *
     * @param endpointIds endpoint ids
     * @return matched endpoints
     */
    public Set<Endpoint> internalQuery(Set<EndpointId> endpointIds) {
        return QueryUtility.getAllByQuery(e -> DomainRegistry.getEndpointRepository()
            .endpointsOfQuery(e), new EndpointQuery(endpointIds));
    }

    @Transactional
    public void expireEndpoint(EndpointExpireCommand command, String projectId, String id,
                               String changeId) {
        log.debug("start of expire endpoint");
        EndpointId endpointId = new EndpointId(id);
        EndpointQuery endpointQuery =
            new EndpointQuery(endpointId, new ProjectId(projectId));
        DomainRegistry.getPermissionCheckService()
            .canAccess(endpointQuery.getProjectIds(), EDIT_API);
        CommonApplicationServiceRegistry.getIdempotentService()
            .idempotent(changeId, (ignored) -> {
                Optional<Endpoint> endpoint =
                    DomainRegistry.getEndpointRepository().endpointOfId(endpointId);
                endpoint.ifPresent(e -> {
                    e.expire(command.getExpireReason());
                });
                return null;
            }, ENDPOINT);
    }
}
