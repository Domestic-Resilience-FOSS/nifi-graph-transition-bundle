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
import java.util.List;

public class Transition {
	/**
	 * The name of the transition.
	 */
	private String subject;
	/**
	 * The name in the schema repository for the schema that will be applied to the input records.
	 */
	private String sourceSubject;
	/**
	 * The version number of the source schema
	 */
	private Integer sourceSchemaVersion;
	/**
	 * A description of this mapping's purpose to the user/organization.
	 */
	private String description;
	/**
	 * The vertices to be defined and mapped.
	 */
	private List<Vertex> vertices;
	/**
	 * The edges to be defined and mapped;
	 */
	private List<Edge> edges;

	public Transition(@JsonProperty("subject") String subject,
					  @JsonProperty(value = "sourceSubject", required = true) String sourceSubject,
					  @JsonProperty(value = "sourceSchemaVersion", required = true) Integer sourceSchemaVersion,
					  @JsonProperty("description") String description) {
		this.subject = subject;
		this.sourceSubject = sourceSubject;
		this.sourceSchemaVersion = sourceSchemaVersion;
		this.description = description;
	}


	public static class TransitionBuilder {
		private String subject;
		private String sourceSubject;
		private Integer sourceSchemaVersion;
		private String description;
		private String status;
		private boolean isTemplate;
		private boolean isEphemeral;

		TransitionBuilder() {
		}

		/**
		 * @return {@code this}.
		 */
		@JsonProperty("subject")
		public Transition.TransitionBuilder subject(final String subject) {
			this.subject = subject;
			return this;
		}

		/**
		 * @return {@code this}.
		 */
		@JsonProperty(value = "sourceSubject", required = true)
		public Transition.TransitionBuilder sourceSubject(final String sourceSubject) {
			this.sourceSubject = sourceSubject;
			return this;
		}

		/**
		 * @return {@code this}.
		 */
		@JsonProperty(value = "sourceSchemaVersion", required = true)
		public Transition.TransitionBuilder sourceSchemaVersion(final Integer sourceSchemaVersion) {
			this.sourceSchemaVersion = sourceSchemaVersion;
			return this;
		}

		/**
		 * @return {@code this}.
		 */
		@JsonProperty("description")
		public Transition.TransitionBuilder description(final String description) {
			this.description = description;
			return this;
		}

		/**
		 * @return {@code this}.
		 */
		@JsonProperty("status")
		public Transition.TransitionBuilder status(final String status) {
			this.status = status;
			return this;
		}

		/**
		 * @return {@code this}.
		 */
		@JsonProperty(value = "isTemplate", defaultValue = "false")
		public Transition.TransitionBuilder isTemplate(final boolean isTemplate) {
			this.isTemplate = isTemplate;
			return this;
		}

		/**
		 * @return {@code this}.
		 */
		@JsonProperty(value = "isEphemeral", defaultValue = "false")
		public Transition.TransitionBuilder isEphemeral(final boolean isEphemeral) {
			this.isEphemeral = isEphemeral;
			return this;
		}

		public Transition build() {
			return new Transition(this.subject, this.sourceSubject, this.sourceSchemaVersion, this.description);
		}

		@Override
		public String toString() {
			return "Transition.TransitionBuilder(subject=" + this.subject + ", sourceSubject=" + this.sourceSubject + ", sourceSchemaVersion=" + this.sourceSchemaVersion + ", description=" + this.description + ", status=" + this.status + ", isTemplate=" + this.isTemplate + ", isEphemeral=" + this.isEphemeral + ")";
		}
	}

	public static Transition.TransitionBuilder builder() {
		return new Transition.TransitionBuilder();
	}

	public Transition() {
	}

	public Transition(final String subject,
					  final String sourceSubject,
					  final Integer sourceSchemaVersion,
					  final String description,
					  final List<Vertex> vertices,
					  final List<Edge> edges) {
		this.subject = subject;
		this.sourceSubject = sourceSubject;
		this.sourceSchemaVersion = sourceSchemaVersion;
		this.description = description;
		this.vertices = vertices;
		this.edges = edges;
	}

	/**
	 * The name of the graph mapping.
	 */
	public void setSubject(final String subject) {
		this.subject = subject;
	}

	/**
	 * The name in the schema repository for the schema that will be applied to the input records.
	 */
	public void setSourceSubject(final String sourceSubject) {
		this.sourceSubject = sourceSubject;
	}

	public void setSourceSchemaVersion(final Integer sourceSchemaVersion) {
		this.sourceSchemaVersion = sourceSchemaVersion;
	}

	/**
	 * A description of this mapping's purpose to the user/organization.
	 */
	public void setDescription(final String description) {
		this.description = description;
	}

	/**
	 * The vertices to be defined and mapped.
	 */
	public void setVertices(final List<Vertex> vertices) {
		this.vertices = vertices;
	}

	/**
	 * The edges to be defined and mapped;
	 */
	public void setEdges(final List<Edge> edges) {
		this.edges = edges;
	}

	/**
	 * The name of the graph mapping.
	 */
	public String getSubject() {
		return this.subject;
	}

	/**
	 * The name in the schema repository for the schema that will be applied to the input records.
	 */
	public String getSourceSubject() {
		return this.sourceSubject;
	}

	public Integer getSourceSchemaVersion() {
		return this.sourceSchemaVersion;
	}

	/**
	 * A description of this mapping's purpose to the user/organization.
	 */
	public String getDescription() {
		return this.description;
	}

	/**
	 * The vertices to be defined and mapped.
	 */
	public List<Vertex> getVertices() {
		return this.vertices;
	}

	/**
	 * The edges to be defined and mapped;
	 */
	public List<Edge> getEdges() {
		return this.edges;
	}
}
