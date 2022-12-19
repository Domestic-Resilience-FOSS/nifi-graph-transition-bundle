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
package com.boozallen.graph.transition.generation;

import org.apache.avro.Schema;
import org.apache.nifi.avro.AvroTypeUtil;
import com.boozallen.graph.transition.api.SchemaDataSource;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.schemaregistry.services.SchemaRegistry;
import org.apache.nifi.serialization.record.RecordSchema;
import org.apache.nifi.serialization.record.SchemaIdentifier;

import java.util.Map;

import static com.boozallen.graph.transition.generation.IdentifierConstants.*;

/**
 * This implementation is meant to wrap a NiFi schema registry and provide simplified access to it to other
 * components.
 */
public class NiFiSchemaRegistrySchemaDataSource implements SchemaDataSource {
    private SchemaRegistry schemaRegistry;

    public NiFiSchemaRegistrySchemaDataSource(SchemaRegistry schemaRegistry) {
        this.schemaRegistry = schemaRegistry;
    }

    @Override
    public Schema getSchema(Map<String, Object> identifiers) {
        SchemaIdentifier.Builder identifier = SchemaIdentifier.builder();
        if (identifiers.containsKey(IDENTIFIER_NAME)) {
            identifier = identifier.name((String) identifiers.get(IDENTIFIER_NAME));
        }
        if (identifiers.containsKey(IDENTIFIER_VERSION)) {
            identifier = identifier.version((Integer) identifiers.get(IDENTIFIER_VERSION));
        }
        if (identifiers.containsKey(IDENTIFIER_ID)) {
            identifier = identifier.id(Long.valueOf((String) identifiers.get(IDENTIFIER_ID)));
        }
        if (identifiers.containsKey(IDENTIFIER_BRANCH)) {
            identifier = identifier.branch((String) identifiers.get(IDENTIFIER_BRANCH));
        }

        SchemaIdentifier si = identifier.build();

        try {
            RecordSchema schema = schemaRegistry.retrieveSchema(si);

            return AvroTypeUtil.extractAvroSchema(schema);
        } catch (Exception e) {
            throw new ProcessException(String.format("Error with this identifier: %s", identifiers.toString()), e);
        }
    }
}
