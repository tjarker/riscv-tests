import java.io.{File, FileWriter}
import scala.io.Source
import scala.util.matching.Regex

object SpikeLogToCsv extends App {

  val hexNumPat = "(0x[0-9A-Fa-f]+)"

  val instructionInfoPattern: Regex =
    s"""core\\s+\\d+:\\s+$hexNumPat\\s+\\($hexNumPat\\)\\s+(.+)""".r

  object CommitInfoPattern {

    val regCommit: Regex =
      s"""core\\s+\\d+:\\s+\\d+\\s+$hexNumPat\\s+\\($hexNumPat\\)\\s+x(\\d+)\\s+$hexNumPat""".r

    val loadCommit: Regex =
      s"""core\\s+\\d+:\\s+\\d+\\s+$hexNumPat\\s+\\($hexNumPat\\)\\s+x(\\d+)\\s+$hexNumPat\\s+mem\\s+$hexNumPat""".r

    val storeCommit: Regex =
      s"""core\\s+\\d+:\\s+\\d+\\s+$hexNumPat\\s+\\($hexNumPat\\)\\s+mem\\s+$hexNumPat\\s+$hexNumPat""".r

    val noCommit: Regex =
      s"""core\\s+\\d+:\\s+\\d+\\s+$hexNumPat\\s+\\($hexNumPat\\)""".r

  }

  class UsageExeption extends Exception("""
    |Usage: 
    | SpikeLogToCsv <source> <dest>
    | SpikeLogToCsv -a <source1> <dest1> <source2> <dest2> ...
    |""".stripMargin)
  class ParsingException(file: String, line: Int, content: String)
      extends Exception(s"Error parsing $file:$line\n$content")
  class LogPairMismatchException(file: String, line: Int)
      extends Exception(
        s"The pair of log messages at line $file:$line do not match."
      )

  def shortenHexString(s: String): String = if(s == "") "" else {
      val shortened = s.drop(2).dropWhile(_ == '0')
      if(shortened == "") "0" else shortened
    }

  def removeSpinTrace(items: Seq[(String, Int)]): Boolean = items match {
    case Seq((first, _), (second, _)) =>
      first match {
        case CommitInfoPattern.noCommit(_, _) => false
        case _                                => true
      }
    case _ => false
  }

  def removeTrapLines(item: String): Boolean = !item.contains(">>>>")

  def transform(source: String)(items: Seq[(String, Int)]): String = {
    val Seq((first, i), (second, _)) = items

    val (pc, instr, assem) = first match {
      case instructionInfoPattern(pc, instr, assem) =>
        (pc, instr, assem.replaceAll("\\s+", " "))
      case _ =>
        throw new ParsingException(source, i, first)
    }

    val pcStr = (BigInt(pc.drop(2), 16)).toString(16)

    if(assem == "ecall") return s"$pcStr;${shortenHexString(instr)};$assem;;;;\n"

    val (pc2, instr2, dest, value, addr, wrData) = second match {
      case CommitInfoPattern.regCommit(pc, instr, dest, value) =>
        (pc, instr, dest, value, "", "")
      case CommitInfoPattern.loadCommit(pc, instr, dest, value, addr) =>
        (
          pc,
          instr,
          dest,
          value,
          addr,
          ""
        )
      case CommitInfoPattern.storeCommit(pc, instr, addr, wrData) =>
        (
          pc,
          instr,
          "",
          "",
          addr,
          wrData
        )
      case CommitInfoPattern.noCommit(pc, instr) =>
        (pc, instr, "", "", "", "")
      case _ =>
        throw new ParsingException(source, i + 1, second)
    }

    if (pc != pc2 || instr != instr2)
      throw new LogPairMismatchException(source, i)

    s"$pcStr;${shortenHexString(instr)};$assem;$dest;${shortenHexString(value)};${shortenHexString(addr)};${shortenHexString(wrData)}\n"
  }

  def process(source: String, sink: String): Unit = {
    val infile = Source.fromFile(source)
    val outfile = new FileWriter(new File(sink))

    infile
      .getLines()
      .drop(22)
      .filter(removeTrapLines)
      .zipWithIndex
      .grouped(2)
      .filter(removeSpinTrace)
      .map(transform(source))
      .toSeq
      .dropRight(6)
      .foreach(outfile.write)
      

    infile.close()
    outfile.close()
  }

  args.toList match {
    case "-a"::rest => {
      rest.grouped(2).foreach { case Seq(s, d) => process(s, d) }
    }
    case a::b::Nil => process(a, b)
    case _ =>
      throw new UsageExeption
  }

  
}
