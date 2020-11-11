/**
 * Streaming Hardware Generator - ETH Zurich
 * Copyright (C) 2015 Francois Serre (serref@inf.ethz.ch)
 */

import SB.HW.{ComplexHW, FixedPoint}
import SPL.FFT.{DFT, StreamDiagC}
import StreamingModule.StreamingModule
import linalg.Fields.Complex
import linalg.{Matrix, Vec}
import org.scalacheck.{Gen, Properties, Shrink}
import org.scalacheck.Prop.forAll

object DFTTest extends Properties("DFT") {
  implicit def shrinkSB[T]: Shrink[StreamingModule[T]] = Shrink { input: StreamingModule[T] =>

    input match {
      case StreamingModule.Product(factors) =>

        (0 until factors.size).toStream.map(i => StreamingModule.Product[T]((factors.take(i)) ++ (factors.drop(i + 1))))
      case SB.Product(factors) =>

        (0 until factors.size).toStream.map(i => SB.Product[T]((factors.take(i)) ++ (factors.drop(i + 1))))
      case SB.ITensor(r, factor, k) if k > factor.n => (1 to k - factor.n).toStream.map(i => SB.ITensor(r - i, factor, k - i))
      case StreamingModule.ItProduct(r, factor: SB.Product[T], endLoop) => shrinkSB[T].shrink(factor).map(f => StreamingModule.ItProduct(r, f, endLoop))
      case StreamingModule.ItProduct(r, factor, endLoop) => (1 until r).reverse.toStream.map(i => StreamingModule.ItProduct(i, factor, endLoop))
      //case slp:SLP[Int] if slp.size>1 =>
      case _ => (1 until input.k).reverse.toStream.map(k => input.spl.stream(k)(input.hw))
    }
  }

  implicit val cphw = ComplexHW(FixedPoint(8, 8))
  property("CTDFT conforms to the definition")=forAll(for {
    n <- Gen.choose(2,10)
    r <- Gen.choose(1, n-1)
    if n % r == 0
  } yield (n,r)) { case (n,r) =>
    val sb = DFT.CTDFT(n, r) // Temporal(Vector(Vec.fromInt(2, 3)), Vector(Matrix[F2](2, 2, Vector(1, 1, 1, 0))))(Unsigned(16))
      val res = (0 until (1 << n)).map(j => Vec(sb.eval(
        Seq.tabulate(1 << n)(i => if (i == j) 1.0 else 0.0), 0
      ).toVector)).reduce[Matrix[Complex[Double]]](_ :: _)
      val wht = Matrix.tabulate[Complex[Double]](1 << n, 1 << n)((i, j) => DFT.omega(n, i * j))
      (res - wht).norm < 0.0001
    }



  val genSteady = for {
    t <- Gen.choose(1, 2)
    k <- Gen.choose(1, 2)
    n = t + k
    r <- Gen.choose(1, n)
    if n % r == 0
  } yield DFT.CTDFT(n, r).stream(k)(ComplexHW(FixedPoint(8, 8)))
  property("CTDFT") = forAll(genSteady) { sb: StreamingModule[Complex[Double]] =>
      sb.test(Vector.tabulate(2 << sb.n)(i => Complex(i))) match {
        case Some(value) if value.re < 0.01 => true
        case _ => false
      }
    } //(implicitly,shrinkSB,implicitly,implicitly,implicitly)


  val genPease = for {
    t <- Gen.choose(1, 2)
    k <- Gen.choose(1, 2)
    n = t + k
    r <- Gen.choose(1, n)
    if n % r == 0
  } yield DFT.Pease(n, r).stream(k)(ComplexHW(FixedPoint(8, 8)))
  property("Pease") =

    forAll(genPease) { sb: StreamingModule[Complex[Double]] =>
      sb.test(Vector.tabulate(2 << sb.n)(i => Complex(i))) match {
        case Some(value) if value.re < 0.01 => true
        case _ => false
      }
    } //(implicitly,shrinkSB,implicitly,implicitly,implicitly)

