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
import com.boozallen.graph.transition.api.GraphTransitionSchemaRegistry;
import com.boozallen.graph.transition.exception.TransitionNotFoundException;
import org.apache.avro.Schema;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnEnabled;
import org.apache.nifi.avro.AvroTypeUtil;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.ValidationContext;
import org.apache.nifi.components.ValidationResult;
import org.apache.nifi.components.Validator;
import org.apache.nifi.controller.AbstractControllerService;
import org.apache.nifi.controller.ConfigurationContext;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.schema.access.SchemaField;
import org.apache.nifi.schema.access.SchemaNotFoundException;
import org.apache.nifi.schemaregistry.services.SchemaRegistry;
import org.apache.nifi.serialization.record.RecordSchema;
import org.apache.nifi.serialization.record.SchemaIdentifier;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Tags({"schema", "registry", "graph"})
@CapabilityDescription("Provides a simple implementation of the Graph Transition Schema Registry component interface. " +
	"Under no circumstances should it be considered for use in production.")
public class SimpleGraphTransitionSchemaRegistryImpl extends AbstractControllerService implements GraphTransitionSchemaRegistry {
	public static final PropertyDescriptor DELEGATE_REGISTRY = new PropertyDescriptor.Builder()
		.displayName("Delegate Registry")
		.name("delegate-registry")
		.description("Delegate schema retrieval to another registry. This enables schema registry behavior " +
			"to be integrated with other solutions such as the HortonWorks registry. If set, this registry will enforce " +
			"validation on all dynamic properties requiring them to be Transition objects.")
		.identifiesControllerService(SchemaRegistry.class)
		.addValidator(Validator.VALID)
		.build();

	public static final PropertyDescriptor SEARCH_DIRECTORY = new PropertyDescriptor.Builder()
		.displayName("Search Directory")
		.name("search-directory")
		.description("Specifies a directory to search and load into the schema registry. Will search for files" +
			" that match either *.avsc or *.json recursively in that directory")
		.required(false)
		.addValidator(StandardValidators.createDirectoryExistsValidator(true, false))
		.build();

	public static final List<PropertyDescriptor> DESCRIPTORS = Collections.unmodifiableList(
		Arrays.asList(
			DELEGATE_REGISTRY, SEARCH_DIRECTORY
		)
	);

	private Map<String, Object> recordSchemaMap = new ConcurrentHashMap<>();

	private ConfigurationContext context;
	private SchemaRegistry delegatedRegistry;

	private static ObjectMapper objectMapper = new ObjectMapper();

