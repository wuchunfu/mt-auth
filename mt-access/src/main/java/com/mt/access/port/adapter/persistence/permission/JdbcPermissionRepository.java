package com.mt.access.port.adapter.persistence.permission;

import com.mt.access.domain.model.endpoint.EndpointId;
import com.mt.access.domain.model.permission.Permission;
import com.mt.access.domain.model.permission.PermissionId;
import com.mt.access.domain.model.permission.PermissionQuery;
import com.mt.access.domain.model.permission.PermissionRepository;
import com.mt.access.domain.model.permission.PermissionType;
import com.mt.access.domain.model.project.ProjectId;
import com.mt.access.port.adapter.persistence.BatchInsertKeyValue;
import com.mt.common.domain.CommonDomainRegistry;
import com.mt.common.domain.model.audit.Auditable;
import com.mt.common.domain.model.domain_event.DomainId;
import com.mt.common.domain.model.restful.SumPagedRep;
import com.mt.common.domain.model.sql.DatabaseUtility;
import com.mt.common.domain.model.validate.Checker;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPermissionRepository implements PermissionRepository {


    private static final String INSERT_SQL = "INSERT INTO permission " +
        "(" +
        "id, " +
        "created_at, " +
        "created_by, " +
        "modified_at, " +
        "modified_by, " +
        "version, " +
        "name, " +
        "parent_id, " +
        "domain_id, " +
        "project_id, " +
        "shared, " +
        "system_create, " +
        "tenant_id, " +
        "type" +
        ") VALUES " +
        "(?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
    private static final String INSERT_LINKED_PERMISSION_MAP_SQL =
        "INSERT INTO linked_permission_ids_map " +
            "(" +
            "id, " +
            "domain_id" +
            ") VALUES " +
            "(?,?)";
    private static final String FIND_BY_DOMAIN_ID_SQL =
        "SELECT p.*, lpm.domain_id AS lp_id FROM permission AS p LEFT JOIN linked_permission_ids_map lpm ON p.id = lpm.id " +
            "WHERE p.domain_id = ?";
    private static final String FIND_DOMAIN_ID_BY_NAME_AND_TENANT_ID =
        "SELECT p.domain_id FROM permission p " +
            "WHERE p.tenant_id IN (%s) AND p.name IN (%s) ORDER BY p.id ASC LIMIT ? OFFSET ?";
    private static final String COUNT_BY_NAME_AND_TENANT_ID =
        "SELECT COUNT(*) AS count FROM permission p WHERE p.tenant_id IN (%s) AND p.name IN (%s)";
    private static final String DELETE_LINKED_API_PERMISSION_BY_ID_SQL =
        "DELETE FROM linked_permission_ids_map lpm WHERE lpm.id = ?";
    private static final String DELETE_LINKED_API_PERMISSION_BY_DOMAIN_ID_SQL =
        "DELETE FROM linked_permission_ids_map lpm WHERE lpm.domain_id = ?";
    private static final String DELETE_BY_ID_SQL = "DELETE FROM permission p WHERE p.id = ?";
    private static final String BATCH_DELETE_LINKED_API_PERMISSION_BY_ID_AND_DOMAIN_ID_SQL =
        "DELETE FROM linked_permission_ids_map lpm WHERE lpm.id = ? AND lpm.domain_id IN (%s)";
    private static final String FIND_ALL_ENDPOINT_ID_USED =
        "SELECT DISTINCT p.name FROM permission p WHERE p.type='API' and p.parent_id IS NOT NULL";
    private static final String DYNAMIC_COUNT_QUERY_SQL = "SELECT COUNT(*) AS count FROM permission p WHERE %s";
    private static final String DYNAMIC_DATA_QUERY_SQL =
        "SELECT temp.*, lpm.domain_id AS lp_id FROM " +
            "(SELECT * FROM permission p WHERE %s ORDER BY p.id ASC LIMIT ? OFFSET ?) " +
            "AS temp " +
            "LEFT JOIN linked_permission_ids_map lpm ON temp.id = lpm.id ";
    ;
    private static final String FIND_ALL_PERMISSION_ID_USED =
        "SELECT DISTINCT p.domain_id FROM permission p";
    private static final String COUNT_PROJECT_CREATED_TOTAL =
        "SELECT COUNT(*) AS count FROM permission p " +
            "WHERE p.project_id = ? and p.type = 'COMMON' and p.parent_id IS NOT NULL";
    private static final String FIND_LINKED_API_PERMISSION_FOR_SQL =
        "SELECT lpm.domain_id FROM permission p " +
            "RIGHT JOIN linked_permission_ids_map lpm ON p.id = lpm.id WHERE p.domain_id IN (%s)";
    private static final String UPDATE_SQL = "UPDATE permission p SET " +
        "p.modified_at = ? ," +
        "p.modified_by = ?, " +
        "p.version = ?, " +
        "p.name = ? " +
        "WHERE p.id = ? AND p.version = ? ";

    @Override
    public void add(Permission permission) {
        CommonDomainRegistry.getJdbcTemplate()
            .update(INSERT_SQL,
                permission.getId(),
                permission.getCreatedAt(),
                permission.getCreatedBy(),
                permission.getModifiedAt(),
                permission.getModifiedBy(),
                0,
                permission.getName(),
                permission.getParentId() == null ? null :
                    permission.getParentId().getDomainId(),

                permission.getPermissionId().getDomainId(),
                permission.getProjectId().getDomainId(),
                permission.getShared(),
                permission.getSystemCreate(),
                permission.getTenantId() == null ? null :
                    permission.getTenantId().getDomainId(),
                permission.getType().name()
            );
        //for linked tables
        List<BatchInsertKeyValue> linkedPermList = new ArrayList<>();
        if (Checker.notNullOrEmpty(permission.getLinkedApiPermissionIds())) {
            List<BatchInsertKeyValue> collect = permission.getLinkedApiPermissionIds().stream()
                .map(ee -> new BatchInsertKeyValue(permission.getId(), ee.getDomainId())).collect(
                    Collectors.toList());
            linkedPermList.addAll(collect);
            CommonDomainRegistry.getJdbcTemplate()
                .batchUpdate(INSERT_LINKED_PERMISSION_MAP_SQL, linkedPermList,
                    linkedPermList.size(),
                    (ps, perm) -> {
                        ps.setLong(1, perm.getId());
                        ps.setString(2, perm.getValue());
                    });
        }
    }

    @Override
    public void addAll(Set<Permission> permissions) {
        List<Permission> arrayList = new ArrayList<>(permissions);
        CommonDomainRegistry.getJdbcTemplate()
            .batchUpdate(INSERT_SQL, arrayList, permissions.size(),
                (ps, permission) -> {
                    ps.setLong(1, permission.getId());
                    ps.setLong(2, Instant.now().toEpochMilli());
                    ps.setString(3, "NOT_HTTP");
                    ps.setLong(4, Instant.now().toEpochMilli());
                    ps.setString(5, "NOT_HTTP");
                    ps.setLong(6, 0L);
                    ps.setString(7, permission.getName());
                    ps.setString(8, permission.getParentId() == null ? null :
                        permission.getParentId().getDomainId());
                    ps.setString(9, permission.getPermissionId().getDomainId());
                    ps.setString(10, permission.getProjectId().getDomainId());
                    ps.setBoolean(11, permission.getShared());
                    ps.setBoolean(12, permission.getSystemCreate());
                    ps.setString(13, permission.getTenantId() == null ? null :
                        permission.getTenantId().getDomainId());
                    ps.setString(14, permission.getType().name());
                });
        //for linked tables
        List<BatchInsertKeyValue> linkedPermList = new ArrayList<>();
        permissions.forEach(e -> {
            if (Checker.notNullOrEmpty(e.getLinkedApiPermissionIds())) {
                List<BatchInsertKeyValue> collect = e.getLinkedApiPermissionIds().stream()
                    .map(ee -> new BatchInsertKeyValue(e.getId(), ee.getDomainId())).collect(
                        Collectors.toList());
                linkedPermList.addAll(collect);
            }
        });
        CommonDomainRegistry.getJdbcTemplate()
            .batchUpdate(INSERT_LINKED_PERMISSION_MAP_SQL, linkedPermList, linkedPermList.size(),
                (ps, permission) -> {
                    ps.setLong(1, permission.getId());
                    ps.setString(2, permission.getValue());
                });
    }

    @Override
    public SumPagedRep<Permission> query(PermissionQuery query) {
        List<String> whereClause = new ArrayList<>();
        if (Checker.notNullOrEmpty(query.getIds())) {
            String inClause = DatabaseUtility.getInClause(query.getIds().size());
            String byDomainIds = String.format("p.domain_id IN (%s)", inClause);
            whereClause.add(byDomainIds);
        }
        if (Checker.notNull(query.getParentId())) {
            String byParentId = "p.parent_id = ?";
            whereClause.add(byParentId);
        }
        if (Checker.notNull(query.getParentIdNull())) {
            String byParentId = "p.parent_id IS NULL";
            whereClause.add(byParentId);
        }
        if (Checker.notNullOrEmpty(query.getProjectIds())) {
            String inClause = DatabaseUtility.getInClause(query.getProjectIds().size());
            String byProjectIds = String.format("p.project_id IN (%s)", inClause);
            whereClause.add(byProjectIds);
        }
        if (Checker.notNullOrEmpty(query.getTenantIds())) {
            String inClause = DatabaseUtility.getInClause(query.getTenantIds().size());
            String byTenantIds = String.format("p.tenant_id IN (%s)", inClause);
            whereClause.add(byTenantIds);
        }
        if (Checker.notNullOrEmpty(query.getNames())) {
            String inClause = DatabaseUtility.getInClause(query.getNames().size());
            String byNames = String.format("p.name IN (%s)", inClause);
            whereClause.add(byNames);
        }
        if (Checker.notNull(query.getShared())) {
            String shared = "p.shared = ?";
            whereClause.add(shared);
        }
        if (Checker.notNullOrEmpty(query.getTypes())) {
            String inClause = DatabaseUtility.getInClause(query.getTypes().size());
            String byTypes = String.format("p.type IN (%s)", inClause);
            whereClause.add(byTypes);
        }
        String join = String.join(" AND ", whereClause);
        String finalDataQuery = String.format(DYNAMIC_DATA_QUERY_SQL, join);
        String finalCountQuery = String.format(DYNAMIC_COUNT_QUERY_SQL, join);
        List<Object> args = new ArrayList<>();
        if (Checker.notNullOrEmpty(query.getIds())) {
            args.addAll(
                query.getIds().stream().map(DomainId::getDomainId).collect(Collectors.toSet()));
        }
        if (Checker.notNull(query.getParentId())) {
            args.add(
                query.getParentId().getDomainId());
        }
        if (Checker.notNullOrEmpty(query.getProjectIds())) {
            args.addAll(
                query.getProjectIds().stream().map(DomainId::getDomainId)
                    .collect(Collectors.toSet()));
        }
        if (Checker.notNullOrEmpty(query.getTenantIds())) {
            args.addAll(
                query.getTenantIds().stream().map(DomainId::getDomainId)
                    .collect(Collectors.toSet()));
        }
        if (Checker.notNullOrEmpty(query.getNames())) {
            args.addAll(
                query.getNames());
        }
        if (Checker.notNull(query.getShared())) {
            args.add(
                query.getShared());
        }
        if (Checker.notNullOrEmpty(query.getTypes())) {
            args.addAll(
                query.getTypes().stream().map(Enum::name).collect(Collectors.toSet()));
        }
        Long count = CommonDomainRegistry.getJdbcTemplate()
            .query(finalCountQuery,
                new DatabaseUtility.ExtractCount(),
                args.toArray()
            );

        args.add(query.getPageConfig().getPageSize());
        args.add(query.getPageConfig().getOffset());

        List<Permission> data = CommonDomainRegistry.getJdbcTemplate()
            .query(finalDataQuery,
                new RowMapper(),
                args.toArray()
            );
        return new SumPagedRep<>(data, count);
    }

    @Override
    public SumPagedRep<PermissionId> queryPermissionId(PermissionQuery query) {
        String inSql = DatabaseUtility.getInClause(query.getTenantIds().size());
        String inSql2 = DatabaseUtility.getInClause(query.getNames().size());
        List<Object> args = new ArrayList<>();
        args.addAll(
            query.getTenantIds().stream().map(DomainId::getDomainId).collect(Collectors.toSet()));
        args.addAll(query.getNames());
        args.add(query.getPageConfig().getPageSize());
        args.add(query.getPageConfig().getOffset());
        List<PermissionId> data = CommonDomainRegistry.getJdbcTemplate()
            .query(
                String.format(FIND_DOMAIN_ID_BY_NAME_AND_TENANT_ID, inSql, inSql2),
                rs -> {
                    if (!rs.next()) {
                        return Collections.emptyList();
                    }
                    List<PermissionId> permissionIds = new ArrayList<>();
                    do {
                        permissionIds.add(new PermissionId(rs.getString("domain_id")));
                    } while (rs.next());
                    return permissionIds;
                },
                args.toArray()
            );
        List<Object> countArgs = new ArrayList<>();
        countArgs.addAll(
            query.getTenantIds().stream().map(DomainId::getDomainId).collect(Collectors.toSet()));
        countArgs.addAll(query.getNames());
        Long count = CommonDomainRegistry.getJdbcTemplate()
            .query(
                String.format(COUNT_BY_NAME_AND_TENANT_ID, inSql, inSql2),
                new DatabaseUtility.ExtractCount(),
                countArgs.toArray()
            );
        return new SumPagedRep<>(data, count);
    }

    @Override
    public void remove(Permission permission) {

        if (Checker.notNullOrEmpty(permission.getLinkedApiPermissionIds())) {

            CommonDomainRegistry.getJdbcTemplate()
                .update(DELETE_LINKED_API_PERMISSION_BY_ID_SQL,
                    permission.getId()
                );
        }
        CommonDomainRegistry.getJdbcTemplate()
            .update(DELETE_BY_ID_SQL,
                permission.getId()
            );
    }


    @Override
    public Permission query(PermissionId id) {
        List<Permission> data = CommonDomainRegistry.getJdbcTemplate()
            .query(FIND_BY_DOMAIN_ID_SQL,
                new RowMapper(),
                id.getDomainId()
            );
        return data.isEmpty() ? null : data.get(0);
    }

    @Override
    public void update(Permission old, Permission updated) {
        if (updated.sameAs(old)) {
            return;
        }
        int update = CommonDomainRegistry.getJdbcTemplate()
            .update(UPDATE_SQL,
                updated.getModifiedAt(),
                updated.getModifiedBy(),
                updated.getVersion() + 1,
                updated.getName(),
                updated.getId(),
                updated.getVersion()
            );
        DatabaseUtility.checkUpdate(update);
        DatabaseUtility.updateMap(old.getLinkedApiPermissionIds(),
            updated.getLinkedApiPermissionIds(),
            (added) -> {
                //for linked tables
                List<BatchInsertKeyValue> insertKeyValues = new ArrayList<>();
                List<BatchInsertKeyValue> collect = added.stream()
                    .map(ee -> new BatchInsertKeyValue(old.getId(), ee.getDomainId()))
                    .collect(
                        Collectors.toList());
                insertKeyValues.addAll(collect);
                CommonDomainRegistry.getJdbcTemplate()
                    .batchUpdate(INSERT_LINKED_PERMISSION_MAP_SQL, insertKeyValues,
                        insertKeyValues.size(),
                        (ps, perm) -> {
                            ps.setLong(1, perm.getId());
                            ps.setString(2, perm.getValue());
                        });
            }, (removed) -> {
                String inClause = DatabaseUtility.getInClause(removed.size());
                List<Object> args = new ArrayList<>();
                args.add(old.getId());
                args.addAll(
                    removed.stream().map(DomainId::getDomainId).collect(Collectors.toSet()));
                CommonDomainRegistry.getJdbcTemplate()
                    .update(
                        String.format(BATCH_DELETE_LINKED_API_PERMISSION_BY_ID_AND_DOMAIN_ID_SQL,
                            inClause),
                        args.toArray()
                    );
            });
    }

    @Override
    public Set<EndpointId> allApiPermissionLinkedEpId() {
        List<EndpointId> data = CommonDomainRegistry.getJdbcTemplate()
            .query(
                FIND_ALL_ENDPOINT_ID_USED,
                rs -> {
                    if (!rs.next()) {
                        return Collections.emptyList();
                    }
                    List<EndpointId> list = new ArrayList<>();
                    do {
                        list.add(new EndpointId(rs.getString("name")));
                    } while (rs.next());
                    return list;
                }
            );
        return new HashSet<>(data);
    }

    @Override
    public Set<PermissionId> allPermissionId() {
        List<PermissionId> data = CommonDomainRegistry.getJdbcTemplate()
            .query(
                FIND_ALL_PERMISSION_ID_USED,
                rs -> {
                    if (!rs.next()) {
                        return Collections.emptyList();
                    }
                    List<PermissionId> list = new ArrayList<>();
                    do {
                        list.add(new PermissionId(rs.getString("domain_id")));
                    } while (rs.next());
                    return list;
                }
            );
        return new HashSet<>(data);
    }

    @Override
    public Set<PermissionId> getLinkedApiPermissionFor(Set<PermissionId> permissionIds) {
        String inClause = DatabaseUtility.getInClause(permissionIds.size());
        List<PermissionId> data = CommonDomainRegistry.getJdbcTemplate()
            .query(
                String.format(FIND_LINKED_API_PERMISSION_FOR_SQL, inClause),
                rs -> {
                    if (!rs.next()) {
                        return Collections.emptyList();
                    }
                    List<PermissionId> list = new ArrayList<>();
                    do {
                        list.add(new PermissionId(rs.getString("domain_id")));
                    } while (rs.next());
                    return list;
                },
                permissionIds.stream().map(DomainId::getDomainId).distinct().toArray()
            );
        return new HashSet<>(data);
    }

    @Override
    public long countProjectCreateTotal(ProjectId projectId) {
        Long count = CommonDomainRegistry.getJdbcTemplate()
            .query(
                COUNT_PROJECT_CREATED_TOTAL,
                new DatabaseUtility.ExtractCount(),
                projectId.getDomainId()
            );
        return count;
    }

    @Override
    public void removeLinkedApiPermission(PermissionId permissionId) {
        CommonDomainRegistry.getJdbcTemplate()
            .update(DELETE_LINKED_API_PERMISSION_BY_DOMAIN_ID_SQL,
                permissionId.getDomainId()
            );
    }

    private static class RowMapper implements ResultSetExtractor<List<Permission>> {

        @Override
        public List<Permission> extractData(ResultSet rs)
            throws SQLException, DataAccessException {
            if (!rs.next()) {
                return Collections.emptyList();
            }
            List<Permission> permissions = new ArrayList<>();
            long currentId = -1L;
            Permission permission = null;
            do {
                long dbId = rs.getLong(Auditable.DB_ID);
                if (currentId != dbId) {
                    permission = Permission.fromDatabaseRow(
                        DatabaseUtility.getNullableLong(rs, Auditable.DB_ID),
                        DatabaseUtility.getNullableLong(rs, Auditable.DB_CREATED_AT),
                        rs.getString(Auditable.DB_CREATED_BY),
                        DatabaseUtility.getNullableLong(rs, Auditable.DB_MODIFIED_AT),
                        rs.getString(Auditable.DB_MODIFIED_BY),
                        DatabaseUtility.getNullableInteger(rs, Auditable.DB_VERSION),
                        rs.getString("name"),
                        new PermissionId(rs.getString("domain_id")),
                        Checker.notNull(rs.getString("parent_id")) ?
                            new PermissionId(rs.getString("parent_id")) : null,
                        new ProjectId(rs.getString("project_id")),
                        DatabaseUtility.getNullableBoolean(rs, "shared"),
                        DatabaseUtility.getNullableBoolean(rs, "system_create"),
                        Checker.notNull(rs.getString("tenant_id")) ?
                            new ProjectId(rs.getString("tenant_id")) : null,
                        PermissionType.valueOf(rs.getString("type"))
                    );
                    permissions.add(permission);
                    currentId = dbId;
                }
                Set<PermissionId> linkedApiPermissionIds = permission.getLinkedApiPermissionIds();
                String rawId = rs.getString("lp_id");
                if (Checker.notNull(rawId)) {
                    linkedApiPermissionIds.add(new PermissionId(rawId));
                }
            } while (rs.next());
            return permissions;
        }
    }
}
