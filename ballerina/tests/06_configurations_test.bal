// Copyright (c) 2023, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
//
// WSO2 Inc. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

import ballerina/test;
import graphql.parser;

@test:Config {
    groups: ["configs", "validation"],
    dataProvider: dataProviderQueryDepthConfigurations
}
function testQueryDepthConfigurations(int maxQueryDepth, string documentFileName) returns error? {
    string document = check getGraphQLDocumentFromFile(string `${documentFileName}.graphql`);
    parser:DocumentNode documentNode = check getDocumentNode(document);
    QueryDepthValidatorVisitor validator = new (maxQueryDepth, new);
    documentNode.accept(validator);
    json expectedPayload = check getJsonContentFromFile(string `${documentFileName}.json`);
    test:assertEquals(validator.getErrors(), expectedPayload);
}

function dataProviderQueryDepthConfigurations() returns map<[int, string]> {
    map<[int, string]> dataSet = {
        "1": [2, "query_exceeding_max_depth"],
        "2": [2, "fragment_query_exceeding_max_depth"],
        "3": [2, "query_with_named_operation_exceeding_max_depth"]
        
    };
    return dataSet;
}

@test:Config {
    groups: ["configs", "validation", "introspection"],
    dataProvider: dataProviderIntrospectionConfigurations
}
function testIntrospectionConfigurations(boolean introspection, string documentFileName) returns error? {
    string document = check getGraphQLDocumentFromFile(string `${documentFileName}.graphql`);
    parser:DocumentNode documentNode = check getDocumentNode(document);
    NodeModifierContext nodeModifierContext = new;
    FragmentValidatorVisitor fragmentValidator = new FragmentValidatorVisitor(documentNode.getFragments(), nodeModifierContext);
    documentNode.accept(fragmentValidator);
    IntrospectionValidatorVisitor validator = new (introspection, nodeModifierContext);
    documentNode.accept(validator);
    json|error expectedPayload = getJsonContentFromFile(string `${documentFileName}.json`);
    test:assertEquals(validator.getErrors(), expectedPayload is error ? () : expectedPayload);
}

function dataProviderIntrospectionConfigurations() returns map<[boolean, string]> {
    map<[boolean, string]> dataSet = {
        "1": [false, "schema_introspection_disable"],
        "2": [false, "type_introspection_disable"],
        "3": [false, "introspection_disable"],
        "4": [false, "introspection_disable_with_typename_introspection"],
        "5": [false, "introspection_disable_with_mutation"],
        "6": [false, "introspection_disable_config_with_fragments"]
        
    };
    return dataSet;
}
