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

package batect.model.events

import batect.model.steps.WaitForContainerToBecomeHealthyStep
import batect.config.Container

data class ContainerStartedEvent(val container: Container) : TaskEvent() {
    override fun apply(context: TaskEventContext) {
        if (context.isAborting) {
            return
        }

        val creationEvent = context.getPastEventsOfType<ContainerCreatedEvent>()
                .single { it.container == container }

        context.queueStep(WaitForContainerToBecomeHealthyStep(container, creationEvent.dockerContainer))
    }

    override fun toString() = "${this::class.simpleName}(container: '${container.name}')"
}