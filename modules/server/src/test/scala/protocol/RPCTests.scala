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
package protocol

import cats.{Monad, MonadError}
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.google.protobuf.InvalidProtocolBufferException
import org.scalatest._
import freestyle.rpc.common._
import freestyle.rpc.protocol.Utils.handlers.client._
import freestyle.rpc.server._

class RPCTests extends RpcBaseTestSuite with BeforeAndAfterAll {

  import freestyle.rpc.protocol.Utils._
  import freestyle.rpc.protocol.Utils.client.MyRPCClient
  import freestyle.rpc.protocol.Utils.database._
  import freestyle.rpc.protocol.Utils.implicits._

  override protected def beforeAll(): Unit = {
    import freestyle.rpc.server.implicits._
    serverStart[ConcurrentMonad].unsafeRunSync()
  }

  override protected def afterAll(): Unit = {
    import freestyle.rpc.server.implicits._
    serverStop[ConcurrentMonad].unsafeRunSync()
  }

  "frees-rpc server" should {

    import freestyle.rpc.server.implicits._

    "allow to startup a server and check if it's alive" in {

      def check[F[_]](implicit S: GrpcServer[F]): F[Boolean] =
        S.isShutdown

      check[ConcurrentMonad].unsafeRunSync() shouldBe false

    }

    "allow to get the port where it's running" in {

      def check[F[_]](implicit S: GrpcServer[F]): F[Int] =
        S.getPort

      check[ConcurrentMonad].unsafeRunSync() shouldBe SC.port

    }

  }

