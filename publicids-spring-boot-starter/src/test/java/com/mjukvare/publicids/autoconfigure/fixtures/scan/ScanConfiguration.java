package com.mjukvare.publicids.autoconfigure.fixtures.scan;

import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.context.annotation.Configuration;

// Registers this package as an auto-configuration package, like @SpringBootApplication does for an application
@Configuration(proxyBeanMethods = false)
@AutoConfigurationPackage
public class ScanConfiguration {
}
