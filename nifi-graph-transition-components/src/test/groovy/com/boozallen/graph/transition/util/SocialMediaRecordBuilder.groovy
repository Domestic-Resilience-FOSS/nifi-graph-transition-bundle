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

class SocialMediaRecordBuilder {
    Faker faker
    def accounts = []
    def types = ["Chat", "SentFile", "SharedArticle"]
    def services = ["BigCorp", "MicroblogInc", "Federated"]

    SocialMediaRecordBuilder(int accountCeiling) {
        faker = new Faker()
        def random = new Random()
        1.upto(accountCeiling) {
            def email = faker.internet().emailAddress()
            def account = [
                Username: email[0..email.indexOf("@")],
                Service : services[random.nextInt(services.size())]
            ]
            if (random.nextBoolean()) {
                account['IdentityVerified'] = true
            }
            accounts << account
        }
    }

    List<Map<String, Object>> buildRecordSet(int bound, boolean skipCount) {
        def alreadyDone = []
        def random = new Random()
        def retVal = []
        1.upto(bound) {
            def left = null
            def right = null
            while ((left == null && right == null) || left == right) {
                left = accounts[random.nextInt(accounts.size())]
                right = accounts[random.nextInt(accounts.size())]

                def adKey = "${left['Username']}::${right['Username']}"
                if (adKey in alreadyDone) {
                    left = null
                    right = null
                }
            }

            def update = { l, r, type ->
                def struct = [
                    LeftUsername   : l['Username'],
                    LeftService    : l['Service'],
                    RightService   : r['Service'],
                    RightUsername  : r['Username'],
                    InteractionType: type
                ]

                if (!skipCount) {
                    struct['InteractionCount'] = random.nextInt(250)
                }

                struct
            }

            types.each { type ->
                retVal << update(left, right, type)
                retVal << update(right, left, type)
            }
        }

        retVal
    }

    List<Map<String, Object>> buildRecordSet(int bound) {
        buildRecordSet(bound, false)
    }
}
