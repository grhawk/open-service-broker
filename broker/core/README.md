# Open service broker core

All the [Spring components](https://spring.io/projects/spring-framework) 
(entities, repositories, services, controllers, services) needed in any
 Spring boot application that provide an implementation of the 
 [Open service broker API](https://github.com/openservicebrokerapi/servicebroker/blob/v2.11/spec.md).
  
 
## Usage
In your Spring boot application `build.gradle`, include the following dependency:
```$groovy
dependencies {
    compile 'com.swisscom.cloud.sb:broker-core:6.1.1-SNAPSHOT'
```

See [broker Spring boot application](https://github.com/swisscom/open-service-broker/tree/develop/broker) for
an example of usage.

## Configuration 
In `src/main/resources/log4j*` you have an example of logging configuration. For an example of needed 
Spring configuration see [broker Spring boot application](https://github.com/swisscom/open-service-broker/tree/develop/broker).

## Testing

### Test BOSH
For running the `BoshFacadeTest` or `BoshClientTest` against your bosh, the
[dummy bosh-release](https://github.com/pivotal-cf-experimental/dummy-boshrelease) needs to be uploaded.
You can set your bosh credentials in `~/.gradle/gradle.properties`:
```
boshUrl=<BASEURL OF YOUR BOSH>
uaaUrl=<BASEURL OF UAA USED FOR AUTHENTICATION>
boshUsername=<YOUR BOSH USERNAME>
boshPassword=<YOUR BOSH PASSWORD>
boshMocked=false
```

You can follow [this guide](https://bosh.io/docs/quick-start/) to setup a local bosh-lite with VirtualBox. In short:
```bash
mkdir -p ~/bosh-env/virtualbox
cd ~/bosh-env/virtualbox
git clone https://github.com/cloudfoundry/bosh-deployment.git
./bosh-deployment/virtualbox/create-env.sh
source .envrc
bosh -e vbox -n update-cloud-config bosh-deployment/warden/cloud-config.yml
bosh upload-release https://github.com/pivotal-cf-experimental/dummy-boshrelease/releases/download/v2/dummy-2.tgz
bosh upload-stemcell --sha1 632b2fd291daa6f597ff6697139db22fb554204c https://bosh.io/d/stemcells/bosh-warden-boshlite-ubuntu-xenial-go_agent?v=315.13
```

## Build
Run:
```bash
$ ./gradlew build
```

The resulting jar will be at `build/libs/broker-core-6.1.1-SNAPSHOT.jar`