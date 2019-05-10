/*
 * Copyright (c) 2018 Swisscom (Switzerland) Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.swisscom.cloud.sb.broker.services.bosh

import com.google.common.base.Optional
import com.swisscom.cloud.sb.broker.model.Parameter
import com.swisscom.cloud.sb.broker.model.ServiceDetail
import com.swisscom.cloud.sb.broker.provisioning.lastoperation.LastOperationJobContext
import com.swisscom.cloud.sb.broker.services.bosh.client.BoshClient
import com.swisscom.cloud.sb.broker.services.bosh.resources.BoshConfigRequest
import com.swisscom.cloud.sb.broker.services.bosh.resources.BoshConfigResponse
import com.swisscom.cloud.sb.broker.services.bosh.resources.GenericConfig
import com.swisscom.cloud.sb.broker.services.bosh.resources.Task
import com.swisscom.cloud.sb.broker.services.common.TemplateConfig
import com.swisscom.cloud.sb.broker.util.Resource
import com.swisscom.cloud.sb.broker.util.RestTemplateBuilder
import com.swisscom.cloud.sb.broker.util.servicedetail.ServiceDetailKey
import com.swisscom.cloud.sb.broker.util.servicedetail.ServiceDetailsHelper
import groovy.transform.CompileStatic
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.util.Assert

import static com.swisscom.cloud.sb.broker.services.bosh.BoshServiceDetailKey.*
import static com.swisscom.cloud.sb.broker.services.bosh.resources.BoshConfigRequest.boshConfigRequest

@Component
@CompileStatic
class BoshFacade {
    private static final Logger LOG = LoggerFactory.getLogger(BoshFacade.class);

    private static final String DEPLOYMENT_PREFIX = 'd-'
    private static final String HOST_NAME_POSTFIX = '.service.consul'
    private static final String PARAM_BOSH_DIRECTOR_UUID = "bosh-director-uuid"
    private static final String PARAM_PREFIX = 'prefix'
    private static final String PARAM_GUID = 'guid'

    private final BoshBasedServiceConfig serviceConfig
    private final BoshClient boshClient
    private final TemplateConfig templateConfig

    private BoshFacade(BoshBasedServiceConfig serviceConfig) {
        this.boshClient = BoshClient.of(serviceConfig, new RestTemplateBuilder())
        this.serviceConfig = serviceConfig
        this.templateConfig = serviceConfig.getTemplateConfig()
    }

    static BoshFacade of(BoshBasedServiceConfig serviceConfig) {
        validateConfig(serviceConfig)
        return new BoshFacade(serviceConfig)
    }

    Collection<ServiceDetail> handleTemplatingAndCreateDeployment(String serviceInstanceGuid,
                                                                  String templateUniqueIdentifier,
                                                                  Set<Parameter> parameters,
                                                                  BoshTemplateCustomizer templateCustomizer) {
        Assert.hasText(serviceInstanceGuid, "Service Instance GUID cannot be empty!")
        Assert.hasText(templateUniqueIdentifier, "Template identifier cannot be empty!")
        Assert.notNull(parameters, "Parameters cannot be null!")
        Assert.notNull(templateCustomizer, "Template customizer cannot be null!")
        BoshTemplate template = BoshTemplate.of(readTemplateContent(templateUniqueIdentifier))

        if (serviceConfig.shuffleAzs) {
            template.shuffleAzs()
        }

        template.replace(PARAM_GUID, serviceInstanceGuid)
        template.replace(PARAM_PREFIX, DEPLOYMENT_PREFIX)
        template.replace(PARAM_BOSH_DIRECTOR_UUID, this.boshClient.fetchBoshInfo().uuid)

        updateTemplateFromDatabaseConfiguration(template, parameters)

        Collection<ServiceDetail> result = templateCustomizer.customizeBoshTemplate(template, serviceInstanceGuid) ?: []

        result.add(ServiceDetail.from(BOSH_DEPLOYMENT_ID, generateDeploymentId(serviceInstanceGuid)))
        result.add(ServiceDetail.from(BOSH_TASK_ID_FOR_DEPLOY, this.boshClient.postDeployment(template.build())))

        generateHostNames(serviceInstanceGuid, template.instanceCount()).each {
            result.add(ServiceDetail.from(ServiceDetailKey.HOST, it))
        }

        return result
    }

    /**
     * Replaces placeholders in BoshTemplate and creates all configs defined in serviceConfig.genericConfigs
     * @return List of all created BoshConfigs, which is empty if there were none or there was an Exception
     */
    List<BoshConfigResponse> handleTemplatingAndCreateConfigs(String serviceInstanceGuid,
                                                              BoshTemplateCustomizer templateCustomizer) {
        Assert.hasText(serviceInstanceGuid, "Service Instance GUID cannot be empty!")
        Assert.notNull(templateCustomizer, "Template customizer cannot be null!")
        List<BoshConfigResponse> result = new ArrayList<>(serviceConfig.getGenericConfigs().size())
        for (GenericConfig config : serviceConfig.getGenericConfigs()) {
            BoshTemplate template = BoshTemplate.of(
                    templateConfig.getTemplateForServiceKey(config.templateName).first())
            template.replace(PARAM_GUID, serviceInstanceGuid)
            templateCustomizer.customizeBoshConfigTemplate(template, config.type, serviceInstanceGuid)
            BoshConfigRequest request = boshConfigRequest()
                    .name(serviceInstanceGuid)
                    .type(config.type)
                    .content(template.build())
                    .build()
            result.add(this.boshClient.postConfig(request))
        }
        return result
    }

    boolean isBoshDeployTaskSuccessful(LastOperationJobContext context) {
        Assert.notNull(context.serviceInstance.details, "details should not be null")
        return isBoshTaskSuccessful(findBoshTaskIdForDeploy(context))
    }

    void deleteBoshConfigs(LastOperationJobContext context) {
        Assert.notNull(context.serviceInstance.guid, "guid should not be null")
        Assert.hasText(context.serviceInstance.guid, "guid should not be empty")
        for (config in serviceConfig.genericConfigs) {
            deleteConfig(context.serviceInstance.guid, config.type)
        }
    }

    Optional<String> deleteBoshDeploymentIfExists(LastOperationJobContext context) {
        Assert.notNull(context.serviceInstance.details, "details should not be null")
        return deleteBoshDeployment(findBoshDeploymentId(context))
    }

    // TODO: Challenge behaviour regarding missing BOSH_TASK_ID_FOR_UNDEPLOY
    /**
     * Returns true when the tasks state is successful or BOSH_TASK_ID_FOR_UNDEPLOY can not be found. Design decision
     * to be permissive towards deletion of resources.
     * @param context with details containing the BOSH_TASK_ID_FOR_UNDEPLOY
     * @return success of the deployment deletion
     */
    boolean isBoshUndeployTaskSuccessful(LastOperationJobContext context) {
        Assert.notNull(context.serviceInstance.details, "details should not be null")
        Optional<String> maybe = ServiceDetailsHelper.from(context.serviceInstance.details).
                findValue(BOSH_TASK_ID_FOR_UNDEPLOY)
        if (maybe.present) {
            return isBoshTaskSuccessful(maybe.get())
        } else {
            return true
        }
    }

    void deleteConfig(String name, String type) {
        this.boshClient.deleteConfig(name, type)
    }

    private static void validateConfig(BoshBasedServiceConfig config) {
        Assert.notNull(config.getPortRange(), "Port range cannot be null!")
        Assert.notNull(config.getBoshManifestFolder(), "Bosh manifest folder cannot be null!")
        Assert.notNull(config.getShuffleAzs(), "Shuffle AZs cannot be null!")
        Assert.notNull(config.getGenericConfigs(), "Bosh generic configs cannot be null!")
        Assert.notNull(config.getTemplateConfig(), "TemplateConfig cannot be null!")
        Assert.notNull(config.getIpRanges(), "IP ranges cannot be null!")
        Assert.notNull(config.getProtocols(), "Protocols cannot be null!")
        Assert.hasText(config.getBoshDirectorBaseUrl(), "Bosh director base url cannot be empty!")
        Assert.hasText(config.getBoshDirectorUsername(), "Bosh director username cannot be empty!")
        Assert.hasText(config.getBoshDirectorPassword(), "Bosh director password cannot be empty!")
        if (config.getTemplateConfig().getServiceTemplates().isEmpty()) {
            Assert.hasText(config.getBoshManifestFolder(),
                           "Bosh manifest folder must be set when service templates is empty!")
        }
    }

    private boolean isBoshTaskSuccessful(String taskId) {
        Assert.hasText(taskId, "Task ID cannot be empty!")
        Task task = this.boshClient.getTask(taskId)
        if (task.state == null) {
            throw new RuntimeException("Unknown bosh task state: ${task.toString()}")
        }
        if ([Task.State.cancelled, Task.State.cancelling, Task.State.errored].contains(task.state)) {
            throw new RuntimeException("Task failed: ${task.toString()}")
        }
        return Task.State.done == task.state
    }

    private static String generateDeploymentId(String serviceInstanceGuid) {
        return DEPLOYMENT_PREFIX + serviceInstanceGuid
    }

    private static String findBoshTaskIdForDeploy(LastOperationJobContext context) {
        Assert.notNull(context.serviceInstance.details, "details should not be null")
        return ServiceDetailsHelper.from(context.serviceInstance.details).
                getValue(BOSH_TASK_ID_FOR_DEPLOY)
    }

    private static List<String> generateHostNames(String guid, int hostCount) {
        (List<String>) (0..<hostCount).inject([]) {
            result, i -> result.add("${guid}-${i}${HOST_NAME_POSTFIX}"); return result
        }
    }

    private String readTemplateContent(String templateIdentifier, String version = "1.0.0") {
        Assert.hasText(templateIdentifier, "empty string is invalid template identifier")
        try {
            String template = templateConfig.getTemplateForServiceKey(templateIdentifier, version)[0]
            return template
        } catch (NoSuchElementException e) {
            try {
                // Fallback method which was used by BOSH deployments
                String fileName = templateIdentifier + (templateIdentifier.endsWith('.yml') ? '' : '.yml')
                File file = new File(serviceConfig.boshManifestFolder, fileName)
                if (file.exists()) {
                    LOG.info("Using template file:${file.absolutePath}")
                    return file.text
                }
                LOG.info("Will try to read file:${fileName} from embedded resources")

                def result = Resource.readTestFileContent(fileName.startsWith('/') ? fileName : ('/' + fileName))
                return result
            } catch (Exception ex) {
                ex.printStackTrace()
                throw new IllegalStateException("No template could be found for templateIdentifier ${templateIdentifier}")
            }
        }
    }

    private static
    void updateTemplateFromDatabaseConfiguration(BoshTemplate template, Set<Parameter> parameters) {
        parameters.each {Parameter p -> template.replace(p.name, p.value)}
    }


    private static String findBoshDeploymentId(LastOperationJobContext context) {
        return ServiceDetailsHelper.from(context.serviceInstance.details).
                findValue(BOSH_DEPLOYMENT_ID).
                or(generateDeploymentId(context.serviceInstance.guid))
    }

    private Optional<String> deleteBoshDeployment(String id) {
        return this.boshClient.deleteDeploymentIfExists(id)
    }

}
