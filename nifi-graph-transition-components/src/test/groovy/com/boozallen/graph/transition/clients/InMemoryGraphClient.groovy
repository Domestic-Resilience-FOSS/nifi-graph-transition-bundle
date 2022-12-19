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

package com.boozallen.graph.transition.clients

import org.apache.nifi.annotation.lifecycle.OnEnabled
import org.apache.nifi.controller.AbstractControllerService
import org.apache.nifi.controller.ConfigurationContext
import org.apache.nifi.graph.GraphClientService
import org.apache.nifi.graph.GraphQueryResultCallback
import org.apache.tinkerpop.gremlin.structure.Graph
import org.janusgraph.core.JanusGraphFactory

import javax.script.ScriptEngineManager

class InMemoryGraphClient extends AbstractControllerService implements GraphClientService {
    Graph graph

    @OnEnabled
    void onEnabled(ConfigurationContext context) {
        graph = JanusGraphFactory.build().set('storage.backend', 'inmemory').open()
    }

    @Override
    Map<String, String> executeQuery(String query, Map<String, Object> parameters, GraphQueryResultCallback graphQueryResultCallback) {
        def engine = new ScriptEngineManager().getEngineByName("groovy")
        parameters.each {
            engine.put(it.key, it.value)
        }
        engine.put("graph", graph)
        engine.put("g", graph.traversal())

        def response = engine.eval(query)

        if (response instanceof Map) {
            Map resultMap = (Map) response
            if (!resultMap.isEmpty()) {
                Iterator outerResultSet = resultMap.entrySet().iterator()
                while(outerResultSet.hasNext()) {
                    Map.Entry<String, Object> innerResultSet = (Map.Entry<String, Object>) outerResultSet.next()
                    if (innerResultSet.getValue() instanceof Map) {
                        Iterator resultSet = ((Map) innerResultSet.getValue()).entrySet().iterator()
                        while (resultSet.hasNext()) {
                            Map.Entry<String, Object> tempResult = (Map.Entry<String, Object>) resultSet.next()
                            Map<String, Object> tempRetObject = new HashMap<>()
                            tempRetObject.put(tempResult.getKey(), tempResult.getValue())
                            AbstractMap.SimpleEntry returnObject = new AbstractMap.SimpleEntry<String, Object>(tempResult.getKey(), tempRetObject)
                            Map<String, Object> resultReturnMap = new HashMap<>()
                            resultReturnMap.put(innerResultSet.getKey(), returnObject)
                            getLogger().info(resultReturnMap.toString())
                            graphQueryResultCallback.process(resultReturnMap, resultSet.hasNext())
                        }
                    } else {
                        Map<String, Object> resultReturnMap = new HashMap<>()
                        resultReturnMap.put(innerResultSet.getKey(), innerResultSet.getValue())
                        graphQueryResultCallback.process(resultReturnMap, false)
                    }
                }

            }
        }

        return [:]
    }

    @Override
    String getTransitUrl() {
        return "memory://localhost/graph"
    }
}
