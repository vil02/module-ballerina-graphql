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

package io.ballerina.stdlib.graphql.runtime.engine;

import io.ballerina.runtime.api.Environment;
import io.ballerina.runtime.api.Future;
import io.ballerina.runtime.api.Parameter;
import io.ballerina.runtime.api.creators.ValueCreator;
import io.ballerina.runtime.api.types.RemoteMethodType;
import io.ballerina.runtime.api.types.ResourceMethodType;
import io.ballerina.runtime.api.types.ServiceType;
import io.ballerina.runtime.api.types.Type;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.values.BArray;
import io.ballerina.runtime.api.values.BError;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BObject;
import io.ballerina.runtime.api.values.BString;
import io.ballerina.runtime.api.values.BValue;
import io.ballerina.stdlib.graphql.runtime.schema.FieldFinder;
import io.ballerina.stdlib.graphql.runtime.schema.SchemaRecordGenerator;
import io.ballerina.stdlib.graphql.runtime.schema.TypeFinder;
import io.ballerina.stdlib.graphql.runtime.schema.types.Schema;

import java.util.ArrayList;
import java.util.List;

import static io.ballerina.stdlib.graphql.runtime.engine.EngineUtils.ARGUMENTS_FIELD;
import static io.ballerina.stdlib.graphql.runtime.engine.EngineUtils.DATA_FIELD;
import static io.ballerina.stdlib.graphql.runtime.engine.EngineUtils.ENGINE_FIELD;
import static io.ballerina.stdlib.graphql.runtime.engine.EngineUtils.GRAPHQL_SERVICE_OBJECT;
import static io.ballerina.stdlib.graphql.runtime.engine.EngineUtils.MUTATION;
import static io.ballerina.stdlib.graphql.runtime.engine.EngineUtils.NAME_FIELD;
import static io.ballerina.stdlib.graphql.runtime.engine.EngineUtils.QUERY;
import static io.ballerina.stdlib.graphql.runtime.engine.EngineUtils.SCHEMA_RECORD;
import static io.ballerina.stdlib.graphql.runtime.engine.EngineUtils.VALUE_FIELD;
import static io.ballerina.stdlib.graphql.runtime.engine.EngineUtils.isPathsMatching;
import static io.ballerina.stdlib.graphql.runtime.engine.ResponseGenerator.getDataFromService;
import static io.ballerina.stdlib.graphql.runtime.utils.Utils.REMOTE_STRAND_METADATA;
import static io.ballerina.stdlib.graphql.runtime.utils.Utils.RESOURCE_STRAND_METADATA;

/**
 * This handles Ballerina GraphQL Engine.
 */
public class Engine {
    private Engine() {
    }

    public static Object createSchema(BObject service) {
        try {
            ServiceType serviceType = (ServiceType) service.getType();
            TypeFinder typeFinder = new TypeFinder(serviceType);
            typeFinder.populateTypes();

            FieldFinder fieldFinder = new FieldFinder(typeFinder.getTypeMap());
            fieldFinder.populateFields();
            Schema schema = new Schema(fieldFinder.getTypeMap());

            SchemaRecordGenerator schemaRecordGenerator = new SchemaRecordGenerator(schema);
            return schemaRecordGenerator.getSchemaRecord();
        } catch (BError e) {
            return e;
        }
    }

    public static void attachServiceToEngine(BObject service, BObject engine) {
        engine.addNativeData(GRAPHQL_SERVICE_OBJECT, service);
    }

    public static void executeQuery(Environment environment, BObject visitor, BObject node) {
        BObject engine = visitor.getObjectValue(ENGINE_FIELD);
        BMap<BString, Object> data = (BMap<BString, Object>) visitor.getMapValue(DATA_FIELD);
        Future future = environment.markAsync();
        BObject service = (BObject) engine.getNativeData(GRAPHQL_SERVICE_OBJECT);
        List<String> paths = new ArrayList<>();
        paths.add(node.getStringValue(NAME_FIELD).getValue());
        List<Object> pathSegments = new ArrayList<>();
        pathSegments.add(StringUtils.fromString(node.getStringValue(NAME_FIELD).getValue()));
        CallbackHandler callbackHandler = new CallbackHandler(future);
        ExecutionContext executionContext = new ExecutionContext(environment, visitor, callbackHandler, QUERY);
        executeResourceMethod(executionContext, service, node, data, paths, pathSegments);
    }

    public static void executeMutation(Environment environment, BObject visitor, BObject node) {
        BObject engine = visitor.getObjectValue(ENGINE_FIELD);
        BMap<BString, Object> data = visitor.getMapValue(DATA_FIELD);
        Future future = environment.markAsync();
        BObject service = (BObject) engine.getNativeData(GRAPHQL_SERVICE_OBJECT);
        String fieldName = node.getStringValue(NAME_FIELD).getValue();
        CallbackHandler callbackHandler = new CallbackHandler(future);
        List<Object> pathSegments = new ArrayList<>();
        pathSegments.add(fieldName);
        ExecutionContext executionContext = new ExecutionContext(environment, visitor, callbackHandler, MUTATION);
        executeRemoteMethod(executionContext, service, node, data, pathSegments);
    }

