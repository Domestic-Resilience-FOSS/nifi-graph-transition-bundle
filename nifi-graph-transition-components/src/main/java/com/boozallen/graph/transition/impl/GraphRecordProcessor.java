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
import com.boozallen.graph.transition.impl.execution.GraphExecutor;
import com.boozallen.graph.transition.impl.execution.GremlinExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.boozallen.graph.transition.api.GraphTransitionSchemaRegistry;
import com.boozallen.graph.transition.api.SchemaDataSource;
import com.boozallen.graph.transition.generation.NiFiSchemaRegistrySchemaDataSource;
import com.boozallen.graph.transition.generation.ParameterGenerator;
import org.apache.nifi.annotation.behavior.InputRequirement;
import org.apache.nifi.annotation.behavior.ReadsAttribute;
import org.apache.nifi.annotation.behavior.ReadsAttributes;
import org.apache.nifi.annotation.behavior.WritesAttribute;
import org.apache.nifi.annotation.behavior.WritesAttributes;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.components.AllowableValue;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.Validator;
import org.apache.nifi.distributed.cache.client.DistributedMapCacheClient;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.graph.GraphClientService;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.serialization.RecordReader;
import org.apache.nifi.serialization.RecordReaderFactory;
import org.apache.nifi.serialization.RecordSetWriter;
import org.apache.nifi.serialization.RecordSetWriterFactory;
import org.apache.nifi.serialization.record.Record;
import org.apache.nifi.util.StringUtils;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@InputRequirement(InputRequirement.Requirement.INPUT_REQUIRED)
@ReadsAttributes({
	@ReadsAttribute(attribute = GraphRecordProcessor.TRANSITION_SCHEMA_NAME, description = "The name of the transition schema to fetch."),
	@ReadsAttribute(attribute = GraphRecordProcessor.TRANSITION_SCHEMA_VERSION, description = "The version of the transition schema to fetch."),
	@ReadsAttribute(attribute = GraphRecordProcessor.RECORD_COUNT, description = "The number of records estimated in the record set. Used to help " +
		"show progress when logging is turned up.")
})
@WritesAttributes({
	@WritesAttribute(attribute = GraphRecordProcessor.GRAPH_OPERATION_TIME, description = "The amount of time it took to execute all of the graph operations.")
})
@CapabilityDescription("Provides a record-oriented processor for graph data ingestion.")
@Tags({"graph", "record"})
public class GraphRecordProcessor extends AbstractProcessor {
	public static final PropertyDescriptor CLIENT_SERVICE = new PropertyDescriptor.Builder()
		.name("client-service")
		.displayName("Client Service")
		.identifiesControllerService(GraphClientService.class)
		.addValidator(Validator.VALID)
		.required(true)
		.build();

	public static final AllowableValue TARGET_GREMLIN = new AllowableValue("gremlin", "Gremlin");
	public static final PropertyDescriptor TARGET_DATABASE = new PropertyDescriptor.Builder()
		.name("target-database")
		.displayName("Target Database")
		.description("This configuration option controls which supported execution engine will " +
			"be used to convert the abstract representation of the graph data into database operations.")
		.allowableValues(TARGET_GREMLIN)
		.defaultValue(TARGET_GREMLIN.getValue())
		.required(true)
		.addValidator(Validator.VALID)
		.build();
	public static final PropertyDescriptor READER_SERVICE = new PropertyDescriptor.Builder()
		.name("reader-service")
		.displayName("Record Reader")
		.description("The record reader to use with this processor.")
		.identifiesControllerService(RecordReaderFactory.class)
		.required(true)
		.addValidator(Validator.VALID)
		.build();
	public static final PropertyDescriptor WRITER_SERVICE = new PropertyDescriptor.Builder()
		.name("writer-service")
		.displayName("Failed Record Writer")
		.description("The record writer to use for writing failed records.")
		.identifiesControllerService(RecordSetWriterFactory.class)
		.required(true)
		.addValidator(Validator.VALID)
		.build();
	public static final PropertyDescriptor SCHEMA_REGISTRY = new PropertyDescriptor.Builder()
		.name("schema-registry")
		.displayName("Schema Registry")
		.description("The schema registry that will supply all of the additional schemas used for mapping.")
		.identifiesControllerService(GraphTransitionSchemaRegistry.class)
		.required(true)
		.addValidator(Validator.VALID)
		.build();
	public static final PropertyDescriptor PRIMARY_CACHE = new PropertyDescriptor.Builder()
		.name("primary-cache")
		.displayName("Graph Element Cache")
		.description("The primary distributed map cache to use for looking up existing IDs.")
		.required(false)
		.identifiesControllerService(DistributedMapCacheClient.class)
		.addValidator(Validator.VALID)
		.build();

	public static final List<PropertyDescriptor> DESCRIPTORS = Collections.unmodifiableList(Arrays.asList(
		CLIENT_SERVICE, TARGET_DATABASE, READER_SERVICE, WRITER_SERVICE,
		SCHEMA_REGISTRY, PRIMARY_CACHE, GremlinExecutor.HEADER_SCRIPT, GremlinExecutor.RECORD_SCRIPT
	));

	public static final Relationship REL_SUCCESS = new Relationship.Builder().name("success").build();
	public static final Relationship REL_FAILURE = new Relationship.Builder().name("failure").build();
	public static final Relationship REL_ERRORS = new Relationship.Builder().name("errors").build();
	public static final Relationship REL_GRAPH = new Relationship.Builder().name("graph response").autoTerminateDefault(true).build();