  "frees-rpc client" should {

    implicit val freesRPCServiceClientHandler: FreesRPCServiceClientHandler[ConcurrentMonad] =
      new FreesRPCServiceClientHandler[ConcurrentMonad]

    "be able to run unary services" in {

      def clientProgram[F[_]](implicit APP: MyRPCClient[F]): F[C] =
        APP.u(a1.x, a1.y)

      clientProgram[ConcurrentMonad].unsafeRunSync() shouldBe c1

    }

    "handle errors in unary services" in {

      def clientProgram[F[_]](
          errorCode: String)(implicit APP: MyRPCClient[F], M: MonadError[F, Throwable]): F[C] =
        M.handleError(APP.uwe(a1, errorCode))(ex => C(ex.getMessage, a1))

      clientProgram[ConcurrentMonad]("SE")
        .unsafeRunSync() shouldBe C("INVALID_ARGUMENT: SE", a1)
      clientProgram[ConcurrentMonad]("SRE")
        .unsafeRunSync() shouldBe C("INVALID_ARGUMENT: SRE", a1)
      clientProgram[ConcurrentMonad]("RTE")
        .unsafeRunSync() shouldBe C("INTERNAL: RTE", a1)
      clientProgram[ConcurrentMonad]("Thrown")
        .unsafeRunSync() shouldBe C("UNKNOWN", a1)
    }

    "be able to run unary services with avro schema" in {

      def clientProgram[F[_]](implicit APP: MyRPCClient[F]): F[C] =
        APP.uws(a1.x, a1.y)

      clientProgram[ConcurrentMonad].unsafeRunSync() shouldBe c1

    }

    "be able to run server streaming services" in {

      def clientProgram[F[_]](implicit APP: MyRPCClient[F]): F[List[C]] =
        APP.ss(a2.x, a2.y)

      clientProgram[ConcurrentMonad].unsafeRunSync() shouldBe cList

    }

    "handle errors in server streaming services" in {

      def clientProgram[F[_]](errorCode: String)(
          implicit APP: MyRPCClient[F],
          M: MonadError[F, Throwable]): F[List[C]] =
        M.handleError(APP.sswe(a1, errorCode))(ex => List(C(ex.getMessage, a1)))

      clientProgram[ConcurrentMonad]("SE")
        .unsafeRunSync() shouldBe List(C("INVALID_ARGUMENT: SE", a1))
      clientProgram[ConcurrentMonad]("SRE")
        .unsafeRunSync() shouldBe List(C("INVALID_ARGUMENT: SRE", a1))
      clientProgram[ConcurrentMonad]("RTE")
        .unsafeRunSync() shouldBe List(C("UNKNOWN", a1)) //todo: consider preserving the exception as is done for unary
      clientProgram[ConcurrentMonad]("Thrown")
        .unsafeRunSync() shouldBe List(C("UNKNOWN", a1))
    }

    "be able to run client streaming services" in {

      def clientProgram[F[_]](implicit APP: MyRPCClient[F]): F[D] =
        APP.cs(cList, i)

      clientProgram[ConcurrentMonad].unsafeRunSync() shouldBe dResult
    }

    "be able to run client bidirectional streaming services" in {

      ignoreOnTravis(
        "TODO: restore once https://github.com/frees-io/freestyle-rpc/issues/237 is fixed")

      def clientProgram[F[_]](implicit APP: MyRPCClient[F]): F[E] =
        APP.bs(eList)

      clientProgram[ConcurrentMonad].unsafeRunSync() shouldBe e1

    }

    "be able to run client bidirectional streaming services with avro schema" in {

      def clientProgram[F[_]](implicit APP: MyRPCClient[F]): F[E] =
        APP.bsws(eList)

      clientProgram[ConcurrentMonad].unsafeRunSync() shouldBe e1

    }

    "be able to run rpc services monadically" in {

      def clientProgram[F[_]: Monad](implicit APP: MyRPCClient[F]): F[(C, C, List[C], D, E, E)] = {
        for {
          u <- APP.u(a1.x, a1.y)
          v <- APP.uws(a1.x, a1.y)
          w <- APP.ss(a2.x, a2.y)
          x <- APP.cs(cList, i)
          y <- APP.bs(eList)
          z <- APP.bsws(eList)
        } yield (u, v, w, x, y, z)
      }

      clientProgram[ConcurrentMonad].unsafeRunSync() shouldBe ((c1, c1, cList, dResult, e1, e1))

    }

    "#67 issue - booleans as request are not allowed" in {

      def clientProgram[F[_]](implicit APP: MyRPCClient[F]): F[C] =
        APP.notAllowed(true)

      assertThrows[InvalidProtocolBufferException](clientProgram[ConcurrentMonad].unsafeRunSync())

    }

    "be able to invoke services with empty requests" in {

      def clientProgram[F[_]](implicit APP: MyRPCClient[F]): F[Empty.type] =
        APP.empty

      clientProgram[ConcurrentMonad].unsafeRunSync() shouldBe Empty

    }

    "#71 issue - empty for avro" in {

      def clientProgram[F[_]](implicit APP: MyRPCClient[F]): F[Empty.type] =
        APP.emptyAvro

      clientProgram[ConcurrentMonad].unsafeRunSync() shouldBe Empty
    }

    "empty for avro with schema" in {

      def clientProgram[F[_]](implicit APP: MyRPCClient[F]): F[Empty.type] =
        APP.emptyAvroWithSchema

      clientProgram[ConcurrentMonad].unsafeRunSync() shouldBe Empty
    }

    "#71 issue - empty response with one param for avro" in {

      def clientProgram[F[_]](implicit APP: MyRPCClient[F]): F[Empty.type] =
        APP.emptyAvroParam(a4)

      clientProgram[ConcurrentMonad].unsafeRunSync() shouldBe Empty
    }

    "empty response with one param for avro with schema" in {

      def clientProgram[F[_]](implicit APP: MyRPCClient[F]): F[Empty.type] =
        APP.emptyAvroWithSchemaParam(a4)

      clientProgram[ConcurrentMonad].unsafeRunSync() shouldBe Empty
    }

    "#71 issue - response with empty params for avro" in {

      def clientProgram[F[_]](implicit APP: MyRPCClient[F]): F[A] =
        APP.emptyAvroParamResponse

      clientProgram[ConcurrentMonad].unsafeRunSync() shouldBe a4

    }

    "response with empty params for avro with schema" in {

      def clientProgram[F[_]](implicit APP: MyRPCClient[F]): F[A] =
        APP.emptyAvroWithSchemaParamResponse

      clientProgram[ConcurrentMonad].unsafeRunSync() shouldBe a4

    }

    "#71 issue - empty response with one param for proto" in {

      def clientProgram[F[_]](implicit APP: MyRPCClient[F]): F[Empty.type] =
        APP.emptyParam(a4)

      clientProgram[ConcurrentMonad].unsafeRunSync() shouldBe Empty
    }

    "#71 issue - response with empty params for proto" in {

      def clientProgram[F[_]](implicit APP: MyRPCClient[F]): F[A] =
        APP.emptyParamResponse

      clientProgram[ConcurrentMonad].unsafeRunSync() shouldBe a4

    }

    "be able to have non request methods" in {

      def clientProgram[F[_]: cats.Functor](implicit client: service.RPCService.Client[F]): F[Int] =
        client.sumA

      clientProgram[ConcurrentMonad].unsafeRunSync() shouldBe 3000
    }

    "BigDecimal param with Proto" in {

      val bd: BigDecimal = BigDecimal(scala.util.Random.nextDouble())

      def clientProgram[F[_]: cats.Functor](
          implicit client: service.RPCService.Client[F]): F[BigDecimal] =
        client.bigDecimalParamResponse(bd)

      clientProgram[ConcurrentMonad].unsafeRunSync() shouldBe bd
    }

    "BigDecimal param with Avro" in {

      val bd: BigDecimal = BigDecimal(scala.util.Random.nextDouble())

      def clientProgram[F[_]: cats.Functor](
          implicit client: service.RPCService.Client[F]): F[BigDecimal] =
        client.bigDecimalAvroParam(bd)

      clientProgram[ConcurrentMonad].unsafeRunSync() shouldBe bd
    }

    "BigDecimal param with AvroWithSchema" in {

      val bd: BigDecimal = BigDecimal(scala.util.Random.nextDouble())

      def clientProgram[F[_]: cats.Functor](
          implicit client: service.RPCService.Client[F]): F[BigDecimal] =
        client.bigDecimalAvroWithSchemaParam(bd)

      clientProgram[ConcurrentMonad].unsafeRunSync() shouldBe bd
    }

  }

