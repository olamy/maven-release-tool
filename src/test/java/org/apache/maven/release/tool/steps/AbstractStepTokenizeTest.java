/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.release.tool.steps;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AbstractStepTokenizeTest {

    @Test
    void plainWhitespaceSplit() {
        assertEquals(List.of("mvn", "clean", "install"), AbstractStep.tokenize("mvn clean install"));
    }

    @Test
    void collapseRepeatedWhitespace() {
        assertEquals(List.of("a", "b", "c"), AbstractStep.tokenize("  a  b\tc "));
    }

    @Test
    void doubleQuotedValueKeptAsSingleArgWithQuotesStripped() {
        List<String> tokens =
                AbstractStep.tokenize("mvn release:prepare -Darguments=\"-DskipTests -Dspotbugs.skip=true\"");
        assertEquals(
                List.of("mvn", "release:prepare", "-Darguments=-DskipTests -Dspotbugs.skip=true"),
                tokens,
                "quoted -Darguments value must stay as one token, quotes stripped");
    }

    @Test
    void singleQuotedValueKeptAsSingleArg() {
        assertEquals(List.of("mvn", "-Dmessage=hello world"), AbstractStep.tokenize("mvn -Dmessage='hello world'"));
    }

    @Test
    void quotesInsideDifferentQuoteTypeArePreserved() {
        assertEquals(List.of("say", "it's me"), AbstractStep.tokenize("say \"it's me\""));
    }

    @Test
    void fullReleaseCommandWithArgumentsAndProfilesStaysIntact() {
        String cmd = "mvn release:prepare release:perform -DskipTests "
                + "-Darguments=\"-DskipTests -P webtide-harbor,normal,amsa -DdeployAtEnd=true\" "
                + "-DlocalCheckout=true -B -e";
        List<String> tokens = AbstractStep.tokenize(cmd);
        assertEquals(
                List.of(
                        "mvn",
                        "release:prepare",
                        "release:perform",
                        "-DskipTests",
                        "-Darguments=-DskipTests -P webtide-harbor,normal,amsa -DdeployAtEnd=true",
                        "-DlocalCheckout=true",
                        "-B",
                        "-e"),
                tokens);
    }

    @Test
    void emptyInputProducesEmptyList() {
        assertEquals(List.of(), AbstractStep.tokenize(""));
        assertEquals(List.of(), AbstractStep.tokenize("   "));
    }
}
