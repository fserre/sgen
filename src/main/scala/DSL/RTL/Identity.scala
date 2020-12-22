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

package DSL.RTL

import DSL.RTL.Signals.Sig
import DSL.RTL.HardwareType.HW
import DSL.RTL.{SB, StreamingModule}
import DSL.SPL.SPL

case class Identity[T:HW](override val t:Int,override val k:Int) extends SB[T](t,k) {
  override def implement(inputs: Seq[Sig[T]])(implicit sb:SB[?]): Seq[Sig[T]] = inputs
  override def spl: SPL[T] = DSL.SPL.Identity[T](t+k)
}

object Identity{
  def unapply[T](arg: StreamingModule[T]):Boolean = arg match{
    case _:Identity[T] => true
    case _ => false
  }
}