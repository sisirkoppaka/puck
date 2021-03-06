package puck.parser.gen

import puck.util._
import com.nativelibs4java.opencl._
import java.util.zip._
import epic.trees._
import puck.parser._
import java.io.{PrintStream, FileOutputStream}
import epic.trees.BinaryRule
import epic.trees.UnaryRule
import puck.parser.SymId
import puck.parser.RuleStructure

// These kernels assume that the parent and the child named (L or R) are swapped
// in the workspace tables.
case class CLOutsideKernels(outside_L_NNKernels: CLBinaryRuleUpdater,
                            outside_R_NNKernels: CLBinaryRuleUpdater,
                            outside_L_NTKernels: CLBinaryRuleUpdater,
                            outside_R_NTKernels: CLBinaryRuleUpdater,
                            outside_L_TNKernels: CLBinaryRuleUpdater,
                            outside_R_TNKernels: CLBinaryRuleUpdater,
                            outside_L_TTKernels: CLBinaryRuleUpdater,
                            outside_R_TTKernels: CLBinaryRuleUpdater,
                            outsideNUKernels: CLUnaryRuleUpdater,
                            outsideTUKernels: CLUnaryRuleUpdater) {

  def write(prefix: String, out: ZipOutputStream) {
    outside_L_NNKernels.write(s"$prefix/outside_L_NN", out)
    outside_R_NNKernels.write(s"$prefix/outside_R_NN", out)
    outside_L_NTKernels.write(s"$prefix/outside_L_NT", out)
    outside_R_NTKernels.write(s"$prefix/outside_R_NT", out)
    outside_L_TNKernels.write(s"$prefix/outside_L_TN", out)
    outside_R_TNKernels.write(s"$prefix/outside_R_TN", out)
    outside_L_TTKernels.write(s"$prefix/outside_L_TT", out)
    outside_R_TTKernels.write(s"$prefix/outside_R_TT", out)
    outsideNUKernels.write(s"$prefix/outsideNU", out)
    outsideTUKernels.write(s"$prefix/outsideTU", out)
  }
}

object CLOutsideKernels {

  def read(prefix: String, in: ZipFile)(implicit context: CLContext) = {
    val outside_L_NN = CLBinaryRuleUpdater.read(in, s"$prefix/outside_L_NN")
    val outside_R_NN = CLBinaryRuleUpdater.read(in, s"$prefix/outside_R_NN")
    val outside_L_NT = CLBinaryRuleUpdater.read(in, s"$prefix/outside_L_NT")
    val outside_R_NT = CLBinaryRuleUpdater.read(in, s"$prefix/outside_R_NT")
    val outside_L_TN = CLBinaryRuleUpdater.read(in, s"$prefix/outside_L_TN")
    val outside_R_TN = CLBinaryRuleUpdater.read(in, s"$prefix/outside_R_TN")
    val outside_L_TT = CLBinaryRuleUpdater.read(in, s"$prefix/outside_L_TT")
    val outside_R_TT = CLBinaryRuleUpdater.read(in, s"$prefix/outside_R_TT")
    val outsideNU = CLUnaryRuleUpdater.read(in, s"$prefix/outsideNU")
    val outsideTU = CLUnaryRuleUpdater.read(in, s"$prefix/outsideTU")
    CLOutsideKernels(outside_L_NN, outside_R_NN,
      outside_L_NT, outside_R_NT,
      outside_L_TN, outside_R_TN,
      outside_L_TT, outside_R_TT,
      outsideNU, outsideTU)
  }

  def rotateLeftToParent[C, L](r: (BinaryRule[SymId[C, L]], Int)) = {
    BinaryRule(r._1.left, r._1.parent, r._1.right) -> r._2
  }

  def rotateRightToParent[C, L](r: (BinaryRule[SymId[C, L]], Int)) = {
    BinaryRule(r._1.right, r._1.left, r._1.parent) -> r._2
  }

  def rotateChildToParent[C, L](r: (UnaryRule[SymId[C, L]], Int)) = {
    UnaryRule(r._1.child, r._1.parent, r._1.chain) -> r._2
  }

  def make[C, L](structure: RuleStructure[C, L], directWrite: Boolean, semiring: RuleSemiring, genType: GenType = GenType.VariableLength)(implicit context: CLContext) = {
    val parserGen = genType.generator(structure, directWrite, semiring)
    val outside_L_NNKernels = parserGen.binaryRuleApplication(structure.nontermRules.map(rotateLeftToParent), "outside_L_nn_binaries", LoopType.OutsideL)

    val outside_R_NNKernels =
      parserGen.binaryRuleApplication(structure.nontermRules.map(rotateRightToParent), "outside_R_nn_binaries", LoopType.OutsideR)

    val outside_L_NTKernels =
      parserGen.binaryRuleApplication(structure.rightTermRules.map(rotateLeftToParent), "outside_L_nt_binaries", LoopType.OutsideLTerm)

    val outside_R_NTKernels =
      parserGen.binaryRuleApplication(structure.rightTermRules.map(rotateRightToParent), "outside_R_nt_binaries", LoopType.OutsideR)

    val outside_L_TNKernels =
      parserGen.binaryRuleApplication(structure.leftTermRules.map(rotateLeftToParent), "outside_L_tn_binaries", LoopType.OutsideL)

    val outside_R_TNKernels =
      parserGen.binaryRuleApplication(structure.leftTermRules.map(rotateRightToParent), "outside_R_tn_binaries", LoopType.OutsideRTerm)

    val outside_L_TTKernels =
       parserGen.binaryRuleApplication(structure.bothTermRules.map(rotateLeftToParent), "outside_L_tt_binaries", LoopType.OutsideLTerm)

    val outside_R_TTKernels =
      parserGen.binaryRuleApplication(structure.bothTermRules.map(rotateRightToParent), "outside_R_tt_binaries", LoopType.OutsideRTerm)

    val outsideNUKernels =  parserGen.unaryRuleApplication(structure.unaryRules.map(rotateChildToParent), "outside_nn_unaries")

    val outsideTUKernels =  parserGen.unaryRuleApplication(structure.unaryTermRules.map(rotateChildToParent), "outside_nt_unaries")

    CLOutsideKernels(outside_L_NNKernels,
      outside_R_NNKernels,
      outside_L_NTKernels,
      outside_R_NTKernels,
      outside_L_TNKernels,
      outside_R_TNKernels,
      outside_L_TTKernels,
      outside_R_TTKernels,
      outsideNUKernels,
      outsideTUKernels)
  }
}
