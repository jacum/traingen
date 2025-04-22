import java.io._
import java.net.{HttpURLConnection, URI, URL}
import java.net.URLEncoder

object MaryTTSGenerator {
  val maryHost = "http://localhost:59125"
  val outputDir = "./audio_outputs/"

  def synthesize(text: String, fileName: String, voice: String = "cmu-slt-hsmm"): Unit = {
    val params = Map(
      "INPUT_TEXT" -> text,
      "INPUT_TYPE" -> "TEXT",
      "OUTPUT_TYPE" -> "AUDIO",
      "AUDIO" -> "WAVE",
      "VOICE" -> voice
    ).map { case (k, v) => s"$k=${URLEncoder.encode(v, "UTF-8")}" }.mkString("&")

    val url = URI.create(s"$maryHost/process?$params").toURL
    val conn = url.openConnection().asInstanceOf[HttpURLConnection]
    conn.setRequestMethod("GET")

    if (conn.getResponseCode == 200) {
      new File(outputDir).mkdirs()
      val inputStream = conn.getInputStream
      val outputStream = new FileOutputStream(s"$outputDir$fileName.wav")

      val buffer = new Array[Byte](8192)
      Iterator.continually(inputStream.read(buffer))
        .takeWhile(_ != -1)
        .foreach(read => outputStream.write(buffer, 0, read))

      outputStream.close()
      inputStream.close()
      println(s"✅ Generated: $fileName.wav")
    } else {
      println(s"❌ Failed to generate: $text (${conn.getResponseCode})")
    }
  }

  def main(args: Array[String]): Unit = {
    val utterances = Map(
      "hello_world" -> "Hello, world!",
      "server_error" -> "A server error has occurred.",
      "goodbye" -> "Goodbye and thank you!"
    )

    utterances.foreach { case (fileName, text) =>
      synthesize(text, fileName)
    }
  }
}