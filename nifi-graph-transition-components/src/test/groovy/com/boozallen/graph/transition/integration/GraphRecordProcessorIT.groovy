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

package com.boozallen.graph.transition.integration

import groovy.json.JsonOutput
import com.boozallen.graph.transition.clients.InMemoryDistributedMapCacheClient
import com.boozallen.graph.transition.clients.InMemoryGraphClient
import com.boozallen.graph.transition.util.TestUtil
import com.boozallen.graph.transition.util.TransactionRecordBuilder
import groovy.json.JsonSlurper
import org.apache.nifi.json.JsonTreeReader
import org.apache.nifi.schema.access.SchemaAccessUtils
import org.apache.nifi.serialization.record.MockRecordWriter
import org.apache.nifi.util.TestRunner
import org.apache.nifi.util.TestRunners
import org.junit.Before
import org.junit.Test

class GraphRecordProcessorIT {
    InMemoryGraphClient memClient
    TestRunner runner
    def reader

    @Before
    void setup() {
        def registry = TestUtil.buildSchemaRegistry()
        def writer = new MockRecordWriter()
        reader = new JsonTreeReader()
        runner = TestRunners.newTestRunner(com.boozallen.graph.transition.impl.GraphRecordProcessor.class)
        runner.addControllerService("registry", registry)
        runner.addControllerService("reader", reader)
        runner.addControllerService("writer", writer)
        runner.setProperty(com.boozallen.graph.transition.impl.GraphRecordProcessor.READER_SERVICE, "reader")
        runner.setProperty(com.boozallen.graph.transition.impl.GraphRecordProcessor.SCHEMA_REGISTRY, "registry")
        runner.setProperty(com.boozallen.graph.transition.impl.GraphRecordProcessor.WRITER_SERVICE, "writer")
        runner.setProperty(reader, SchemaAccessUtils.SCHEMA_ACCESS_STRATEGY, SchemaAccessUtils.SCHEMA_NAME_PROPERTY)
        runner.setProperty(reader, SchemaAccessUtils.SCHEMA_REGISTRY, "registry")

        registry.addMapping("SocialMedia", getClass().getResourceAsStream("/social_media_mapping.json").text)
        registry.addMapping("TransactionMapping", getClass().getResourceAsStream("/transaction_mapping.json").text)

        runner.enableControllerService(registry)
        runner.enableControllerService(writer)
        runner.enableControllerService(reader)

        def mapCache = new InMemoryDistributedMapCacheClient()
        memClient = new InMemoryGraphClient()
        runner.addControllerService("memClient", memClient)
        runner.addControllerService("mapCache", mapCache)
        runner.setProperty(com.boozallen.graph.transition.impl.GraphRecordProcessor.CLIENT_SERVICE, "memClient")
        runner.setProperty(com.boozallen.graph.transition.impl.GraphRecordProcessor.PRIMARY_CACHE, "mapCache")
        runner.enableControllerService(memClient)
        runner.enableControllerService(mapCache)
        runner.assertValid()
    }

    @Test
    void testDefaultValue() {
        def builder = new com.boozallen.graph.transition.util.SocialMediaRecordBuilder(100)
        def missing = builder.buildRecordSet(10, true)

        def json = JsonOutput.toJson(missing)

        runner.enqueue(json, [
            "graph.name"       : "graph",
            "schema.name"      : "SocialMediaActivity",
            "transition.name": "SocialMedia"
        ])
        runner.run()

        runner.assertTransferCount(com.boozallen.graph.transition.impl.GraphRecordProcessor.REL_SUCCESS, 1)
        runner.assertTransferCount(com.boozallen.graph.transition.impl.GraphRecordProcessor.REL_GRAPH, 1)
        runner.assertTransferCount(com.boozallen.graph.transition.impl.GraphRecordProcessor.REL_FAILURE, 0)

        def traversal = memClient.graph.traversal()
        assert traversal.E().has('Count', -1).count().next() == 60
    }

    @Test
    void testSocialMediaScenario() {
        def builder = new com.boozallen.graph.transition.util.SocialMediaRecordBuilder(100)
        def records = builder.buildRecordSet(25)

        def json = JsonOutput.toJson(records)

        runner.enqueue(json, [
            "graph.name"       : "graph",
            "schema.name"      : "SocialMediaActivity",
            "transition.name": "SocialMedia"
        ])
        runner.run()

        runner.assertTransferCount(com.boozallen.graph.transition.impl.GraphRecordProcessor.REL_SUCCESS, 1)
        runner.assertTransferCount(com.boozallen.graph.transition.impl.GraphRecordProcessor.REL_GRAPH, 1)
        runner.assertTransferCount(com.boozallen.graph.transition.impl.GraphRecordProcessor.REL_FAILURE, 0)

        def traversal = memClient.graph.traversal()
        traversal.V().hasLabel("social_media_user").each { vertex ->
            assert traversal.V(vertex).outE().count().next() >= 3
            assert traversal.V(vertex).outE().has("Type", "Chat").count().next() >= 1
            assert traversal.V(vertex).outE().has("Type", "SentFile").count().next() >= 1
            assert traversal.V(vertex).outE().has("Type", "SharedArticle").count().next() >= 1
        }
    }

