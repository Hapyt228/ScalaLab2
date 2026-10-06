object Gilb {

  def main(args: Array[String]): Unit = {
    var result = 0
    var counter = 0
    val mode = 2

    if (mode >= 0) {
      mode match {
        case 0 =>
          if (result < 10) {
            while (counter < 5) {
              if (counter % 2 == 0) {
                result += counter
              } else {
                result -= 1
              }
              counter += 1
            }
          }
        case 1 | 2 =>
          for (number <- 0 until 5) {
            number match {
              case 0 =>
                if (result == 0) {
                  result = 10
                }
              case 1 | 2 =>
                if (result < 20) {
                  result += number
                } else {
                  result -= 1
                }
              case _ =>
                result += 1
            }
          }
        case _ =>
          while (true) {
            if (counter >= 3) {
              return
            }
            counter += 1
          }
      }
    }
  }
}