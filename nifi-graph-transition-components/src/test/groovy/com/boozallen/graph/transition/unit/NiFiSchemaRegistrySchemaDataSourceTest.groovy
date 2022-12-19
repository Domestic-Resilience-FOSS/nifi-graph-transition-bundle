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

import com.boozallen.graph.transition.generation.IdentifierConstants
import com.boozallen.graph.transition.generation.NiFiSchemaRegistrySchemaDataSource
import org.apache.avro.Schema
import org.apache.nifi.avro.AvroTypeUtil
import org.apache.nifi.processor.AbstractProcessor
import org.apache.nifi.processor.ProcessContext
import org.apache.nifi.processor.ProcessSession
import org.apache.nifi.processor.exception.ProcessException
import org.apache.nifi.serialization.record.MockSchemaRegistry
import org.apache.nifi.util.TestRunners
import org.junit.BeforeClass
import org.junit.Test

class NiFiSchemaRegistrySchemaDataSourceTest {
    @BeforeClass
    static void beforeAll() {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "DEBUG")
        System.setProperty("org.slf4j.simplelogger.log.com.boozallen.graph.transition", "DEBUG")
    }

    @Test
    void test() {
        def registry = new MockSchemaRegistry()
        def processor = new AbstractProcessor() {
            @Override
            void onTrigger(ProcessContext processContext, ProcessSession processSession) throws ProcessException {

            }
        }
        def runner = TestRunners.newTestRunner(processor)
        runner.addControllerService("registry", registry)
        runner.enableControllerService(registry)
        registry.addSchema("simple", AvroTypeUtil.createSchema(new Schema.Parser().parse("""
            {
                "type": "record",
                "name": "simple",
                "fields": [
                    { "name": "msg", "type": "string" }
                ]
            }
        """)))

        def schemaDataSource = new NiFiSchemaRegistrySchemaDataSource(registry)
        def schema = schemaDataSource.getSchema([(IdentifierConstants.IDENTIFIER_NAME): "simple"])
        assert schema
        assert schema.getField("msg")
    }
}
