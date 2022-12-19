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

import com.boozallen.graph.transition.api.model.Transition
import com.boozallen.graph.transition.generation.ParameterGenerator
import com.boozallen.graph.transition.util.TestUtil
import com.boozallen.graph.transition.util.TransactionRecordBuilder
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.apache.nifi.flowfile.FlowFile
import org.apache.nifi.json.JsonTreeReader
import org.apache.nifi.processor.ProcessSession
import org.apache.nifi.schema.access.SchemaAccessUtils
import org.apache.nifi.serialization.record.Record
import org.apache.nifi.util.MockFlowFile
import org.apache.nifi.util.TestRunner
import org.apache.nifi.util.TestRunners
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

class GraphDumpProcessorTest {
    TestRunner runner
    def reader
    def processor

    @BeforeClass
    static void beforeAll() {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "DEBUG")
        System.setProperty("org.slf4j.simplelogger.log.com.boozallen.graph.transition", "DEBUG")
    }

    @Before
    void setup() {
        def registry = TestUtil.buildSchemaRegistry()
        processor = new TestableGraphDumpProcessor(throwError: false)
        reader = new JsonTreeReader()
        runner = TestRunners.newTestRunner(processor)
        runner.addControllerService("registry", registry)
        runner.addControllerService("reader", reader)
        runner.setProperty(com.boozallen.graph.transition.impl.GraphDumpProcessor.READER_SERVICE, "reader")
        runner.setProperty(com.boozallen.graph.transition.impl.GraphDumpProcessor.SCHEMA_REGISTRY, "registry")
        runner.setProperty(reader, SchemaAccessUtils.SCHEMA_ACCESS_STRATEGY, SchemaAccessUtils.SCHEMA_NAME_PROPERTY)
        runner.setProperty(reader, SchemaAccessUtils.SCHEMA_REGISTRY, "registry")

        registry.addMapping("SocialMedia", getClass().getResourceAsStream("/social_media_mapping.json").text)
        registry.addMapping("TransactionMapping", getClass().getResourceAsStream("/transaction_mapping.json").text)
        registry.addMapping("SocialMediaTransitionComplex", getClass().getResourceAsStream("/social_media_mapping_complex.json").text)
        registry.addMapping("TargetSchemaMapping", getClass().getResourceAsStream("/array_test/mapping_with_target_schema.json").text)
        registry.addMapping("DefaultValue", getClass().getResourceAsStream("/default_value_test/default_value_mapping.json").text)
        registry.addMapping("DefaultValueOverride", getClass().getResourceAsStream("/default_value_test/default_value_override_mapping.json").text)

        runner.enableControllerService(registry)
        runner.enableControllerService(reader)

        runner.assertValid()
    }

    @Test
    void testDefaultValue() {
        def records = [
                [
                        Name: "TestName"
                ]
        ]
        def json = JsonOutput.toJson(records)
        runner.enqueue(json, [
                "graph.name"       : "graph",
                "schema.name"      : "DefaultValueSource",
                "transition.name": "DefaultValue"
        ])
        runner.run()

        runner.assertTransferCount(com.boozallen.graph.transition.impl.GraphDumpProcessor.REL_ORIGINAL, 1)
        runner.assertTransferCount(com.boozallen.graph.transition.impl.GraphDumpProcessor.REL_FAILURE, 0)
        runner.assertTransferCount(com.boozallen.graph.transition.impl.GraphDumpProcessor.REL_SUCCESS, 1)
        testFlowFiles(runner.getFlowFilesForRelationship(com.boozallen.graph.transition.impl.GraphDumpProcessor.REL_SUCCESS))
        FlowFile successFlowFile = runner.getFlowFilesForRelationship(com.boozallen.graph.transition.impl.GraphDumpProcessor.REL_SUCCESS).get(0)
        def flowFile = new String(successFlowFile.getData())
        JsonSlurper slurper = new JsonSlurper()
        def parsed = slurper.parseText(flowFile)
        assert parsed.newVertices.keySet().contains('left')
        assert parsed.newVertices.left.properties.keySet().contains('Name')
        assert parsed.newVertices.left.properties.keySet().contains('DefaultName')
    }

    @Test
    void testDefaultValueOverride() {
        def records = [
                [
                        Name: "TestName"
                ]
        ]
        def json = JsonOutput.toJson(records)
        runner.enqueue(json, [
                "graph.name"       : "graph",
                "schema.name"      : "DefaultValueSource",
                "transition.name": "DefaultValueOverride"
        ])
        runner.run()

        runner.assertTransferCount(com.boozallen.graph.transition.impl.GraphDumpProcessor.REL_ORIGINAL, 1)
        runner.assertTransferCount(com.boozallen.graph.transition.impl.GraphDumpProcessor.REL_FAILURE, 0)
        runner.assertTransferCount(com.boozallen.graph.transition.impl.GraphDumpProcessor.REL_SUCCESS, 1)
        testFlowFiles(runner.getFlowFilesForRelationship(com.boozallen.graph.transition.impl.GraphDumpProcessor.REL_SUCCESS))
        FlowFile successFlowFile = runner.getFlowFilesForRelationship(com.boozallen.graph.transition.impl.GraphDumpProcessor.REL_SUCCESS).get(0)
        def flowFile = new String(successFlowFile.getData())
        JsonSlurper slurper = new JsonSlurper()
        def parsed = slurper.parseText(flowFile)
        assert parsed.newVertices.keySet().contains('left')
        assert parsed.newVertices.left.properties.keySet().contains('Name')
        assert parsed.newVertices.left.properties.keySet().contains('DefaultName')
        assert parsed.newVertices.left.properties.DefaultName == "Override"
    }

    @Test
    void testArrayWithTargetSchema() {
        def builder = new com.boozallen.graph.transition.util.IdentificationListRecordBuilder()
        def records = []
        1.upto(5) {
            records << builder.createRecord()
        }
        def json = JsonOutput.toJson(records)
        runner.enqueue(json, [
                "graph.name"       : "graph",
                "schema.name"      : "NestedIdentificationRecord",
                "transition.name": "TargetSchemaMapping"
        ])
        runner.run()

        runner.assertTransferCount(com.boozallen.graph.transition.impl.GraphDumpProcessor.REL_ORIGINAL, 1)
        runner.assertTransferCount(com.boozallen.graph.transition.impl.GraphDumpProcessor.REL_FAILURE, 0)
        runner.assertTransferCount(com.boozallen.graph.transition.impl.GraphDumpProcessor.REL_SUCCESS, 5)
        testFlowFiles(runner.getFlowFilesForRelationship(com.boozallen.graph.transition.impl.GraphDumpProcessor.REL_SUCCESS))
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

        runner.assertTransferCount(com.boozallen.graph.transition.impl.GraphDumpProcessor.REL_ORIGINAL, 1)
        runner.assertTransferCount(com.boozallen.graph.transition.impl.GraphDumpProcessor.REL_FAILURE, 0)
        runner.assertTransferCount(com.boozallen.graph.transition.impl.GraphDumpProcessor.REL_SUCCESS, 5)
        testFlowFiles(runner.getFlowFilesForRelationship(com.boozallen.graph.transition.impl.GraphDumpProcessor.REL_SUCCESS))
    }

    void testFlowFiles(List<MockFlowFile> ffs) {
        def slurper = new JsonSlurper()
        ffs.each { ff ->
            def raw = runner.getContentAsByteArray(ff)
            def str = new String(raw)
            def parsed = slurper.parseText(str)
            ['existing', 'newVertices', 'edges'].each { key ->
                assert parsed.containsKey(key)
            }
        }
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

        runner.assertTransferCount(com.boozallen.graph.transition.impl.GraphDumpProcessor.REL_ORIGINAL, 1)
        runner.assertTransferCount(com.boozallen.graph.transition.impl.GraphDumpProcessor.REL_FAILURE, 0)
        runner.assertTransferCount(com.boozallen.graph.transition.impl.GraphDumpProcessor.REL_SUCCESS, 150)
        testFlowFiles(runner.getFlowFilesForRelationship(com.boozallen.graph.transition.impl.GraphDumpProcessor.REL_SUCCESS))
    }

    @Test
    void testArrayType() {
        def builder = new com.boozallen.graph.transition.util.SocialMediaRecordBuilder(10)
        def records = builder.buildRecordSet(2)

        def json = JsonOutput.toJson(records)

        runner.enqueue(json, [
                "graph.name"       : "graph",
                "schema.name"      : "SocialMediaActivity",
                "transition.name": "SocialMediaTransitionComplex"
        ])
        runner.run()

        runner.assertTransferCount(com.boozallen.graph.transition.impl.GraphDumpProcessor.REL_ORIGINAL, 1)
        runner.assertTransferCount(com.boozallen.graph.transition.impl.GraphDumpProcessor.REL_FAILURE, 0)
        runner.assertTransferCount(com.boozallen.graph.transition.impl.GraphDumpProcessor.REL_SUCCESS, 12)
        testFlowFiles(runner.getFlowFilesForRelationship(com.boozallen.graph.transition.impl.GraphDumpProcessor.REL_SUCCESS))
    }

    @Test
    void testErrorHandling() {
        def builder = new com.boozallen.graph.transition.util.SocialMediaRecordBuilder(100)
        def records = builder.buildRecordSet(25)
        def json = JsonOutput.toJson(records)

        processor.throwError = true

        runner.enqueue(json, [
                "graph.name"       : "graph",
                "schema.name"      : "SocialMediaActivity",
                "transition.name": "SocialMedia"
        ])
        runner.run()

        runner.assertTransferCount(com.boozallen.graph.transition.impl.GraphDumpProcessor.REL_ORIGINAL, 0)
        runner.assertTransferCount(com.boozallen.graph.transition.impl.GraphDumpProcessor.REL_FAILURE, 1)
        runner.assertTransferCount(com.boozallen.graph.transition.impl.GraphDumpProcessor.REL_SUCCESS, 0)
    }

    static final class TestableGraphDumpProcessor extends com.boozallen.graph.transition.impl.GraphDumpProcessor {
        boolean throwError

        void processRecord(ProcessSession session, FlowFile input, ParameterGenerator codeGenerator, Transition graphMapping,
                           Record record, List<FlowFile> flowFiles, long recordIndex) {
            if (throwError) {
                throw new RuntimeException("Testing error!")
            } else {
                super.processRecord(session, input, codeGenerator, graphMapping, record, flowFiles, recordIndex)
            }
        }
    }
}
