package jgo.compiler
package interm
package expr

import types._
import instr._
import instr.TypeConversions._
import codeseq._

trait Expr extends Typed {
  /**
   * Provides the code necessary for computing the value
   * of this expression and placing the result on the top
   * of the operand stack. This code is called the
   * <i>evaluation code</i> of this expression.
   */
  private[expr] def eval: CodeBuilder
  
  private[expr] def addressable = false
    
  private[expr] def call(args: List[Expr]): Either[String, Expr] =
    for (resultT <- checkCall(funcType, args).right)
    yield BasicExpr((args foldLeft eval) { _ |+| _.eval } |+| InvokeLambda(funcType.get), resultT)
}

object Expr {
  def apply(eval: => CodeBuilder, t: Type): Expr = new BasicExpr(eval, t)
}
