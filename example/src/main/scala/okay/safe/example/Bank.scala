package okay.safe.example

import language.experimental.safe
import language.experimental.separationChecking

import okay.safe.*
import okay.safe.std.*

/*
 * THE PROGRAM SIDE of a bank: compiled in safe mode. The effect is data — what a program may ask of a bank —, and
 * it is SENSITIVE: no handler takes it but under its audit. The programs and the audit are pure: nothing here can
 * reach a client, a clock or a console; the host (host/BankHost.scala) gives the client to a handler at the edge.
 */

/** what a program may ask of a bank */
enum Bank[X]:
  case Balance(account: String) extends Bank[Long]
  case Transfer(from: String, to: String, amount: Long) extends Bank[Unit]

object Bank:
  given Sensitive[Bank] = Sensitive()
  def balance(account: String): Word[Bank, Long] = word(Balance(account))
  def transfer(from: String, to: String, amount: Long): Word[Bank, Unit] = word(Transfer(from, to, amount))

object Payroll:
  /** every member of `staff` paid from `from`, while the money lasts: what is left */
  def pay[R[_]: Has[Bank] : Has[Throws % String]](from: String, staff: List[(String, Long)]): Long ! R =
    staff match
      case Nil => Bank.balance(from)
      case (who, amount) :: rest =>
        for
          left <- Bank.balance(from)
          _ <- if left < amount then Throws.raise[String](s"$from cannot pay $amount to $who").into[R]
               else Bank.transfer(from, who, amount).into[R]
          after <- pay[R](from, rest)
        yield after

  /** THE AUDIT: every operation recorded when it is asked and when it is answered; a transfer above `limit`
   * refused — recorded, and the program failed before the bank is asked */
  def audited[T[_]: Has[Audit] : Has[Throws % String], A](limit: Long): Interceptor[Bank, T, A, A] =
    new Interceptor[Bank, T, A, A]:
      def ret(a: A): A ! T = pure(a)
      def apply[X](op: Bank[X], k: Continuation[X, T, A], pass: Pass[Bank, T]): A ! T = op match
        case Bank.Transfer(from, to, amount) if amount > limit =>
          val refused: A ! T = Throws.raise[String](s"over the limit: $amount").into[T]
          Audit.record(s"refused: $amount from $from to $to").flatMap(_ => refused)
        case _ =>
          for
            _ <- Audit.record(s"asked: $op")
            answer <- k.after(pass(op).flatMap(x => Audit.record(s"answered: $op → $x").map(_ => x)))
          yield answer
