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

package com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.repository.bucket4j;

import io.github.bucket4j.grid.GridBucketState;
import io.github.bucket4j.grid.ProxyManager;
import io.github.bucket4j.grid.ignite.IgniteBucketBuilder;
import org.apache.ignite.IgniteCache;

/**
 * Bucket4j rate limiter configuration.
 *
 * @author Liel Chayoun
 * @since 2018-04-06
 */
public class Bucket4jIgniteRateLimiter extends Bucket4jRateLimiter<IgniteBucketBuilder, io.github.bucket4j.grid.ignite.Ignite> {

    private final IgniteCache<String, GridBucketState> cache;

    public Bucket4jIgniteRateLimiter(IgniteCache<String, GridBucketState> cache) {
        super(io.github.bucket4j.grid.ignite.Ignite.class);
        this.cache = cache;
        super.init();
    }

    @Override
    protected ProxyManager<String> getProxyManager(io.github.bucket4j.grid.ignite.Ignite extension) {
        return extension.proxyManagerForCache(cache);
    }
}