    @Test
    void testTransactionScenario() {
        def builder = new TransactionRecordBuilder()
        def records = []
        1.upto(5) {
            records << builder.createRecord()
        }
        def json = JsonOutput.toJson(records)

        runner.enqueue(json, [
            "graph.name"       : "graph",
            "schema.name"      : "TransactionRecord",
            "transition.name": "TransactionMapping"
        ])
        runner.run()

        runner.assertTransferCount(com.boozallen.graph.transition.impl.GraphRecordProcessor.REL_SUCCESS, 1)
        runner.assertTransferCount(com.boozallen.graph.transition.impl.GraphRecordProcessor.REL_GRAPH, 1)
        runner.assertTransferCount(com.boozallen.graph.transition.impl.GraphRecordProcessor.REL_FAILURE, 0)

        def traversal = memClient.graph.traversal()
        assert traversal.V().hasLabel("SystemEntity").count().next() == 10
        assert traversal.V().hasLabel("person").count().next() == 10
        assert traversal.V().hasLabel("identifier").count().next() == 10
        assert traversal.V().hasLabel("location").count().next() == 10
        assert traversal.V().hasLabel("transaction").count().next() == 5

        ["person": "names", "identifier": "identifies", "location": "locates"].each { pair ->
            traversal.V().hasLabel(pair.key).each { person ->
                assert traversal.V(person).outE(pair.value).count().next() == 1
                assert traversal.V(person).outE(pair.value).inV().hasLabel("SystemEntity").count().next() == 1
            }
        }

        traversal.V().hasLabel("transaction").each { transaction ->
            traversal.V(transaction).outE("received").inV().hasLabel("SystemEntity").count().next() == 1
            traversal.V(transaction).inE("sent").outV().hasLabel("SystemEntity").count().next() == 1
        }
    }

    @Test
    void testDeduplication() {
        def builder = new TransactionRecordBuilder()
        def records = []
        1.upto(5) {
            records << builder.staticRecord()
        }
        def json = JsonOutput.toJson(records)

        runner.enqueue(json, [
            "graph.name"       : "graph",
            "schema.name"      : "TransactionRecord",
            "transition.name": "TransactionMapping"
        ])
        runner.run()

        runner.assertTransferCount(com.boozallen.graph.transition.impl.GraphRecordProcessor.REL_SUCCESS, 1)
        runner.assertTransferCount(com.boozallen.graph.transition.impl.GraphRecordProcessor.REL_GRAPH, 1)
        runner.assertTransferCount(com.boozallen.graph.transition.impl.GraphRecordProcessor.REL_FAILURE, 0)

        def traversal = memClient.graph.traversal()
        assert traversal.V().hasLabel("SystemEntity").count().next() == 10
        assert traversal.V().hasLabel("person").count().next() == 2
        assert traversal.V().hasLabel("identifier").count().next() == 2
        assert traversal.V().hasLabel("location").count().next() == 2
        assert traversal.V().hasLabel("transaction").count().next() == 5

        ["person": "names", "identifier": "identifies", "location": "locates"].each { pair ->
            traversal.V().hasLabel(pair.key).each { person ->
                assert traversal.V(person).outE(pair.value).count().next() == 5
                assert traversal.V(person).outE(pair.value).inV().hasLabel("SystemEntity").count().next() == 5
            }
        }

        traversal.V().hasLabel("transaction").each { transaction ->
            traversal.V(transaction).outE("received").inV().hasLabel("SystemEntity").count().next() == 1
            traversal.V(transaction).inE("sent").outV().hasLabel("SystemEntity").count().next() == 1
        }
    }

    @Test
    void testMultipleReturnTypes() {
        def builder = new TransactionRecordBuilder()
        def records = []
        1.upto(1) {
            records << builder.staticRecord()
        }
        def json = JsonOutput.toJson(records)

        runner.setProperty(com.boozallen.graph.transition.impl.GraphRecordProcessor.RECORD_SCRIPT, "Map<String, Object> vertexHashes = new HashMap()\n vertexHashes.put('1', (Long) 123) \n vertexHashes.put('2', (Long) 145)\n[ 'result': vertexHashes, 'testKey': vertexHashes ]")
        runner.enqueue(json, [
                "graph.name"       : "graph",
                "schema.name"      : "TransactionRecord",
                "transition.name": "TransactionMapping"
        ])
        runner.run()

        runner.assertTransferCount(com.boozallen.graph.transition.impl.GraphRecordProcessor.REL_SUCCESS, 1)
        runner.assertTransferCount(com.boozallen.graph.transition.impl.GraphRecordProcessor.REL_GRAPH, 1)
        runner.assertTransferCount(com.boozallen.graph.transition.impl.GraphRecordProcessor.REL_FAILURE, 0)
        runner.assertTransferCount(com.boozallen.graph.transition.impl.GraphRecordProcessor.REL_ERRORS, 1)
        JsonSlurper slurper = new JsonSlurper()
        def graphResponse = slurper.parse(runner.getFlowFilesForRelationship(com.boozallen.graph.transition.impl.GraphRecordProcessor.REL_GRAPH).get(0).toByteArray())
        assert ((List) graphResponse).size() == 4
        def resultCount = 0
        def testKey = 0
        ((List) graphResponse).stream().forEach{ it ->
           def iit = (Map) it
            if (iit.keySet().contains('result')) {
                resultCount++
            } else if( iit.keySet().contains('testKey')) {
                testKey++
            }
        }
        assert resultCount == 2
        assert testKey == 2
    }
}
