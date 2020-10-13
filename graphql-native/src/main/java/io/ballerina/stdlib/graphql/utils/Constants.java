/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.stdlib.graphql.utils;

import org.ballerinalang.jvm.types.BPackage;

/**
 * Constants used in Ballerina GraphQL native library.
 */
public class Constants {
    private Constants() {}

    private static final String ORG_NAME = "ballerina";
    private static final String MODULE_NAME = "graphql";
    private static final String VERSION = "0.1.0";

    static final BPackage PACKAGE_ID = new BPackage(ORG_NAME, MODULE_NAME, VERSION);

    // Type names
    public static final String ERROR_FIELD_NOT_FOUND = "FieldNotFoundError";

    // Operations
    public static final String OPERATION_QUERY = "query";

    public static final String NATIVE_SERVICE_OBJECT = "graphql.service";
}
