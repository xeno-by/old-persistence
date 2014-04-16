package scala.reflect.persistence

import java.io.DataOutputStream
import scala.annotation.tailrec
import scala.language.postfixOps

class AstCompressor(out: DataOutputStream) {
  import Enrichments._


  /* Reparse the tree using the new dictionary */
  /* TODO: should be either nester or private. Is public here for tests */
  def splitTree(node: Node): (NodeDict, List[List[NodeBFS]], List[(Int, Int)]) = {
    val keyList = node.computeFreqs.toList
      .filter(entry => entry._1.size < Math.sqrt(node.flattenBFS.size))
      .map(entry => (entry._1, entry._1.size * entry._2))
      .sortBy(entry => (entry._2, entry._1.size))
      .map(entry => entry._1).toList
    /* origin dictionary, with empty frequencies */
    val originDict: NodeDict = keyList.map(k => (k, 0)) toMap
    @tailrec def loop(que: List[(Node, Int, Int)], dict: NodeDict, occ: List[List[NodeBFS]], edges: List[(Int, Int)]): (NodeDict, List[List[NodeBFS]], List[(Int, Int)]) = que match {
      case Nil => (dict, occ, edges)
      case nd :: nds =>
        val bfs = nd._1.childrenBFSIdx
        keyList.find(entry => entry.matchBFS(bfs)) match {
          case None => sys.error("Cannot find matching entry in the dictionary")
          case Some(entry) =>
            /* node, parsing index, index of the node in BFS in the tree corresponding to the index to which the subroot was linked */ 
            val subRoots = bfs.intersectBFS(entry).subRoots map (s => (s._1, nds.size, s._2))
            loop(nds ++ subRoots,
              dict + (entry -> (dict(entry) + 1)),
              occ :+ entry,
              edges :+ (nd._2, nd._3))
        }
    }
    val (dict, occ, edges) = loop((node, -1,-1) :: Nil, originDict, Nil, Nil)
    (dict.filter(entry => entry._2 > 0), occ, edges)
  }

  /* TODO: should be either nester or private. Is public here for tests */
  def genHuffman(dict: NodeDict) : Map[List[NodeBFS], List[Byte]] = {
    trait HufTree {val freq: Int }
    case class HufLeaf(key: List[NodeBFS], freq: Int) extends HufTree
    case class HufNode(freq: Int, left: HufTree, right: HufTree) extends HufTree
    @tailrec def computeHufTree(que: List[HufTree]): HufTree = que match {
      case Nil => sys.error("Error in Huffman tree generation ")
      case x :: Nil => x
      case x :: y :: xs => computeHufTree((xs :+ HufNode(x.freq + y.freq, x , y)).sortBy(_.freq))
    }
    def computeHufValues(hufTree: HufTree, cde: List[Byte] = Nil): List[(List[NodeBFS], List[Byte])] = hufTree match{
      case HufLeaf(key, _) => (key, cde) :: Nil
      case HufNode(_, left, right) => computeHufValues(left, cde :+ 0x1.toByte) ++ computeHufValues(right, cde :+ 0x0.toByte)
    }
    val hufQueue: List[HufTree] = dict.toList.map(entry => HufLeaf(entry._1, entry._2))
    computeHufValues(computeHufTree(hufQueue)) toMap
  }
  
  def encodeOccurrences(occ: List[List[NodeBFS]]): String = ???
  
  def encodeDict(dict: NodeDict): String = ???

  def apply(node: Node): Unit = ???
}