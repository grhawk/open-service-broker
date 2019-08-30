package com.swisscom.cloud.sb.broker.services.bosh.impl

import com.github.tomakehurst.wiremock.common.FileSource
import com.github.tomakehurst.wiremock.core.Options
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.extension.Parameters
import com.github.tomakehurst.wiremock.extension.ResponseTransformer
import com.github.tomakehurst.wiremock.http.Request
import com.github.tomakehurst.wiremock.http.Response
import com.github.tomakehurst.wiremock.http.trafficlistener.ConsoleNotifyingWiremockNetworkTrafficListener
import com.github.tomakehurst.wiremock.junit.WireMockRule
import com.swisscom.cloud.sb.broker.model.Parameter
import com.swisscom.cloud.sb.broker.model.ServiceDetail
import com.swisscom.cloud.sb.broker.services.bosh.BoshBasedServiceConfig
import com.swisscom.cloud.sb.broker.services.bosh.BoshDirectorService
import com.swisscom.cloud.sb.broker.services.bosh.client.BoshCloudConfig
import com.swisscom.cloud.sb.broker.services.bosh.client.BoshDeployment
import com.swisscom.cloud.sb.broker.services.bosh.resources.GenericConfig
import com.swisscom.cloud.sb.broker.services.common.ServiceTemplate
import com.swisscom.cloud.sb.broker.services.common.TemplateConfig
import org.junit.ClassRule
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.*

import java.time.LocalDateTime

import static com.github.tomakehurst.wiremock.client.WireMock.recordSpec
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import static com.google.common.base.Strings.isNullOrEmpty
import static com.swisscom.cloud.sb.broker.services.bosh.client.BoshWebClientTest.BoshInfoContentTransformer.of
import static com.swisscom.cloud.sb.broker.services.bosh.resources.GenericConfig.genericConfig
import static java.util.Collections.emptyMap
import static java.util.Collections.singletonList

@Stepwise
class WebClientBoshDirectorServiceTest extends Specification {
    private static final Logger LOG = LoggerFactory.getLogger(WebClientBoshDirectorServiceTest.class)

    private final static boolean BOSH_MOCKED = Boolean.valueOf(System.getProperty("bosh.mocked"))
    private final static boolean BOSH_REPLIES_PERSISTED = Boolean.valueOf(System.getProperty("bosh.persisted"))
    private static final String BOSH_INFO_TRANSFORMER_NAME = "bosh-info"

    private final static String BOSH_BASE_URL = System.getProperty("bosh.url")
    private final static String BOSH_USERNAME = System.getProperty("bosh.username")
    private final static char[] BOSH_PASSWORD = System.getProperty("bosh.password").toCharArray()

    private static final String UAA_URL = System.getProperty("uaa.url")

    private final String SERVICE_INSTANCE_GUID = '7eff4b56-be53-4925-a4fe-afde1e00111a'

    private final String DEPLOYMENT_ID = "d-${SERVICE_INSTANCE_GUID}"
    private static final String CLOUD_CONFIG_NAME = "bosh-dummy-cloud-config-template"
    private static final String CLOUD_CONFIG_PATH = "/bosh/" + CLOUD_CONFIG_NAME + ".yml"
    private static final String DEPLOYMENT_TEMPLATE_NAME = "bosh-dummy-template"
    private static final String DEPLOYMENT_TEMPLATE_PATH = "/bosh/" + DEPLOYMENT_TEMPLATE_NAME + ".yml"
    private static final Set<Parameter> EMPTY_PARAMETERS = Collections.emptySet()

    @ClassRule
    public static WireMockRule boshWireMock

    @ClassRule
    public static WireMockRule uaaWireMock

