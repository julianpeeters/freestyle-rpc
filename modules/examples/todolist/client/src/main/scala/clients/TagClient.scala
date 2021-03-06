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

package examples.todolist.client
package clients

import examples.todolist.protocol.Protocols._
import freestyle.tagless.tagless

@tagless(true)
trait TagClient[F[_]] {

  def reset(): F[Int]

  def insert(request: TagRequest): F[Option[TagMessage]]

  def retrieve(id: Int): F[Option[TagMessage]]

  def list(): F[TagList]

  def update(tag: TagMessage): F[Option[TagMessage]]

  def remove(id: Int): F[Int]

}
