package ai.metarank.tool.esci

import ai.metarank.flow.PrintProgress
import ai.metarank.tool.esci.Page.{BookPage, ProductPage}
import ai.metarank.tool.esci.ProductInfo.ProductInfoOrig
import ai.metarank.util.{Logging, SortedGroupBy}
import cats.effect.{ExitCode, IO, IOApp}
import com.github.luben.zstd.ZstdInputStream
import com.opencsv.{CSVParserBuilder, CSVReaderBuilder}
import fs2.Stream
import fs2.io.file.{Files, Path}
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import io.circe.parser._
import io.circe.syntax._
import org.apache.hadoop.conf.Configuration
import org.apache.parquet.example.data.simple.SimpleGroup
import org.apache.parquet.example.data.simple.convert.GroupRecordConverter
import org.apache.parquet.hadoop.ParquetFileReader
import org.apache.parquet.hadoop.util.HadoopInputFile
import org.apache.parquet.io.ColumnIOFactory

import scala.jdk.CollectionConverters._
import java.io.{BufferedInputStream, File, FileInputStream, FileReader, InputStreamReader}
import scala.collection.mutable.ArrayBuffer
import scala.util.Try

/** Open questions:
  *   - what is the best policy on mapping ESCI labels to positive/negative samples
  *   - training CE with distilbert vs ms-marco
  *   - bi-encoders vs cross-encoders
  *   - small vs large datasets
  *   - titles or title+query?
  */
object GenerateLLMTraining extends IOApp with Logging {
  case class Row(
      query: String,
      qid: String,
      asin: String,
      locale: String,
      label: String,
      small: Boolean,
      large: Boolean,
      split: String
  )
  object Row {
    def apply(tokens: Array[String]): Row = {
      Row(
        query = tokens(2).trim.toLowerCase(),
        qid = tokens(3),
        asin = tokens(4),
        locale = tokens(5),
        label = tokens(6),
        small = if (tokens(7) == "1") true else false,
        large = if (tokens(8) == "1") true else false,
        split = tokens(9)
      )
    }

  }
  case class Query(
      query: String,
      qid: String,
      split: String,
      locale: String,
      small: Boolean,
      large: Boolean,
      products: List[QueryProd]
  )
  object Query {
    def apply(group: List[Row]): Query = {
      val head = group.head
      Query(
        query = head.query,
        qid = head.qid,
        split = head.split,
        locale = head.locale,
        small = head.small,
        large = head.large,
        products = group.map(row => QueryProd(row.asin, row.label))
      )
    }
  }
  case class QueryProd(asin: String, label: String)
  case class TitleDesc(title: String, desc: String, brand: String, color: String, bullets: String)
  case class TrainSample(query: String, e: List[TitleDesc], s: List[TitleDesc], c: List[TitleDesc], i: List[TitleDesc])
  implicit val titleDescCodec: Codec[TitleDesc]     = deriveCodec
  implicit val trainSampleCodec: Codec[TrainSample] = deriveCodec

  override def run(args: List[String]): IO[ExitCode] = args match {
    case parsedPath :: rankingPath :: parqPath :: Nil =>
      for {
        prodictsOrig <- loadProductInfoOrig(parqPath)
        queries      <- loadQueries(rankingPath).map(_.filter(q => q.small && (q.locale == "us")))
        products     <- loadProductInfo(parsedPath)
        _            <- save(queries.filter(_.split == "train"), products, prodictsOrig, "/tmp/train-small.json")
        _            <- save(queries.filter(_.split == "test"), products, prodictsOrig, "/tmp/test-small.json")
      } yield {
        ExitCode.Success
      }
    case _ => IO.raiseError(new Exception("need path to esci.json.zst"))
  }

  def loadPages(path: String) = fs2.io
    .readInputStream[IO](
      IO(new BufferedInputStream(new ZstdInputStream(new FileInputStream(new File(path))), 102400)),
      chunkSize = 10240
    )
    .through(fs2.text.utf8.decode)
    .through(fs2.text.lines)
    .filter(_.nonEmpty)
    .parEvalMapUnordered(16)(line => IO.fromEither(decode[Page](line)))