    def setupSpec() {
        WireMockConfiguration boshWireMockConfiguration = options().
                withRootDirectory("src/test/resources/bosh").
                port(35555).
                useChunkedTransferEncoding(Options.ChunkedEncodingPolicy.BODY_FILE).
                extensions(of(UAA_URL, "http://localhost:18443", BOSH_INFO_TRANSFORMER_NAME))


        WireMockConfiguration uaaWireMockConfiguration = options().
                withRootDirectory("src/test/resources/uaa").
                useChunkedTransferEncoding(Options.ChunkedEncodingPolicy.BODY_FILE).
                port(18443)

        if (LOG.isTraceEnabled()) {
            boshWireMockConfiguration = boshWireMockConfiguration.networkTrafficListener(
                    new ConsoleNotifyingWiremockNetworkTrafficListener())
            uaaWireMockConfiguration = uaaWireMockConfiguration.networkTrafficListener(
                    new ConsoleNotifyingWiremockNetworkTrafficListener())
        }

        boshWireMock = new WireMockRule(boshWireMockConfiguration)
        uaaWireMock = new WireMockRule(uaaWireMockConfiguration)
        boshWireMock.start()
        uaaWireMock.start()

        if (!BOSH_MOCKED) {
            LOG.info("Start recording with bosh wiremock targeting '${BOSH_BASE_URL}' and uaa wiremock targeting '${UAA_URL}'")
            boshWireMock.startRecording(recordSpec().
                    forTarget(BOSH_BASE_URL).
                    extractBinaryBodiesOver(10240).
                    extractTextBodiesOver(256).
                    makeStubsPersistent(BOSH_REPLIES_PERSISTED).
                    transformers(BOSH_INFO_TRANSFORMER_NAME))

            uaaWireMock.startRecording(recordSpec().
                    forTarget(UAA_URL).
                    extractBinaryBodiesOver(10240).
                    extractTextBodiesOver(256).
                    makeStubsPersistent(BOSH_REPLIES_PERSISTED)
            )
        }
    }
    @Shared
    private String deleteDeploymentTaskId


    BoshBasedServiceConfig config

    BoshDirectorService sut

    def setup() {
        this.config = createBoshFacadeConfig()
        sut = WebClientBoshDirectorService.of(boshWireMock.baseUrl(),
                                              config.getBoshDirectorUsername(),
                                              config.getBoshDirectorPassword().toCharArray(),
                                              config.getGenericConfigs(),
                                              config.getTemplateConfig(),
                                              config.getBoshManifestFolder())

        LOG.info("Testing against {} and with URL '{}' with username '{}' and password '{}'",
                 BOSH_MOCKED ? "mocked bosh" : "live bosh",
                 config.getBoshDirectorBaseUrl(),
                 config.getBoshDirectorUsername(),
                 isNullOrEmpty(config.getBoshDirectorPassword()) ? " NO PASSWORD PROVIDED" :
                 "<CONFIDENTIAL>")
    }

    private BoshBasedServiceConfig createBoshFacadeConfig() {
        return new BoshBasedServiceConfig() {
            @Override
            String getPortRange() {
                return ""
            }

            @Override
            String getBoshManifestFolder() {
                return "."
            }

            @Override
            List<GenericConfig> getGenericConfigs() {
                return singletonList(
                        genericConfig().
                                templateName(CLOUD_CONFIG_NAME).
                                type("cloud").
                                build())
            }

            @Override
            TemplateConfig getTemplateConfig() {
                return TemplateConfig.of([
                        new ServiceTemplate() {
                            String getName() {
                                CLOUD_CONFIG_NAME
                            }

                            String getVersion() {
                                "1.0.0"
                            }

                            List<String> getTemplates() {
                                [new File(this.getClass().getResource(CLOUD_CONFIG_PATH).file).text]
                            }
                        },
                        new ServiceTemplate() {
                            String getName() {
                                DEPLOYMENT_TEMPLATE_NAME
                            }

                            String getVersion() {
                                "1.0.0"
                            }

                            List<String> getTemplates() {
                                [new File(this.getClass().getResource(DEPLOYMENT_TEMPLATE_PATH).file).text]
                            }
                        }])
            }

            @Override
            List<String> getIpRanges() {
                return []
            }

            @Override
            List<String> getProtocols() {
                return []
            }

            @Override
            String getBoshDirectorBaseUrl() {
                return boshWireMock.baseUrl()
            }

            @Override
            String getBoshDirectorUsername() {
                return BOSH_USERNAME
            }

            @Override
            String getBoshDirectorPassword() {
                return BOSH_PASSWORD
            }
        }
    }

    private static List<GenericConfig> genericConfigs() {
        [new GenericConfig() {
            @Override
            String getTemplateName() {
                return "test"
            }

            @Override
            String getType() {
                return "cloud"
            }
        }]
    }

