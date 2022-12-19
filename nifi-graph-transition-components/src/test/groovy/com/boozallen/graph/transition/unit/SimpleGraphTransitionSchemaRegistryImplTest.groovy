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

import com.boozallen.graph.transition.api.GraphTransitionSchemaRegistry
import com.boozallen.graph.transition.api.model.Transition
import com.boozallen.graph.transition.exception.TransitionNotFoundException
import org.apache.avro.Schema
import org.apache.nifi.avro.AvroTypeUtil
import org.apache.nifi.processor.AbstractProcessor
import org.apache.nifi.processor.ProcessContext
import org.apache.nifi.processor.ProcessSession
import org.apache.nifi.processor.exception.ProcessException
import org.apache.nifi.schema.access.SchemaField
import org.apache.nifi.schema.access.SchemaNotFoundException
import org.apache.nifi.serialization.record.MockSchemaRegistry
import org.apache.nifi.serialization.record.RecordSchema
import org.apache.nifi.serialization.record.SchemaIdentifier
import org.apache.nifi.serialization.record.StandardSchemaIdentifier
import org.apache.nifi.util.TestRunner
import org.apache.nifi.util.TestRunners
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

class SimpleGraphTransitionSchemaRegistryImplTest {
    @BeforeClass
    static void beforeAll() {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "DEBUG")
        System.setProperty("org.slf4j.simplelogger.log.com.boozallen.graph.transition", "DEBUG")
    }

    TestRunner runner
    GraphTransitionSchemaRegistry registry

    static final List<String> SCHEMAS = ["SocialMediaActivity", "SocialMediaInteraction", "SocialMediaUser"]

    @Before
    void setup() {
        registry = new com.boozallen.graph.transition.impl.SimpleGraphTransitionSchemaRegistryImpl()
        runner = TestRunners.newTestRunner(new AbstractProcessor() {
            @Override
            void onTrigger(ProcessContext processContext, ProcessSession processSession) throws ProcessException {

            }
        })
        runner.addControllerService("registry", registry)
    }

    @Test
    void testWithDelegation() {
        def delegate = new MockSchemaRegistry()
        runner.addControllerService("delegate", delegate)
        SCHEMAS.each {
            String text = this.class.getResourceAsStream("/schemas/${it}.avsc").text
            delegate.addSchema(it, AvroTypeUtil.createSchema(new Schema.Parser().parse(text)))
        }
        runner.setProperty(registry, "social_media_mapping", this.class.getResourceAsStream("/social_media_mapping.json").text)
        runner.setProperty(registry, com.boozallen.graph.transition.impl.SimpleGraphTransitionSchemaRegistryImpl.DELEGATE_REGISTRY, "delegate")
        runner.enableControllerService(delegate)
        runner.enableControllerService(registry)
        runner.assertValid()
        testRegistry(registry)
    }

    @Test
    void testCustomValidate() {
        def delegate = new MockSchemaRegistry()
        runner.addControllerService("delegate", delegate)
        SCHEMAS.each {
            runner.setProperty(registry, it, this.class.getResourceAsStream("/schemas/${it}.avsc").text)
        }
        runner.setProperty(registry, "social_media_mapping", this.class.getResourceAsStream("/social_media_mapping.json").text)
        runner.setProperty(registry, com.boozallen.graph.transition.impl.SimpleGraphTransitionSchemaRegistryImpl.DELEGATE_REGISTRY, "delegate")
        runner.enableControllerService(delegate)

        boolean threw = false
        def message
        try {
            runner.enableControllerService(registry)
        } catch (IllegalStateException ex) {
            threw = true
            message = ex.message
        } finally {
            assert threw
            assert message?.toLowerCase().contains("transition json definition")
        }
    }

    @Test
    void testTransitionNotFoundErrorHandling() {
        runner.setProperty(registry, "social_media_mapping", "junk data")
        runner.enableControllerService(registry)
        runner.assertValid()
        def ex;
        try {
            registry.getTransitionMapping("social_media_mapping")
        } catch (ProcessException exc) {
            ex = exc
        } finally {
            assert ex && ex instanceof ProcessException
        }
    }

    @Test
    void testTransitionBadDefinitionErrorHandling() {
        runner.enableControllerService(registry)
        runner.assertValid()
        def ex;
        try {
            registry.getTransitionMapping("Missing")
        } catch (TransitionNotFoundException exc) {
            ex = exc
        } finally {
            assert ex && ex instanceof TransitionNotFoundException
        }
    }

    @Test
    void testSchemaMissingThrowsException() {
        runner.enableControllerService(registry)
        runner.assertValid()
        def ex;
        try {
            registry.retrieveSchema(new StandardSchemaIdentifier.Builder().name("Missing").build())
        } catch (SchemaNotFoundException exc) {
            ex = exc
        } finally {
            assert ex && ex instanceof SchemaNotFoundException
        }
    }

    @Test
    void testWithNoDelegation() {
        SCHEMAS.each {
            runner.setProperty(registry, it, this.class.getResourceAsStream("/schemas/${it}.avsc").text)
        }
        runner.setProperty(registry, "social_media_mapping", this.class.getResourceAsStream("/social_media_mapping.json").text)
        runner.enableControllerService(registry)
        runner.assertValid()

        testRegistry(registry)
    }

    @Test
    void testLoadSchemasFromDirectory() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        URL url = loader.getResource("array_test")
        String path = url.toURI().getPath()
        runner.setProperty(registry, com.boozallen.graph.transition.impl.SimpleGraphTransitionSchemaRegistryImpl.SEARCH_DIRECTORY, path)
        runner.enableControllerService(registry)
        assert registry.getTransitionMapping("NestedIdentificationRecordTransition")
        assert registry.getTransitionMapping("NestedIdentificationRecordTransition").getSourceSubject() == "NestedIdentificationRecord"
        SchemaIdentifier identifier = new StandardSchemaIdentifier.Builder().name("EmployeeRecord").build()
        assert registry.retrieveSchema(identifier)
    }

    @Test
    void testSuppliedFields() {
        def fields = registry.getSuppliedSchemaFields();
        assert fields
        assert fields.contains(SchemaField.SCHEMA_NAME)
        assert fields.contains(SchemaField.SCHEMA_TEXT)
        assert fields.contains(SchemaField.SCHEMA_VERSION)
    }

    void testRegistry(com.boozallen.graph.transition.impl.SimpleGraphTransitionSchemaRegistryImpl registry) {
        def activity = registry.retrieveSchema(new StandardSchemaIdentifier.Builder().name("SocialMediaActivity").build())
        def interaction = registry.retrieveSchema(new StandardSchemaIdentifier.Builder().name("SocialMediaInteraction").build())
        def user = registry.retrieveSchema(new StandardSchemaIdentifier.Builder().name("SocialMediaUser").build())
        def transition = registry.getTransitionMapping("social_media_mapping")

        assert activity && activity instanceof RecordSchema
        assert interaction && interaction instanceof RecordSchema
        assert user && user instanceof RecordSchema
        assert transition && transition instanceof Transition
    }
}
