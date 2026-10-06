object Stats {

  def sumPositive(a: Array[Int]): Int = {
    var s = 0
    var i = 0
    while (i < a.length) {
      if (a(i) > 0) {
        s += a(i)
      }
      i += 1
    }
    s
  }

  def countDigits(n: Int): Int = {
    var c = 0
    var m = n
    do {
      m = m / 10
      c += 1
    } while (m > 0)
    c
  }

  def squares(n: Int): Seq[Int] = {
    val r = for (i <- 1 to n) yield i * i
    r
  }

  def grade(score: Int): String = {
    var g = ""
    score / 10 match {
      case 10 => g = "A"
      case 9 => g = "A"
      case 8 => g = "B"
      case 7 => g = "C"
      case _ => g = "D"
    }
    g
  }

  def classify(a: Array[Int]): Int = {
    var k = 0
    for (i <- 0 until a.length) {
      if (a(i) < 0) {
        k += 1
      } else {
        for (j <- 0 until i) {
          if (a(j) == a(i)) {
            k += 3
          }
        }
      }
    }
    k
  }

  def main(args: Array[String]): Unit = {
    val a = Array(5, -3, 0, 7, 7, 12)
    println(sumPositive(a))
    println(countDigits(12345))
    println(squares(4))
    println(grade(85))
    println(classify(a))
  }
}