/*
 * Copyright 2017 47 Degrees, LLC. <http://www.47deg.com>
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

package freestyle
package rpc

import cats.~>
import cats.arrow.FunctionK
import cats.effect.{IO, Sync}
import freestyle.free.Capture
import freestyle.rpc.client.handlers._
import journal.Logger
import monix.eval.Task
import monix.execution.Scheduler

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.Future
import scala.concurrent.duration._

trait IOCapture {

  implicit def syncCapture[F[_]](implicit F: Sync[F]): Capture[F] =
    new Capture[F] { def capture[A](a: => A): F[A] = F.delay(a) }
}

trait BaseAsync extends freestyle.async.Implicits {

  protected[this] val asyncLogger: Logger            = Logger[this.type]
  protected[this] val atMostDuration: FiniteDuration = 10.seconds

}

trait FutureAsyncInstances extends BaseAsync {

  implicit val future2Task: Future ~> Task =
    λ[Future ~> Task] { fa =>
      asyncLogger.info(s"${Thread.currentThread().getName} Deferring Future to Task...")
      Task.deferFuture(fa)
    }

}

trait TaskAsyncInstances extends BaseAsync {

  implicit def task2Future(implicit S: Scheduler): Task ~> Future = λ[Task ~> Future](_.runAsync)

  implicit val task2Task: Task ~> Task = FunctionK.id[Task]

  implicit def task2IO(implicit S: Scheduler): Task ~> IO = λ[Task ~> IO](_.toIO)
}

trait IOAsyncInstances extends BaseAsync {

  implicit val io2Task: IO ~> Task = λ[IO ~> Task](_.to[Task])
}

trait RPCAsyncImplicits extends FutureAsyncInstances with TaskAsyncInstances with IOAsyncInstances