  "frees-rpc client with compression" should {

    implicit val freesRPCServiceClientHandler: FreesRPCServiceClientCompressedHandler[
      ConcurrentMonad] =
      new FreesRPCServiceClientCompressedHandler[ConcurrentMonad]

    "be able to run unary services" in {

      def clientProgram[F[_]](implicit APP: MyRPCClient[F]): F[C] =
        APP.u(a1.x, a1.y)

      clientProgram[ConcurrentMonad].unsafeRunSync() shouldBe c1

    }

    "be able to run unary services with avro schema" in {

      def clientProgram[F[_]](implicit APP: MyRPCClient[F]): F[C] =
        APP.uws(a1.x, a1.y)

      clientProgram[ConcurrentMonad].unsafeRunSync() shouldBe c1

    }

    "be able to run server streaming services" in {

      def clientProgram[F[_]](implicit APP: MyRPCClient[F]): F[List[C]] =
        APP.ss(a2.x, a2.y)

      clientProgram[ConcurrentMonad].unsafeRunSync() shouldBe cList

    }

    "be able to run client streaming services" in {

      def clientProgram[F[_]](implicit APP: MyRPCClient[F]): F[D] =
        APP.cs(cList, i)

      clientProgram[ConcurrentMonad].unsafeRunSync() shouldBe dResult
    }

    "be able to run client bidirectional streaming services" in {

      def clientProgram[F[_]](implicit APP: MyRPCClient[F]): F[E] =
        APP.bs(eList)

      clientProgram[ConcurrentMonad].unsafeRunSync() shouldBe e1

    }

    "be able to run client bidirectional streaming services with avro schema" in {

      def clientProgram[F[_]](implicit APP: MyRPCClient[F]): F[E] =
        APP.bsws(eList)

      clientProgram[ConcurrentMonad].unsafeRunSync() shouldBe e1

    }

    "be able to run rpc services monadically" in {

      def clientProgram[F[_]: Monad](implicit APP: MyRPCClient[F]): F[(C, C, List[C], D, E, E)] = {
        for {
          u <- APP.u(a1.x, a1.y)
          v <- APP.uws(a1.x, a1.y)
          w <- APP.ss(a2.x, a2.y)
          x <- APP.cs(cList, i)
          y <- APP.bs(eList)
          z <- APP.bsws(eList)
        } yield (u, v, w, x, y, z)
      }

      clientProgram[ConcurrentMonad].unsafeRunSync() shouldBe ((c1, c1, cList, dResult, e1, e1))

    }

    "#67 issue - booleans as request are not allowed" in {

      def clientProgram[F[_]](implicit APP: MyRPCClient[F]): F[C] =
        APP.notAllowed(true)

      assertThrows[InvalidProtocolBufferException](clientProgram[ConcurrentMonad].unsafeRunSync())

    }

    "be able to invoke services with empty requests" in {

      def clientProgram[F[_]](implicit APP: MyRPCClient[F]): F[Empty.type] =
        APP.empty

      clientProgram[ConcurrentMonad].unsafeRunSync() shouldBe Empty

    }

    "#71 issue - empty for avro" in {

      def clientProgram[F[_]](implicit APP: MyRPCClient[F]): F[Empty.type] =
        APP.emptyAvro

      clientProgram[ConcurrentMonad].unsafeRunSync() shouldBe Empty
    }

    "empty for avro with schema" in {

      def clientProgram[F[_]](implicit APP: MyRPCClient[F]): F[Empty.type] =
        APP.emptyAvroWithSchema

      clientProgram[ConcurrentMonad].unsafeRunSync() shouldBe Empty
    }

    "#71 issue - empty response with one param for avro" in {

      def clientProgram[F[_]](implicit APP: MyRPCClient[F]): F[Empty.type] =
        APP.emptyAvroParam(a4)

      clientProgram[ConcurrentMonad].unsafeRunSync() shouldBe Empty
    }

    "empty response with one param for avro with schema" in {

      def clientProgram[F[_]](implicit APP: MyRPCClient[F]): F[Empty.type] =
        APP.emptyAvroWithSchemaParam(a4)

      clientProgram[ConcurrentMonad].unsafeRunSync() shouldBe Empty
    }

    "#71 issue - response with empty params for avro" in {

      def clientProgram[F[_]](implicit APP: MyRPCClient[F]): F[A] =
        APP.emptyAvroParamResponse

      clientProgram[ConcurrentMonad].unsafeRunSync() shouldBe a4

    }

    "response with empty params for avro with schema" in {

      def clientProgram[F[_]](implicit APP: MyRPCClient[F]): F[A] =
        APP.emptyAvroWithSchemaParamResponse

      clientProgram[ConcurrentMonad].unsafeRunSync() shouldBe a4

    }

    "#71 issue - empty response with one param for proto" in {

      def clientProgram[F[_]](implicit APP: MyRPCClient[F]): F[Empty.type] =
        APP.emptyParam(a4)

      clientProgram[ConcurrentMonad].unsafeRunSync() shouldBe Empty
    }

    "#71 issue - response with empty params for proto" in {

      def clientProgram[F[_]](implicit APP: MyRPCClient[F]): F[A] =
        APP.emptyParamResponse

      clientProgram[ConcurrentMonad].unsafeRunSync() shouldBe a4

    }

  }

}