  val genDiagC = for {
    t <- Gen.choose(1, 2)
    k <- Gen.choose(1, 2)
    n = t + k
    r <- Gen.choose(1, n - 1)
    if n % r == 0
  } yield StreamDiagC(n, r).stream(k)(ComplexHW(FixedPoint(16, 16)))
  property("DiagC") = forAll(genDiagC) { sb: StreamingModule[Complex[Double]] =>
      sb.test(Vector.tabulate((sb.n) << sb.n)(i => Complex(1))) match {
        case Some(value) if value.re < 0.01 => true
        case _ => false
      }
  } //(implicitly,shrinkSB,implicitly,implicitly,implicitly)



  property("ItPeaseDFT conforms to the definition") =forAll(for {
    n <- Gen.choose(2,10)
    r <- Gen.choose(1, n-1)
    if n % r == 0
  } yield (n,r)) { case (n,r) =>
      val sb = DFT.ItPease(n, r) // Temporal(Vector(Vec.fromInt(2, 3)), Vector(Matrix[F2](2, 2, Vector(1, 1, 1, 0))))(Unsigned(16))
      val res = (0 until (1 << n)).map(j => Vec(sb.eval(
        Seq.tabulate(1 << n)(i => if (i == j) 1.0 else 0.0), 0
      ).toVector)).reduce[Matrix[Complex[Double]]](_ :: _)
      val wht = Matrix.tabulate[Complex[Double]](1 << n, 1 << n)((i, j) => DFT.omega(n, i * j))
      (res - wht).norm < 0.0001
    }


  val genItPease = for {
    t <- Gen.choose(1, 3)
    k <- Gen.choose(1, 2)
    n = t + k
    r <- Gen.choose(1, n - 1)
    if n % r == 0
    if k >= r
  } yield DFT.ItPease(n, r).stream(k)(ComplexHW(FixedPoint(8, 8)))
  property("ItPease")= forAll(genItPease) { sb: StreamingModule[Complex[Double]] =>
      sb.test(Vector.tabulate(2 << sb.n)(i => Complex(i))) match {
        case Some(value) if value.re < 0.01 => true
        case _ => false
      }
    } //(implicitly,shrinkSB,implicitly,implicitly,implicitly)


  property("ItPeaseFused conforms to the definition")=forAll(for {
    n <- Gen.choose(2,10)
    r <- Gen.choose(1, n-1)
    if n % r == 0
  } yield (n,r)) { case (n,r) =>
      val sb = DFT.ItPeaseFused(n, r) // Temporal(Vector(Vec.fromInt(2, 3)), Vector(Matrix[F2](2, 2, Vector(1, 1, 1, 0))))(Unsigned(16))
      val res = (0 until (1 << n)).map(j => Vec(sb.eval(
        Seq.tabulate(1 << n)(i => if (i == j) 1.0 else 0.0), 0
      ).toVector)).reduce[Matrix[Complex[Double]]](_ :: _)
      val wht = Matrix.tabulate[Complex[Double]](1 << n, 1 << n)((i, j) => DFT.omega(n, i * j))
      (res - wht).norm < 0.0001
    }


  val genItPeaseFused = for {
    t <- Gen.choose(1, 3)
    k <- Gen.choose(1, 2)
    n = t + k
    r <- Gen.choose(1, n - 1)
    if n % r == 0
    if k >= r
  } yield DFT.ItPeaseFused(n, r).stream(k)(ComplexHW(FixedPoint(8, 8)))
  property("ItPeaseFused") = forAll(genItPeaseFused) { sb: StreamingModule[Complex[Double]] =>
      sb.test(Vector.tabulate(2 << sb.n)(i => Complex(i))) match {
        case Some(value) if value.re < 0.01 => true
        case _ => false
      }
    } //(implicitly,shrinkSB,implicitly,implicitly,implicitly)

}