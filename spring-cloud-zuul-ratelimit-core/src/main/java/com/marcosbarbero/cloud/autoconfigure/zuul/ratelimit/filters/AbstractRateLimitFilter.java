/*
 * Copyright 2012-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.filters;

import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.RateLimitUtils;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.properties.RateLimitProperties;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.properties.RateLimitProperties.Policy;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.properties.RateLimitProperties.Policy.MatchType;
import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import org.springframework.cloud.netflix.zuul.filters.Route;
import org.springframework.cloud.netflix.zuul.filters.RouteLocator;
import org.springframework.web.util.UrlPathHelper;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.support.RateLimitConstants.REQUEST_ROUTE;

/**
 * @author Marcos Barbero
 * @author Liel Chayoun
 */
abstract class AbstractRateLimitFilter extends ZuulFilter {

    private final RateLimitProperties properties;
    private final RouteLocator routeLocator;
    private final UrlPathHelper urlPathHelper;
    private final RateLimitUtils rateLimitUtils;

    private boolean alreadyLimited;

    AbstractRateLimitFilter(RateLimitProperties properties, RouteLocator routeLocator, UrlPathHelper urlPathHelper, RateLimitUtils rateLimitUtils) {
        this.properties = properties;
        this.routeLocator = routeLocator;
        this.urlPathHelper = urlPathHelper;
        this.rateLimitUtils = rateLimitUtils;
    }

    @Override
    public boolean shouldFilter() {
        HttpServletRequest request = RequestContext.getCurrentContext().getRequest();
        return properties.isEnabled() && !policy(route(request), request).isEmpty();
    }

    Route route(HttpServletRequest request) {
        Route route = (Route) RequestContext.getCurrentContext().get(REQUEST_ROUTE);
        if (route != null) {
            return route;
        }

        String requestURI = urlPathHelper.getPathWithinApplication(request);

        return Optional.ofNullable(routeLocator.getMatchingRoute(requestURI)).map(matchingRoute -> {
            RequestContext.getCurrentContext().put(REQUEST_ROUTE, matchingRoute);
            return matchingRoute;
        }).orElse(null);
    }

    protected List<Policy> policy(Route route, HttpServletRequest request) {
        String routeId = Optional.ofNullable(route).map(Route::getId).orElse(null);
        alreadyLimited = false;
        return properties.getPolicies(routeId).stream()
                .filter(policy -> applyPolicy(request, route, policy))
                .collect(Collectors.toList());
    }

    private boolean applyPolicy(HttpServletRequest request, Route route, Policy policy) {
        List<MatchType> types = policy.getType();
        boolean tmp = alreadyLimited;
        if (policy.isBreakOnMatch() && types.stream().allMatch(type -> type.apply(request, route, rateLimitUtils))) {
            alreadyLimited = true;
        }
        return (types.isEmpty() || types.stream().allMatch(type -> type.apply(request, route, rateLimitUtils))) && !tmp;
    }
}
