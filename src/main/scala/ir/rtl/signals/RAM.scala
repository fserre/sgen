/*
 *    _____ ______          SGen - A Generator of Streaming Hardware
 *   / ___// ____/__  ____  Department of Computer Science, ETH Zurich, Switzerland
 *   \__ \/ / __/ _ \/ __ \
 *  ___/ / /_/ /  __/ / / / Copyright (C) 2020-2021 François Serre (serref@inf.ethz.ch)
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

package ir.rtl.signals

import ir.rtl.{Component, SB}
import ir.rtl.hardwaretype.{HW,Unsigned}


case class DualControlRAM[U](input: Sig[U], addrWr: Sig[Int], addrRd: Sig[Int], latency: Int) extends Sig[U] {
  override def parents: Seq[(Sig[?], Int)] = Seq((input, latency + 2), (addrWr, latency + 2), (addrRd, 1))

  override val hw: HW[U] = input.hw
  override val pipeline = 1

  override def implement(cp: (Sig[?], Int) => Component): Component = {
    val mem = new ir.rtl.RAMWr(cp(addrWr, latency + 2), cp(input, latency + 2))
    new ir.rtl.RAMRd(mem, cp(addrRd, 1))
  }

  override def graphDeclaration: String = graphName + "[label=\"RAM bank (" + (1 << addrRd.hw.size) + " × " + hw.size + " bits, latency=" + latency + ") |<data> Data|<wr> Write address |<rd> Read address \",shape=record];"

  override def graphNode: Seq[String] = {
    List(addrWr.graphName + " -> " + graphName + ":wr;",
      //m.we.sigDef.graphName + " -> " + graphName + ":we;",
      input.graphName + " -> " + graphName + ":data;",
      addrRd.graphName + " -> " + graphName + ":rd;"
    )
  }
  override val hash = Seq(input,addrWr,latency).hashCode()
}

case class SingleControlRAM[U](input: Sig[U], addrWr: Sig[Int], latency: Int, T: Int) extends Sig[U] {
  val timeRd: Int = T + 1

  override def parents: Seq[(Sig[?], Int)] = Seq((input, latency + 2), (addrWr, latency + 2), (addrWr, timeRd))

  override val hw: HW[U] = input.hw

  override val pipeline = 1

  override def implement(cp: (Sig[?], Int) => Component): Component = {
    val mem = new ir.rtl.RAMWr(cp(addrWr, latency + 2), cp(input, latency + 2))
    new ir.rtl.RAMRd(mem, cp(addrWr, timeRd))
  }

  override def graphDeclaration: String = graphName + "[label=\"RAM bank (" + (1 << addrWr.hw.size) + " × " + hw.size + " bits, latency=" + latency + ") |<data> Data|<wr> Write address\",shape=record];"

  override def graphNode: Seq[String] = {
    List(addrWr.graphName + " -> " + graphName + ":wr;",
      //m.we.sigDef.graphName + " -> " + graphName + ":we;",
      input.graphName + " -> " + graphName + ":data;"
    )
  }
  override val hash = Seq(input,addrWr,latency).hashCode()
}

/*
case class DoubleShiftReg[U](input:Sig[U], control:Sig[Int]) extends Sig[U]{
  require(control.hw==Unsigned(1))
  override val hw: HW[U] = input.hw
  override val pipeline = 1
  override def parents: Seq[(Sig[?], Int)] = Seq((input, 2), (input, 0), (control, 0))
  def implement(cp: (Sig[?], Int) => Component): Component =new ir.rtl.Mux(cp(control,0), Seq(cp(input,0),cp(input,2)))
}
*/
/*
object RAM {

  case class RAMWr[U](wrAddress: Sig[Int], input: Sig[U], _latency: Int) extends Operator[U](wrAddress, input)(input.hw) {
    override def implement(implicit cp: Sig[_] => Component): Component = new ir.rtl.RAMWr(wrAddress, input)

    override val latency = _latency + 1

    override def graphDeclaration = ""

    override def graphNode = List()
  }

  case class RAMRd[U](mem: RAMWr[U], rdAddress: Sig[Int]) extends Operator[U](mem, rdAddress)(mem.hw) {
    override def implement(implicit cp: Sig[_] => Component): Component = cp(mem) match {
      case mem: ir.rtl.RAMWr => new ir.rtl.RAMRd(mem, rdAddress)
      case _ => throw new Exception("Expecting a RAM component")
    }

    override val pipeline = 1
    override val latency = 1

    override def graphDeclaration = graphName + "[label=\"RAM bank (" + (1 << rdAddress.hw.size) + " × " + hw.size + " bits, latency=" + mem.latency + ") |<data> Data|<wr> Write address |<rd> Read address \",shape=record];"

    override def graphNode = {
      List(mem.wrAddress.graphName + " -> " + graphName + ":wr;",
        //m.we.sigDef.graphName + " -> " + graphName + ":we;",
        mem.input.graphName + " -> " + graphName + ":data;",
        rdAddress.graphName + " -> " + graphName + ":rd;"
      )
    }

  }

  def apply[U: HW](input: Sig[U], addrWr: Sig[Int], addrRd: Sig[Int], latency: Int) = new RAMRd(new RAMWr(addrWr, input, latency), addrRd)
}
*/