package com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.filters.pre;

import static com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.support.RateLimitConstants.HEADER_REMAINING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.repository.RateLimiterErrorHandler;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.repository.RedisRateLimiter;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * @author Marcos Barbero
 * @since 2017-06-30
 */
public class RedisRateLimitPreFilterTest extends BaseRateLimitPreFilterTest {

    private StringRedisTemplate redisTemplate;

    @BeforeEach
    @Override
    public void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        RateLimiterErrorHandler rateLimiterErrorHandler = mock(RateLimiterErrorHandler.class);
        this.setRateLimiter(new RedisRateLimiter(rateLimiterErrorHandler, this.redisTemplate));
        super.setUp();
    }

    @Test
    @Override
    @SuppressWarnings("unchecked")
    public void testRateLimitExceedCapacity() throws Exception {
        ValueOperations ops = mock(ValueOperations.class);
        doReturn(ops).when(redisTemplate).opsForValue();

        when(ops.increment(anyString(), anyLong())).thenReturn(3L);
        super.testRateLimitExceedCapacity();
    }

    @Test
    @Override
    @SuppressWarnings("unchecked")
    public void testRateLimit() throws Exception {
        ValueOperations ops = mock(ValueOperations.class);
        when(ops.increment(anyString(), anyLong())).thenReturn(1L);
        doReturn(ops).when(redisTemplate).opsForValue();
        when(ops.increment(anyString(), anyLong())).thenReturn(2L);


        this.request.setRequestURI("/serviceA");
        this.request.setRemoteAddr("10.0.0.100");

        assertTrue(this.filter.shouldFilter());

        for (int i = 0; i < 2; i++) {
            this.filter.run();
        }

        String key = "null_serviceA_10.0.0.100_anonymous";
        String remaining = this.response.getHeader(HEADER_REMAINING + key);
        assertEquals("0", remaining);

        TimeUnit.SECONDS.sleep(2);

        when(ops.increment(anyString(), anyLong())).thenReturn(1L);
        this.filter.run();
        remaining = this.response.getHeader(HEADER_REMAINING + key);
        assertEquals("1", remaining);
    }

    @Test
    public void testShouldReturnCorrectRateRemainingValue() {
        String redisKey = "null:serviceA:10.0.0.100:anonymous:GET";
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(eq(redisKey), eq("1"), anyLong(), any())).thenReturn(true, false);
        when(ops.increment(eq(redisKey), anyLong())).thenReturn(2L);

        this.request.setRequestURI("/serviceA");
        this.request.setRemoteAddr("10.0.0.100");
        this.request.setMethod("GET");

        assertTrue(this.filter.shouldFilter());

        String key = "null_serviceA_10.0.0.100_anonymous_GET";

        long requestCounter = 2;
        for (int i = 0; i < 2; i++) {
            this.filter.run();
            Long remaining = Long.valueOf(Objects.requireNonNull(this.response.getHeader(HEADER_REMAINING + key)));
            assertEquals(--requestCounter, remaining);
        }
    }
}
