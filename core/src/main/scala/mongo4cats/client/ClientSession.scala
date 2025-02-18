/*
 * Copyright 2020 Kirill5k
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

package mongo4cats.client

import cats.Monad
import cats.effect.Async
import cats.syntax.alternative._
import cats.syntax.functor._
import com.mongodb.reactivestreams.client.{ClientSession => JClientSession}
import mongo4cats.helpers._

import scala.util.Try

abstract class ClientSession[F[_]] {

  /** Returns true if there is an active transaction on this session, and false otherwise
    *
    * @return
    *   true if there is an active transaction on this session
    */
  def hasActiveTransaction: Boolean

  /** Gets the transaction options. If session has no active transaction, then None is returned
    *
    * @return
    *   the transaction options
    */
  def transactionOptions: Option[TransactionOptions]

  /** Start a transaction in the context of this session with the given transaction options. A transaction can not be started if there is
    * already an active transaction on this session.
    *
    * @param options
    *   the options to apply to the transaction
    */
  def startTransaction(options: TransactionOptions): F[Unit]
  def startTransaction: F[Unit] = startTransaction(TransactionOptions())

  /** Abort a transaction in the context of this session. A transaction can only be aborted if one has first been started.
    */
  def abortTransaction: F[Unit]

  /** Commit a transaction in the context of this session. A transaction can only be commmited if one has first been started.
    */
  def commitTransaction: F[Unit]

  private[mongo4cats] def session: JClientSession
}

final private class LiveClientSession[F[_]](
    private[mongo4cats] val session: JClientSession
)(implicit F: Async[F])
    extends ClientSession[F] {

  override def hasActiveTransaction: Boolean = session.hasActiveTransaction

  override def transactionOptions: Option[TransactionOptions] =
    hasActiveTransaction.guard[Option].as(session.getTransactionOptions)

  override def startTransaction(options: TransactionOptions): F[Unit] =
    F.fromTry(Try(session.startTransaction(options)))

  override def commitTransaction: F[Unit] =
    session.commitTransaction().asyncVoid[F]

  override def abortTransaction: F[Unit] =
    session.abortTransaction().asyncVoid[F]
}

object ClientSession {
  private[client] def make[F[_]: Async](session: JClientSession): F[ClientSession[F]] =
    Monad[F].pure(new LiveClientSession[F](session))
}
