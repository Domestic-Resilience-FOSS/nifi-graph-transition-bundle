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

public class Edge implements Cloneable {
	/**
	 * Required elements will throw an exception during transformation if they are missing.
	 */
	protected boolean required;
	/**
	 * The source vertex ID
	 */
	protected String fromVertexId;
	/**
	 * The target vertex ID
	 */
	protected String toVertexId;
	/**
	 * The Avro schema name that corresponds to the edge's schema
	 */
	protected String edgeSchemaName;
	/**
	 * The version number of the edge's Avro schema
	 */
	protected Integer edgeSchemaVersion;
	/**
	 * The graph label associated with this edge
	 */
	protected String label;

	/**
	 * The property mappings between the source schema and the target edge schema
	 */
	protected List<PropertyMapping> properties;

	/**
	 * This constructor was created to facilitate Jackson automatically binding JSON to this POJO.
	 */
	public Edge(@JsonProperty(value = "required", defaultValue = "true") Boolean required,
				@JsonProperty(value = "fromVertexId", required = true) String fromVertexId,
				@JsonProperty(value = "toVertexId", required = true) String toVertexId,
				@JsonProperty(value = "edgeSchemaName", required = true) String edgeSchemaName,
				@JsonProperty(value = "edgeSchemaVersion", required = true) Integer edgeSchemaVersion,
				@JsonProperty(value = "label", required = true) String label,
				@JsonProperty("properties") List<PropertyMapping> properties) {
		this.fromVertexId = fromVertexId;
		this.toVertexId = toVertexId;
		this.edgeSchemaName = edgeSchemaName;
		this.edgeSchemaVersion = edgeSchemaVersion;
		this.required = required == null ? true : required;
		this.label = label;
		this.properties = properties != null ? properties : new ArrayList<>();
	}

	public Edge() {
	}

	public Edge clone() {
		return new Edge(required, fromVertexId, toVertexId, edgeSchemaName, edgeSchemaVersion, label, properties);
	}

	public List<PropertyMapping> getProperties() {
		return this.properties;
	}

	public void setProperties(final List<PropertyMapping> properties) {
		this.properties = properties;
	}

	public boolean isRequired() {
		return required;
	}

	public void setRequired(boolean required) {
		this.required = required;
	}

	public String getFromVertexId() {
		return fromVertexId;
	}

	public void setFromVertexId(String fromVertexId) {
		this.fromVertexId = fromVertexId;
	}

	public String getToVertexId() {
		return toVertexId;
	}

	public void setToVertexId(String toVertexId) {
		this.toVertexId = toVertexId;
	}

	public String getEdgeSchemaName() {
		return edgeSchemaName;
	}

	public void setEdgeSchemaName(String edgeSchemaName) {
		this.edgeSchemaName = edgeSchemaName;
	}

	public Integer getEdgeSchemaVersion() {
		return edgeSchemaVersion;
	}

	public void setEdgeSchemaVersion(Integer edgeSchemaVersion) {
		this.edgeSchemaVersion = edgeSchemaVersion;
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
		if (!(o instanceof Edge)) return false;
		final Edge other = (Edge) o;
		if (!other.canEqual((Object) this)) return false;
		final Object this$properties = this.getProperties();
		final Object other$properties = other.getProperties();
		if (this$properties == null ? other$properties != null : !this$properties.equals(other$properties)) return false;
		return true;
	}

	protected boolean canEqual(final Object other) {
		return other instanceof Edge;
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
		return "Edge(properties=" + this.getProperties() + ")";
	}
}
