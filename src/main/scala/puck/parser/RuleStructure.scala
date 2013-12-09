package puck.parser

import epic.parser.projections.GrammarRefinements
import epic.parser.BaseGrammar
import epic.trees.{BinaryRule, UnaryRule}
import breeze.util.Index

/**
 *
 * @author dlwh
 */
@SerialVersionUID(1L)
case class RuleStructure[C, L](refinements: GrammarRefinements[C, L], grammar: BaseGrammar[L], scores: Array[Float]) {

  def numCoarseSyms = refinements.labels.coarseIndex.size

  def maskSize = puck.roundUpToMultipleOf(numCoarseSyms, 32) / 32

  val (
  nontermIndex,
  termIndex,
  leftTermRules,
  rightTermRules,
  nontermRules,
  bothTermRules,
  unaryRules,
  unaryTermRules,
  identityTermUnaries,
  root: Int) = {
    val rules = grammar.index.zipWithIndex.toIndexedSeq
    // jesus
    val termIndex, nontermIndex = Index[L]()
    val nonterms = rules.collect {
      case (BinaryRule(p,_,_),_) => p
      case (UnaryRule(p,c, _),_) if p != c => p
    }.toSet


    val rhsSyms = rules.flatMap {
      case (rule@BinaryRule(p,l,r),_) => Iterator(l,r)
      case (rule@UnaryRule(p,c,_),_) if p != c => Iterator(c)
      case (rule@UnaryRule(p,c,_),_) => Iterator.empty
    }.toSet

    val syms = nonterms ++ rhsSyms

    val idedSyms = {
      for(sym <- syms) yield {
        val isTerm = !nonterms.contains(sym)
        if(isTerm) {
          sym -> SymId(refinements.labels.project(sym), sym, grammar.labelIndex(sym), termIndex.index(sym), isTerm)
        } else {
          sym -> SymId(refinements.labels.project(sym), sym, grammar.labelIndex(sym), nontermIndex.index(sym), isTerm)
        }
      }
    }.toMap

    val root = grammar.root

    val ( _binaries, _unaries) = rules.map {
      case (r,i) => (r.map(idedSyms), i)
    }.partition(_._1.isInstanceOf[BinaryRule[_]])

    val binaries = _binaries.asInstanceOf[IndexedSeq[(BinaryRule[SymId[C, L]], Int)]]
    val unaries = _unaries.asInstanceOf[IndexedSeq[(UnaryRule[SymId[C, L]], Int)]]
    val groupedByTerminess = binaries.groupBy {case (r,i) => r.left.isTerminal -> r.right.isTerminal}

    val leftTermRules = groupedByTerminess.getOrElse(true -> false, IndexedSeq.empty)
    val rightTermRules = groupedByTerminess.getOrElse(false -> true, IndexedSeq.empty)
    val bothTermRules = groupedByTerminess.getOrElse(true -> true, IndexedSeq.empty)
    val nontermRules = groupedByTerminess.getOrElse(false -> false, IndexedSeq.empty)

    // exclude identity unaries for terminals, which have a terminal parent.
    val uByTerminess = unaries.filter{case (r,_) => !r.parent.isTerminal} groupBy {case (r,i) => r.child.isTerminal}
    val tUnaries = uByTerminess.getOrElse(true, IndexedSeq.empty)
    val ntUnaries = uByTerminess.getOrElse(false, IndexedSeq.empty)
    val tIdentUnaries = unaries.collect { case pair@(UnaryRule(p, c, _), _) if p == c && p.isTerminal => pair }

    (nontermIndex, termIndex, leftTermRules,
      rightTermRules, nontermRules, bothTermRules, ntUnaries, tUnaries, tIdentUnaries, nontermIndex(root))
  }


  def numTerms = termIndex.size
  def numNonTerms = nontermIndex.size

  /** Maps an indexed terminal symbol back to the grammar's *refined* index*/
  val projectedTerminalMap = Array.tabulate(numTerms)(i => refinements.labels.project(grammar.labelIndex(termIndex.get(i))))
  val projectedNonterminalMap = Array.tabulate(numNonTerms)(i => refinements.labels.project(grammar.labelIndex(nontermIndex.get(i))))

  /** Maps an indexed terminal symbol back to the grammar's *refined* index*/
  val terminalMap = Array.tabulate(numTerms)(i => grammar.labelIndex(termIndex.get(i)))
  /** Maps an indexed nonterminal symbol back to the grammar's *refined* index*/
  val nonterminalMap = Array.tabulate(numNonTerms)(i => grammar.labelIndex(nontermIndex.get(i)))
  val reverseIndex = Array.fill[Int](grammar.labelIndex.size)(-1)
  for(i <- 0 until terminalMap.length) {
    reverseIndex(terminalMap(i)) = i
  }
  for(i <- 0 until nonterminalMap.length) {
    reverseIndex(nonterminalMap(i)) = i
  }
  def labelIndexToTerminal(label: Int) = reverseIndex(label)
  def labelIndexToNonterminal(label: Int) = reverseIndex(label)

  def clusterer:GrammarClusterer[C, L] = new AgglomerativeGrammarClusterer()//55)//new ILPGrammarClusterer(12, 55)
  def unaryClusterer:GrammarClusterer[C, L] = new AgglomerativeGrammarClusterer()//55)//new ILPGrammarClusterer(12, 55)
  def termParentClusterer:GrammarClusterer[C, L] = new AgglomerativeGrammarClusterer()//55)//new ILPGrammarClusterer(12, 55)

  lazy val nontermUnariesParent : IndexedSeq[IndexedSeq[(UnaryRule[SymId[C, L]], Int)]] = clusterer.partitionUnaries(unaryRules).toIndexedSeq
  lazy val nontermUnariesChild : IndexedSeq[IndexedSeq[(UnaryRule[SymId[C, L]], Int)]] = clusterer.partitionUnaries(unaryRules).toIndexedSeq

  lazy val termUnariesParent : IndexedSeq[IndexedSeq[(UnaryRule[SymId[C, L]], Int)]] = unaryClusterer.partitionUnaries(unaryTermRules).toIndexedSeq
  lazy val termUnariesChild : IndexedSeq[IndexedSeq[(UnaryRule[SymId[C, L]], Int)]] = termParentClusterer.partitionUnaries(unaryTermRules).toIndexedSeq

  def numRules = grammar.index.size

  // TODO this really shouldn't be here:
  def maskHeader =  """#define NUM_FIELDS """ + maskSize + """

  typedef struct { int fields[NUM_FIELDS]; } mask_t;

  inline void set_bit(mask_t* mask, int bit, int shouldSet) {
    int field = (bit/32);
    int modulus = bit%32;
    mask->fields[field] = mask->fields[field] | (shouldSet<<(modulus));
  }

  /* Intel gets sad from this one?
  inline int is_set(mask_t* mask, int bit) {
    int field = (bit/32);
    int modulus = bit%32;
    return mask->fields[field] & (1<<(modulus));
  }
  */

   #define is_set(mask, bit)  ((mask)->fields[(bit)/32] & (1<<((bit)%32)))

                                                           """
}

final case class SymId[C, L](coarseSym: C, fineSym: L, system: Int, gpu: Int, isTerminal: Boolean)