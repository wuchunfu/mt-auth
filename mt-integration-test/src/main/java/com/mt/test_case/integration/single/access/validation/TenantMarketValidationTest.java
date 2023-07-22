package com.mt.test_case.integration.single.access.validation;

import com.mt.test_case.helper.TenantContext;
import com.mt.test_case.helper.args.SubRequestCapacityArgs;
import com.mt.test_case.helper.args.SubRequestEndpointIdArgs;
import com.mt.test_case.helper.args.SubRequestProjectIdArgs;
import com.mt.test_case.helper.args.SubRequestRejectArgs;
import com.mt.test_case.helper.args.SubRequestReplenishArgs;
import com.mt.test_case.helper.pojo.Client;
import com.mt.test_case.helper.pojo.Endpoint;
import com.mt.test_case.helper.pojo.Permission;
import com.mt.test_case.helper.pojo.RejectSubRequestCommand;
import com.mt.test_case.helper.pojo.Role;
import com.mt.test_case.helper.pojo.SubscriptionReq;
import com.mt.test_case.helper.pojo.SumTotal;
import com.mt.test_case.helper.pojo.UpdateType;
import com.mt.test_case.helper.pojo.User;
import com.mt.test_case.helper.utility.ClientUtility;
import com.mt.test_case.helper.utility.EndpointUtility;
import com.mt.test_case.helper.utility.MarketUtility;
import com.mt.test_case.helper.utility.PermissionUtility;
import com.mt.test_case.helper.utility.RoleUtility;
import com.mt.test_case.helper.utility.TenantUtility;
import com.mt.test_case.helper.utility.TestContext;
import com.mt.test_case.helper.utility.UrlUtility;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@Slf4j
public class TenantMarketValidationTest {
    protected static TenantContext tenantContextA;
    protected static TenantContext tenantContextB;
    protected static Client clientA;

    @BeforeAll
    public static void initTenant() {
        log.info("init tenant in progress");
        TestContext.init();
        tenantContextA = TenantUtility.initTenant();
        tenantContextB = TenantUtility.initTenant();

        clientA = ClientUtility.createValidBackendClient();
        clientA.setResourceIndicator(true);
        ResponseEntity<Void> tenantClient =
            ClientUtility.createTenantClient(tenantContextA, clientA);
        clientA.setId(UrlUtility.getId(tenantClient));

        log.info("init tenant complete");
    }

    @ParameterizedTest
    @ArgumentsSource(SubRequestEndpointIdArgs.class)
    public void validation_create_endpoint_id(String endpointId, HttpStatus status) {
        //create shared endpoint tenantA
        Endpoint endpoint =
            EndpointUtility.createValidSharedEndpointObj(clientA.getId());
        ResponseEntity<Void> tenantEndpoint =
            EndpointUtility.createTenantEndpoint(tenantContextA, endpoint);
        endpoint.setId(UrlUtility.getId(tenantEndpoint));
        SubscriptionReq req =
            MarketUtility.createValidSubReq(tenantContextB, endpoint.getId());
        //send sub req tenantB
        req.setEndpointId(endpointId);
        ResponseEntity<Void> response =
            MarketUtility.subToEndpoint(tenantContextB.getCreator(), req);
        Assertions.assertEquals(status, response.getStatusCode());
    }

    @ParameterizedTest
    @ArgumentsSource(SubRequestProjectIdArgs.class)
    public void validation_create_project_id(String projectId, HttpStatus status) {
        //create shared endpoint tenantA
        Endpoint endpoint =
            EndpointUtility.createValidSharedEndpointObj(clientA.getId());
        ResponseEntity<Void> tenantEndpoint =
            EndpointUtility.createTenantEndpoint(tenantContextA, endpoint);
        endpoint.setId(UrlUtility.getId(tenantEndpoint));
        SubscriptionReq req =
            MarketUtility.createValidSubReq(tenantContextB, endpoint.getId());
        //send sub req tenantB
        req.setProjectId(projectId);
        ResponseEntity<Void> response =
            MarketUtility.subToEndpoint(tenantContextB.getCreator(), req);
        Assertions.assertEquals(status, response.getStatusCode());
    }

    @ParameterizedTest
    @ArgumentsSource(SubRequestCapacityArgs.class)
    public void validation_create_burst_capacity(Integer burstCapacity, HttpStatus status) {
        //create shared endpoint tenantA
        Endpoint endpoint =
            EndpointUtility.createValidSharedEndpointObj(clientA.getId());
        ResponseEntity<Void> tenantEndpoint =
            EndpointUtility.createTenantEndpoint(tenantContextA, endpoint);
        endpoint.setId(UrlUtility.getId(tenantEndpoint));
        SubscriptionReq req =
            MarketUtility.createValidSubReq(tenantContextB, endpoint.getId());
        //send sub req tenantB
        req.setBurstCapacity(burstCapacity);
        ResponseEntity<Void> response =
            MarketUtility.subToEndpoint(tenantContextB.getCreator(), req);
        Assertions.assertEquals(status, response.getStatusCode());
    }

