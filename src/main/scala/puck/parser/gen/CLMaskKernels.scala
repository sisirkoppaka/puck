package puck.parser.gen

import puck.parser.{RuleSemiring, RuleStructure}
import puck.linalg.CLMatrix
import com.nativelibs4java.opencl._
import org.bridj.Pointer
import java.util.zip.{ZipOutputStream, ZipFile}
import puck.util.ZipUtil
import scala.Array

/**
 * TODO
 *
 * @author dlwh
 **/
case class CLMaskKernels(maskSize: Int, getMasksKernel: CLKernel) {


  def write(out: ZipOutputStream) {
    ZipUtil.addKernel(out, "computeMasksKernel", getMasksKernel)
    ZipUtil.serializedEntry(out, "MasksInts", Array(maskSize))
  }

  def getMasks(masks: CLMatrix[Int],
               inside: CLMatrix[Float],
               outside: CLMatrix[Float],
               firstOutside: Int,
               chartIndices: Array[Int],
               root: Int, threshold: Float,
               events: CLEvent*)(implicit queue: CLQueue):CLEvent = {
    require(masks.rows == maskSize, masks.rows + " " + maskSize)
    require(masks.cols == inside.cols)
    require(masks.cols == outside.cols)
    queue.finish()



    val ptrCI = Pointer.pointerToArray[java.lang.Integer](chartIndices)
    val intBufferCI = queue.getContext.createIntBuffer(CLMem.Usage.InputOutput, chartIndices.length)
    val evCI = intBufferCI.write(queue, 0, chartIndices.length, ptrCI, false, events:_*)

    getMasksKernel.setArgs(masks.data.safeBuffer,
      inside.data.safeBuffer, outside.data.safeBuffer, Integer.valueOf(outside.offset), intBufferCI,
      Integer.valueOf(chartIndices(chartIndices.length-1)), Integer.valueOf(inside.rows),
      Integer.valueOf(root), java.lang.Float.valueOf(threshold))
    //, LocalSize.ofIntArray(fieldSize * groupSize * 5))

    val ev = getMasksKernel.enqueueNDRange(queue, Array(chartIndices.length-1, 1), Array(1, 1), evCI)

    ev.invokeUponCompletion(new Runnable() {
      def run() = { ptrCI.release(); intBufferCI.release();}
    })
    ev
  }

}

object CLMaskKernels {
  def read(zf: ZipFile)(implicit ctxt: CLContext) = {
    val ints = ZipUtil.deserializeEntry[Array[Int]](zf.getInputStream(zf.getEntry("MasksInts")))
    CLMaskKernels(ints(0), ZipUtil.readKernel(zf, "computeMasksKernel"))
  }

  def make[C, L](structure: RuleStructure[C, L])(implicit context: CLContext, semiring: RuleSemiring) = {
    val cellSize = (structure.numNonTerms max structure.numTerms)
    val maskSize = structure.maskSize

    val prog = context.createProgram(programText(cellSize, structure))

    CLMaskKernels(maskSize, prog.createKernel("computeMasks"))
  }


  def programText[L, C](cellSize: Int, structure: RuleStructure[C, L]): String = {


    structure.maskHeader ++ """
      #define NUM_SYMS """ + cellSize + """

      """ + structure.terminalMap.padTo(cellSize, 0).mkString("__constant int terminalProjections[] = {", ", ", "};") +
      """
      """ + structure.nonterminalMap.padTo(cellSize, 0).mkString("__constant int nonterminalProjections[] = {", ", ", "};") +
      """

// each global_id(0) corresponds to a single sentence.
// we have some number of workers for each sentence, global_size(1)
// for each cell in the sentence, each worker in parallel reads a sym from a cell, thresholds it, and then sets
// the mask if the threshold is exceeded. Each worker has its own mask for its share of the cells. At the
// end, the masks are or'd together and written out.
// the masks are then
// indices(i) is the first cell in the i'th sentence
// indices(i+1)-1 is the last cell in the i'th sentence
// the last cell has the root score.
//
/** TODO this isn't optimized at all */
__kernel void computeMasks(__global mask_t* masksOut,
                           __global const float* inside,
                           __global const float* _outside,
                           const int _outsideOff,
                           __global const int* indices,
                           const int numIndices,
                           int numSyms,
                           int root,
                           float thresh) {
  const int sentence = get_global_id(0);
  const int firstCell = indices[sentence];
  const int lastCell = indices[sentence + 1];
  __global const float* outside = _outside + _outsideOff;
  const float root_score = inside[(lastCell-1) * numSyms + root];

  float cutoff = root_score + thresh;

  for(int cell = firstCell; cell < lastCell; cell++) {
    __constant const int* projections = (masksOut[cell].fields[0] == 0) ? nonterminalProjections : terminalProjections;

    __global const float* in = inside + (cell * numSyms);
    __global const float* out = outside + (cell * numSyms);
    mask_t myMask;
    for(int i = 0; i < NUM_FIELDS; ++i) {
      myMask.fields[i] = 0;
    }

    for(int sym = 0; sym < NUM_SYMS; ++sym) {
      float score = (in[sym] + out[sym]);
      int keep = score >= cutoff;
      int field = projections[sym];

      set_bit(&myMask, field, keep);
    }
    masksOut[cell] = myMask;
  }

}
      """
  }
}
