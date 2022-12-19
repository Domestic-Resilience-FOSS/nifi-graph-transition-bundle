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

import com.boozallen.graph.transition.api.model.Edge;
import com.boozallen.graph.transition.api.model.PropertyMapping;
import com.boozallen.graph.transition.api.model.Transition;
import com.boozallen.graph.transition.api.model.Vertex;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.primitives.Longs;
import com.boozallen.graph.transition.api.SchemaDataSource;
import com.boozallen.graph.transition.exception.InvalidRecordPathException;
import org.apache.avro.Schema;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.nifi.attribute.expression.language.EvaluationContext;
import org.apache.nifi.attribute.expression.language.PreparedQuery;
import org.apache.nifi.attribute.expression.language.Query;
import org.apache.nifi.attribute.expression.language.StandardEvaluationContext;
import org.apache.nifi.avro.AvroTypeUtil;
import org.apache.nifi.distributed.cache.client.Deserializer;
import org.apache.nifi.distributed.cache.client.DistributedMapCacheClient;
import org.apache.nifi.distributed.cache.client.Serializer;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.record.path.FieldValue;
import org.apache.nifi.record.path.RecordPath;
import org.apache.nifi.record.path.RecordPathResult;
import org.apache.nifi.record.path.exception.RecordPathException;
import org.apache.nifi.record.path.util.RecordPathCache;
import org.apache.nifi.schema.validation.SchemaValidationContext;
import org.apache.nifi.schema.validation.StandardSchemaValidator;
import org.apache.nifi.serialization.record.MapRecord;
import org.apache.nifi.serialization.record.Record;
import org.apache.nifi.serialization.record.RecordField;
import org.apache.nifi.serialization.record.RecordSchema;
import org.apache.nifi.serialization.record.type.ArrayDataType;
import org.apache.nifi.serialization.record.util.DataTypeUtils;
import org.apache.nifi.serialization.record.validation.RecordSchemaValidator;
import org.apache.nifi.serialization.record.validation.SchemaValidationResult;
import org.apache.nifi.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class ParameterGenerator {
	private DistributedMapCacheClient cache;
	private RecordPathCache recordPathCache = new RecordPathCache(100);
	private SchemaDataSource schemaDataSource;
	private ComponentLog log;

	private static final ObjectMapper MAPPER = new ObjectMapper();

	static {
		MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
	}

	public static final String LABEL_INGEST_EVENT = "ingest_event";

	public ParameterGenerator() {
		this.cache = null;
	}

	public Map<String, Object> generate(Transition mapping, Record record, Map<String, String> attributes) throws IOException {
		if (log.isDebugEnabled()) {
			try {
				StringBuilder builder = new StringBuilder()
					.append(String.format("Generating for %s%n", mapping.getSubject()))
					.append(String.format("For this record: %s", MAPPER.writeValueAsString(record.toMap())));
				log.debug(builder.toString());
			} catch (Exception ex) {
				log.debug("Processing record with an ObjectMapper error ", ex);
			}
		}
		Map<String, Record> nodes = new HashMap<>();
		Map<String, Vertex> nodeMap = new HashMap<>();
		Map<String, String> nodeLabels = new HashMap<>();

		Map<String, Object> retVal = new HashMap<>();
		List<Map<String, Object>> edges = new ArrayList<>();
		Map<String, Object> existing = new HashMap<>();
		Map<String, Object> newVertices = new HashMap<>();
		retVal.put("existing", existing);
		retVal.put("newVertices", newVertices);
		retVal.put("edges", edges);

		try {
			generateVertices(mapping, record, nodes, nodeMap, nodeLabels, attributes);

			siftVertices(nodes, nodeMap, nodeLabels, existing, newVertices);

			generateEdges(mapping, record, nodes, edges, attributes);
		} catch (ProcessException pe) {
			if (log.isDebugEnabled()) {
				log.debug("A process exception was captured while processing the transition.");
				log.debug(dumpCurrentWork(retVal));
				log.debug("Rethrowing pe...");
			}
			throw pe;
		}

		return retVal;
	}

	private String dumpCurrentWork(Map<String, Object> work) {
		try {
			return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(work);
		} catch (JsonProcessingException e) {
			throw new ProcessException(e);
		}
	}

	private void generateVertices(Transition mapping, Record record, Map<String, Record> nodes,
								  Map<String, Vertex> nodeMap, Map<String, String> nodeLabels, Map<String, String> attributes) {
		mapping.getVertices().stream().forEach(graphVertex -> {
			Record nodeRecord;
			if (!StringUtils.isEmpty(graphVertex.getSourceRecordPath())) {
				generateVertexFromRecordPath(mapping, graphVertex, record, nodes, nodeMap, nodeLabels, attributes);
			} else {
				Map<String, Object> lookup = new HashMap<>();
				lookup.put(IdentifierConstants.IDENTIFIER_NAME, graphVertex.getVertexSchemaName());
				lookup.put(IdentifierConstants.IDENTIFIER_VERSION, graphVertex.getVertexSchemaVersion());
				nodeRecord = new MapRecord(AvroTypeUtil.createSchema(schemaDataSource.getSchema(lookup)), new HashMap<>());
				final Record tempRecord = nodeRecord;
				if (graphVertex.getProperties() != null) {
					try {
						graphVertex.getProperties().stream().forEach(propertyMapping -> mapProperty(graphVertex.getVertexMappingId(), propertyMapping, record, tempRecord, attributes));
					} catch (ProcessException pe) {
						if (log.isDebugEnabled()) {
							log.debug(String.format("Error processing vertex %s", graphVertex.getVertexMappingId()));
						}
						throw pe;
					}
				}
				processVertex(nodeRecord, graphVertex, nodes, nodeMap, nodeLabels);
			}
		});
	}

	private List<Object> convertToList(Object value) {
		if (value == null) {
			return new ArrayList<>();
		}
		List<Object> list;
		if (value.getClass().isArray()) {
			list = Arrays.asList((Object[]) value);
		} else if (value instanceof Collection) {
			list = new ArrayList<>((Collection<Object>) value);
		} else {
			list = Collections.singletonList(value);
		}
		return list;
	}

	private void generateVertexFromRecordPath(Transition mapping, Vertex graphVertex, Record record,
											  Map<String, Record> nodes, Map<String, Vertex> nodeMap,
											  Map<String, String> nodeLabels, Map<String, String> attributes) {
		RecordPath sourcePath;
		try {
			sourcePath = recordPathCache.getCompiled(graphVertex.getSourceRecordPath());
		} catch (RecordPathException ex) {
			throw new InvalidRecordPathException(graphVertex.getVertexMappingId(), graphVertex.getSourceRecordPath(), ex);
		}

		Record nodeRecord;

		RecordPathResult result = sourcePath.evaluate(record);
		Optional<FieldValue> resultFV = result.getSelectedFields().findFirst();
		if (resultFV.isPresent()) {
			FieldValue fieldValue = resultFV.get();
			Object value = fieldValue.getValue();

			if (value instanceof Record) {
				nodeRecord = (Record) value;
				processVertex(nodeRecord, graphVertex, nodes, nodeMap, nodeLabels);
			} else if (value instanceof Collection | (value != null && value.getClass().isArray())) {
				List list = convertToList(value);
				Object first = list.get(0);
				List<Record> records;

				boolean hasMapping = !graphVertex.getProperties().isEmpty();

				if (first instanceof Record) {
					records = (List<Record>) list;
				} else {
					records = new ArrayList<>();
					Map<String, Object> lookup = new HashMap<>();
					lookup.put(IdentifierConstants.IDENTIFIER_NAME, graphVertex.getVertexSchemaName());
					lookup.put(IdentifierConstants.IDENTIFIER_VERSION, graphVertex.getVertexSchemaVersion());
					Schema referencedType = schemaDataSource.getSchema(lookup);
					RecordSchema schema = AvroTypeUtil.createSchema(referencedType);
					List valueList = convertToList(value);
					valueList.forEach(item -> {
						Map<String, Object> fields = new HashMap<>();
						String fieldName = schema.getField(0).getFieldName();
						fields.put(fieldName, item);
						Record tempRecord = new MapRecord(schema, fields);
						records.add(tempRecord);
					});
				}

				for (int index = 0; index < records.size(); index++) {
					Record currentRecord = records.get(index);
					if (hasMapping) {
						Map<String, Object> lookup = new HashMap<>();
						lookup.put(IdentifierConstants.IDENTIFIER_NAME, graphVertex.getVertexSchemaName());
						lookup.put(IdentifierConstants.IDENTIFIER_VERSION, graphVertex.getVertexSchemaVersion());
						nodeRecord = new MapRecord(AvroTypeUtil.createSchema(schemaDataSource.getSchema(lookup)), new HashMap<>());
						final Record tempRecord = nodeRecord;
						graphVertex.getProperties()
							.stream()
							.forEach(propertyMapping -> mapProperty(graphVertex.getVertexMappingId(), propertyMapping, currentRecord, tempRecord, attributes));
						processVertex(index, nodeRecord, graphVertex, nodes, nodeMap, nodeLabels);
					} else {
						processVertex(index, currentRecord, graphVertex, nodes, nodeMap, nodeLabels);
					}
				}
				updateArrayEdgeLinking(graphVertex.getVertexMappingId(), records.size(), mapping);
			}
		}
	}

	/**
	 * Create an easy to read label that describes an edge in error messages.
	 *
	 * @param edge
	 * @return A string representing the edge relationship
	 */
	private String createEdgeObjectIdentifier(Edge edge) {
		return String.format("Edge[%s: %s<=>%s]", edge.getEdgeSchemaName(), edge.getFromVertexId(), edge.getToVertexId());
	}

	private void siftVertices(Map<String, Record> nodes, Map<String, Vertex> nodeMap, Map<String, String> nodeLabels,
							  Map<String, Object> existing, Map<String, Object> newVertices) throws IOException {
		for (Map.Entry<String, Record> node : nodes.entrySet()) {
			String id = node.getKey();
			String label = nodeLabels.get(node.getKey());
			List<String> graphPropertiesExcluded = new ArrayList<>();
			Vertex dto = nodeMap.get(id);
			List<PropertyMapping> Vertex = dto.getProperties();
			if (Vertex != null) {
				graphPropertiesExcluded = Vertex.stream().filter(PropertyMapping -> PropertyMapping.isExcludeFromCache()).map(PropertyMapping -> {
					FieldValue fieldValue;
					fieldValue = getGraphFieldValue(dto.getVertexMappingId(), PropertyMapping, node.getValue());
					return fieldValue.getField().getFieldName();
				}).collect(Collectors.toList());
			}
			RecordCachedDetails details = convertVertex(nodeMap.get(node.getKey()).isAlwaysCreate(), node.getValue(), graphPropertiesExcluded);
			putVertex(id, label, details, existing, newVertices);
		}
	}

	private void generateEdges(Transition mapping, Record record, Map<String, Record> nodes,
							   List<Map<String, Object>> edges, Map<String, String> attributes) {
		mapping.getEdges().stream().forEach(graphEdge -> {
			boolean fromExists = vertexExists(graphEdge.getFromVertexId(), nodes);
			boolean toExists = vertexExists(graphEdge.getToVertexId(), nodes);

			if (!fromExists && graphEdge.isRequired()) {
				throw new ProcessException(String.format("Missing the left node %s in the relationship %s.",
					graphEdge.getFromVertexId(), graphEdge.getLabel()));
			} else if (!fromExists && !graphEdge.isRequired()) {
				return;
			}
			if (!toExists && graphEdge.isRequired()) {
				throw new ProcessException(String.format("Missing the right node %s in the relationship %s.",
					graphEdge.getToVertexId(), graphEdge.getLabel()));
			} else if (!fromExists && !graphEdge.isRequired()) {
				return;
			}

			if ((!toExists || !fromExists) && graphEdge.isRequired()) {
				throw new ProcessException(String.format("The edge %s from %s to %s could not be created because one or both sides were missing.",
					graphEdge.getLabel(), graphEdge.getFromVertexId(), graphEdge.getToVertexId()));
			} else if (toExists && fromExists) {
				generateEdge(graphEdge, record, attributes, edges);
			}
		});
	}

	private void generateEdge(Edge graphEdge, Record record, Map<String, String> attributes, List<Map<String, Object>> edges) {
		Record edgeRecord = null;
		boolean canAdd = true;
		Map<String, Object> lookup = new HashMap<>();
		lookup.put(IdentifierConstants.IDENTIFIER_NAME, graphEdge.getEdgeSchemaName());
		lookup.put(IdentifierConstants.IDENTIFIER_VERSION, graphEdge.getEdgeSchemaVersion());
		edgeRecord = new MapRecord(AvroTypeUtil.createSchema(schemaDataSource.getSchema(lookup)), new HashMap<>());
		final Record tempRecord = edgeRecord;
		if (graphEdge.getProperties() != null) {
			graphEdge
				.getProperties()
				.forEach(propertyMapping -> mapProperty(createEdgeObjectIdentifier(graphEdge), propertyMapping, record, tempRecord, attributes));
		}
		canAdd = validateMappedRecord(createEdgeObjectIdentifier(graphEdge), graphEdge.isRequired(), edgeRecord.getSchema(), edgeRecord);

		if (canAdd) {
			Map<String, Object> edge = createEdgeMap(graphEdge.getFromVertexId(), graphEdge.getToVertexId(),
				graphEdge.getLabel(), edgeRecord, graphEdge.isRequired());
			edges.add(edge);
		} else if (!canAdd && graphEdge.isRequired()) {
			throw new ProcessException(String.format("Edge %s from %s to %s could not be added because it failed validation.",
				graphEdge.getLabel(), graphEdge.getFromVertexId(), graphEdge.getToVertexId()));
		} else if (!canAdd && !graphEdge.isRequired() && log.isDebugEnabled()) {
			log.debug(String.format("Optional edge %s from %s to %s failed validation, so it will not be added.",
				graphEdge.getLabel(), graphEdge.getFromVertexId(), graphEdge.getToVertexId()));
		}
	}

	private void updateArrayEdgeLinking(String graphNodeId, int size, Transition mapping) {
		List<Edge> allEdges = mapping.getEdges();
		List<Edge> edges = allEdges.stream().filter(edge -> edge.getFromVertexId().equals(graphNodeId) ||
			edge.getToVertexId().equals(graphNodeId)).collect(Collectors.toList());

		Iterator<Edge> it = allEdges.iterator();
		while (it.hasNext()) {
			Edge edge = it.next();
			if (edge.getFromVertexId().equals(graphNodeId) ||
				edge.getToVertexId().equals(graphNodeId)) {
				it.remove();
			}
		}

		edges.forEach(edge -> {
			for (int index = 0; index < size; index++) {
				Edge newEdge = edge.clone();
				String newTarget = String.format("%s_%d", graphNodeId, index);
				if (edge.getToVertexId().equals(graphNodeId)) {
					newEdge.setToVertexId(newTarget);
				} else if (edge.getFromVertexId().equals(graphNodeId)) {
					newEdge.setFromVertexId(newTarget);
				} else {
					throw new ProcessException("Should not be here...");
				}

				allEdges.add(newEdge);
			}
		});
	}

	private void processVertex(Record nodeRecord,
							   Vertex graphVertex,
							   Map<String, Record> nodes,
							   Map<String, Vertex> nodeMap,
							   Map<String, String> nodeLabels) {
		processVertex(null, nodeRecord, graphVertex, nodes, nodeMap, nodeLabels);
	}

	private void processVertex(Integer index,
							   Record nodeRecord,
							   Vertex graphVertex,
							   Map<String, Record> nodes,
							   Map<String, Vertex> nodeMap,
							   Map<String, String> nodeLabels) {
		if (nodeRecord != null) {
			boolean canAdd = true;
			if (nodeRecord != null) {
				if (log.isDebugEnabled()) {
					log.debug(String.format("Validating vertex %s", graphVertex.getVertexMappingId()));
				}
				canAdd = validateMappedRecord(graphVertex.getVertexMappingId(), graphVertex.isRequired(), nodeRecord.getSchema(), nodeRecord);
			}

			if (canAdd) {
				String nodeKey = index == null ? graphVertex.getVertexMappingId() : String.format("%s_%d", graphVertex.getVertexMappingId(), index);
				nodes.put(nodeKey, nodeRecord);
				nodeMap.put(nodeKey, graphVertex);
				nodeLabels.put(nodeKey, graphVertex.getLabel());
			} else if (!canAdd && log.isDebugEnabled()) {
				log.debug(String.format("Cannot add vertex with mapping ID %s", graphVertex.getVertexMappingId()));
			}
		}
	}

	private void putVertex(String id, String label, RecordCachedDetails details, Map<String, Object> existing, Map<String, Object> newVertices) {
		if (details.exists()) {
			existing.put(id, details.existingId);
		} else {
			HashMap<String, Object> vertex = new HashMap<>();
			vertex.put("label", label);
			vertex.put("properties", details.properties);
			vertex.put("hash", details.hash);
			vertex.put("skipCache", details.skipCache);
			newVertices.put(id, vertex);
		}
	}

	private boolean vertexExists(String id, Map<String, Record> vertices) {
		return vertices.containsKey(id);
	}

	private boolean nodeIsRequired(String id, Map<String, Vertex> nodes) {
		Vertex vertex = nodes.get(id);
		if (vertex == null) {
			return false;
		}
		return vertex.isRequired();
	}

	private Map<String, Object> createEdgeMap(String from, String to, String label, Record edgeRecord, boolean required) {
		Map<String, Object> retVal = new HashMap<>();
		retVal.put("from", from);
		retVal.put("to", to);
		retVal.put("label", label);
		retVal.put("required", required);

		Map<String, Object> properties = new HashMap<>();
		retVal.put("properties", properties);
		if (edgeRecord != null) {
			edgeRecord.getRawFieldNames().stream().forEach(name -> {
				Object literal = edgeRecord.getValue(name);
				properties.put(name, literal);
			});
		}

		return retVal;
	}

	Serializer<String> keySerializer = (v, o) -> o.write(v.getBytes(StandardCharsets.UTF_8));
	Deserializer<Long> valueDeserializer = bytes -> (bytes != null && bytes.length > 0) ? Longs.fromByteArray(bytes) : null;

	private Long getCachedId(String hash) throws IOException {
		if (cache == null) {
			return null;
		}

		return cache.get(hash, keySerializer, valueDeserializer);
	}

	private RecordCachedDetails convertVertex(boolean alwaysCreate, Record nodeRecord) throws IOException {
		return convertVertex(alwaysCreate, nodeRecord, new ArrayList<String>());
	}

	private RecordCachedDetails convertVertex(boolean alwaysCreate, Record nodeRecord, List<String> graphPropertiesExcluded) throws IOException {
		RecordCachedDetails details = new RecordCachedDetails();
		if (nodeRecord != null) {
			String hash = hashRecord(nodeRecord, graphPropertiesExcluded);
			Long cachedId = getCachedId(hash);
			if (cachedId == null) {
				Map<String, Object> properties = new HashMap<>();
				nodeRecord.getSchema().getFields().stream().forEach(field -> {
					Object literal = nodeRecord.getValue(field.getFieldName());
					if (literal instanceof Timestamp) {
						Timestamp timestamp = (Timestamp) literal;
						literal = new Date(timestamp.getTime());
					}
					properties.put(field.getFieldName(), literal);
				});
				properties.put("_sys_id", hash);
				details.hash = hash;
				details.properties = properties;
				details.skipCache = alwaysCreate;
			} else {
				details.existingId = cachedId;
			}
		} else {
			details.properties = new HashMap<>();
		}

		return details;
	}

	private String runExpressionLanguage(String query, Map<String, String> attributes) {
		PreparedQuery pq = Query.prepare(query);
		EvaluationContext context = new StandardEvaluationContext(attributes);
		return pq.evaluateExpressions(context, null);
	}

	private void mapProperty(String objectIdentifier,
							 PropertyMapping propertyMapping,
							 Record sourceRecord,
							 Record graphRecord,
							 Map<String, String> attributes) {
		RecordPath sourceRecordPath;
		try {
			sourceRecordPath = recordPathCache.getCompiled(propertyMapping.getSourcePropertyName());
		} catch (RecordPathException ex) {
			throw new InvalidRecordPathException(objectIdentifier, propertyMapping.getSourcePropertyName(), ex);
		}

		Optional<FieldValue> sourceFieldValue = sourceRecordPath != null ?
			sourceRecordPath.evaluate(sourceRecord).getSelectedFields().findFirst() : Optional.empty();

		if (sourceFieldValue.isPresent() || propertyMapping.getDefaultValue() != null) {
			Object value = sourceFieldValue.isPresent() ? sourceFieldValue.get().getValue() : null;
			RecordPath graphRecordPath;
			try {
				graphRecordPath = recordPathCache.getCompiled(propertyMapping.getGraphPropertyName());
			} catch (RecordPathException ex) {
				throw new InvalidRecordPathException(objectIdentifier, propertyMapping.getGraphPropertyName(), ex);
			}

			RecordPathResult graphRecordPathResult = graphRecordPath.evaluate(graphRecord);
			Optional<FieldValue> graphFieldValueOpt = graphRecordPathResult.getSelectedFields().findFirst();

			if (!graphFieldValueOpt.isPresent()) {
				throw new ProcessException(String.format("No property path for %s", propertyMapping.getGraphPropertyName()));
			}

			FieldValue graphFieldValue = graphFieldValueOpt.get();
			if (propertyMapping.getDefaultValue() != null && (value == null || StringUtils.isEmpty(value.toString()))) {
				Object graphDefaultValue = propertyMapping.getDefaultValue();
				if (graphDefaultValue instanceof String) {
					value = runExpressionLanguage((String) graphDefaultValue, attributes);
				} else {
					value = graphDefaultValue;
				}
			}
			/*
			 * This is to make sure that the value we are putting into the field matches the expected type.
			 */
			String fieldName = graphFieldValue.getField().getFieldName();
			RecordSchema parentSchema = graphFieldValue.getParentRecord().get().getSchema();
			Optional<RecordField> tempField = parentSchema.getField(fieldName);
			RecordField recordField = null;
			if (tempField.isPresent()) {
				recordField = tempField.get();
			} else {
				throw new ProcessException(String.format("Field %s not present on graph schema with identifier %s", fieldName, objectIdentifier));
			}

			if (value != null) {
				if ((recordField.getDataType().getClass() == ArrayDataType.class) && !(value.getClass().isArray() || value instanceof Collection)) {
					List<Object> valueList = new ArrayList<>();
					valueList.add(value);
					value = DataTypeUtils.convertType(valueList, recordField.getDataType(), recordField.getFieldName());
				} else {
					value = DataTypeUtils.convertType(value, recordField.getDataType(), recordField.getFieldName());
				}
			}
			graphFieldValue.updateValue(value);
		} else {
			throw new ProcessException(String.format("Nothing found for path %s on %s", propertyMapping.getSourcePropertyName(), objectIdentifier));
		}
	}

	private boolean validateMappedRecord(String objectIdentifier,
										 boolean isRequired,
										 RecordSchema validationSchema,
										 Record record) {
		boolean retVal = true;
		final SchemaValidationContext validationContext = new SchemaValidationContext(validationSchema, false, true);
		final RecordSchemaValidator validator = new StandardSchemaValidator(validationContext);
		SchemaValidationResult validationResult = validator.validate(record);
		if (!validationResult.isValid()) {
			StringBuilder sb = new StringBuilder();
			validationResult.getValidationErrors().stream().forEach(validationError -> sb.append(String.format("Field: \"%s\" Value: \"%s\" Details: %s%n", validationError.getFieldName(), validationError.getInputValue(), validationError.getExplanation())));

			if (isRequired) {
				throw new ProcessException(String.format("Error validating fields on object %s; %s", objectIdentifier, sb.toString()));
			} else if (!isRequired && log.isDebugEnabled()) {
				log.debug(String.format("Error validating mapped record: %s", sb.toString()));
			}
			retVal = false;
		}
		return retVal;
	}

	private FieldValue getGraphFieldValue(String objectIdentifier, PropertyMapping propertyMapping, Record graphRecord) {
		RecordPath graphRecordPath;
		try {
			graphRecordPath = recordPathCache.getCompiled(propertyMapping.getGraphPropertyName());
		} catch (RecordPathException ex) {
			throw new InvalidRecordPathException(objectIdentifier, propertyMapping.getGraphPropertyName(), ex);
		}
		RecordPathResult graphRecordPathResult = graphRecordPath.evaluate(graphRecord);
		Optional<FieldValue> graphFieldValueOpt = graphRecordPathResult.getSelectedFields().findFirst();
		if (!graphFieldValueOpt.isPresent()) {
			throw new ProcessException(String.format("No property path for %s", propertyMapping.getGraphPropertyName()));
		}
		return graphFieldValueOpt.get();
	}

	private String hashRecord(Record record, List<String> excludeFromHash) {
		StringBuilder sb = new StringBuilder();
		Set<String> rawFieldNames = record.getRawFieldNames().stream().filter(s -> {
			return !excludeFromHash.contains(s);
		}).collect(Collectors.toSet());
		List<String> names = new ArrayList<>(rawFieldNames);
		Collections.sort(names);
		names.forEach(name -> sb.append(String.format("%s:%s;", name, record.getAsString(name))));
		return DigestUtils.sha1Hex(sb.toString());
	}

	private String hashRecord(Record record) {
		return hashRecord(record, new ArrayList<String>());
	}

	public void setSchemaDatasource(SchemaDataSource dataSource) {
		schemaDataSource = dataSource;
	}

	public void setComponentLog(ComponentLog log) {
		this.log = log;
		if (this.log.isDebugEnabled()) {
			log.debug("Debug logging enabled.");
		}
	}

	public void setCache(DistributedMapCacheClient primaryCache) {
		this.cache = primaryCache;
	}

	static final class RecordCachedDetails {
		protected boolean skipCache;
		protected Long existingId;
		protected Map<String, Object> properties;
		protected String hash;
		protected String required;

		public boolean exists() {
			return existingId != null;
		}
	}

}
