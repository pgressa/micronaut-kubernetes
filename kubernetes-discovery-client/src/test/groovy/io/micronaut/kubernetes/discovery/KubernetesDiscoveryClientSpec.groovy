/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.kubernetes.discovery

import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Property
import io.micronaut.context.env.Environment
import io.micronaut.discovery.ServiceInstance
import io.micronaut.kubernetes.client.v1.KubernetesServiceConfiguration
import io.micronaut.kubernetes.test.KubernetesSpecification
import io.micronaut.test.annotation.MicronautTest
import io.reactivex.Flowable
import spock.lang.Ignore

import javax.inject.Inject
import java.util.stream.Collectors
import java.util.stream.Stream

@MicronautTest(environments = [Environment.KUBERNETES])
@Slf4j
@Property(name = "kubernetes.client.namespace", value = "kubernetesdiscoveryclientspec")
class KubernetesDiscoveryClientSpec extends KubernetesSpecification{

    @Inject
    KubernetesDiscoveryClient discoveryClient

    @Inject
    List<KubernetesServiceConfiguration> serviceConfigurations;

    void "it can get service instances"() {
        given:
        List<String> ipAddresses = operations.getEndpoints("example-service", namespace)
                .getSubsets()
                .stream()
                .flatMap(s -> s.addresses.stream())
                .map(a -> a.ip).collect(Collectors.toList())

        when:
        List<ServiceInstance> serviceInstances = Flowable.fromPublisher(discoveryClient.getInstances('example-service')).blockingFirst()

        then:
        serviceInstances.size() == ipAddresses.size()
        serviceInstances.every {
            it.port == 8081
            it.metadata.get('foo', String).get() == 'bar'
        }
        ipAddresses.every { String ip ->
            serviceInstances.find { it.host == ip }
        }
    }

    @Ignore("Needs to be fixed in scope of broader manual service discovery in next pr")
    void "it can get service instances from other namespace"() {
        given:
        List<String> ipAddresses = getIps('micronaut-kubernetes-a')

        when:
        List<ServiceInstance> serviceInstances = Flowable.fromPublisher(discoveryClient.getInstances('example-service-in-other-namespace')).blockingFirst()

        then:
        serviceInstances.size() == ipAddresses.size()
        serviceInstances.every {
            it.port == 8081
            it.metadata.get('foo', String).get() == 'bar'
        }
        ipAddresses.every { String ip ->
            serviceInstances.find { it.host == ip }
        }
    }

    void "it can list all services"() {
        given:
        List<String> allServices = Stream.concat(
                operations.listServices(namespace).items.stream().map(s -> s.metadata.name),
                serviceConfigurations.stream().map(KubernetesServiceConfiguration::getServiceId) as Stream<? extends String>)
                .distinct().collect(Collectors.toList())

        when:
        List<String> serviceIds = Flowable.fromPublisher(discoveryClient.serviceIds).blockingFirst()

        then:
        serviceIds.size() == allServices.size()
        allServices.every { serviceIds.contains it }
    }

    void "service #serviceId is secure"(String serviceId) {
        when:
        List<ServiceInstance> serviceInstances = Flowable.fromPublisher(discoveryClient.getInstances(serviceId)).blockingFirst()

        then:
        serviceInstances.first().secure

        where:
        serviceId << ['secure-service-port-name', 'secure-service-port-number', 'secure-service-labels']
    }

    void "non-secure-service is not secure"() {
        when:
        List<ServiceInstance> serviceInstances = Flowable.fromPublisher(discoveryClient.getInstances('non-secure-service')).blockingFirst()

        then:
        !serviceInstances.first().secure
    }
}
