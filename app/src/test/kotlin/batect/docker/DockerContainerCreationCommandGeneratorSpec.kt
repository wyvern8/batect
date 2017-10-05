/*
   Copyright 2017 Charles Korn.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package batect.docker

import batect.config.Container
import batect.config.PortMapping
import batect.config.VolumeMount
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.withMessage
import batect.ui.ConsoleInfo
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.throws
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object DockerContainerCreationCommandGeneratorSpec : Spek({
    describe("a Docker container creation command generator") {
        val generator = DockerContainerCreationCommandGenerator()

        given("a simple container definition, a built image and an explicit command to run") {
            val container = Container("the-container", imageSourceDoesNotMatter())
            val command = "doStuff"
            val image = DockerImage("the-image")
            val network = DockerNetwork("the-network")
            val consoleInfo = mock<ConsoleInfo>()

            on("generating the command") {
                val commandLine = generator.createCommandLine(container, command, emptyMap(), image, network, consoleInfo)

                it("generates the correct command line, taking the command from the task") {
                    assertThat(commandLine, equalTo(listOf(
                        "docker", "create",
                        "-it",
                        "--network", network.id,
                        "--hostname", container.name,
                        "--network-alias", container.name,
                        image.id,
                        command).asIterable()))
                }
            }
        }

        given("a simple container definition, a built image and no explicit command to run") {
            val container = Container("the-container", imageSourceDoesNotMatter())
            val command = null
            val image = DockerImage("the-image")
            val network = DockerNetwork("the-network")
            val consoleInfo = mock<ConsoleInfo>()

            on("generating the command") {
                val commandLine = generator.createCommandLine(container, command, emptyMap(), image, network, consoleInfo)

                it("generates the correct command line, taking the command from the container") {
                    assertThat(commandLine, equalTo(listOf(
                        "docker", "create",
                        "-it",
                        "--network", network.id,
                        "--hostname", container.name,
                        "--network-alias", container.name,
                        image.id).asIterable()))
                }
            }
        }

        given("a container with some additional environment variables") {
            val container = Container("the-container", imageSourceDoesNotMatter())
            val command = null
            val additionalEnvironmentVariables = mapOf("SOME_VAR" to "some value")
            val image = DockerImage("the-image")
            val network = DockerNetwork("the-network")
            val consoleInfo = mock<ConsoleInfo>()

            on("generating the command") {
                val commandLine = generator.createCommandLine(container, command, additionalEnvironmentVariables, image, network, consoleInfo)

                it("generates the correct command line, taking the command from the container") {
                    assertThat(commandLine, equalTo(listOf(
                        "docker", "create",
                        "-it",
                        "--network", network.id,
                        "--hostname", container.name,
                        "--network-alias", container.name,
                        "--env", "SOME_VAR=some value",
                        image.id).asIterable()))
                }
            }
        }

        given("a container with some additional environment variables that override values for enviroment variables for the container") {
            val container = Container("the-container", imageSourceDoesNotMatter(), environment = mapOf("SOME_VAR" to "original value"))
            val command = null
            val additionalEnvironmentVariables = mapOf("SOME_VAR" to "some value")
            val image = DockerImage("the-image")
            val network = DockerNetwork("the-network")
            val consoleInfo = mock<ConsoleInfo>()

            on("generating the command") {
                val commandLine = generator.createCommandLine(container, command, additionalEnvironmentVariables, image, network, consoleInfo)

                it("generates the correct command line, taking the command from the container") {
                    assertThat(commandLine, equalTo(listOf(
                        "docker", "create",
                        "-it",
                        "--network", network.id,
                        "--hostname", container.name,
                        "--network-alias", container.name,
                        "--env", "SOME_VAR=some value",
                        image.id).asIterable()))
                }
            }
        }

        given("the host console does not have the TERM environment variable set") {
            val container = Container("the-container", imageSourceDoesNotMatter())
            val command = null
            val image = DockerImage("the-image")
            val network = DockerNetwork("the-network")
            val consoleInfo = mock<ConsoleInfo>()

            on("generating the command") {
                val commandLine = generator.createCommandLine(container, command, emptyMap(), image, network, consoleInfo)

                it("generates the correct command line, not setting the TERM environment variable of the container") {
                    assertThat(commandLine, equalTo(listOf(
                        "docker", "create",
                        "-it",
                        "--network", network.id,
                        "--hostname", container.name,
                        "--network-alias", container.name,
                        image.id).asIterable()))
                }
            }
        }

        given("the host console has the TERM environment variable set") {
            val command = null
            val image = DockerImage("the-image")
            val network = DockerNetwork("the-network")
            val consoleInfo = mock<ConsoleInfo> {
                on { terminalType } doReturn "some-terminal"
            }

            given("and the container configuration does not have a definition for the TERM environment variable") {
                val container = Container("the-container", imageSourceDoesNotMatter())

                on("generating the command") {
                    val commandLine = generator.createCommandLine(container, command, emptyMap(), image, network, consoleInfo)

                    it("generates the correct command line, setting the TERM environment variable of the container to the value of the host") {
                        assertThat(commandLine, equalTo(listOf(
                            "docker", "create",
                            "-it",
                            "--network", network.id,
                            "--hostname", container.name,
                            "--network-alias", container.name,
                            "--env", "TERM=some-terminal",
                            image.id).asIterable()))
                    }
                }
            }

            given("and the container configuration does have a definition for the TERM environment variable") {
                val container = Container(
                    "the-container",
                    imageSourceDoesNotMatter(),
                    environment = mapOf("TERM" to "container-terminal"))

                on("generating the command") {
                    val commandLine = generator.createCommandLine(container, command, emptyMap(), image, network, consoleInfo)

                    it("generates the correct command line, setting the TERM environment variable of the container to the value from the container configuration") {
                        assertThat(commandLine, equalTo(listOf(
                            "docker", "create",
                            "-it",
                            "--network", network.id,
                            "--hostname", container.name,
                            "--network-alias", container.name,
                            "--env", "TERM=container-terminal",
                            image.id).asIterable()))
                    }
                }
            }
        }

        given("a container configuration with all optional configuration options specified") {
            val container = Container("the-container",
                imageSourceDoesNotMatter(),
                "the-container-command",
                mapOf("SOME_VAR" to "SOME_VALUE", "OTHER_VAR" to "OTHER_VALUE"),
                "/workingdir",
                setOf(VolumeMount("/local1", "/container1", null), VolumeMount("/local2", "/container2", "ro")),
                setOf(PortMapping(1000, 2000), PortMapping(3000, 4000)))

            val image = DockerImage("the-image")
            val network = DockerNetwork("the-network")
            val consoleInfo = mock<ConsoleInfo>()

            on("generating the command") {
                val commandLine = generator.createCommandLine(container, "some-command", emptyMap(), image, network, consoleInfo)

                it("generates the correct command line") {
                    assertThat(commandLine, equalTo(listOf(
                        "docker", "create",
                        "-it",
                        "--network", network.id,
                        "--hostname", container.name,
                        "--network-alias", container.name,
                        "--env", "SOME_VAR=SOME_VALUE",
                        "--env", "OTHER_VAR=OTHER_VALUE",
                        "--workdir", "/workingdir",
                        "--volume", "/local1:/container1",
                        "--volume", "/local2:/container2:ro",
                        "--publish", "1000:2000",
                        "--publish", "3000:4000",
                        image.id,
                        "some-command").asIterable()))
                }
            }
        }

        // FIXME This stuff is hard and there are lots of edge cases. Surely there's a better way...
        // References:
        // - https://www.gnu.org/software/bash/manual/html_node/Quoting.html
        // - http://www.grymoire.com/Unix/Quote.html
        mapOf(
            "echo hello" to listOf("echo", "hello"),
            "echo  hello" to listOf("echo", "hello"),
            """echo "hello world"""" to listOf("echo", "hello world"),
            """echo 'hello world'""" to listOf("echo", "hello world"),
            """echo hello\ world""" to listOf("echo", "hello world"),
            """echo 'hello "world"'""" to listOf("echo", """hello "world""""),
            """echo "hello 'world'"""" to listOf("echo", "hello 'world'"),
            """echo "hello \"world\""""" to listOf("echo", """hello "world""""),
            """echo "hello 'world'"""" to listOf("echo", "hello 'world'"),
            """echo 'hello "world"'""" to listOf("echo", """hello "world""""),
            """echo can\'t""" to listOf("echo", "can't"),
            // This next example comes from http://stackoverflow.com/a/28640859/1668119
            """sh -c 'echo "\"un'\''kno\"wn\$\$\$'\'' with \$\"\$\$. \"zzz\""'""" to listOf("sh", "-c", """echo "\"un'kno\"wn\$\$\$' with \$\"\$\$. \"zzz\""""")
        ).forEach { command, expectedSplit ->
            given("a simple container definition, a built image and the command '$command'") {
                val container = Container("the-container", imageSourceDoesNotMatter())
                val image = DockerImage("the-image")
                val network = DockerNetwork("the-network")
                val consoleInfo = mock<ConsoleInfo>()

                on("generating the command") {
                    val commandLine = generator.createCommandLine(container, command, emptyMap(), image, network, consoleInfo)
                    val expectedCommandLine = listOf("docker", "create",
                        "-it",
                        "--network", network.id,
                        "--hostname", container.name,
                        "--network-alias", container.name,
                        image.id) + expectedSplit

                    it("generates the correct command line") {
                        assertThat(commandLine, equalTo(expectedCommandLine.asIterable()))
                    }
                }
            }
        }

        mapOf(
            """echo "hello""" to "it contains an unbalanced double quote",
            """echo 'hello""" to "it contains an unbalanced single quote",
            """echo hello\""" to """it ends with a backslash (backslashes always escape the following character, for a literal backslash, use '\\')""",
            """echo "hello\""" to """it ends with a backslash (backslashes always escape the following character, for a literal backslash, use '\\')"""
        ).forEach { command, expectedErrorMessage ->
            given("a simple container definition, a built image and the command '$command'") {
                val container = Container("the-container", imageSourceDoesNotMatter())
                val image = DockerImage("the-image")
                val network = DockerNetwork("the-network")
                val consoleInfo = mock<ConsoleInfo>()

                on("generating the command") {
                    it("throws an exception with the message '$expectedErrorMessage'") {
                        assertThat({ generator.createCommandLine(container, command, emptyMap(), image, network, consoleInfo) },
                            throws<ContainerCreationFailedException>(withMessage("Command line `$command` is invalid: $expectedErrorMessage")))
                    }
                }
            }
        }
    }
})
