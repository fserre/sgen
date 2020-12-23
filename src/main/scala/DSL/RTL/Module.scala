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

import java.io.PrintWriter

import scala.annotation.tailrec
import scala.collection.mutable
import scala.language.implicitConversions
import scala.sys.process._

/**
 * Class that represents a RTL module
 */
abstract class Module {
  def inputs: Seq[Input]

  def outputs: Seq[Output]

  lazy val name: String = this.getClass.getSimpleName.toLowerCase
  
  val dependencies: mutable.Set[String] = mutable.Set[String]()

  def description: Iterator[String] =Iterator[String]()
}
