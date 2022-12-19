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

/**
 * This maps fields between an input schema and a schema representing the schema of a graph entity.
 */
public class PropertyMapping {
	/**
	 * The property name in the graph entity. Applies to both edges and vertices.
	 */
	protected String graphPropertyName;
	/**
	 * The property name in the source schema.
	 */
	protected String sourcePropertyName;
	/**
	 * The default value to apply to the target field if the input value is missing. This can be used to
	 * override defaults specified in the schemas themselves so long as the value type is acceptable to the schema
	 * system. An expression language string can also be supplied her to make this behavior dynamic.
	 *
	 * Example: /firstName
	 */
	protected String defaultValue;

	/**
	 * A boolean flag used to control whether or not this property is excluded from the mechanisms used to
	 * generate a cache ID for the graph element that owns this property in the transition.
	 */
	protected boolean excludeFromCache;

	public PropertyMapping(@JsonProperty(value = "graphPropertyName", required = true) String graphPropertyName, @JsonProperty(value = "sourcePropertyName", required = true) String sourcePropertyName, @JsonProperty("defaultValue") String defaultValue, @JsonProperty("excludeFromCache") boolean excludeFromCache) {
		this.graphPropertyName = graphPropertyName;
		this.sourcePropertyName = sourcePropertyName;
		this.defaultValue = defaultValue;
		this.excludeFromCache = excludeFromCache;
	}

	public PropertyMapping() {
	}

	/**
	 * The property name in the graph entity. Applies to both edges and vertices.
	 */
	public String getGraphPropertyName() {
		return this.graphPropertyName;
	}

	/**
	 * The property name in the source schema.
	 */
	public String getSourcePropertyName() {
		return this.sourcePropertyName;
	}

	/**
	 * The default value to apply to the target field if the input value is missing. This can be used to
	 * override defaults specified in the schemas themselves so long as the value type is acceptable to the schema
	 * system.
	 */
	public String getDefaultValue() {
		return this.defaultValue;
	}

	public boolean isExcludeFromCache() {
		return this.excludeFromCache;
	}

	/**
	 * The property name in the graph entity. Applies to both edges and vertices.
	 */
	public void setGraphPropertyName(final String graphPropertyName) {
		this.graphPropertyName = graphPropertyName;
	}

	/**
	 * The property name in the source schema.
	 */
	public void setSourcePropertyName(final String sourcePropertyName) {
		this.sourcePropertyName = sourcePropertyName;
	}

	/**
	 * The default value to apply to the target field if the input value is missing. This can be used to
	 * override defaults specified in the schemas themselves so long as the value type is acceptable to the schema
	 * system.
	 */
	public void setDefaultValue(final String defaultValue) {
		this.defaultValue = defaultValue;
	}

	public void setExcludeFromCache(final boolean excludeFromCache) {
		this.excludeFromCache = excludeFromCache;
	}

	@Override
	public boolean equals(final Object o) {
		if (o == this) return true;
		if (!(o instanceof PropertyMapping)) return false;
		final PropertyMapping other = (PropertyMapping) o;
		if (!other.canEqual((Object) this)) return false;
		if (this.isExcludeFromCache() != other.isExcludeFromCache()) return false;
		final Object this$graphPropertyName = this.getGraphPropertyName();
		final Object other$graphPropertyName = other.getGraphPropertyName();
		if (this$graphPropertyName == null ? other$graphPropertyName != null : !this$graphPropertyName.equals(other$graphPropertyName)) return false;
		final Object this$sourcePropertyName = this.getSourcePropertyName();
		final Object other$sourcePropertyName = other.getSourcePropertyName();
		if (this$sourcePropertyName == null ? other$sourcePropertyName != null : !this$sourcePropertyName.equals(other$sourcePropertyName)) return false;
		final Object this$defaultValue = this.getDefaultValue();
		final Object other$defaultValue = other.getDefaultValue();
		if (this$defaultValue == null ? other$defaultValue != null : !this$defaultValue.equals(other$defaultValue)) return false;
		return true;
	}

	protected boolean canEqual(final Object other) {
		return other instanceof PropertyMapping;
	}

	@Override
	public int hashCode() {
		final int PRIME = 59;
		int result = 1;
		result = result * PRIME + (this.isExcludeFromCache() ? 79 : 97);
		final Object $graphPropertyName = this.getGraphPropertyName();
		result = result * PRIME + ($graphPropertyName == null ? 43 : $graphPropertyName.hashCode());
		final Object $sourcePropertyName = this.getSourcePropertyName();
		result = result * PRIME + ($sourcePropertyName == null ? 43 : $sourcePropertyName.hashCode());
		final Object $defaultValue = this.getDefaultValue();
		result = result * PRIME + ($defaultValue == null ? 43 : $defaultValue.hashCode());
		return result;
	}

	@Override
	public String toString() {
		return "PropertyMapping(graphPropertyName=" + this.getGraphPropertyName() + ", sourcePropertyName=" + this.getSourcePropertyName() + ", defaultValue=" + this.getDefaultValue() + ", excludeFromCache=" + this.isExcludeFromCache() + ")";
	}
}