    private BoshBasedServiceConfig createBoshBasedConfig(List<Object> params) {
        LOG.info(params[0].toString())
        new BoshBasedServiceConfig() {

            @Override
            String getPortRange() {
                LOG.info("getPortRange:")
                LOG.info(params[0].toString())
                return params[0]
            }

            @Override
            String getBoshManifestFolder() {
                return params[1]
            }

            @Override
            List<GenericConfig> getGenericConfigs() {
                return params[2]
            }

            @Override
            TemplateConfig getTemplateConfig() {
                return params[3]
            }

            @Override
            List<String> getIpRanges() {
                return params[4]
            }

            @Override
            List<String> getProtocols() {
                return params[5]
            }

            @Override
            String getBoshDirectorBaseUrl() {
                return params[6]
            }

            @Override
            String getBoshDirectorUsername() {
                return params[7]
            }

            @Override
            String getBoshDirectorPassword() {
                return params[8]
            }
        }
    }

    def cleanupSpec() {
        if (!BOSH_MOCKED) {
            boshWireMock.stopRecording()
            uaaWireMock.stopRecording()
        }
        boshWireMock.stop()
        uaaWireMock.stop()
    }

    @Unroll
    @PendingFeature
    def "instantiation should fail if the passed configuration is wrong: #message"() {
        given:
        BoshBasedServiceConfig testConfig = createBoshBasedConfig([portRange, manifestFolder, genericConfigs, templateConfigs, ipRanges, protocols, baseUrl, user, pass])

        when:
        sut = WebClientBoshDirectorService.of(testConfig.getBoshDirectorBaseUrl(),
                                              testConfig.getBoshDirectorUsername(),
                                              testConfig.getBoshDirectorPassword().toCharArray(),
                                              testConfig.getGenericConfigs(),
                                              testConfig.getTemplateConfig(),
                                              testConfig.getBoshManifestFolder())

        then:
        def exception = thrown(IllegalArgumentException.class)
        exception.message == message

        where:
        portRange | manifestFolder | genericConfigs   | templateConfigs | ipRanges | protocols | baseUrl | user | pass | message
        null      | "."            | genericConfigs() | []              | []       | []        | "a"     | "a"  | "a"  | "Port range cannot be null!"
        ""        | null           | genericConfigs() | []              | []       | []        | "a"     | "a"  | "a"  | "Bosh manifest folder cannot be null!"
        ""        | "."            | null             | []              | []       | []        | "a"     | "a"  | "a"  | "Bosh generic configs cannot be null!"
        ""        | "."            | genericConfigs() | null            | []       | []        | "a"     | "a"  | "a"  | "TemplateConfig cannot be null!"
        ""        | "."            | genericConfigs() | []              | null     | []        | "a"     | "a"  | "a"  | "IP ranges cannot be null!"
        ""        | "."            | genericConfigs() | []              | []       | null      | "a"     | "a"  | "a"  | "Protocols cannot be null!"
        ""        | "."            | genericConfigs() | []              | []       | []        | null    | "a"  | "a"  | "Bosh director base url cannot be empty!"
        ""        | "."            | genericConfigs() | []              | []       | []        | ""      | "a"  | "a"  | "Bosh director base url cannot be empty!"
        ""        | "."            | genericConfigs() | []              | []       | []        | "a"     | null | "a"  | "Bosh director username cannot be empty!"
        ""        | "."            | genericConfigs() | []              | []       | []        | "a"     | ""   | "a"  | "Bosh director username cannot be empty!"
        ""        | "."            | genericConfigs() | []              | []       | []        | "a"     | "a"  | null | "Bosh director password cannot be empty!"
        ""        | "."            | genericConfigs() | []              | []       | []        | "a"     | "a"  | ""   | "Bosh director password cannot be empty!"
        ""        | ""             | genericConfigs() | []              | []       | []        | "a"     | "a"  | "a"  | "Bosh manifest folder must be set when service templates is empty!"
    }

    def "should create generic config"() {
        when:
        List<BoshCloudConfig> boshConfigResponses = sut.requestParameterizedBoshConfig(SERVICE_INSTANCE_GUID,
                                                                                       emptyMap())

        then:
        boshConfigResponses != null
        boshConfigResponses.size() == config.getGenericConfigs().size()
        BoshCloudConfig boshConfigResponse = boshConfigResponses.first()
        !boshConfigResponse.getId().isEmpty()
        boshConfigResponse.getType() == "cloud"
        boshConfigResponse.getCurrent()
        boshConfigResponse.getCreatedAt().isBefore(LocalDateTime.now())
        boshConfigResponse.getContent() == new File(this.getClass().
                getResource("/bosh/bosh-dummy-cloud-config-manifest.yml").file).text
    }


