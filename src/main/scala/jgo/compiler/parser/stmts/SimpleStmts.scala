package jgo.compiler
package parser.stmts

import parser.exprs._
import parser.scoped._

import interm._
import codeseq._
import instr._
import instr.TypeConversions._
import types._
import symbol._

trait SimpleStmts extends Expressions with Symbols with GrowablyScoped with StmtUtils {
  lazy val simpleStmt: P[CodeBuilder] =                           "simple statement" $ //was PP; not sure why
    ( assignment
    | shortVarDecl
    | incOrDecStmt
    | sendStmt
    | expression  ^^  evaluate
    | success(CodeBuilder.empty)
    )
  
  lazy val sendStmt: P[CodeBuilder] =                               "send statement" $
    (expression <~ "<-") ~ expression  ^^ send //the first expr must be of channel type
  
  lazy val incOrDecStmt: P[CodeBuilder] =         "increment or decrement statement" $
    ( expression <~ "++"  ^^ incr
    | expression <~ "--"  ^^ decr
    //| failure("`++' or `--' expected")
    )
  
  lazy val assignment: P[CodeBuilder] =                       "assignment statement" $
    ( (exprList   <~ "=")   ~ exprList    ^^ assign
    /*| (expression <~ "+=")  ~ expression
    | (expression <~ "-=")  ~ expression
    | (expression <~ "|=")  ~ expression
    | (expression <~ "^=")  ~ expression
    | (expression <~ "*=")  ~ expression
    | (expression <~ "/=")  ~ expression
    | (expression <~ "%=")  ~ expression
    | (expression <~ "<<=") ~ expression
    | (expression <~ ">>=") ~ expression
    | (expression <~ "&=")  ~ expression
    | (expression <~ "&^=") ~ expression*/
    )
  
  lazy val shortVarDecl: P[CodeBuilder] =               "short variable declaration" $
    (identList <~ ":=") ~ exprList  ^^declAssign   //the number of idents must = the number of exprs
  
  
  private def evaluate(e: Expr): CodeBuilder = e.eval
  
  private def declAssign(left: List[String], right: List[Expr]): CodeBuilder = {
    var declCode, leftCode, rightCode = CodeBuilder.empty
    var actuallySawDecl = false
    
    checkArity(left, right)
    
    for ((l, r) <- left zip right) {
      if (!growable.alreadyDefined(l)) { //not already defined in innermost scope
        actuallySawDecl = true
        val v = new LocalVar(l, r.t)
        growable.put(l, v)
        declCode  = declCode    |+| Decl(v)
        leftCode  = leftCode    |+| r.eval
        rightCode = StoreVar(v) |+| rightCode
      }
      else getVariable(l) foreach {
        v =>
        if (v.t <<= r.t) {
          leftCode  = leftCode    |+| r.eval
          rightCode = StoreVar(v) |+| rightCode
        }
        else
          recordErr("right operand of := has type %s not assignable to type %s of left operand %s",
            r.t, v.t, l)
      }
    }
    
    if (!actuallySawDecl)
      recordErr("no new variables on left side of :=")
    
    declCode |+| leftCode |+| rightCode
  }
  
  private def assign(left: List[Expr], right: List[Expr]): CodeBuilder = {
    var leftCode, rightCode = CodeBuilder.empty
    
    checkArity(left, right)
    
    for ((l, r) <- left zip right) {
      if (l.t <<= r.t) 
        l match {
          case lval: LvalExpr =>
            leftCode  = leftCode |+| lval.storePrefix(r.eval)
            rightCode = lval.storeSuffix |+| rightCode
          case _ =>
            recordErr("not an lvalue")
        }
      else
        recordErr("right-hand type %s not assignable to left-hand type %s", r.t, l.t)
    }
    leftCode |+| rightCode
  }
  
  private def send(e1: Expr, e2: Expr): CodeBuilder = e1 match {
    case HasType(SendChanType(t)) =>
      if (t <<= e2.t)
        e1.eval |+| e2.eval |+| ChanSend
      else
        badStmt("right operand of <- has type %s not assignable to left operand's element type %s", e2.t, t)
    case HasType(t) =>
      badStmt("left operand of <- has type %s; must be a channel that can send", t)
  }
  
  private def incr(e: Expr): CodeBuilder = e match {
    case VarLval(vr) OfType (t: IntegralType) => //I think this is a poster case for OfType!
      Incr(vr, 1, t)
    case (l: LvalExpr) OfType (t: IntegralType) =>
      l.store(l.load |+| PushInt(1, t) |+| Add(t))
    
    case (l: LvalExpr) OfType t =>
      badStmt("lvalue operand of ++ has type %s, which is not an integral type", t)
    case _ =>
      badStmt("operand of ++ not an lvalue")
  }
  
  private def decr(e: Expr): CodeBuilder = e match {
    case VarLval(vr) OfType (t: IntegralType) => //I think this is a poster case for OfType!
      Decr(vr, 1, t)
    case (l: LvalExpr) OfType (t: IntegralType) =>
      l.store(l.load |+| PushInt(1, t) |+| Sub(t))
    
    case (l: LvalExpr) OfType t =>
      badStmt("lvalue operand of -- has type %s, which is not an integral type", t)
    case _ =>
      badStmt("operand of -- not an lvalue")
  }
}
