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
package org.apache.maven.release.tool.model;

public record StepResult(boolean succeeded, String message, Action suggestedAction) {

    public enum Action {
        CONTINUE,
        RETRY,
        PAUSE,
        ABORT
    }

    public static StepResult ok() {
        return new StepResult(true, null, Action.CONTINUE);
    }

    public static StepResult ok(String message) {
        return new StepResult(true, message, Action.CONTINUE);
    }

    public static StepResult failure(String message) {
        return new StepResult(false, message, Action.RETRY);
    }

    public static StepResult abort(String message) {
        return new StepResult(false, message, Action.ABORT);
    }

    public static StepResult pause(String message) {
        return new StepResult(true, message, Action.PAUSE);
    }
}
