import org.bridj.Pointer

/**
 * TODO
 *
 * @author dlwh
 **/
package object puck {
  implicit class RichPointer[T](pointer: Pointer[T]) extends AnyVal {
    def update(v: T) {pointer.set(v)}
    def update(off: Long, v: T) {pointer.set(off, v)}
    def apply(off: Long = 0) {pointer.get(off)}
    def toArray = {pointer.toArray}

    def +(off: Long) = pointer.next(off)
  }

  implicit class RichFloatPointer(pointer: Pointer[Float]) extends AnyVal {
    def update(v: Float) {pointer.setFloat(v)}
    def update(off: Long, v: Float) {pointer.setFloatAtIndex(off, v)}
    def apply(off: Long = 0) {pointer.getFloatAtIndex(off)}
    def toArray = {pointer.getFloats}
    def copyToArray(array: Array[Float]) {
      pointer.getFloats(array)
    }

    def +(off: Long) = pointer.next(off)
  }

  implicit class RichIntPointer(pointer: Pointer[Int]) extends AnyVal {
    def update(v: Int) {pointer.setInt(v)}
    def update(off: Long, v: Int) {pointer.setIntAtIndex(off, v)}
    def apply(off: Long = 0) {pointer.getIntAtIndex(off)}
    def toArray = {pointer.getInts}
    def copyToArray(array: Array[Int]) {
      pointer.getInts(array)
    }

    def +(off: Long) = pointer.next(off)
  }

  implicit class RichDoublePointer(pointer: Pointer[Double]) extends AnyVal {
    def update(v: Double) {pointer.setDouble(v)}
    def update(off: Long, v: Double) {pointer.setDoubleAtIndex(off, v)}
    def apply(off: Long = 0) {pointer.getDoubleAtIndex(off)}
    def toArray = {pointer.getDoubles}
    def copyToArray(array: Array[Double]) {
      pointer.getDoubles(array)
    }


    def +(off: Long) = pointer.next(off)
  }

  implicit class RichCharPointer(pointer: Pointer[Char]) extends AnyVal {
    def update(v: Char) {pointer.setChar(v)}
    def update(off: Long, v: Char) {pointer.setCharAtIndex(off, v)}
    def apply(off: Long = 0) {pointer.getCharAtIndex(off)}
    def toArray = {pointer.getChars}
    def copyToArray(array: Array[Char]) {
      pointer.getChars(array.length).copyToArray(array)
    }

    def +(off: Long) = pointer.next(off)
  }

  implicit class RichLongPointer(pointer: Pointer[Long]) extends AnyVal {
    def update(v: Long) {pointer.setLong(v)}
    def update(off: Long, v: Long) {pointer.setLongAtIndex(off, v)}
    def apply(off: Long = 0) {pointer.getLongAtIndex(off)}
    def toArray = {pointer.getLongs}
    def copyToArray(array: Array[Long]) {
      pointer.getLongs(array.length).copyToArray(array)
    }

    def +(off: Long) = pointer.next(off)
  }


}
