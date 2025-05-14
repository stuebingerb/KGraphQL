package com.apurebase.kgraphql

import com.apurebase.kgraphql.schema.execution.Executor
import io.kotest.matchers.shouldBe
import io.ktor.server.application.install
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test

class KtorConfigurationTest : KtorTest() {

    @Test
    fun `default configuration should use Parallel executor`() {
        var checked = false
        testApplication {
            application {
                val config = install(GraphQL) {
                    schema {
                        query("dummy") {
                            resolver { -> "dummy" }
                        }
                    }
                }
                checked = true
                config.schema.configuration.executor shouldBe Executor.Parallel
            }
        }
        checked shouldBe true
    }

    @Test
    fun `update configuration`() {
        var checked = false
        testApplication {
            application {
                val config = install(GraphQL) {
                    executor = Executor.DataLoaderPrepared
                    schema {
                        query("dummy") {
                            resolver { -> "dummy" }
                        }
                    }
                }
                checked = true
                config.schema.configuration.executor shouldBe Executor.DataLoaderPrepared
            }
        }
        checked shouldBe true
    }
}
