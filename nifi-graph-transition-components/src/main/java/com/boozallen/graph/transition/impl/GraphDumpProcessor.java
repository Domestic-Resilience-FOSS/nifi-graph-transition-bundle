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
package com.boozallen.graph.transition.impl;

import com.boozallen.graph.transition.api.model.Transition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.Validator;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.flowfile.attributes.CoreAttributes;
import com.boozallen.graph.transition.api.GraphTransitionSchemaRegistry;
import com.boozallen.graph.transition.api.SchemaDataSource;
import com.boozallen.graph.transition.generation.ParameterGenerator;
import com.boozallen.graph.transition.generation.NiFiSchemaRegistrySchemaDataSource;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.serialization.RecordReader;
import org.apache.nifi.serialization.RecordReaderFactory;
import org.apache.nifi.serialization.record.Record;
import org.apache.nifi.util.StringUtils;


import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class GraphDumpProcessor extends AbstractProcessor {
	public static final PropertyDescriptor READER_SERVICE = new PropertyDescriptor.Builder()
		.name("dump-reader-service")
		.displayName("Record Reader")
		.description("The record reader to use with this processor.")
		.identifiesControllerService(RecordReaderFactory.class)
		.required(true)
		.addValidator(Validator.VALID)
		.build();
	public static final PropertyDescriptor SCHEMA_REGISTRY = new PropertyDescriptor.Builder()
		.name("dump-schema-registry")
		.displayName("Schema Registry")
		.description("The schema registry that will supply all of the additional schemas used for mapping.")
		.identifiesControllerService(GraphTransitionSchemaRegistry.class)
		.required(true)
		.addValidator(Validator.VALID)
		.build();

	public static final List<PropertyDescriptor> DESCRIPTORS = Collections.unmodifiableList(Arrays.asList(
		READER_SERVICE, SCHEMA_REGISTRY
	));

	public static final Relationship REL_ORIGINAL = new Relationship.Builder().name("original").build();
	public static final Relationship REL_SUCCESS = new Relationship.Builder().name("success").build();
	public static final Relationship REL_FAILURE = new Relationship.Builder().name("failure").build();
	public static final Relationship REL_ERRORS = new Relationship.Builder().name("errors").build();

	public static final Set<Relationship> RELATIONSHIPS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
		REL_ORIGINAL, REL_SUCCESS, REL_FAILURE, REL_ERRORS
	)));

	@Override
	public Set<Relationship> getRelationships() {
		return RELATIONSHIPS;
	}

	@Override
	public List<PropertyDescriptor> getSupportedPropertyDescriptors() {
		return DESCRIPTORS;
	}

	private RecordReaderFactory recordReaderFactory;
	private GraphTransitionSchemaRegistry schemaRegistry;
	private SchemaDataSource dataSource;
	private ObjectMapper mapper = new ObjectMapper();

	@OnScheduled
	public void onScheduled(ProcessContext context) {
		recordReaderFactory = context.getProperty(READER_SERVICE).asControllerService(RecordReaderFactory.class);

		schemaRegistry = context.getProperty(SCHEMA_REGISTRY).asControllerService(GraphTransitionSchemaRegistry.class);
		dataSource = new NiFiSchemaRegistrySchemaDataSource(schemaRegistry);
	}

	public static final String TRANSITION_SCHEMA_NAME = "transition.name";
	public static final String GRAPH_OPERATION_TIME = "graph.operations.took";

	@Override
	public void onTrigger(ProcessContext context, ProcessSession session) {
		FlowFile input = session.get();
		if (input == null) {
			return;
		}

		String transitionName = input.getAttribute(TRANSITION_SCHEMA_NAME);

		if (StringUtils.isEmpty(transitionName)) {
			getLogger().error("Missing transition name attribute.");
			session.transfer(input, REL_FAILURE);
			return;
		}

		Transition graphMapping = schemaRegistry.getTransitionMapping(transitionName);

		ParameterGenerator codeGenerator = new ParameterGenerator();
		codeGenerator.setComponentLog(getLogger());
		codeGenerator.setSchemaDatasource(dataSource);

		List<FlowFile> flowFiles = new ArrayList<>();
		boolean failed = false;
		long delta = 0;
		Map<String, String> attributes = new HashMap<>();
		attributes.put("schema.name", graphMapping.getSourceSubject());
		try (InputStream is = session.read(input);
			 RecordReader reader = recordReaderFactory.createRecordReader(attributes, is, -1l, getLogger());) {

			Record record;

			long start = System.currentTimeMillis();
			long recordIndex = 0;
			while ((record = reader.nextRecord()) != null) {
				processRecord(session, input, codeGenerator, graphMapping, record, flowFiles, recordIndex);
				recordIndex++;
			}
			long end = System.currentTimeMillis();
			delta = (end - start) / 1000;

			getLogger().info(String.format("Took %s seconds.", delta));
		} catch (Exception ex) {
			getLogger().error("", ex);
			failed = true;
		} finally {
			if (failed) {
				flowFiles.forEach(session::remove);
				session.transfer(input, REL_FAILURE);
			} else {
				input = session.putAttribute(input, GRAPH_OPERATION_TIME, String.valueOf(delta));
				session.transfer(input, REL_ORIGINAL);
				session.transfer(flowFiles, REL_SUCCESS);
			}
		}
	}

	protected void processRecord(ProcessSession session, FlowFile input, ParameterGenerator codeGenerator, Transition graphMapping,
								 Record record, List<FlowFile> flowFiles, long recordIndex) {
		FlowFile current = session.create(input);
		boolean error = false;
		try (OutputStream os = session.write(current)) {
			Map<String, Object> parameters = codeGenerator.generate(graphMapping, record, input.getAttributes());
			String output = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(parameters);
			os.write(output.getBytes(StandardCharsets.UTF_8));
		} catch (Exception ex) {
			getLogger().error("", ex);
			error = true;
		} finally {
			if (!error) {
				current = session.putAttribute(current, CoreAttributes.FILENAME.key(), String.format("record-%d.json", recordIndex));
				flowFiles.add(current);
			} else {
				session.remove(current);
			}
		}
	}
}
