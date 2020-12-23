/*
 *    _____ ______          SGen - A Generator of Streaming Hardware
 *   / ___// ____/__  ____  Department of Computer Science, ETH Zurich, Switzerland
 *   \__ \/ / __/ _ \/ __ \
 *  ___/ / /_/ /  __/ / / / Copyright (C) 2020 François Serre (serref@inf.ethz.ch)
 * /____/\____/\___/_/ /_/  https://github.com/fserre/sgen
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA
 *
 */

package transforms.fft

import ir.rtl.hardwaretype.{ComplexHW, FixedPoint, HW, Unsigned}
import ir.rtl.{SB,RAMControl}
import ir.spl.{Repeatable, SPL}
import ir.rtl.signals.{Const, Counter, ROM, Sig, Timer}
import linalg.Fields.Complex
import linalg.Fields.Complex._

case class DiagC(override val n: Int, r: Int, l: Int) extends SPL[Complex[Double]](n) with Repeatable[Complex[Double]] {
  def pow(x: Int): Int = {
    val j = x % (1 << r)
    val i = ((x >> r) >> (r * l)) << (r * l)
    i * j
  }

  def coef(i: Int): Complex[Double] = DFT.omega(n, pow(i))

  override def eval(inputs: Seq[Complex[Double]], set: Int): Seq[Complex[Double]] = inputs.zipWithIndex.map { case (input, i) => input * coef(i % (1 << n)) }

  override def stream(k: Int,control:RAMControl)(implicit hw2: HW[Complex[Double]]): SB[Complex[Double]] = new SB(n - k, k) {
    override def implement(inputs: Seq[Sig[Complex[Double]]])(implicit sb: SB[?]): Seq[Sig[Complex[Double]]] = {
      (0 until K).map(p => {
        val twiddles = Vector.tabulate(T)(c => coef((c * K) + p))
        val twiddleHW = hw match {
          case ComplexHW(FixedPoint(magnitude, fractional)) => ComplexHW(FixedPoint(2, magnitude + fractional - 2))
          case _ => hw
        }
        val control = Timer(T)
        val twiddle = ROM(twiddles, control)(twiddleHW, sb)
        inputs(p) * twiddle
      })
    }

    override def spl: SPL[Complex[Double]] = DiagC(this.n, r, l)
  }
}

case class StreamDiagC(override val n: Int, r: Int) extends SPL[Complex[Double]](n) with Repeatable[Complex[Double]] {
  def pow(x: Int, l: Int): Int = {
    val j = x % (1 << r)
    val i = ((x >> r) >> (r * l)) << (r * l)
    i * j
  }

  def coef(i: Int, l: Int): Complex[Double] = DFT.omega(n, pow(i, l))

  override def eval(inputs: Seq[Complex[Double]], set: Int): Seq[Complex[Double]] = inputs.zipWithIndex.map { case (input, i) => input * coef(i % (1 << n), set % (n / r)) }

  override def stream(k: Int,control:RAMControl)(implicit hw2: HW[Complex[Double]]): SB[Complex[Double]] = {
    //require(k>=r)
    new SB(n - k, k) {
      override def implement(inputs: Seq[Sig[Complex[Double]]])(implicit sb: SB[?]): Seq[Sig[Complex[Double]]] = {
        (0 until K).map(p => {
          val j = p % (1 << r)
          val coefs = Vector.tabulate(1 << (this.n - r))(i => DFT.omega(this.n, i * j))
          //val twiddles = Vector.tabulate(T)(c => coef((c * K) + p))
          val twiddleHW = hw match {
            case ComplexHW(FixedPoint(magnitude, fractional)) => ComplexHW(FixedPoint(2, magnitude + fractional - 2))
            case _ => hw
          }
          /*val control = Timer(T)
          val twiddle = ROM(twiddles, control)(twiddleHW, sb)*/
          val control1 = Timer(T) :: Const(p >> r)(Unsigned(this.k - r), implicitly)
          //println(control1)
          val control2a = Counter(this.n / r)
          val control2 = ROM(Vector.tabulate(this.n / r)(i => ((1 << (i * r)) - 1) ^ ((1 << (this.n - r)) - 1)), control2a)(Unsigned(this.n - r), implicitly)
          val control = control1 & control2
          val twiddle = ROM(coefs, control)(twiddleHW, implicitly)
          inputs(p) * twiddle
        })
      }

      override def toString: String = "StreamDiagC(" + this.n + "," + r + "," + this.k + ")"

      override def spl: SPL[Complex[Double]] = StreamDiagC(this.n, r)
    }
  }
}