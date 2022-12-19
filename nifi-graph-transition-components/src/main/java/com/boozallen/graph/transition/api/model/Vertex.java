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
package com.boozallen.graph.transition.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * This is a representation of a vertex in the mapping.
 */
public class Vertex {
	/**
	 * The vertex's mapping ID. This is the name used within the transition to identify it within the logical subgraph
	 */
	protected String vertexMappingId;
	/**
	 * A boolean flag used to control whether or not this element must be present. If it is optional, and missing
	 * from a generated logical subgraph, any edges that connect to it will not be generated.
	 */
	protected boolean required;
	/**
	 * A boolean flag that controls whether or not this vertex should always be generated from incoming data as
	 * opposed to using the cache.
	 */
	protected boolean alwaysCreate;
	/**
	 * A record path operation to use for reaching into a record and using the targeted field as the
	 * starting point for defining the source record.
	 */
	protected String sourceRecordPath;
	/**
	 * The name/subject of the graph schema that will be used to validate the fields of this vertex
	 */
	protected String vertexSchemaName;
	/**
	 * The version number associated with the vertex's Avro schema.
	 */
	protected Integer vertexSchemaVersion;
	/**
	 * The label to associated with this graph element.
	 */
	protected String label;

	/**
	 * A list of mappings between source schema and graph schema fields.
	 */
	protected List<PropertyMapping> properties;

	public Vertex() {
	}

	/**
	 * This constructor exists to help Jackson map JSON to this POJO automatically.
	 */
	public Vertex(@JsonProperty(value = "required", defaultValue = "true") boolean required, @JsonProperty("alwaysCreate") boolean alwaysCreate, @JsonProperty("sourceRecordPath") String sourceRecordPath, @JsonProperty(value = "vertexMappingId", required = true) String vertexMappingId, @JsonProperty(value = "vertexSchemaName", required = true) String vertexSchemaName, @JsonProperty(value = "vertexSchemaVersion", required = true) Integer vertexSchemaVersion, @JsonProperty("properties") List<PropertyMapping> properties) {
		this.alwaysCreate = alwaysCreate;
		this.required = required;
		this.sourceRecordPath = sourceRecordPath;
		this.vertexMappingId = vertexMappingId;
		this.vertexSchemaName = vertexSchemaName;
		this.vertexSchemaVersion = vertexSchemaVersion;
		this.properties = properties != null ? properties : new ArrayList<>();
	}

	public List<PropertyMapping> getProperties() {
		return this.properties;
	}

	public void setProperties(final List<PropertyMapping> properties) {
		this.properties = properties;
	}

	public String getVertexMappingId() {
		return vertexMappingId;
	}

	public void setVertexMappingId(String vertexMappingId) {
		this.vertexMappingId = vertexMappingId;
	}

	public boolean isRequired() {
		return required;
	}

	public void setRequired(boolean required) {
		this.required = required;
	}

	public boolean isAlwaysCreate() {
		return alwaysCreate;
	}

	public void setAlwaysCreate(boolean alwaysCreate) {
		this.alwaysCreate = alwaysCreate;
	}

	public String getSourceRecordPath() {
		return sourceRecordPath;
	}

	public void setSourceRecordPath(String sourceRecordPath) {
		this.sourceRecordPath = sourceRecordPath;
	}

	public String getVertexSchemaName() {
		return vertexSchemaName;
	}

	public void setVertexSchemaName(String vertexSchemaName) {
		this.vertexSchemaName = vertexSchemaName;
	}

	public Integer getVertexSchemaVersion() {
		return vertexSchemaVersion;
	}

	public void setVertexSchemaVersion(Integer vertexSchemaVersion) {
		this.vertexSchemaVersion = vertexSchemaVersion;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	@Override
	public boolean equals(final Object o) {
		if (o == this) return true;
		if (!(o instanceof Vertex)) return false;
		final Vertex other = (Vertex) o;
		if (!other.canEqual((Object) this)) return false;
		final Object this$properties = this.getProperties();
		final Object other$properties = other.getProperties();
		if (this$properties == null ? other$properties != null : !this$properties.equals(other$properties)) return false;
		return true;
	}

	protected boolean canEqual(final Object other) {
		return other instanceof Vertex;
	}

	@Override
	public int hashCode() {
		final int PRIME = 59;
		int result = 1;
		final Object $properties = this.getProperties();
		result = result * PRIME + ($properties == null ? 43 : $properties.hashCode());
		return result;
	}

	@Override
	public String toString() {
		return "Vertex(properties=" + this.getProperties() + ")";
	}
}
