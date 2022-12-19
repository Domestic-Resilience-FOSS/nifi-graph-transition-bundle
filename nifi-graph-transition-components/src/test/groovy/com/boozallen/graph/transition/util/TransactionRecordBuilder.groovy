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

class TransactionRecordBuilder {
    Faker faker = new Faker()

    Map createRecord() {
        [
            SenderFirstName      : faker.name().firstName(),
            SenderLastName       : faker.name().lastName(),
            SenderStreetAddress  : faker.address().streetAddress(),
            SenderCity           : faker.address().city(),
            SenderState          : faker.address().state(),
            SenderCountry        : faker.address().country(),
            SenderEmail          : faker.internet().emailAddress(),
            SenderPhoneNumber    : faker.phoneNumber().cellPhone(),
            ReceiverFirstName    : faker.name().firstName(),
            ReceiverLastName     : faker.name().lastName(),
            ReceiverStreetAddress: faker.address().streetAddress(),
            ReceiverCity         : faker.address().city(),
            ReceiverState        : faker.address().state(),
            ReceiverCountry      : faker.address().country(),
            ReceiverEmail        : faker.internet().emailAddress(),
            ReceiverPhoneNumber  : faker.phoneNumber().cellPhone(),
            SenderLocalCurrency  : faker.currency().code(),
            AmountSent           : (double) faker.number().numberBetween(50, 500)
        ]
    }

    Map staticRecord() {
        [
            SenderFirstName      : "John",
            SenderLastName       : "Smith",
            SenderStreetAddress  : "555 Main Street",
            SenderCity           : "Arlington",
            SenderState          : "VA",
            SenderCountry        : "USA",
            SenderEmail          : "test@test.com",
            SenderPhoneNumber    : "555-555-5555",
            ReceiverFirstName    : "Jane",
            ReceiverLastName     : "Doe",
            ReceiverStreetAddress: "1234 South Street",
            ReceiverCity         : "Alexandria",
            ReceiverState        : "VA",
            ReceiverCountry      : "USA",
            ReceiverEmail        : "jane.doe@company.com",
            ReceiverPhoneNumber  : "703-867-5309",
            SenderLocalCurrency  : "USD",
            AmountSent           : 500.0d
        ]
    }
}
