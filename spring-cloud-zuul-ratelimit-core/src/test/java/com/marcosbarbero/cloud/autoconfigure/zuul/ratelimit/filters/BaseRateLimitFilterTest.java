package com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.filters;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.RateLimiter;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.properties.Policy;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.properties.RateLimitProperties;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.repository.InMemoryRateLimiter;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.filters.commons.TestRouteLocator;
import com.netflix.zuul.context.RequestContext;
import com.netflix.zuul.monitoring.CounterFactory;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.springframework.cloud.netflix.zuul.filters.Route;
import org.springframework.cloud.netflix.zuul.filters.RouteLocator;
import org.springframework.cloud.netflix.zuul.metrics.EmptyCounterFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * @author Marcos Barbero
 * @since 2017-06-30
 */
public class BaseRateLimitFilterTest {

    protected RateLimitFilter filter;

    protected MockHttpServletRequest request;
    protected MockHttpServletResponse response;

    protected RequestContext context = RequestContext.getCurrentContext();
    protected RateLimiter rateLimiter = new InMemoryRateLimiter();

    private Route createRoute(String id, String path) {
        return new Route(id, path, null, null, false, Collections.emptySet());
    }

    private RateLimitProperties properties() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setEnabled(true);
        properties.setBehindProxy(true);

        Map<String, Policy> policies = new HashMap<>();

        Policy policy = new Policy();
        policy.setLimit(2L);
        policy.setRefreshInterval(2L);
        policy.setType(asList(Policy.Type.ORIGIN, Policy.Type.URL, Policy.Type.USER));

        policies.put("serviceA", policy);
        properties.setPolicies(policies);

        return properties;
    }

    private RouteLocator routeLocator() {
        return new TestRouteLocator(asList("ignored"),
                asList(createRoute("serviceA", "/serviceA"), createRoute("serviceB", "/serviceB")));
    }

    protected void setRateLimiter(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Before
    public void setUp() {
        CounterFactory.initialize(new EmptyCounterFactory());
        this.request = new MockHttpServletRequest();
        this.response = new MockHttpServletResponse();
        this.filter = new RateLimitFilter(this.rateLimiter, this.properties(), this.routeLocator());
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

        String remaining = this.response.getHeader(RateLimitFilter.Headers.REMAINING);
        assertEquals("0", remaining);

        TimeUnit.SECONDS.sleep(2);

        this.filter.run();
        remaining = this.response.getHeader(RateLimitFilter.Headers.REMAINING);
        assertEquals(remaining, "1");
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
        assertEquals("Too many requests", this.context.getResponseStatusCode(), TOO_MANY_REQUESTS.value());
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