    public static void executeIntrospection(Environment environment, BObject visitor, BObject node, BValue result) {
        Future future = environment.markAsync();
        BMap<BString, Object> data = visitor.getMapValue(DATA_FIELD);
        List<Object> pathSegments = new ArrayList<>();
        pathSegments.add(StringUtils.fromString(node.getStringValue(NAME_FIELD).getValue()));
        CallbackHandler callbackHandler = new CallbackHandler(future);
        ExecutionContext executionContext = new ExecutionContext(environment, visitor, callbackHandler, SCHEMA_RECORD);
        ResourceCallback resourceCallback = new ResourceCallback(executionContext, node, data, pathSegments);
        callbackHandler.addCallback(resourceCallback);
        resourceCallback.notifySuccess(result);
    }

    static void executeResourceMethod(ExecutionContext executionContext, BObject service, BObject node,
                                      BMap<BString, Object> data, List<String> paths, List<Object> pathSegments) {
        ServiceType serviceType = (ServiceType) service.getType();
        for (ResourceMethodType resourceMethod : serviceType.getResourceMethods()) {
            if (isPathsMatching(resourceMethod, paths)) {
                getExecutionResult(executionContext, service, node, resourceMethod, data, pathSegments);
                return;
            }
        }
        // The resource not found. This should be a either a resource with hierarchical paths or introspection query
        getDataFromService(executionContext, service, node, data, paths, pathSegments);
    }

    static void executeRemoteMethod(ExecutionContext executionContext, BObject service, BObject node,
                                    BMap<BString, Object> data, List<Object> pathSegments) {
        ServiceType serviceType = (ServiceType) service.getType();
        BString fieldName = node.getStringValue(NAME_FIELD);
        for (RemoteMethodType remoteMethod : serviceType.getRemoteMethods()) {
            if (remoteMethod.getName().equals(fieldName.getValue())) {
                getExecutionResult(executionContext, service, node, remoteMethod, data, pathSegments);
                return;
            }
        }
    }

    private static void getExecutionResult(ExecutionContext executionContext, BObject service, BObject node,
                                           ResourceMethodType resourceMethod, BMap<BString, Object> data,
                                           List<Object> pathSegments) {
        BMap<BString, Object> arguments = getArgumentsFromField(node);
        Object[] args = getArgsForMethod(resourceMethod.getParameters(), resourceMethod.getParameterTypes(), arguments);
        ResourceCallback callback =
                new ResourceCallback(executionContext, node, data, pathSegments);
        executionContext.getCallbackHandler().addCallback(callback);
        executionContext.getEnvironment().getRuntime().invokeMethodAsync(service, resourceMethod.getName(), null,
                                                                         RESOURCE_STRAND_METADATA, callback, args);
    }

    private static void getExecutionResult(ExecutionContext executionContext, BObject service, BObject node,
                                           RemoteMethodType remoteMethod, BMap<BString, Object> data,
                                           List<Object> pathSegments) {
        BMap<BString, Object> arguments = getArgumentsFromField(node);
        Object[] args = getArgsForMethod(remoteMethod.getParameters(), remoteMethod.getParameterTypes(), arguments);
        ResourceCallback callback = new ResourceCallback(executionContext, node, data, pathSegments);
        executionContext.getCallbackHandler().addCallback(callback);
        executionContext.getEnvironment().getRuntime().invokeMethodAsync(service, remoteMethod.getName(), null,
                                                                         REMOTE_STRAND_METADATA, callback, args);
    }

    public static BMap<BString, Object> getArgumentsFromField(BObject node) {
        BArray argumentArray = node.getArrayValue(ARGUMENTS_FIELD);
        BMap<BString, Object> argumentsMap = ValueCreator.createMapValue();
        for (int i = 0; i < argumentArray.size(); i++) {
            BObject argumentNode = (BObject) argumentArray.get(i);
            BMap<BString, Object> argNameRecord = (BMap<BString, Object>) argumentNode.getMapValue(NAME_FIELD);
            BMap<BString, Object> argValueRecord = (BMap<BString, Object>) argumentNode.getMapValue(VALUE_FIELD);
            BString argName = argNameRecord.getStringValue(VALUE_FIELD);
            Object argValue = argValueRecord.get(VALUE_FIELD);
            argumentsMap.put(argName, argValue);
        }
        return argumentsMap;
    }

    private static Object[] getArgsForMethod(Parameter[] parameters, Type[] types, BMap<BString, Object> arguments) {
        Object[] result = new Object[parameters.length * 2];
        for (int i = 0, j = 0; i < parameters.length; i += 1, j += 2) {
            if (arguments.get(StringUtils.fromString(parameters[i].name)) == null) {
                result[j] = types[i].getZeroValue();
                result[j + 1] = false;
            } else {
                result[j] = arguments.get(StringUtils.fromString(parameters[i].name));
                result[j + 1] = true;
            }
        }
        return result;
    }
}