    @ParameterizedTest
    @ArgumentsSource(SubRequestReplenishArgs.class)
    public void validation_create_replenish_rate(Integer replenishRate, HttpStatus status) {
        //create shared endpoint tenantA
        Endpoint endpoint =
            EndpointUtility.createValidSharedEndpointObj(clientA.getId());
        ResponseEntity<Void> tenantEndpoint =
            EndpointUtility.createTenantEndpoint(tenantContextA, endpoint);
        endpoint.setId(UrlUtility.getId(tenantEndpoint));
        SubscriptionReq req =
            MarketUtility.createValidSubReq(tenantContextB, endpoint.getId());
        //send sub req tenantB
        req.setReplenishRate(replenishRate);
        ResponseEntity<Void> response =
            MarketUtility.subToEndpoint(tenantContextB.getCreator(), req);
        Assertions.assertEquals(status, response.getStatusCode());
    }

    @ParameterizedTest
    @ArgumentsSource(SubRequestRejectArgs.class)
    public void validation_reject(String reject, HttpStatus status) {
        //create shared endpoint tenantA
        Endpoint endpoint =
            EndpointUtility.createValidSharedEndpointObj(clientA.getId());
        ResponseEntity<Void> tenantEndpoint =
            EndpointUtility.createTenantEndpoint(tenantContextA, endpoint);
        endpoint.setId(UrlUtility.getId(tenantEndpoint));
        //send sub req tenantB
        SubscriptionReq randomTenantSubReqObj =
            MarketUtility.createValidSubReq(tenantContextB, endpoint.getId());
        ResponseEntity<Void> voidResponseEntity =
            MarketUtility.subToEndpoint(tenantContextB.getCreator(), randomTenantSubReqObj);
        Assertions.assertEquals(HttpStatus.OK, voidResponseEntity.getStatusCode());
        String subReqId = UrlUtility.getId(voidResponseEntity);
        //tenantA reject request
        RejectSubRequestCommand command = new RejectSubRequestCommand();
        command.setRejectionReason(reject);
        ResponseEntity<Void> response =
            MarketUtility.rejectSubReq(tenantContextA, subReqId, command);
        Assertions.assertEquals(status, response.getStatusCode());
    }

    @ParameterizedTest
    @ArgumentsSource(SubRequestCapacityArgs.class)
    public void validation_update_burst_capacity(Integer burstCapacity, HttpStatus status) {
        //create shared endpoint tenantA
        Endpoint endpoint =
            EndpointUtility.createValidSharedEndpointObj(clientA.getId());
        ResponseEntity<Void> tenantEndpoint =
            EndpointUtility.createTenantEndpoint(tenantContextA, endpoint);
        endpoint.setId(UrlUtility.getId(tenantEndpoint));
        SubscriptionReq req =
            MarketUtility.createValidSubReq(tenantContextB, endpoint.getId());
        ResponseEntity<Void> response4 =
            MarketUtility.subToEndpoint(tenantContextB.getCreator(), req);
        req.setId(UrlUtility.getId(response4));
        //send sub req tenantB
        req.setBurstCapacity(burstCapacity);
        ResponseEntity<Void> response =
            MarketUtility.updateSubReq(tenantContextB.getCreator(), req);
        Assertions.assertEquals(status, response.getStatusCode());
    }

    @ParameterizedTest
    @ArgumentsSource(SubRequestReplenishArgs.class)
    public void validation_update_replenish_rate(Integer replenishRate, HttpStatus status) {
        //create shared endpoint tenantA
        Endpoint endpoint =
            EndpointUtility.createValidSharedEndpointObj(clientA.getId());
        ResponseEntity<Void> tenantEndpoint =
            EndpointUtility.createTenantEndpoint(tenantContextA, endpoint);
        endpoint.setId(UrlUtility.getId(tenantEndpoint));
        SubscriptionReq req =
            MarketUtility.createValidSubReq(tenantContextB, endpoint.getId());
        ResponseEntity<Void> response4 =
            MarketUtility.subToEndpoint(tenantContextB.getCreator(), req);
        req.setId(UrlUtility.getId(response4));
        //send sub req tenantB
        req.setReplenishRate(replenishRate);
        ResponseEntity<Void> response =
            MarketUtility.updateSubReq(tenantContextB.getCreator(), req);
        Assertions.assertEquals(status, response.getStatusCode());
    }

}
