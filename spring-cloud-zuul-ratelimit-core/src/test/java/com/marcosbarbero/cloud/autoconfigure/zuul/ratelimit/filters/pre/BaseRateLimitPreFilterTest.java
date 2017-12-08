package com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.filters.pre;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

import com.google.common.collect.Maps;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.*;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.properties.RateLimitProperties;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.properties.RateLimitProperties.Policy;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.filters.RateLimitPreFilter;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.filters.commons.TestRouteLocator;
import com.netflix.zuul.context.RequestContext;
import com.netflix.zuul.monitoring.CounterFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.cloud.netflix.zuul.filters.Route;
import org.springframework.cloud.netflix.zuul.filters.RouteLocator;
import org.springframework.cloud.netflix.zuul.metrics.EmptyCounterFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.util.UrlPathHelper;

/**
 * @author Marcos Barbero
 * @since 2017-06-30
 */
public abstract class BaseRateLimitPreFilterTest {

    RateLimitPreFilter filter;

    @Mock
    RequestAttributes requestAttributes;

    MockHttpServletRequest request;
    MockHttpServletResponse response;

    private RequestContext context;
    private RateLimiter rateLimiter;
    private RateLimitKeyGenerator rateLimitKeyGenerator;

    private UserIDGenerator userIDGenerator = new DefaultUserIDGenerator();

    private Route createRoute(String id, String path) {
        return new Route(id, path, null, null, false, Collections.emptySet());
    }

    private RateLimitProperties properties() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setEnabled(true);
        properties.setBehindProxy(true);

        List<Policy> policies = new ArrayList<>();

        Policy policy = new Policy();
        policy.setLimit(2L);
        policy.setQuota(2L);
        policy.setRefreshInterval(2L);
        LinkedHashMap<Policy.Type, String> map = Maps.newLinkedHashMap();
        map.put(Policy.Type.ROUTE, "serviceA");
        map.put(Policy.Type.ORIGIN, "");
        map.put(Policy.Type.URL, "");
        map.put(Policy.Type.USER, "");
        policy.setTypes(map);
        policies.add(policy);

        properties.setPolicyList(policies);

        return properties;
    }

    private RouteLocator routeLocator() {
        return new TestRouteLocator(asList("ignored"),
                asList(createRoute("serviceA", "/serviceA"), createRoute("serviceB", "/serviceB")));
    }

    void setRateLimiter(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        CounterFactory.initialize(new EmptyCounterFactory());
        this.request = new MockHttpServletRequest();
        this.response = new MockHttpServletResponse();
        this.rateLimitKeyGenerator = new DefaultRateLimitKeyGenerator(this.userIDGenerator, this.properties());
        UrlPathHelper urlPathHelper = new UrlPathHelper();
        this.filter = new RateLimitPreFilter(
                this.properties(),
                this.routeLocator(),
                urlPathHelper,
                this.rateLimiter,
                this.rateLimitKeyGenerator,
                this.userIDGenerator);
        this.context = new RequestContext();
        RequestContext.testSetCurrentContext(this.context);
        RequestContextHolder.setRequestAttributes(requestAttributes);
        this.context.clear();
        this.context.setRequest(this.request);
        this.context.setResponse(this.response);
    }

    @Test
    public void testRateLimit() throws Exception {
        this.request.setRequestURI("/serviceA");
        this.request.setRemoteAddr("10.0.0.100");

        assertTrue(this.filter.shouldFilter());

        for (int i = 0; i < 2; i++) {
            this.filter.run();
        }

        String remaining = this.response.getHeader(RateLimitPreFilter.REMAINING_HEADER);
        assertEquals("0", remaining);

        TimeUnit.SECONDS.sleep(3);

        this.filter.run();
        remaining = this.response.getHeader(RateLimitPreFilter.REMAINING_HEADER);
        assertEquals("1", remaining);
    }

    @Test
    public void testRateLimitExceedCapacity() throws Exception {
        this.request.setRequestURI("/serviceA");
        this.request.setRemoteAddr("10.0.0.100");
        this.request.addHeader("X-FORWARDED-FOR", "10.0.0.1");

        assertTrue(this.filter.shouldFilter());

        try {
            for (int i = 0; i <= 3; i++) {
                this.filter.run();
            }
        } catch (Exception e) {
            // do nothing
        }

        String exceeded = (String) this.context.get("rateLimitExceeded");
        assertTrue("RateLimit Exceeded", Boolean.valueOf(exceeded));
        assertEquals("Too many requests", TOO_MANY_REQUESTS.value(), this.context.getResponseStatusCode());
    }

    @Test
    public void testNoRateLimitService() throws Exception {
        this.request.setRequestURI("/serviceZ");
        this.request.setRemoteAddr("10.0.0.100");

        assertFalse(this.filter.shouldFilter());

        try {
            for (int i = 0; i <= 3; i++) {
                this.filter.run();
            }
        } catch (Exception e) {
            // do nothing
        }

        String exceeded = (String) this.context.get("rateLimitExceeded");
        assertFalse("RateLimit not exceeded", Boolean.valueOf(exceeded));
    }

    @Test
    public void testNoRateLimit() throws Exception {
        this.request.setRequestURI("/serviceB");
        this.request.setRemoteAddr("127.0.0.1");
        assertFalse(this.filter.shouldFilter());
    }
}
