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

package com.boozallen.graph.transition.util

import org.apache.avro.Schema
import org.apache.nifi.avro.AvroTypeUtil

class TestUtil {
    static com.boozallen.graph.transition.mock.MockGraphTransitionSchemaRegistry buildSchemaRegistry() {
        def registry = new com.boozallen.graph.transition.mock.MockGraphTransitionSchemaRegistry()
        [
                'IdentificationRecord',
                'LocationRecord',
                'PersonNameRecord',
                'TransactionDetailRecord',
                'TransactionRecord',
                'SocialMediaActivity',
                'SocialMediaInteraction',
                'SocialMediaUser',
                'ComplexTypeTest',
                'BaseVertex',
                'BaseEdge',
                'NestedIdentificationRecord',
                'GenericPersonIdentificationNumber',
                'EmployeeRecord'
        ].each {
            registry.addSchema(it, AvroTypeUtil.createSchema(
                    new Schema.Parser().parse(
                            TestUtil.class.getResourceAsStream("/schemas/${it}.avsc").text
                    )
            ))
        }

        [
                'DefaultValue',
                'DefaultValueSource'
        ].each {
            registry.addSchema(it, AvroTypeUtil.createSchema(
                    new Schema.Parser().parse(
                            TestUtil.class.getResourceAsStream("/default_value_test/${it}.avsc").text
                    )
            ))
        }

        registry
    }
}