    def "should create bosh deployment"() {
        when:
        BoshDeployment result = sut.requestParameterizedBoshDeployment(SERVICE_INSTANCE_GUID,
                                                                       DEPLOYMENT_TEMPLATE_NAME,
                                                                       EMPTY_PARAMETERS)
        then:
        result != null
        !result.taskId.isEmpty()
    }


    def "should delete bosh deployment"() {
        when:
        BoshDeployment result = sut.deleteBoshDeploymentIfExists(DEPLOYMENT_ID)

        then:
        result != null
        result != BoshDeployment.EMPTY
    }


    def "should delete generic config"() {
        when:
        BoshCloudConfig result = sut.deleteBoshConfig(SERVICE_INSTANCE_GUID)

        then:
        result != null
        result != BoshCloudConfig.EMPTY
    }

    def "should return empty BoshCloudConfig if there is no config deleted"() {
        when:
        BoshCloudConfig result = sut.deleteBoshConfig(SERVICE_INSTANCE_GUID)

        then:
        result != null
        result == BoshCloudConfig.EMPTY
    }

    @Unroll
    def "should fail creating config because '#message'"() {
        when:
        List<BoshCloudConfig> boshConfigResponses = sut.requestParameterizedBoshConfig(guid, emptyMap())

        then:
        boshConfigResponses == null
        def exception = thrown(IllegalArgumentException.class)
        exception.message == message

        where:
        guid | message
        null | "Can't request a BoshCloudConfig with null name"
        ""   | "Can't request a BoshCloudConfig with null name"
        " "  | "Can't request a BoshCloudConfig with null name"
    }

    @Unroll
    def "should fail to deploy because '#message'"() {
        when:
        Collection<ServiceDetail> details = sut.requestParameterizedBoshDeployment(guid,
                                                                                   templateIdentifier,
                                                                                   parameters)

        then:
        details == null
        def exception = thrown(IllegalArgumentException.class)
        exception.message == message

        where:
        guid   | templateIdentifier       | parameters       | message
        null   | DEPLOYMENT_TEMPLATE_NAME | EMPTY_PARAMETERS | "Can't request a BoshDeployment with null serviceInstanceGuid"
        ""     | DEPLOYMENT_TEMPLATE_NAME | EMPTY_PARAMETERS | "Can't request a BoshDeployment with null serviceInstanceGuid"
        "guid" | null                     | EMPTY_PARAMETERS | "No template could be found for templateIdentifier 'null'!"
        "guid" | ""                       | EMPTY_PARAMETERS | "No template could be found for templateIdentifier ''!"
        "guid" | DEPLOYMENT_TEMPLATE_NAME | null             | "Can't request a BoshDeployment with null template parameters"
        "guid" | "DOES_NOT_EXIST"         | EMPTY_PARAMETERS | "No template could be found for templateIdentifier 'DOES_NOT_EXIST'!"
    }

    static class BoshInfoContentTransformer extends ResponseTransformer {
        private static final Logger LOG = LoggerFactory.getLogger(BoshInfoContentTransformer.class);

        private final String originalUaaUrl;
        private final String replacementUaaUrl;
        private final String name;

        private BoshInfoContentTransformer(String originalUaaUrl, String replacementUaaUrl, String name) {
            this.originalUaaUrl = originalUaaUrl;
            this.replacementUaaUrl = replacementUaaUrl;
            this.name = name;
        }

        static BoshInfoContentTransformer of(String originalUaaUrl, String replacementUaaUrl, String name) {
            return new BoshInfoContentTransformer(originalUaaUrl, replacementUaaUrl, name);
        }

        @Override
        public Response transform(Request request,
                                  Response responseDefinition,
                                  FileSource files,
                                  Parameters parameters) {
            if (request.getAbsoluteUrl().endsWith("/info")) {
                LOG.debug("transforming for url: " + request.getAbsoluteUrl());
                String body = new String(responseDefinition.getBody());
                return Response.Builder.like(responseDefinition)
                                       .but()
                                       .body(body.replaceAll('"' + originalUaaUrl + '"',
                                                             '"' + replacementUaaUrl + '"'))
                                       .build();
            }
            return responseDefinition;
        }

        @Override
        public String getName() {
            return name;
        }
    }

}
