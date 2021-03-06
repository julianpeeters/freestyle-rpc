/*
 * Copyright 2017-2018 47 Degrees, LLC. <http://www.47deg.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package freestyle.rpc
package testing

import java.util.UUID
import java.util.concurrent.TimeUnit

import io.grpc.{ManagedChannel, Server, ServerServiceDefinition}
import io.grpc.inprocess.{InProcessChannelBuilder, InProcessServerBuilder}
import io.grpc.util.MutableHandlerRegistry

final case class ServerChannel(server: Server, channel: ManagedChannel) {

  def shutdown(): Boolean = {
    channel.shutdown()
    server.shutdown()

    try {
      channel.awaitTermination(1, TimeUnit.MINUTES)
      server.awaitTermination(1, TimeUnit.MINUTES)
    } catch {
      case e: InterruptedException =>
        Thread.currentThread.interrupt()
        throw new RuntimeException(e)
    } finally {
      channel.shutdownNow()
      server.shutdownNow()
      (): Unit
    }
  }
}

object ServerChannel {

  def apply(serverServiceDefinitions: ServerServiceDefinition*): ServerChannel = {
    val serviceRegistry =
      new MutableHandlerRegistry
    val serverName: String =
      UUID.randomUUID.toString
    val serverBuilder: InProcessServerBuilder =
      InProcessServerBuilder
        .forName(serverName)
        .fallbackHandlerRegistry(serviceRegistry)
        .directExecutor()
    val channelBuilder: InProcessChannelBuilder =
      InProcessChannelBuilder.forName(serverName)

    serverServiceDefinitions.toList.map(serverBuilder.addService)

    ServerChannel(serverBuilder.build().start(), channelBuilder.directExecutor.build)
  }

  def withServerChannel[A](services: ServerServiceDefinition*)(f: ServerChannel => A): A = {

    val sc: ServerChannel = apply(services: _*)
    val result: A         = f(sc)
    sc.shutdown()

    result
  }

}
