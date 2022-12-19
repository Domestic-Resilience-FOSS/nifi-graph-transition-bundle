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
import org.apache.nifi.distributed.cache.client.Deserializer
import org.apache.nifi.distributed.cache.client.DistributedMapCacheClient
import org.apache.nifi.distributed.cache.client.Serializer

class MockMapCacheClient extends AbstractControllerService implements DistributedMapCacheClient {
    def _map = [:]

    @Override
    def <K, V> boolean putIfAbsent(K k, V v, Serializer<K> serializer, Serializer<V> serializer1) throws IOException {
        return false
    }

    @Override
    def <K, V> V getAndPutIfAbsent(K k, V v, Serializer<K> serializer, Serializer<V> serializer1, Deserializer<V> deserializer) throws IOException {
        return null
    }

    @Override
    def <K> boolean containsKey(K k, Serializer<K> serializer) throws IOException {
        return false
    }

    @Override
    def <K, V> void put(K k, V v, Serializer<K> serializer, Serializer<V> serializer1) throws IOException {
        _map[k] = v
    }

    @Override
    def <K, V> V get(K k, Serializer<K> serializer, Deserializer<V> deserializer) throws IOException {
        _map[k]
    }

    @Override
    void close() throws IOException {

    }

    @Override
    def <K> boolean remove(K k, Serializer<K> serializer) throws IOException {
        return false
    }

    @Override
    long removeByPattern(String s) throws IOException {
        return 0
    }
}
