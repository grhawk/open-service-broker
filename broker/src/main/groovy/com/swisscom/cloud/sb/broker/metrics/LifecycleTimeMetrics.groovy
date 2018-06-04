package com.swisscom.cloud.sb.broker.metrics

import com.swisscom.cloud.sb.broker.model.ServiceInstance
import com.swisscom.cloud.sb.broker.model.repository.LastOperationRepository
import com.swisscom.cloud.sb.broker.model.repository.ServiceInstanceRepository
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.actuate.metrics.Metric
import org.springframework.stereotype.Service

@Service
@CompileStatic
class LifecycleTimeMetrics extends ServiceBrokerMetrics {

    private final String LIFECYCLE_TIME = "lifecycleTime"

    private HashMap<String, Long> totalLifecycleTimePerService
    private HashMap<String, Long> totalNrOfDeleteInstancesPerService

    @Autowired
    LifecycleTimeMetrics(ServiceInstanceRepository serviceInstanceRepository, LastOperationRepository lastOperationRepository) {
        super(serviceInstanceRepository, lastOperationRepository)
    }

    HashMap<String, Long> calculateLifecycleTimePerService(List<ServiceInstance> serviceInstanceList) {
        HashMap<String, Long> total = new HashMap<>()
        HashMap<String, Long> totalLifecycleTime = new HashMap<>()

        serviceInstanceList.findAll { instance -> instance.deleted }.each {
            serviceInstance ->
                def serviceName = getServiceName(serviceInstance)
                total = addOrUpdateEntryOnHashMap(total, serviceName)
                totalLifecycleTime = addUpLifecycleTime(totalLifecycleTime, serviceName, serviceInstance)
        }
        totalNrOfDeleteInstancesPerService = total
        totalLifecycleTimePerService = totalLifecycleTime
        return calculateMeanLifecycleTime(totalNrOfDeleteInstancesPerService)
    }

    HashMap<String, Long> addUpLifecycleTime(HashMap<String, Long> totalLifecycleTimePerServiceName, String serviceName, ServiceInstance serviceInstance) {
        def dateCreated = serviceInstance.dateCreated.getTime()
        def dateDeleted = serviceInstance.dateDeleted.getTime()
        def lifecycleTime = dateDeleted - dateCreated
        if (totalLifecycleTimePerServiceName.get(serviceName) == null) {
            totalLifecycleTimePerServiceName.put(serviceName, lifecycleTime)
        } else {
            def currentValue = totalLifecycleTimePerServiceName.get(serviceName)
            def newValue = currentValue + lifecycleTime
            totalLifecycleTimePerServiceName.put(serviceName, newValue)
        }
        return totalLifecycleTimePerServiceName
    }

    HashMap<String, Long> calculateMeanLifecycleTime(HashMap<String, Long> totalDeletedServiceInstanceMap) {
        HashMap<String, Long> meanLifecycleTimePerService = new HashMap<>()
        totalDeletedServiceInstanceMap.each { service ->
            def serviceName = service.getKey()
            def totalNrOfInstances = service.getValue()
            def totalLifecycleTime = totalLifecycleTimePerService.get(serviceName)
            def meanLifecycleTime = (totalLifecycleTime / totalNrOfInstances).toLong()
            meanLifecycleTimePerService.put(serviceName, meanLifecycleTime)
        }
        return meanLifecycleTimePerService
    }

    @Override
    Collection<Metric<?>> metrics() {
        List<Metric<?>> metrics = new ArrayList<>()
        List<ServiceInstance> serviceInstanceList = serviceInstanceRepository.findAll()

        def lifecycleTimePerService = calculateLifecycleTimePerService(serviceInstanceList)
        metrics = addCountersFromHashMapToMetrics(lifecycleTimePerService, lifecycleTimePerService, metrics, LIFECYCLE_TIME, SERVICE, TOTAL)
        return metrics
    }

    @Override
    boolean considerServiceInstance(ServiceInstance serviceInstance) {
        return false
    }

    @Override
    String tag() {
        return LifecycleTimeMetrics.class.getSimpleName()
    }
}