  def loadProductInfo(path: String): IO[Map[String, ProductInfo]] = {
    loadPages(path)
      .collect {
        case p: BookPage    => ProductInfo(p)
        case p: ProductPage => ProductInfo(p)
      }
      .through(PrintProgress.tap(None, "products"))
      .compile
      .toList
      .map(list => list.map(l => l.asin -> l).toMap)
  }

  val features =
    List("product_id", "product_title", "product_description", "product_bullet_point", "product_brand", "product_color")
  def loadProductInfoOrig(path: String): IO[Map[String, ProductInfoOrig]] = IO {
    val conf   = new Configuration()
    val reader = ParquetFileReader.open(HadoopInputFile.fromPath(new org.apache.hadoop.fs.Path("file://" + path), conf))
    val schema = reader.getFooter.getFileMetaData.getSchema
    val fields = schema.getFields
    val buf    = ArrayBuffer[ProductInfoOrig]()
    var next   = reader.readNextRowGroup()
    while (next != null) {
      val rows         = next.getRowCount
      val columnIO     = new ColumnIOFactory().getColumnIO(schema)
      val recordReader = columnIO.getRecordReader(next, new GroupRecordConverter(schema))
      var i            = 0
      while (i < rows) {
        val group = recordReader.read().asInstanceOf[SimpleGroup]
        val prod = ProductInfoOrig(
          asin = group.getString("product_id", 0),
          title = group.getString("product_title", 0),
          description = Try(group.getString("product_description", 0)).getOrElse(""),
          bullets = Try(group.getString("product_bullet_point", 0)).getOrElse(""),
          brand = Try(group.getString("product_brand", 0)).getOrElse(""),
          color = Try(group.getString("product_color", 0)).getOrElse(""),
          locale = group.getString("product_locale", 0)
        )
        buf.append(prod)
        i += 1
        if (i % 123456 == 0) {
          logger.info(s"parsed $i parquet records")
        }
        next = reader.readNextRowGroup()
      }
    }
    buf.toList.map(p => p.asin -> p).toMap
  }

  def loadQueriesStream(path: String) = for {
    parser <- IO(new CSVParserBuilder().withSeparator(',').withEscapeChar('\u0000').build())
    reader <- IO(
      new CSVReaderBuilder(new FileReader(new File(path)))
        .withSkipLines(1)
        .withCSVParser(parser)
        .build()
    )
  } yield {
    Stream
      .fromBlockingIterator[IO](reader.iterator().asScala, 128)
      .mapChunks(c => c.map(row => Row.apply(row)))
      .through(PrintProgress.tap(None, "rows"))
      .through(SortedGroupBy.groupBy(_.qid))
      .map(group => Query(group))
  }

  def loadQueries(path: String): IO[List[Query]] = loadQueriesStream(path).flatMap(_.compile.toList)

  def expand(query: Query, products: Map[String, ProductInfo], orig: Map[String, ProductInfoOrig]): TrainSample = {
    TrainSample(
      query = query.query,
      e = query.products
        .filter(_.label == "E")
        .flatMap(p => products.get(p.asin).map(_.asText()).orElse(orig.get(p.asin).map(_.asText()))),
      s = query.products
        .filter(_.label == "S")
        .flatMap(p => products.get(p.asin).map(_.asText()).orElse(orig.get(p.asin).map(_.asText()))),
      c = query.products
        .filter(_.label == "C")
        .flatMap(p => products.get(p.asin).map(_.asText()).orElse(orig.get(p.asin).map(_.asText()))),
      i = query.products
        .filter(_.label == "I")
        .flatMap(p => products.get(p.asin).map(_.asText()).orElse(orig.get(p.asin).map(_.asText())))
    )
  }

  def save(
      queries: List[Query],
      products: Map[String, ProductInfo],
      orig: Map[String, ProductInfoOrig],
      out: String
  ) = {
    Stream(queries: _*)
      .map(q => expand(q, products, orig))
      .map(_.asJson.noSpaces + "\n")
      .through(PrintProgress.tap(None, "groups"))
      .through(fs2.text.utf8.encode)
      .through(Files[IO].writeAll(Path(out)))
      .compile
      .drain
  }

}
