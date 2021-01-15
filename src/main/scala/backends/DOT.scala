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

package backends
import scala.collection.mutable
import scala.collection.immutable
import scala.annotation.tailrec
import ir.rtl._
import ir.rtl.signals
import ir.rtl.signals.Sig

import scala.sys.process._
import java.io.PrintWriter


object DOT:
  extension (mod: Module) 
    final def toRTLGraph: String = 
      val indexes = immutable.HashMap.from(mod.components.filter(_ match 
        case _: Input | _: Output | _: Wire | _: Const | _: RAMWr => false
        case _ => true
      ).zipWithIndex)

      val consts = mutable.ArrayBuffer[BigInt]()  
        
      @tailrec
      def getName(comp: Component): String = comp match
        case Input(_,name) => s"inputs:$name"
        case Output(_,name) => s"outputs:$name"
        case Wire(input) => getName(input)
        case Const(_, value) => 
          consts.addOne(value)
          s"c${consts.size}"
        case _ => s"s${indexes(comp) + 1}"

      inline def node(comp:Component, options: String) = Some(s"      ${getName(comp)}[$options];\n")
      val nodes=mod.components.flatMap(cur => cur match
          case _: Output | _: Input | _: RAMWr | _: Wire | _: Const => None
          case Register(_, cycles) if cycles == 1 => node(cur, """label="Reg",shape=square""")
          case Register(_, cycles) => node(cur, s"""label="Buffer($cycles cycles)",shape=record""")
          case Plus(_) => node(cur, """label="+",shape=circle""")
          case Or(_) => node(cur, """label="|",shape=circle""")
          case Xor(_) => node(cur, """label="^",shape=circle""")
          case And(_) => node(cur, """label="&",shape=circle""")
          case Minus(_,_) => node(cur, """label="-",shape=circle""")
          case Tap(_,range) => node(cur, s"""label="[${if (range.size > 1) s"${range.last}:" else ""}${range.start}]",shape=triangle,orientation=270""")
          case ROM(address, values) => node(cur, s"""label="<title>ROM (${values.size} × ${cur.size} bits) |${values.map(_.toString).mkString("|")}",shape=record""")
          case Mux(_, _) => node(cur, s"""label="",shape=invhouse,orientation=90""")
          case RAMRd(_,rd) => node(cur, s"""label="RAM bank (${1 << rd.size} × ${cur.size} bits) |<data> Data|<wr> Write address |<rd> Read address ",shape=record""")
          case Extern(_,_,module,_,_) => node(cur, s"""label="$module"""")
          case _ => node(cur, s"""label="${cur.getClass.getSimpleName}"""")
      ).mkString("")

      inline def edge(from: Component, to: String) = s"  ${getName(from)}:e -> $to[penwidth=${1 + BigInt(from.size).bitLength}];\n"
      val edges = mod.components.flatMap {cur => cur match
          case Wire(_) | _: RAMWr => Seq()
          case RAMRd(RAMWr(wr,input),rd) => Seq(edge(wr,getName(cur)+":wr:w"),edge(rd,getName(cur)+":rd:w"),edge(input,getName(cur)+":data:w"))
          case ROM(address, _) => edge(address,s"${getName(cur)}:title:w")
          case Mux(address, inputs) => inputs.map(edge(_,s"${getName(cur)}:w")) :+ edge(address,s"${getName(cur)}:s") 
          case _ => cur.parents.map(edge(_,s"${getName(cur)}:w"))
      }.mkString("")

      var res = new StringBuilder
      res ++= s"digraph ${mod.name} {\n"
      res ++= "  rankdir=LR;\n"
      res ++= "  ranksep=1.5;\n"
      res ++= s"""  outputs[shape=record,label="${mod.outputs.map(i => s"<${i.name}> ${i.name} ").mkString("|")}",height=${mod.outputs.size * 1.5}];\n"""
      res ++= s"""  inputs[shape=record,label="${mod.inputs.map { i => "<" + i.name + "> " + i.name + " " }.mkString("|")}",height=${mod.inputs.size * 1.5}];\n"""
      res ++= nodes
      res ++= consts.zipWithIndex.map((v,i)=>s"""      c${i+1}[label="$v",shape=none];\n""").mkString("")
      res ++= edges
      res ++= "}\n"
      res.toString()

    final def showRTLGraph: String = 
      val graph = toRTLGraph
      val pw = PrintWriter("rtl.gv")
      pw.write(graph)
      pw.close()
      "Graphviz/dot -Tpdf rtl.gv -o rtl.pdf".!!
      "cmd /c start rtl.pdf".!!
  
  extension [U](sb: AcyclicStreamingModule[U])
    def toGraph:String =
      val sigs=sb.synch.keys
      val indexes = immutable.HashMap.from(sigs.filter(_ match
        case _: signals.Input[?] | signals.Next | signals.Reset => false
        case _ => true
      ).zipWithIndex)

      val consts = mutable.ArrayBuffer[String]()

      def getName(sig: Sig[?]): String = sig match
        case signals.Input(i) => s"inputs:i$i"
        case signals.Const(value) =>
          consts.addOne(value.toString)
          s"c${consts.size}"
        case signals.Next =>
          consts.addOne("Next")
          s"c${consts.size}"
        case signals.Reset =>
          consts.addOne("Reset")
          s"c${consts.size}"
        case _ => s"s${indexes(sig) + 1}"

      inline def node(sig:Sig[?], options: String) = Some(s"      ${getName(sig)}[$options];\n")
      val nodes=sigs.flatMap(cur => cur match
        case _: signals.Input[?] | _: signals.Const[?] | signals.Next | signals.Reset => None
        case cur:signals.AssociativeSig[?] => node(cur, s"""label="${cur.op}"""")
        case cur:signals.Plus[?] => node(cur, s"""label="+"""")
        case cur:signals.Minus[?] => node(cur, s"""label="-"""")
        case cur:signals.Times[?] => node(cur, s"""label="*"""")
        case signals.ROM(values,address) => node(cur, s"""label="<title>ROM (${values.size} × ${cur.hw.size} bits) |${values.map(_.toString).mkString("|")}",shape=record""")
        case signals.DualControlRAM(input,wr,rd,_) => node(cur, s"""label="RAM bank (${1 << rd.hw.size} × ${cur.hw.size} bits) |<data> Data|<wr> Write address |<rd> Read address ",shape=record""")
        case signals.SingleControlRAM(input,wr,_,_) => node(cur, s"""label="RAM bank (${1 << wr.hw.size} × ${cur.hw.size} bits) |<data> Data|<wr> Write address |<rd> Read address ",shape=record""")
        case _ => node(cur, s"""label="${cur.getClass.getSimpleName}"""")
      ).mkString("")

      inline def edge(from: Sig[?], to: String) = s"  ${getName(from)}:e -> $to[penwidth=${1 + BigInt(from.hw.size).bitLength}];\n"
      val edges=sigs.flatMap {cur => cur match
        case signals.ROM(_,address) => Seq(edge(address,s"${getName(cur)}:title:w"))
        case signals.DualControlRAM(input,wr,rd,_) => Seq(edge(input,s"${getName(cur)}:data:w"),edge(wr,s"${getName(cur)}:wr:w"),edge(rd,s"${getName(cur)}:rd:w"))
        case signals.SingleControlRAM(input,wr,_,_) => Seq(edge(input,s"${getName(cur)}:data:w"),edge(wr,s"${getName(cur)}:wr:w"),edge(wr,s"${getName(cur)}:rd:w"))
        case _ => cur.parents.map(parent=>edge(parent._1,getName(cur)))
      }.mkString("")
        
        
      val res = new StringBuilder
      res ++= "digraph " + sb.name + " {\n"
      res ++= "  rankdir=LR;\n"
      res ++= "  ranksep=1.5;\n"
      res ++= "  outputs[shape=record,label=\"" + (0 until sb.K).map(i => "<o" + i + "> " + i + " ").mkString("|") + "\",height=" + (sb.K * 1.5) + "];\n"
      res ++= "  inputs[shape=record,label=\"" + (0 until sb.K).map(i => "<i" + i + "> " + i + " ").mkString("|") + "\",height=" + (sb.K * 1.5) + "];\n"
      res ++= nodes
      res ++= consts.zipWithIndex.map((v,i)=>s"""      c${i+1}[label="$v",shape=none];\n""").mkString("")
      res ++= edges
      res ++= sb.outputSigs.zipWithIndex.map { case (s, i) => s"  ${getName(s)} -> outputs:o$i:w[penwidth=${1 + BigInt(s.hw.size).bitLength}];\n"}.mkString("")
      res ++= "}\n"
      res.toString()

    def showGraph = 
      val graph = sb.toGraph
      val pw = PrintWriter("graph.gv") 
      pw.write(graph)
      pw.close()
      "dot -Tpdf graph.gv -o graph.pdf".!!
      "cmd /c start graph.pdf".!!