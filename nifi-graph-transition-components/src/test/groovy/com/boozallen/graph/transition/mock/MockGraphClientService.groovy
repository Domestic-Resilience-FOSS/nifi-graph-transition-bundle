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

package com.boozallen.graph.transition.mock

import org.apache.nifi.controller.AbstractControllerService
import org.apache.nifi.graph.GraphClientService
import org.apache.nifi.graph.GraphQueryResultCallback

class MockGraphClientService extends AbstractControllerService implements GraphClientService {
    List statements = []

    @Override
    Map<String, String> executeQuery(String query, Map<String, Object> map, GraphQueryResultCallback graphQueryResultCallback) {
        statements << query
        def wrapper = new SimpleEntry(key: UUID.randomUUID().toString(), value: [ (UUID.randomUUID().toString()): 1l ])
        graphQueryResultCallback.process([ result: wrapper ], false)
        [:]
    }

    @Override
    String getTransitUrl() {
        return "graph://localhost:1234/test"
    }

    class SimpleEntry implements Map.Entry {
        String key
        Object value

        @Override
        Object getKey() {
            key
        }

        @Override
        Object getValue() {
            value
        }

        @Override
        Object setValue(Object value) {
            this.value = value
        }
    }
}
