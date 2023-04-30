package com.hw.integration.single.proxy;

import com.hw.helper.utility.TestContext;
import com.hw.helper.utility.UrlUtility;
import com.hw.helper.utility.UserUtility;
import com.hw.integration.single.access.CommonTest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@Slf4j
public class JwtSecurityTest  extends CommonTest {


    @Test
    public void user_modify_jwt_token_after_login() {
        String defaultUserToken = UserUtility.registerNewUserThenLogin();
        String url = UrlUtility.getAccessUrl("/status/200");
        ResponseEntity<String> exchange = TestContext.getRestTemplate()
            .exchange(url, HttpMethod.GET, getHttpRequest(defaultUserToken + "valueChange"),
                String.class);
        Assert.assertEquals(HttpStatus.UNAUTHORIZED, exchange.getStatusCode());
    }

    @Test
    public void trying_access_protected_api_without_jwt_token() {
        String url = UrlUtility.getAccessUrl("/status/200");
        ResponseEntity<String> exchange =
            TestContext.getRestTemplate()
                .exchange(url, HttpMethod.GET, getHttpRequest(null), String.class);
        Assert.assertEquals(HttpStatus.UNAUTHORIZED, exchange.getStatusCode());
    }

    private HttpEntity<?> getHttpRequest(String authorizeToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAcceptCharset(List.of(StandardCharsets.UTF_8));
        headers.setBearerAuth(authorizeToken);
        return new HttpEntity<>(headers);
    }

}
