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

def transaction = graph.newTransaction()
def trav = transaction.traversal()
def _ids = [:]
def returnMap = [:]

try {
    existing?.each { vertex ->
        _ids[vertex.key] = trav.V(vertex.value).next()
    }

    int added = 0
    newVertices.each { vertex ->
        def _temp = trav.addV(vertex.value['label'])
        vertex.value['properties'].each { kv ->
            if(kv.key != "_sys_id"){    // This meta data property is not defined by the graph schema so it will cause errors
                _temp.property(kv.key, kv.value)
            }
        }
        _ids[vertex.key] = _temp.next()
        if (vertex.value['hash'] && !vertex.value['skipCache']) {
            returnMap[vertex.value['hash']] = _ids[vertex.key].id()
            added++
        }
    }

    if (edges) {
        def _edgeTrav = trav
        edges.each { edge ->
            _edgeTrav = _edgeTrav.V(_ids[edge['from']]).as('left').V(_ids[edge['to']]).as('right').addE(edge['label'])
            if (edge['properties']) {
                edge.properties.each { prop ->
                    _edgeTrav = _edgeTrav.property(prop.key, prop.value)
                }
            }
            _edgeTrav = _edgeTrav.from('left').to('right')
        }
        _edgeTrav.tryNext()
    }

    transaction.commit()

    [
            result: returnMap
    ]
} catch (NoSuchElementException noSuchElement) {
    noSuchElement.printStackTrace()
    transaction.rollback()
    [
            cacheDetails: [:]
    ]
} catch (Exception ex) {
    ex.printStackTrace()
} finally {
    transaction.close()
}