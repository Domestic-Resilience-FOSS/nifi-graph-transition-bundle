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

package com.boozallen.graph.transition.util

import com.github.javafaker.Faker

class IdentificationListRecordBuilder {
    Faker faker = new Faker()

    Map createRecord() {
        def identifications = []
        1.upto(5) {
            identifications.add(
                    [
                            Identification             : faker.name().fullName(),
                            IdentificationType         : "Temp",
                            IdentificationLocationLocal: faker.address().state()

                    ]
            )
        }
        [
                SenderPersonIdentifications: identifications
        ]
    }
}
