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

package ir.rtl

import ir.rtl.hardwaretype.HW
import ir.rtl.signals._
import ir.rtl.{Component, StreamingModule}

import java.io.PrintWriter
import scala.collection.mutable
import scala.sys.process._


abstract class SB[U](t: Int, k: Int)(implicit hw:HW[U]) extends StreamingModule(t, k) {

  def implement(inputs: Seq[Sig[U]])(implicit sb: SB[?]): Seq[Sig[U]]

  private val signals = new mutable.ArrayBuffer[Sig[?]]()
  private val refs = new mutable.HashMap[Sig[?], Int]()
  private implicit val sb:SB[U] = this

  final def signal(i: Int):Sig[?] = signals(i)
  final def ref(sig: Sig[?]):Int = refs.getOrElseUpdate(sig, {
    val res = signals.size
    signals += sig
    res
  })

  final override def implement(rst: Component, token: Int => Component, inputs: Seq[Component]): Seq[Component] = {
    val inputSigs = inputs.map(c => Input(c,hw,this))
    val inputIndexes = inputSigs.map(_.ref.i)

    val outputs = implement(inputSigs)
    assert(outputs.forall(_.sb == this))

    val synch = mutable.HashMap[Int, Int]()
    outputs.foreach(cur => synch.put(cur.ref.i, cur.pipeline))
    inputSigs.foreach(cur => synch.put(cur.ref.i, 0))
    val toImplement = mutable.BitSet.fromSpecific(outputs.map(_.ref.i))
    while (toImplement.nonEmpty) {
      val cur = toImplement.last
      toImplement.remove(cur)
      val curSig = signal(cur)
      curSig.parents.foreach { case (s: SigRef[?], advance: Int) =>
        val time = synch(cur) + advance + s.pipeline
        //println(time)
        synch.get(s.i) match {
          case Some(i) if i > time =>
          case _ => synch.put(s.i, time)
                    toImplement.add(s.i)
        }
      }
    }

    val latency = inputIndexes.map(synch).max
    inputIndexes.foreach(synch(_) = latency)
    _latency = Some(latency)
    val implemented = new mutable.HashMap[(Int, Int), Component]()

    def implementComp(time: Int)(ref: SigRef[?], advance: Int): Component = {
      val advancedTime = time + advance
      ref.sig match {
        case Next() => token(latency - advancedTime)
        case Reset() => rst
        case _: Const[?] => implemented.getOrElseUpdate((ref.i, advancedTime), ref.implement(implementComp(advancedTime)))
        case _ =>
          val diff = synch(ref.i) - advancedTime
          assert(diff >= 0)
          implemented.getOrElseUpdate((ref.i, advancedTime), if (diff == 0) {
            val res = ref.implement(implementComp(advancedTime))
            res.description = ref.sig.toString
            res
          } else
            implementComp(advancedTime)(ref, 1).register)
      }
    }

    outputs.map(implementComp(0)(_, 0))
  }

  private var _latency: Option[Int] = None

  final def latency: Int = {
    if (_latency.isEmpty) outputs
    _latency.get
  }
}