	public static final Set<Relationship> RELATIONSHIPS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
		REL_SUCCESS, REL_FAILURE, REL_ERRORS, REL_GRAPH
	)));

	public static final String TRANSITION_SCHEMA_NAME = "transition.name";
	public static final String TRANSITION_SCHEMA_VERSION = "transition.name.version";
	public static final String RECORD_COUNT = "record.count";
	public static final String GRAPH_OPERATION_TIME = "graph.operations.took";

	@Override
	public Set<Relationship> getRelationships() {
		return RELATIONSHIPS;
	}

	@Override
	public List<PropertyDescriptor> getSupportedPropertyDescriptors() {
		return DESCRIPTORS;
	}

	private GraphClientService clientService;
	private RecordReaderFactory recordReaderFactory;
	private RecordSetWriterFactory recordSetWriterFactory;
	private DistributedMapCacheClient cacheClient;
	private GraphTransitionSchemaRegistry schemaRegistry;
	private SchemaDataSource dataSource;
	private ObjectMapper mapper = new ObjectMapper();

	private String selectedEngine;

	@OnScheduled
	public void onScheduled(ProcessContext context) {
		clientService = context.getProperty(CLIENT_SERVICE).asControllerService(GraphClientService.class);
		recordReaderFactory = context.getProperty(READER_SERVICE).asControllerService(RecordReaderFactory.class);
		recordSetWriterFactory = context.getProperty(WRITER_SERVICE).asControllerService(RecordSetWriterFactory.class);

		schemaRegistry = context.getProperty(SCHEMA_REGISTRY).asControllerService(GraphTransitionSchemaRegistry.class);
		dataSource = new NiFiSchemaRegistrySchemaDataSource(schemaRegistry);


		if (context.getProperty(PRIMARY_CACHE).isSet()) {
			cacheClient = context.getProperty(PRIMARY_CACHE).asControllerService(DistributedMapCacheClient.class);
		} else {
			cacheClient = null;
		}

		this.selectedEngine = context.getProperty(TARGET_DATABASE).getValue();
	}

	private GraphExecutor getExecutor(final RecordSetWriter writer) {
		if (selectedEngine.equals(TARGET_GREMLIN.getValue())) {
			return new GremlinExecutor(getLogger(), clientService, cacheClient, writer);
		}

		throw new ProcessException("Could not create executor.");
	}

	@Override
	public void onTrigger(ProcessContext context, ProcessSession session) {
		FlowFile input = session.get();
		if (input == null) {
			return;
		}

		if (!input.getAttributes().containsKey("graph.name")) {
			session.transfer(input, REL_FAILURE);
			getLogger().error("No graph.name attribute on flowfile.");
			return;
		}

		String transitionName = input.getAttribute(TRANSITION_SCHEMA_NAME);

		Transition graphMapping = schemaRegistry.getTransitionMapping(transitionName);

		ParameterGenerator codeGenerator = new ParameterGenerator();
		codeGenerator.setComponentLog(getLogger());
		codeGenerator.setSchemaDatasource(dataSource);
		codeGenerator.setCache(cacheClient);

		FlowFile output = session.create(input);
		boolean failed = false;
		long delta = 0;
		GraphExecutor executor = null;

		List<Map<String,Object>> graphResponses = new ArrayList<>();
		Map<String, String> attributes = new HashMap<>();
		attributes.put("schema.name", graphMapping.getSourceSubject());
		try (InputStream is = session.read(input);
			 OutputStream os = session.write(output);
			 RecordReader reader = recordReaderFactory.createRecordReader(attributes, is, -1l, getLogger());
			 RecordSetWriter writer = recordSetWriterFactory.createWriter(getLogger(), reader.getSchema(), os, input.getAttributes());
		) {
			Record record;
			executor = getExecutor(writer);
			executor.configure(context, session, input);

			long start = System.currentTimeMillis();
			long recordIndex = 0;
			long batchStart = start;
			String outOf = input.getAttribute(RECORD_COUNT);
			writer.beginRecordSet();
			while ((record = reader.nextRecord()) != null) {
				Map<String, Object> parameters = codeGenerator.generate(graphMapping, record, input.getAttributes());
				graphResponses.addAll(executor.execute(parameters, record));

				recordIndex++;
				if (recordIndex % 100 == 0) {
					long now = System.currentTimeMillis();
					long tempDelta = (now - batchStart) / 1000;
					getLogger().info(String.format("Completed %s records %s in %s seconds",
						recordIndex, !StringUtils.isEmpty(outOf) ? String.format("out of %s", outOf) : "", tempDelta));
					batchStart = System.currentTimeMillis();
				}
			}
			writer.finishRecordSet();
			long end = System.currentTimeMillis();
			delta = (end - start) / 1000;

			getLogger().info(String.format("Took %s seconds.", delta));
		} catch (Exception ex) {
			getLogger().error("", ex);
			failed = true;
		} finally {
			if (failed) {
				session.remove(output);
				session.transfer(input, REL_FAILURE);
			} else {
				input = session.putAttribute(input, GRAPH_OPERATION_TIME, String.valueOf(delta));

				session.getProvenanceReporter().send(input, clientService.getTransitUrl());
				session.transfer(input, REL_SUCCESS);
                FlowFile graph = session.create(input);
                boolean error = false;
                try (OutputStream os = session.write(graph)) {
                    String graphOutput = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(graphResponses);
                    os.write(graphOutput.getBytes(StandardCharsets.UTF_8));
                } catch (Exception ex) {
                    error = true;
                    getLogger().error("", ex);
                } finally {
                    if (!error) {
                        session.transfer(graph, REL_GRAPH);
                    } else {
                        session.remove(graph);
                    }
                }
				if (executor != null && executor.getErrorCount().get() > 0) {
					session.transfer(output, REL_ERRORS);
				} else {
					session.remove(output);
				}
			}
		}
	}
}
