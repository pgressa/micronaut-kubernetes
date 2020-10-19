/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.kubernetes.test

import groovy.util.logging.Slf4j
import io.fabric8.kubernetes.api.model.IntOrString
import io.fabric8.kubernetes.api.model.ServicePortBuilder
import io.fabric8.kubernetes.api.model.ServiceSpecBuilder
import io.micronaut.core.io.ResourceResolver
import io.micronaut.core.io.scan.ClassPathResourceLoader
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * @author Pavol Gressa
 * @since 2.2
 */
@Slf4j
abstract class KubernetesSpecification extends Specification {

    @Shared
    @AutoCleanup
    KubernetesOperations operations = new KubernetesOperations()

    @Shared
    String namespace

    /**
     * @return spec namespace
     */
    def resolveNamespace(){
        getSpecificationContext().getCurrentSpec().name.toLowerCase()
    }

    /**
     * Setup the fixture in namespace.
     * @param namespace
     * @return
     */
    def setupFixture(String namespace){
        createBaseResources(namespace)
        createExampleServiceDeployment(namespace)
        createExampleClientDeployment(namespace)
        createSecureDeployment(namespace)
    }

    def setupSpec() {
        log.info("Using Kubernetes version: ${operations.client.getVersion()}")

        namespace = resolveNamespace()
        log.info("Configuring default namespace: ${namespace}")
        setupFixture(namespace)
    }

    def cleanupSpec() {
        if (operations.getNamespace(namespace) != null) {
            operations.deleteNamespace(namespace)
        }
    }

    def createBaseResources(String namespace) {
        if(operations.getNamespace(namespace)){
            operations.deleteNamespace(namespace)
        }
        operations.createNamespace(namespace)

        operations.createRole("service-discoverer", namespace)
        operations.createRoleBinding("default-service-discoverer", namespace, "service-discoverer")

        operations.createConfigMapFromFile("game-config-properties", namespace, loadFileFromClasspath("k8s/game.properties"))
        operations.createConfigMapFromFile("game-config-yml", namespace, loadFileFromClasspath("k8s/game.yml"), ["app": "game"])
        operations.createConfigMapFromFile("game-config-json", namespace, loadFileFromClasspath("k8s/game.json"))
        operations.createConfigMap("literal-config", namespace, ["special.how": "very", "special.type": "charm"], ["app": "game"])

        operations.createSecret("test-secret", namespace, ["username": encodeSecret("my-app"), "password": encodeSecret("39528\$vdg7Jb")])
        operations.createSecret("another-secret", namespace, ["secretProperty": encodeSecret("secretValue")], ["app": "game"])
        operations.createSecret("mounted-secret", namespace, ["mountedVolumeKey": encodeSecret("mountedVolumeValue")])
    }

    def createExampleServiceDeployment(String namespace) {
        operations.createDeploymentFromFile(loadFileFromClasspath("k8s/example-service-deployment.yml"), "example-service", namespace)
        operations.createService("example-service", namespace,
                new ServiceSpecBuilder()
                        .withType("LoadBalancer")
                        .withPorts(
                                new ServicePortBuilder()
                                        .withPort(8081)
                                        .withTargetPort(new IntOrString(8081))
                                        .build()
                        )
                        .withSelector(["app": "example-service"])
                        .build(),
                ["foo": "bar"])
    }

    def createExampleClientDeployment(String namespace) {
        operations.createDeploymentFromFile(loadFileFromClasspath("k8s/example-client-deployment.yml"), "example-client", namespace)
        operations.createService("example-client", namespace,
                new ServiceSpecBuilder()
                        .withType("LoadBalancer")
                        .withPorts(
                                new ServicePortBuilder()
                                        .withPort(8082)
                                        .withTargetPort(new IntOrString(8082))
                                        .build()
                        )
                        .withSelector(["app": "example-client"])
                        .build())
    }

    def createSecureDeployment(String namespace) {
        operations.createDeploymentFromFile(loadFileFromClasspath("k8s/secure-deployment.yml"), "secure-deployment", namespace)
        operations.createService("secure-service-port-name", namespace,
                new ServiceSpecBuilder()
                        .withType("NodePort")
                        .withPorts(
                                new ServicePortBuilder()
                                        .withName("https")
                                        .withPort(1234)
                                        .build()
                        )
                        .withSelector(["app": "example-service"])
                        .build())

        operations.createService("secure-service-port-number", namespace,
                new ServiceSpecBuilder()
                        .withType("NodePort")
                        .withPorts(
                                new ServicePortBuilder()
                                        .withPort(443)
                                        .build()
                        )
                        .withSelector(["app": "secure-deployment"])
                        .build())

        operations.createService("secure-service-labels", namespace,
                new ServiceSpecBuilder()
                        .withType("NodePort")
                        .withPorts(
                                new ServicePortBuilder()
                                        .withPort(1234)
                                        .build()
                        )
                        .withSelector(["app": "secure-deployment"])
                        .build(), ["secure": "true"])

        operations.createService("non-secure-service", namespace,
                new ServiceSpecBuilder()
                        .withType("NodePort")
                        .withPorts(
                                new ServicePortBuilder()
                                        .withPort(1234)
                                        .build()
                        )
                        .withSelector(["app": "secure-deployment"])
                        .build())
    }

    private static String encodeSecret(String secret){
        return Base64.encoder.encodeToString(secret.bytes)
    }

    private URL loadFileFromClasspath(String path){
        ClassPathResourceLoader loader = new ResourceResolver().getLoader(ClassPathResourceLoader.class).get();
        Optional<URL> resource = loader.getResource("classpath:${path}")
        return resource.orElseThrow(
                () -> new IllegalArgumentException("File ${path} not found on classpath!"))
    }
}
