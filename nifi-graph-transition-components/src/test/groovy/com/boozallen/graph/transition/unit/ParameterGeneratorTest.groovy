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
import com.fasterxml.jackson.databind.ObjectMapper
import com.boozallen.graph.transition.exception.InvalidRecordPathException
import com.boozallen.graph.transition.generation.NiFiSchemaRegistrySchemaDataSource
import com.boozallen.graph.transition.generation.ParameterGenerator
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.apache.avro.Schema
import org.apache.nifi.avro.AvroTypeUtil
import org.apache.nifi.processor.AbstractProcessor
import org.apache.nifi.processor.ProcessContext
import org.apache.nifi.processor.ProcessSession
import org.apache.nifi.processor.exception.ProcessException
import org.apache.nifi.schemaregistry.services.SchemaRegistry
import org.apache.nifi.serialization.record.MapRecord
import org.apache.nifi.serialization.record.MockSchemaRegistry
import org.apache.nifi.serialization.record.Record
import org.apache.nifi.util.TestRunner
import org.apache.nifi.util.TestRunners
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

import java.sql.Timestamp
import java.util.concurrent.TimeUnit

class ParameterGeneratorTest {
    ParameterGenerator codeGenerator
    SchemaRegistry registry
    TestRunner runner

    @BeforeClass
    static void beforeAll() {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "DEBUG")
        System.setProperty("org.slf4j.simplelogger.log.com.boozallen.graph.transition", "DEBUG")
    }

    @Before
    void setup() {
        registry = new MockSchemaRegistry()

        def schemaMap = [
                AddressRecord                 : "/mapping_test/location.avsc",
                ArrayPersonRecord             : "/array_of_primitives/Person.avsc",
                AssociatedLocationRecord      : "/mapping_test/associated_location.avsc",
                ContactRecord                 : "/mapping_test/contact.avsc",
                DetailedUser                  : "/detailed_user.avsc",
                EmployeeRecord                : "/array_test/employee.avsc",
                EmploymentRecord              : "/array_test/employment.avsc",
                EmployerRecord                : "/array_test/employer.avsc",
                Nickname                      : "/array_of_primitives/Nickname.avsc",
                OnlineMessage                 : "/array_of_primitives/OnlineMessage.avsc",
                PersonFullName                : "/record_path_test/PersonFullName.avsc",
                PersonPhoneInput              : "/array_of_primitives/PersonPhoneInput.avsc",
                PersonRecord                  : "/mapping_test/person.avsc",
                PrimitiveArrayInput           : "/array_of_primitives/Input.avsc",
                SimpleLongSourceRecord        : "/record_path_test/SimpleLongSourceRecord.avsc",
                SimplePersonName              : "/record_path_test/SimplePersonName.avsc",
                PersonFullNameExcludeFromCache: "/record_path_test/PersonFullNameExcludeFromCache.avsc",
                SimplePhone                   : "/array_of_primitives/SimplePhoneRecord.avsc",
                Transaction                   : "/mapping_test/transaction.avsc",
                TransactionDetailRecord       : "/mapping_test/transaction_detail.avsc",
                BaseEdge                      : "/schemas/BaseEdge.avsc",
                ComplexPhone                  : "/array_of_primitives/ComplexPhoneRecord.avsc",
                BaseVertex                    : "/schemas/BaseVertex.avsc"
        ]

        schemaMap.each { kv ->
            try {
                registry.addSchema(kv.key, AvroTypeUtil.createSchema(new Schema.Parser().parse(
                        this.getClass().getResourceAsStream(kv.value)
                )))
            } catch (org.apache.avro.AvroTypeException a) {
                println kv.value
                throw new RuntimeException(a)
            }
        }

        def processor = new AbstractProcessor() {
            @Override
            void onTrigger(ProcessContext processContext, ProcessSession processSession) throws ProcessException {

            }
        }
        runner = TestRunners.newTestRunner(processor)
        runner.addControllerService("registry", registry)
        def dataSource = new NiFiSchemaRegistrySchemaDataSource(registry)

        codeGenerator = new ParameterGenerator()
        codeGenerator.componentLog = runner.logger
        codeGenerator.schemaDatasource = dataSource
    }

    @Test
    void doSimpleTest() {
        simpleTest() {
            assert it['existing'].size() == 0
            assert it['newVertices'].size() == 2
        }
    }

    void simpleTest(Closure extra) {
        simpleTest("/mapping_test/transaction_mapping.json", extra)
    }

    Transition parse(String input) {
        new ObjectMapper().readValue(input, Transition.class)
    }

    void simpleTest(String mappingPath, Closure extra) {
        def mapping = parse(this.getClass().getResourceAsStream(mappingPath).text)
        assert mapping

        def mockRecord = new MapRecord(registry.retrieveSchema("Transaction"), [
                sender_firstName  : "John",
                sender_lastName   : "Smith",
                receiver_firstName: "Jane",
                receiver_lastName : "Doe",
                amount            : 3000.00d,
                when              : new Timestamp(Calendar.instance.timeInMillis - (24 * 60 * 60 * 1000))
        ])
        def generatedCode = codeGenerator.generate(mapping, mockRecord, [:])

        if (extra != null) {
            extra.call(generatedCode)
        }
    }

    @Test
    void testDefaultValueSupport() {
        simpleTest("/mapping_test/default_value_test_mapping.json") { generatedCode ->
            assert generatedCode['existing'].size() == 0
            assert generatedCode['newVertices'].size() == 2
            assert generatedCode['edges'].size() == 1
        }
    }

    @Test
    void testWithMapCache() {
        def cache = new com.boozallen.graph.transition.mock.MockMapCacheClient()
        runner.addControllerService("cache", cache)
        runner.enableControllerService(cache)
        codeGenerator.cache = cache
        cache._map["31fb1c47195f2fe9143b2fa416105e44245e1a4d"] = 100l
        simpleTest { generatedCode ->
            assert generatedCode['existing'].size() == 1
            assert generatedCode['newVertices'].size() == 1
        }
    }

    private Record getTestRecord() {
        new MapRecord(registry.retrieveSchema("DetailedUser"), [
                contact           : new MapRecord(registry.retrieveSchema("ContactRecord"), [
                        phoneNumber: "867-5309",
                        email      : "test@test.com"
                ]),
                firstName         : "Test",
                lastName          : "User",
                associatedLocation: new MapRecord(registry.retrieveSchema("AssociatedLocationRecord"), [
                        daysAt : 50,
                        purpose: "WORK",
                        address: new MapRecord(registry.retrieveSchema("AddressRecord"), [
                                street : "55 Main St",
                                city   : "Arlington",
                                state  : "VA",
                                country: "USA"
                        ])
                ])
        ])
    }

    @Test
    void testFromRecordPath() {
        def mapping = parse(this.getClass().getResourceAsStream("/mapping_test/from_rp_mapping.json").text)
        assert mapping

        def mockRecord = getTestRecord()
        def generatedCode = codeGenerator.generate(mapping, mockRecord, [:])
        assert generatedCode['existing'].size() == 0
        assert generatedCode['newVertices'].size() == 4
        assert generatedCode['edges'].size() == 3
    }

    @Test
    void testArrays() {
        def mapping = parse(this.getClass().getResourceAsStream("/array_test/mapping.json").text)
        assert mapping

        def employeeSchema = registry.retrieveSchema("EmployeeRecord")
        def mockRecord = new MapRecord(registry.retrieveSchema("EmploymentRecord"), [
                "companyName"   : "SmallConsulting",
                "companyCountry": "USA",
                "employees"     : [
                        new MapRecord(employeeSchema, [
                                "employeeName": "John Smith",
                                "role"        : "Sr. Software Engineer",
                                "salary"      : 100000.0d
                        ]),
                        new MapRecord(employeeSchema, [
                                "employeeName": "Jane Doe",
                                "role"        : "Software Development Intern",
                                "salary"      : 15000.0d
                        ]),
                        new MapRecord(employeeSchema, [
                                "employeeName": "Capelli Puntato",
                                "role"        : "manager",
                                "salary"      : 175000.0d
                        ])
                ]
        ])
        def generatedCode = codeGenerator.generate(mapping, mockRecord, [:])
        assert generatedCode['existing'].size() == 0
        assert generatedCode['newVertices'].size() == 5
        //assert generatedCode['edges'].size() == 6 //This will be the correct number when provenance data is removed

        List<Map<String, String>> edges = generatedCode['edges']
        assert edges.findAll {
            it.to.startsWith("employees_") &&
                    it.from == "employer" &&
                    it.from == "employer" &&
                    it.label == "employs"
        }.size() == 3
        assert edges.findAll {
            it.from.startsWith("employees_") &&
                    it.to == "employer" &&
                    it.label == "employed_by"
        }.size() == 3
        assert edges.findAll {
            it.to.startsWith("employees_") &&
                    it.from == "ingest_entity" &&
                    it.label == "added"
        }.size() == 3
//        assert edges.findAll {
//            it.to == "employer" &&
//                    it.from == "ingest_entity" &&
//                    it.label == "added"
//        }.size() == 1
    }

    void testBrokenOptional(String mappingPath, Closure update) {
        def slurper = new JsonSlurper()
        def text = this.getClass().getResourceAsStream(mappingPath).text
        def parsed = slurper.parseText(text)
        update.call(parsed)
        def mapping = parse(JsonOutput.toJson(parsed))
        assert mapping
        def record = new MapRecord(registry.retrieveSchema("Transaction"), [
                sender_firstName  : "John",
                sender_lastName   : "Smith",
                receiver_firstName: "Jane",
                receiver_lastName : "Doe",
                amount            : 3000.00d,
                when              : new Timestamp(Calendar.instance.timeInMillis - (24 * 60 * 60 * 1000))
        ])
        def errorHappened = false
        try {
            codeGenerator.generate(mapping, record, [:])
        } catch (Exception ex) {
            ex.printStackTrace() //Just to help validate this
            errorHappened = true
        } finally {
            assert errorHappened
        }
    }

    @Test
    void testEdgeIsMissingProperty_ThrowsNoExceptionWhenOptionalEdge() {
        simpleTest("/mapping_test/missing_elements/edge_is_missing_property.json") { generatedCode ->
            assert generatedCode['existing'].size() == 0
            assert generatedCode['newVertices'].size() == 2
            assert generatedCode['edges'].size() == 1
        }
    }

    @Test
    void testEdgeIsMissingProperty_ThrowsExceptionWhenRequiredEdge() {
        testBrokenOptional("/mapping_test/missing_elements/edge_is_missing_property.json") {
            it['edges'][0]['required'] = true
        }
    }

    @Test
    void testEdgeMissingVertex_ThrowsNoExceptionWhenOptionalEdge() {
        simpleTest("/mapping_test/missing_elements/edge_is_missing_vertex.json") { generatedCode ->
            assert generatedCode['existing'].size() == 0
            assert generatedCode['newVertices'].size() == 1
            assert generatedCode['edges'].size() == 0
        }
    }

    @Test
    void testEdgeMissingVertex_ThrowsExceptionWhenRequiredEdge() {
        testBrokenOptional("/mapping_test/missing_elements/edge_is_missing_vertex.json") {
            it['edges'][0]['required'] = true
        }
    }

    @Test
    void testVertexMissing_ThrowsNoExceptionWhenOptionalVertex() {
        simpleTest("/mapping_test/missing_elements/missing_vertex_mapping.json") { generatedCode ->
            assert generatedCode['existing'].size() == 0
            assert generatedCode['newVertices'].size() == 1
            assert generatedCode['edges'].size() == 0
        }
    }

    @Test
    void testVertexMissing_ThrowsExceptionWhenRequiredVertex() {
        testBrokenOptional("/mapping_test/missing_elements/missing_vertex_mapping.json") {
            it['vertices'][1]['required'] = true
        }
    }

    /*
     * Array of primitives
     */

    @Test
    void testArrayOfPrimitives_SimpleScenario() {
        def mapping = parse(this.getClass().getResourceAsStream("/array_of_primitives/SimpleTransition.json").text)
        assert mapping

        def senderNicknames = ["JSmith", "Johnny S"]
        def receiverNicknames = ["JDoe", "Jane D."]
        def mockRecord = new MapRecord(registry.retrieveSchema("PrimitiveArrayInput"), [
                Sender_fullName   : "John Smith",
                Sender_nicknames  : senderNicknames,
                Receiver_fullName : "Jane Doe",
                Receiver_nicknames: receiverNicknames,
                message           : "Just a test message"
        ])
        def generatedCode = codeGenerator.generate(mapping, mockRecord, [:])

        assert generatedCode
        assert generatedCode["edges"]?.size() == 2
        assert generatedCode["newVertices"]?.size() == 3

        def sender = generatedCode["newVertices"]["sender"]
        assert sender
        assert sender["label"] == "user"
        assert sender["properties"]?.size() == 3
        assert sender["properties"]["fullName"] == "John Smith"
        assert sender["properties"]["nicknames"]?.size() == 2
        senderNicknames.each { name -> assert name in sender["properties"]["nicknames"] }
        def receiver = generatedCode["newVertices"]["receiver"]
        assert receiver
        assert receiver["label"] == "user"
        assert receiver["properties"]?.size() == 3
        assert receiver["properties"]["fullName"] == "Jane Doe"
        assert receiver["properties"]["nicknames"]?.size() == 2
        receiverNicknames.each { name -> assert name in receiver["properties"]["nicknames"] }
        def message = generatedCode["newVertices"]["message"]
        assert message
        assert message["label"] == "message"
        assert message["properties"]?.size() == 2
        assert message["properties"]["message"] == "Just a test message"
    }

    @Test
    void testArrayOfPrimitives_PhoneScenario() {
        def mapping = parse(this.getClass().getResourceAsStream("/array_of_primitives/PhoneTransition.json").text)
        assert mapping

        def nicknames = ["JSmith", "Johnny S", "JS"]
        def phoneNumbers = ["7036758901", "7031234567", "3018951234", "3015431278"]
        def mockRecord = new MapRecord(registry.retrieveSchema("PersonPhoneInput"), [
                fullName    : "John Smith",
                nicknames   : nicknames,
                phoneNumbers: phoneNumbers
        ])

        def validateClosure = { edge ->
            assert edge["label"] == "identifies"
            assert edge["to"] == "user"
        }

        def generatedCode = codeGenerator.generate(mapping, mockRecord, [:])
        assert generatedCode
        assert generatedCode["edges"]?.size() == 7
        def nicknameEdges = generatedCode["edges"].findAll { it["from"].startsWith("nickname_") }
        def phoneNumberEdges = generatedCode["edges"].findAll { it["from"].startsWith("phoneNumber_") }
        assert nicknameEdges?.size() == 3
        nicknameEdges.each(validateClosure)
        assert phoneNumberEdges?.size() == 4
        phoneNumberEdges.each(validateClosure)
        assert generatedCode["newVertices"]?.size() == 8
        def nicknameVertices = generatedCode["newVertices"].findAll { it.key.startsWith("nickname_") }
        assert nicknameVertices?.size() == nicknames.size()
        nicknameVertices.values().each { vertex ->
            assert vertex.label == "nickname"
            assert vertex["properties"]?.size() == 2
            assert vertex["properties"]["nickname"] instanceof String
        }
        def phoneNumberVertices = generatedCode["newVertices"].findAll { it.key.startsWith("phoneNumber_") }
        assert phoneNumberVertices?.size() == phoneNumbers.size()
        phoneNumberVertices.values().each { vertex ->
            assert vertex.label == "phoneNumber"
            assert vertex["properties"]?.size() == 2
            assert vertex["properties"]["phoneNumber"] instanceof String
        }
    }

    @Test
    void testArrayOfPrimitivesWithComplexMapping() {
        def mapping = parse(this.getClass().getResourceAsStream("/array_of_primitives/ComplexPhoneTransition.json").text)
        assert mapping

        def nicknames = ["JSmith", "Johnny S", "JS"]
        def phoneNumbers = ["7036758901", "7031234567", "3018951234", "3015431278"]
        def mockRecord = new MapRecord(registry.retrieveSchema("PersonPhoneInput"), [
                fullName    : "John Smith",
                nicknames   : nicknames,
                phoneNumbers: phoneNumbers
        ])

        def validateClosure = { edge ->
            assert edge["label"] == "identifies"
            assert edge["to"] == "user"
        }

        def generatedCode = codeGenerator.generate(mapping, mockRecord, [:])
        assert generatedCode
        assert generatedCode["edges"]?.size() == 7
        def nicknameEdges = generatedCode["edges"].findAll { it["from"].startsWith("nickname_") }
        def phoneNumberEdges = generatedCode["edges"].findAll { it["from"].startsWith("phoneNumber_") }
        assert nicknameEdges?.size() == 3
        nicknameEdges.each(validateClosure)
        assert phoneNumberEdges?.size() == 4
        phoneNumberEdges.each(validateClosure)
        assert generatedCode["newVertices"]?.size() == 8
        def nicknameVertices = generatedCode["newVertices"].findAll { it.key.startsWith("nickname_") }
        assert nicknameVertices?.size() == nicknames.size()
        nicknameVertices.values().each { vertex ->
            assert vertex.label == "nickname"
            assert vertex["properties"]?.size() == 2
            assert vertex["properties"]["nickname"] instanceof String
        }
        def phoneNumberVertices = generatedCode["newVertices"].findAll { it.key.startsWith("phoneNumber_") }
        assert phoneNumberVertices?.size() == phoneNumbers.size()
        phoneNumberVertices.values().each { vertex ->
            assert vertex.label == "phoneNumber"
            assert vertex["properties"]?.size() == 3
            assert vertex["properties"]["phoneNumber"] instanceof String
        }
    }

    /*
     * Purely record path tests
     */

    @Test
    void testPersonNameScenario() {
        def mapping = parse(this.getClass().getResourceAsStream("/record_path_test/PersonTransition.json").text)
        assert mapping

        def mockRecord = new MapRecord(registry.retrieveSchema("PersonFullName"), [
                PersonFirstName : "John",
                PersonMiddleName: "Brown",
                PersonLastName  : "Smith"
        ])

        def generatedCode = codeGenerator.generate(mapping, mockRecord, [:])
        assert generatedCode
        assert generatedCode["newVertices"]?.size() == 1
        assert generatedCode["newVertices"]["sender"]["properties"]?.size() == 2
        assert generatedCode["newVertices"]["sender"]["properties"]["PersonFullName"] == "John Brown Smith"
    }

    @Test
    void testHashGeneration() {
        def mapping = parse(this.getClass().getResourceAsStream("/record_path_test/PersonTransitionExcludeFromCache.json").text)
        assert mapping

        def mockRecord = new MapRecord(registry.retrieveSchema("PersonFullName"), [
                PersonFirstName : "John",
                PersonMiddleName: "Brown",
                PersonLastName  : "Smith"
        ])

        def generatedCode = codeGenerator.generate(mapping, mockRecord, [:])
        TimeUnit.SECONDS.sleep(2) // this is a delay to make sure CreationDateTime is different and excluded from cache
        def generatedCode2 = codeGenerator.generate(mapping, mockRecord, [:])
        assert generatedCode
        assert generatedCode["newVertices"]?.size() == 1
        assert generatedCode["newVertices"]["sender"]["properties"]?.size() == 4
        assert generatedCode["newVertices"]["sender"]["properties"]["PersonFullName"] == "John Brown Smith"
        assert generatedCode["newVertices"]["sender"]["properties"]["_sys_id"] == generatedCode2["newVertices"]["sender"]["properties"]["_sys_id"]
        assert generatedCode["newVertices"]["sender"]["properties"]["Uuid"] != generatedCode2["newVertices"]["sender"]["properties"]["Uuid"]
        assert generatedCode["newVertices"]["sender"]["properties"]["CreationDateTime"] != generatedCode2["newVertices"]["sender"]["properties"]["CreationDateTime"]
    }

    @Test
    void testPersonNameExpressionLanguageScenario() {
        def mapping = parse(this.getClass().getResourceAsStream("/record_path_test/PersonELTransition.json").text)
        assert mapping

        def mockRecord = new MapRecord(registry.retrieveSchema("PersonFullName"), [
                PersonFirstName : "",
                PersonMiddleName: "",
                PersonLastName  : ""
        ])

        def generatedCode = codeGenerator.generate(mapping, mockRecord, [:])
        assert generatedCode
        assert generatedCode["newVertices"]?.size() == 1
        assert generatedCode["newVertices"]["sender"]["properties"]?.size() == 2
        assert generatedCode["newVertices"]["sender"]["properties"]["PersonFullName"] == "UNKNOWN"
    }

    @Test
    void testDefaultValueWillCastCorrectlyFromStringToLong() {
        def mapping = parse(this.getClass().getResourceAsStream("/record_path_test/TypeCastUsingDefaultValueTransition.json").text)
        assert mapping

        def mockRecord = new MapRecord(registry.retrieveSchema("SimpleLongSourceRecord"), [
                Count: null
        ])

        def generatedCode = codeGenerator.generate(mapping, mockRecord, [:])
        assert generatedCode
        assert generatedCode["newVertices"]?.size() == 1
        assert generatedCode["newVertices"]["test_node"]["properties"]?.size() == 2
        assert generatedCode["newVertices"]["test_node"]["properties"]["Count"] == -10000l
    }

    /*
     * Error handler checks
     */

    @Test(expected = InvalidRecordPathException.class)
    void testWrapsInvalidSourceRecordPath() {
        def mapping = parse(this.getClass().getResourceAsStream("/error_checking/InvalidSourceRecordPathTest.json").text)
        assert mapping
        invalidRecordPathTest(mapping)
    }

    @Test(expected = InvalidRecordPathException.class)
    void testWrapsInvalidSourcePropertyRecordPath() {
        def mapping = parse(this.getClass().getResourceAsStream("/error_checking/InvalidSourcePropertyRecordPathTest.json").text)
        assert mapping
        invalidRecordPathTest(mapping)
    }

    @Test(expected = InvalidRecordPathException.class)
    void testWrapsInvalidGraphPropertyRecordPath() {
        def mapping = parse(this.getClass().getResourceAsStream("/error_checking/InvalidGraphPropertyRecordPathTest.json").text)
        assert mapping
        invalidRecordPathTest(mapping)
    }

    void invalidRecordPathTest(Transition mapping) {
        def nicknames = ["JSmith", "Johnny S", "JS"]
        def phoneNumbers = ["7036758901", "7031234567", "3018951234", "3015431278"]
        def mockRecord = new MapRecord(registry.retrieveSchema("PersonPhoneInput"), [
                fullName    : "John Smith",
                nicknames   : nicknames,
                phoneNumbers: phoneNumbers
        ])
        codeGenerator.generate(mapping, mockRecord, [:])
    }
}
