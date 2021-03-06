/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.apache.ignite.internal.processors.cache.eviction.paged;

import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.cache.CacheWriteSynchronizationMode;
import org.apache.ignite.cache.affinity.rendezvous.RendezvousAffinityFunction;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.DataPageEvictionMode;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.configuration.MemoryConfiguration;
import org.apache.ignite.configuration.MemoryPolicyConfiguration;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.TcpDiscoveryIpFinder;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.apache.ignite.testframework.junits.common.GridCommonAbstractTest;

/**
 *
 */
public class PageEvictionAbstractTest extends GridCommonAbstractTest {
    /** */
    protected static final TcpDiscoveryIpFinder IP_FINDER = new TcpDiscoveryVmIpFinder(true);

    /** Offheap size for memory policy. */
    private static final int SIZE = 96 * 1024 * 1024;

    /** Page size. */
    static final int PAGE_SIZE = 2048;

    /** Number of entries. */
    static final int ENTRIES = 80_000;

    /** Empty pages pool size. */
    private static final int EMPTY_PAGES_POOL_SIZE = 100;

    /** Eviction threshold. */
    private static final double EVICTION_THRESHOLD = 0.9;

    /** Default policy name. */
    private static final String DEFAULT_POLICY_NAME = "dfltPlc";

    /**
     * @param mode Eviction mode.
     * @param configuration Configuration.
     * @return Configuration with given eviction mode set.
     */
    static IgniteConfiguration setEvictionMode(DataPageEvictionMode mode, IgniteConfiguration configuration) {
        MemoryPolicyConfiguration[] policies = configuration.getMemoryConfiguration().getMemoryPolicies();

        for (MemoryPolicyConfiguration plcCfg : policies)
            plcCfg.setPageEvictionMode(mode);

        return configuration;
    }

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String gridName) throws Exception {
        IgniteConfiguration cfg = super.getConfiguration(gridName);

        ((TcpDiscoverySpi)cfg.getDiscoverySpi()).setIpFinder(IP_FINDER);

        MemoryConfiguration dbCfg = new MemoryConfiguration();

        MemoryPolicyConfiguration plc = new MemoryPolicyConfiguration();

        plc.setSize(SIZE);
        plc.setEmptyPagesPoolSize(EMPTY_PAGES_POOL_SIZE);
        plc.setEvictionThreshold(EVICTION_THRESHOLD);
        plc.setName(DEFAULT_POLICY_NAME);

        dbCfg.setMemoryPolicies(plc);
        dbCfg.setPageSize(PAGE_SIZE);
        dbCfg.setDefaultMemoryPolicyName(DEFAULT_POLICY_NAME);

        cfg.setMemoryConfiguration(dbCfg);

        return cfg;
    }

    /**
     * @param name Name.
     * @param cacheMode Cache mode.
     * @param atomicityMode Atomicity mode.
     * @param writeSynchronizationMode Write synchronization mode.
     * @param memoryPlcName Memory policy name.
     * @return Cache configuration.
     */
    protected static CacheConfiguration<Object, Object> cacheConfig(
        String name,
        String memoryPlcName,
        CacheMode cacheMode,
        CacheAtomicityMode atomicityMode,
        CacheWriteSynchronizationMode writeSynchronizationMode
    ) {
        CacheConfiguration<Object, Object> cacheConfiguration = new CacheConfiguration<>()
            .setName(name)
            .setAffinity(new RendezvousAffinityFunction(false, 32))
            .setCacheMode(cacheMode)
            .setAtomicityMode(atomicityMode)
            .setMemoryPolicyName(memoryPlcName)
            .setWriteSynchronizationMode(writeSynchronizationMode);

        if (cacheMode == CacheMode.PARTITIONED)
            cacheConfiguration.setBackups(1);

        return cacheConfiguration;
    }
}
