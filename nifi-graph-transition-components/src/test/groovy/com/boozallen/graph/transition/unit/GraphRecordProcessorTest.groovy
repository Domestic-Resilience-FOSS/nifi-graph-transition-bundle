/*
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership. Booz Allen Hamilton licenses this file to
 * You under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy
 * of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.boozallen.graph.transition.unit

import com.boozallen.graph.transition.util.TestUtil
import groovy.json.JsonOutput
import org.apache.nifi.json.JsonTreeReader
import org.apache.nifi.schema.access.SchemaAccessUtils
import org.apache.nifi.serialization.record.MockRecordWriter
import org.apache.nifi.util.TestRunner
import org.apache.nifi.util.TestRunners
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

class GraphRecordProcessorTest {
    TestRunner runner
    com.boozallen.graph.transition.mock.MockMapCacheClient cache

    @BeforeClass
    static void beforeAll() {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "DEBUG")
        System.setProperty("org.slf4j.simplelogger.log.com.boozallen.graph.transition", "DEBUG")
    }

    @Before
    void before() {
        def registry = TestUtil.buildSchemaRegistry()
        def writer = new MockRecordWriter()
        def reader = new JsonTreeReader()
        def graphService = new com.boozallen.graph.transition.mock.MockGraphClientService()
        cache = new com.boozallen.graph.transition.mock.MockMapCacheClient()
        runner = TestRunners.newTestRunner(com.boozallen.graph.transition.impl.GraphRecordProcessor.class)
        runner.addControllerService("cache", cache)
        runner.addControllerService("graphService", graphService)
        runner.addControllerService("registry", registry)
        runner.addControllerService("reader", reader)
        runner.addControllerService("writer", writer)
        runner.setProperty(com.boozallen.graph.transition.impl.GraphRecordProcessor.READER_SERVICE, "reader")
        runner.setProperty(com.boozallen.graph.transition.impl.GraphRecordProcessor.SCHEMA_REGISTRY, "registry")
        runner.setProperty(com.boozallen.graph.transition.impl.GraphRecordProcessor.WRITER_SERVICE, "writer")
        runner.setProperty(com.boozallen.graph.transition.impl.GraphRecordProcessor.CLIENT_SERVICE, "graphService")
        runner.setProperty(com.boozallen.graph.transition.impl.GraphRecordProcessor.PRIMARY_CACHE, "cache")
        runner.setProperty(reader, SchemaAccessUtils.SCHEMA_ACCESS_STRATEGY, SchemaAccessUtils.SCHEMA_NAME_PROPERTY)
        runner.setProperty(reader, SchemaAccessUtils.SCHEMA_REGISTRY, "registry")

        registry.addMapping("SocialMedia", getClass().getResourceAsStream("/social_media_mapping.json").text)
        registry.addMapping("TransactionMapping", getClass().getResourceAsStream("/transaction_mapping.json").text)

        runner.enableControllerService(cache)
        runner.enableControllerService(graphService)
        runner.enableControllerService(registry)
        runner.enableControllerService(reader)
        runner.enableControllerService(writer)

        runner.assertValid()
    }

    @Test
    void test() {
        def builder = new com.boozallen.graph.transition.util.SocialMediaRecordBuilder(100)
        def records = builder.buildRecordSet(25)

        def json = JsonOutput.toJson(records)

        runner.enqueue(json, [
            "graph.name"       : "graph",
            "schema.name"      : "SocialMediaActivity",
            "transition.name": "SocialMedia"
        ])
        runner.run()

        runner.assertTransferCount(com.boozallen.graph.transition.impl.GraphRecordProcessor.REL_FAILURE, 0)
        runner.assertTransferCount(com.boozallen.graph.transition.impl.GraphRecordProcessor.REL_SUCCESS, 1)

        assert cache._map.size() == 150
        //We now have some reasonble basis to assume it was sending stuff where it was supposed to go
    }
}