	@Override
	protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
		return DESCRIPTORS;
	}

	@Override
	protected PropertyDescriptor getSupportedDynamicPropertyDescriptor(String propertyDescriptorName) {
		return new PropertyDescriptor.Builder()
			.name(propertyDescriptorName)
			.displayName(propertyDescriptorName)
			.addValidator(StandardValidators.NON_EMPTY_EL_VALIDATOR)
			.dynamic(true)
			.build();
	}

	@Override
	protected Collection<ValidationResult> customValidate(ValidationContext context) {
		final List<ValidationResult> results = new ArrayList<>();
		boolean isDelegating = context.getProperty(DELEGATE_REGISTRY).isSet();

		if (isDelegating) {
			context
				.getProperties()
				.keySet()
				.stream()
				.filter(PropertyDescriptor::isDynamic)
				.forEach(propertyDescriptor -> {
					try {
						String raw = context.getProperty(propertyDescriptor).getValue();
						objectMapper.readValue(raw, Transition.class);
					} catch (IOException ex) {
						String message = String.format("Property %s did not have a valid transition JSON definition.", propertyDescriptor.getName());
						results.add(new ValidationResult.Builder().valid(false).subject(propertyDescriptor.getName()).explanation(message).build());
					}
				});
		}

		if (context.getProperty(SEARCH_DIRECTORY).isSet()) {
			URI path = new File(context.getProperty(SEARCH_DIRECTORY).getValue()).toURI();
			try {
				loadSchemas(path);
			} catch (Exception ex) {
				results.add(new ValidationResult.Builder().valid(false).subject(SEARCH_DIRECTORY.getDisplayName()).explanation(ex.getMessage()).build());
			}
		}

		return results;
	}

	@OnEnabled
	public void onEnabled(ConfigurationContext context) {
		this.context = context;
		this.delegatedRegistry = context.getProperty(DELEGATE_REGISTRY).isSet()
			? context.getProperty(DELEGATE_REGISTRY).asControllerService(SchemaRegistry.class)
			: null;

		recordSchemaMap = new ConcurrentHashMap<>();
		if (context.getProperty(SEARCH_DIRECTORY).isSet()) {
			URI path = new File(context.getProperty(SEARCH_DIRECTORY).getValue()).toURI();
			loadSchemas(path);
		}
	}


	@Override
	public Transition getTransitionMapping(String name) {
		try {
			if (recordSchemaMap.containsKey(name)) {
				return (Transition) recordSchemaMap.get(name);
			} else {
				Optional<PropertyDescriptor> result = findProperty(name);
				if (!result.isPresent()) {
					throw new TransitionNotFoundException(name);
				}
				String raw = context.getProperty(result.get()).getValue();
				return objectMapper.readValue(raw, Transition.class);
			}
		} catch (IOException ex) {
			throw new ProcessException(ex);
		}
	}

	@Override
	public RecordSchema retrieveSchema(SchemaIdentifier schemaIdentifier) throws SchemaNotFoundException, IOException {
		if (delegatedRegistry != null) {
			return delegatedRegistry.retrieveSchema(schemaIdentifier);
		} else {
			Optional<String> nameOpt = schemaIdentifier.getName();
			String name;
			if (nameOpt.isPresent()) {
				name = nameOpt.get();
			} else {
				throw new ProcessException("No name identifier specified.");
			}

			if (recordSchemaMap.containsKey(name)) {
				return (RecordSchema) recordSchemaMap.get(name);
			} else {
				Optional<PropertyDescriptor> result = findProperty(name);
				if (!result.isPresent()) {
					throw new SchemaNotFoundException(name);
				}

				String raw = context.getProperty(result.get()).getValue();
				return AvroTypeUtil.createSchema(new Schema.Parser().parse(raw));
			}
		}
	}

	@Override
	public Set<SchemaField> getSuppliedSchemaFields() {
		return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
			SchemaField.SCHEMA_NAME, SchemaField.SCHEMA_TEXT, SchemaField.SCHEMA_VERSION
		)));
	}

	private Optional<PropertyDescriptor> findProperty(String name) {
		Map<PropertyDescriptor, String> properties = context.getProperties();
		return properties.keySet()
			.stream()
			.filter(entry -> entry.getName().equals(name))
			.findFirst();
	}

	private Transition readJson(String fileName) throws IOException {
		File file = new File(fileName);
		return objectMapper.readValue(file, Transition.class);
	}

	private RecordSchema readAvsc(String fileName) throws IOException {
		File file = new File(fileName);
		return AvroTypeUtil.createSchema(new Schema.Parser().parse(file));
	}

	private void loadSchemas(URI searchDirectory) {
		try {
			List<String> files = Files.walk(Paths.get(searchDirectory))
				.filter(Files::isRegularFile)
				.filter(it -> {
					String fname = it.toString();
					return fname.contains(".avsc") || fname.contains(".json");
				}).map(Path::toString).collect(Collectors.toList());

			for (String f : files) {
				try {
					if (f.contains(".avsc")) {
						RecordSchema t = readAvsc(f);
						if (t.getSchemaName().isPresent()) {
							recordSchemaMap.put(t.getSchemaName().get(), t);
						}
					} else if (f.contains(".json")) {
						Transition t = readJson(f);
						recordSchemaMap.put(t.getSubject(), t);
					}
				} catch (Exception ex) {
					throw new ProcessException(String.format("Problem with %s", f), ex);
				}
			}
		} catch (IOException ex) {
			throw new ProcessException("", ex);
		}
	}
}
