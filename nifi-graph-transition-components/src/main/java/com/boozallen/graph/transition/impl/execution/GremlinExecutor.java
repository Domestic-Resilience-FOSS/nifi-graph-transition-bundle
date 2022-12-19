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
package com.boozallen.graph.transition.impl.execution;

import com.boozallen.graph.transition.impl.GraphRecordProcessor;
import org.apache.commons.io.IOUtils;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.distributed.cache.client.DistributedMapCacheClient;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.graph.GraphClientService;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.serialization.RecordSetWriter;
import org.apache.nifi.serialization.record.Record;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * This class fully encapsulates the logic involved in working with the Gremlin APIs.
 */
public class GremlinExecutor extends GraphExecutor {
	public static final PropertyDescriptor RECORD_SCRIPT = new PropertyDescriptor.Builder()
		.name("record-script")
		.displayName("Record Script")
		.defaultValue(loadScript("/record.groovy"))
		.required(false)
		.addValidator(StandardValidators.NON_EMPTY_EL_VALIDATOR)
		.build();
	public static final PropertyDescriptor HEADER_SCRIPT = new PropertyDescriptor.Builder()
		.name("header-script")
		.displayName("Header Script")
		.defaultValue(loadScript("/header.groovy"))
		.expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
		.required(false)
		.addValidator(StandardValidators.NON_EMPTY_EL_VALIDATOR)
		.build();

	public GremlinExecutor(ComponentLog log,
						   GraphClientService clientService,
						   DistributedMapCacheClient mapCacheClient,
						   RecordSetWriter errorWriter) {
		super(log, clientService, mapCacheClient, errorWriter);
	}

	private static String loadScript(String path) {
		try {
			return IOUtils.toString(GraphRecordProcessor.class.getResourceAsStream(path), StandardCharsets.UTF_8);
		} catch (Exception ex) {
			throw new ProcessException(ex);
		}
	}

	private String fullGremlinScript;

	@Override
	public void configure(ProcessContext context,
						  ProcessSession session,
						  FlowFile input) {
		fullGremlinScript = getScript(RECORD_SCRIPT, context, input);
	}

	private String getScript(PropertyDescriptor script, ProcessContext context, FlowFile input) {
		String header = context.getProperty(HEADER_SCRIPT).evaluateAttributeExpressions(input).getValue();
		String scriptValue = context.getProperty(script).getValue();
		return new StringBuilder()
			.append(header)
			.append("\n\n")
			.append(scriptValue)
			.toString();
	}

	@Override
	public List<Map<String, Object>> execute(Map<String, Object> parameters, Record record) {
		List<Map<String, Object>> graphResponses = new ArrayList<>();
		clientService.executeQuery(fullGremlinScript, parameters, (map, b) -> {
			Map.Entry<String, Map> response = (Map.Entry<String, Map>) map.get("result");
			graphResponses.add(map);
			if (response != null) {
				for (Map.Entry<String, Object> entry : ((Map<String, Object>) response.getValue()).entrySet()) {
					String key = entry.getKey();
					Long value = (Long) entry.getValue();

					try {
						if (mapCacheClient != null) {
							mapCacheClient.put(key, value, KEY_SERIALIZER, VALUE_SERIALIZER);
						}
					} catch (IOException ex) {
						throw new ProcessException(ex);
					}
				}
			} else {
				try {
					errorWriter.write(record);
					errors.incrementAndGet();
				} catch (IOException e) {
					throw new ProcessException(e);
				}
			}
		});
		return graphResponses;
	}
}