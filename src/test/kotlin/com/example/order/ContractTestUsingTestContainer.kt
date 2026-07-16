package com.example.order

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.slf4j.LoggerFactory
import org.springframework.boot.test.context.SpringBootTest
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.ComposeContainer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import org.testcontainers.containers.wait.strategy.Wait
import java.io.File
import java.time.Duration

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContractTestUsingTestContainer {
    companion object {
        private val logger = LoggerFactory.getLogger(ContractTestUsingTestContainer::class.java)
        private val DOCKER_COMPOSE_FILE = File("docker-compose.yaml")
        private const val REGISTER_SCHEMAS_SERVICE = "register-schemas"
        private const val SCHEMA_REGISTERED_REGEX = ".*(?i)schemas registered.*"
        private const val JSON_SCHEMA_APP_NETWORK = "json-schema-app-network"

    }

    private val schemaRegistry = schemaRegistry()

    private fun schemaRegistry(): ComposeContainer {
        return ComposeContainer(DOCKER_COMPOSE_FILE)
            .withLocalCompose(true).waitingFor(
                REGISTER_SCHEMAS_SERVICE,
                LogMessageWaitStrategy()
                    .withRegEx(SCHEMA_REGISTERED_REGEX)
                    .withStartupTimeout(Duration.ofSeconds(60))
            )
    }

    @BeforeAll
    fun setup() {
        schemaRegistry.start()
    }

    @AfterAll
    fun tearDown() {
        schemaRegistry.stop()
    }

    private fun testContainer(): GenericContainer<*> {
        return GenericContainer("specmatic/enterprise")
            .withCommand(
                "test"
            )
            .withFileSystemBind(
                "./api-specs",
                "/usr/src/app/api-specs",
                BindMode.READ_ONLY
            )
            .withFileSystemBind(
                "./specmatic.yaml",
                "/usr/src/app/specmatic.yaml",
                BindMode.READ_ONLY,
            )
            .withFileSystemBind(
                "./build/reports/specmatic",
                "/usr/src/app/build/reports/specmatic",
                BindMode.READ_WRITE,
            )
            .withEnv("SCHEMA_REGISTRY_URL", "http://schema-registry:8085")
            .withEnv("KAFKA_BROKER", "broker:9093")
            .waitingFor(
                Wait.forLogMessage(".*Tests run:.*", 1)
                    .withStartupTimeout(Duration.ofMinutes(3))
            )
            .withNetworkMode(JSON_SCHEMA_APP_NETWORK)
            .withLogConsumer(Slf4jLogConsumer(logger))
    }

    @Test
    fun specmaticContractTest() {
        val testContainer = testContainer()
        try {
            testContainer.start()
            val hasSucceeded = testContainer.logs.contains("Failures: 0, WIP: 0, Errors: 0")
            assertThat(hasSucceeded).withFailMessage("Contract test failed!").isTrue()
        } finally {
            testContainer.stop()
        }
    }
}
