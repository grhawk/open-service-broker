package com.swisscom.cloud.sb.broker.services.bosh;

import com.swisscom.cloud.sb.broker.model.Parameter;
import com.swisscom.cloud.sb.broker.services.bosh.client.BoshCloudConfig;
import com.swisscom.cloud.sb.broker.services.bosh.client.BoshDeployment;
import com.swisscom.cloud.sb.broker.services.bosh.client.BoshDirectorTask;
import com.swisscom.cloud.sb.broker.services.bosh.client.BoshDirectorTask.Event;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An interface to all the deployments related functions that BOSH provides.
 * <p>
 * It will speak with the BOSH <em>Director</em>: the main BOSH component that coordinates the Agents and responds to
 * user requests and system events. The Director is the orchestrator of deployments. The <em>Director</em> will perform
 * its duties using {@link BoshDirectorTask} that you can access for information and logs.
 * </p>
 *
 * @see <a href='https://bosh.io/docs/manifest-v2/'>BOSH Documentation for Deployment Config</a>
 * @see <a href='https://bosh.io/docs/cloud-config/'>BOSH Documentation for Director Cloud Config</a>
 * @see <a href='https://bosh.io/docs/configs/'>BOSH Documentation for Generic Configs</a>
 */
public interface BoshDirectorService {

    /**
     * Replaces placeholders in BoshTemplate and creates all configs (like networks or vm types) defined in {@link
     * BoshBasedServiceConfig#getGenericConfigs()}.
     *
     * @param boshCloudConfigName the {@link BoshCloudConfig#getName()} to use
     * @param parameters          the template parameters needed for generating the {@link BoshCloudConfig}
     * @return List of all created BoshConfigs, which is empty if there were none defined or there was an Exception
     * @see <a href='https://bosh.io/docs/configs'>Generic Configs</a>
     */
    List<BoshCloudConfig> requestParameterizedBoshConfig(String boshCloudConfigName, Map<String, String> parameters);

    /**
     * Deletes all configured {@link BoshBasedServiceConfig#getGenericConfigs()} and throws Exception if the deletion
     * failed.
     *
     * @param name name of the {@link BoshCloudConfig} to be deleted
     * @return the {@link BoshCloudConfig} deleted
     */
    BoshCloudConfig deleteBoshConfig(String name);

    /**
     * Replaces the placeholders in the deployment manifest template and request needed {@link BoshDeployment} to BOSH.
     *
     * @param serviceInstanceGuid Id of the service instance to be deployed
     * @param templateId          Name of the service template to be used from {@link com.swisscom.cloud.sb.broker.services.common.TemplateConfig#getServiceTemplates()}
     * @param parameters          Parameters defined in service plan {@link com.swisscom.cloud.sb.broker.servicedefinition.dto.PlanDto#getParameters()}
     * @return a {@link BoshDeployment}
     * @see <a href='https://bosh.io/docs/deploying/'>Bosh Deploying</a>
     */
    BoshDeployment requestParameterizedBoshDeployment(String serviceInstanceGuid,
                                                      String templateId,
                                                      Set<Parameter> parameters);

    /**
     * Cancels certain {@link BoshDeployment}
     *
     * @param toCancel the {@link BoshDeployment} to be cancelled
     * @return the {@link BoshDeployment} which was requested to be cancelled
     */
    BoshDeployment cancelBoshDeployment(BoshDeployment toCancel);

    /**
     * Delete certain {@link BoshDeployment} identified by its {@link BoshDeployment#getName()} if still exists.
     *
     * @param name the {@link BoshDeployment#getName()} we want to delete
     * @return the {@link BoshDeployment} to delete
     */
    BoshDeployment deleteBoshDeploymentIfExists(String name);

    /**
     * Returns certain {@link BoshDirectorTask} with all the {@link Event} initialized to their current state
     *
     * @param id the identification of certain {@link BoshDirectorTask}
     * @return a {@link BoshDirectorTask} with all its {@link BoshDirectorTask#getEvents()} populated
     */
    BoshDirectorTask getBoshDirectorTask(String id);

    /**
     * Returns all {@link BoshDirectorTask} associated to certain {@link BoshDeployment}.
     * <p>The collections of {@link Event} related to the tasks might be not initialized for performance reasons.</p>
     *
     * @param boshDeployment the {@link BoshDeployment} we want to get all associated {@link BoshDirectorTask}
     * @return all the {@link BoshDirectorTask} ordered from older to more recent one.
     */
    Collection<BoshDirectorTask> getBoshDirectorTask(BoshDeployment boshDeployment);
}
