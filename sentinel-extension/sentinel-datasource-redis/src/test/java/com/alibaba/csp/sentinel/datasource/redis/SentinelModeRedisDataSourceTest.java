/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.csp.sentinel.datasource.redis;

import com.alibaba.csp.sentinel.datasource.Converter;
import com.alibaba.csp.sentinel.datasource.ReadableDataSource;
import com.alibaba.csp.sentinel.datasource.redis.config.RedisConnectionConfig;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.*;

import java.util.List;
import java.util.Random;

/**
 * Redis redisSentinel mode test cases for {@link RedisDataSource}.
 *
 * @author tiger
 */
@Ignore(value = "before run this test. you should build your own redisSentinel config in local")
public class SentinelModeRedisDataSourceTest {

    private String host = "localhost";

    private int redisSentinelPort = 5000;

    private String redisSentinelMasterId = "mymaster";

    private String ruleKey = "redis.redisSentinel.flow.rulekey";

    private String channel = "redis.redisSentinel.flow.channel";

    private final RedisClient client = RedisClient.create(RedisURI.Builder.sentinel(host, redisSentinelPort)
            .withSentinelMasterId(redisSentinelMasterId).build());

    @Before
    public void initData() {
        Converter<String, List<FlowRule>> flowConfigParser = buildFlowConfigParser();
        RedisConnectionConfig config = RedisConnectionConfig.builder()
                .withRedisSentinel(host, redisSentinelPort)
                .withRedisSentinel(host, redisSentinelPort)
                .withSentinelMasterId(redisSentinelMasterId).build();
        initRedisRuleData();
        ReadableDataSource<String, List<FlowRule>> redisDataSource = new RedisDataSource<List<FlowRule>>(config, ruleKey, channel, flowConfigParser);
        FlowRuleManager.register2Property(redisDataSource.getProperty());
    }

    @Test
    public void testConnectToSentinelAndPubMsgSuccess() {
        int maxQueueingTimeMs = new Random().nextInt();
        String flowRulesJson = "[{\"resource\":\"test\", \"limitApp\":\"default\", \"grade\":1, \"count\":\"0.0\", \"strategy\":0, \"refResource\":null, " +
                "\"controlBehavior\":0, \"warmUpPeriodSec\":10, \"maxQueueingTimeMs\":" + maxQueueingTimeMs + ", \"controller\":null}]";
        RedisCommands<String, String> subCommands = client.connect().sync();
        subCommands.multi();
        subCommands.set(ruleKey, flowRulesJson);
        subCommands.publish(channel, flowRulesJson);
        subCommands.exec();
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        List<FlowRule> rules = FlowRuleManager.getRules();
        Assert.assertEquals(1, rules.size());
        rules = FlowRuleManager.getRules();
        Assert.assertEquals(rules.get(0).getMaxQueueingTimeMs(), maxQueueingTimeMs);
        String value = subCommands.get(ruleKey);
        List<FlowRule> flowRulesValuesInRedis = buildFlowConfigParser().convert(value);
        Assert.assertEquals(flowRulesValuesInRedis.size(), 1);
        Assert.assertEquals(flowRulesValuesInRedis.get(0).getMaxQueueingTimeMs(), maxQueueingTimeMs);
    }

    @After
    public void clearResource() {
        RedisCommands<String, String> stringRedisCommands = client.connect().sync();
        stringRedisCommands.del(ruleKey);
        client.shutdown();
    }

    private Converter<String, List<FlowRule>> buildFlowConfigParser() {
        return new Converter<String, List<FlowRule>>() {
            @Override
            public List<FlowRule> convert(String source) {
                return JSON.parseObject(source, new TypeReference<List<FlowRule>>() {
                });
            }
        };
    }

    private void initRedisRuleData() {
        String flowRulesJson = "[{\"resource\":\"test\", \"limitApp\":\"default\", \"grade\":1, \"count\":\"0.0\", \"strategy\":0, \"refResource\":null, " +
                "\"controlBehavior\":0, \"warmUpPeriodSec\":10, \"maxQueueingTimeMs\":500, \"controller\":null}]";
        RedisCommands<String, String> stringRedisCommands = client.connect().sync();
        String ok = stringRedisCommands.set(ruleKey, flowRulesJson);
        Assert.assertTrue(ok.equals("OK"));
    }
}
