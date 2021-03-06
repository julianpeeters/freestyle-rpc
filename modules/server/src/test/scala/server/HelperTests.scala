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
package server

import cats.Monad
import freestyle.rpc.common.ConcurrentMonad
import io.grpc.Server

class HelperTests extends RpcServerTestSuite with Helpers {

  import implicits._

  "server helper" should {

    "work as expected" in {

      val grpcServer: GrpcServer[ConcurrentMonad] = grpcServerHandlerTests[ConcurrentMonad]

      server[ConcurrentMonad](Monad[ConcurrentMonad], grpcServer)
        .unsafeRunSync() shouldBe ((): Unit)

      (serverMock.start _: () => Server).verify()
    }

  }

}
