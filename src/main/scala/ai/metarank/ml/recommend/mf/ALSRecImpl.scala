package ai.metarank.ml.recommend.mf

import ai.metarank.config.{ModelConfig, Selector}
import ai.metarank.ml.recommend.KnnConfig
import ai.metarank.ml.recommend.KnnConfig.HnswConfig
import ai.metarank.ml.recommend.embedding.EmbeddingMap
import ai.metarank.ml.recommend.mf.ALSRecImpl.{ALSConfig, EALSRecommenderWrapper}
import ai.metarank.ml.recommend.mf.MFRecImpl.MFModelConfig
import fs2.io.file.Path
import io.circe.generic.semiauto.deriveEncoder
import io.circe.{Decoder, Encoder}
import net.librec.conf.Configuration
import net.librec.data.model.TextDataModel
import net.librec.math.structure.DenseMatrix
import net.librec.recommender.RecommenderContext
import net.librec.recommender.cf.ranking.EALSRecommender

case class ALSRecImpl(config: ALSConfig) extends MFRecImpl {
  override def train(file: Path): EmbeddingMap = {
    val conf = new Configuration()
    conf.setInt("rec.iterator.maximum", config.iterations)
    conf.setFloat("rec.user.regularization", config.userReg)
    conf.setFloat("rec.item.regularization", config.itemReg)
    conf.setInt("rec.factor.number", config.factors)
    conf.setBoolean("rec.recommender.isranking", true)
    conf.setInt("rec.eals.wrmf.judge", 1)
    conf.setInt("rec.eals.overall", 128)
    conf.setFloat("rec.eals.ratio", 0.4f)
    conf.setFloat("rec.wrmf.weight.coefficient", 1.0f)
    conf.set("data.column.format", "UIRT")
    conf.set("dfs.data.dir", file.toNioPath.getParent.toString)
    conf.set("data.input.path", file.toNioPath.getFileName.toString)
    val dataModel = new TextDataModel(conf)
    dataModel.buildDataModel()

    val context = new RecommenderContext(conf, dataModel)

    val rec = new EALSRecommenderWrapper()
    rec.train(context)
    EmbeddingMap(rec.itemMappingData, rec.getItemFactors)
  }
}

object ALSRecImpl {

  case class ALSConfig(
      interactions: List[String] = Nil,
      iterations: Int = 100,
      factors: Int = 100,
      userReg: Float = 0.01f,
      itemReg: Float = 0.01f,
      store: KnnConfig = HnswConfig(),
      selector: Selector = Selector.AcceptSelector()
  ) extends MFModelConfig

  class EALSRecommenderWrapper extends EALSRecommender {
    def getItemFactors: DenseMatrix = itemFactors
  }

  implicit val alsConfigDecoder: Decoder[ALSConfig] = Decoder.instance(c =>
    for {
      ints       <- c.downField("interactions").as[Option[List[String]]]
      iterations <- c.downField("iterations").as[Option[Int]]
      factors    <- c.downField("factors").as[Option[Int]]
      userReg    <- c.downField("userReg").as[Option[Float]]
      itemReg    <- c.downField("itemRef").as[Option[Float]]
      store      <- c.downField("store").as[Option[KnnConfig]]
      selector   <- c.downField("selector").as[Option[Selector]]
    } yield {
      val d = ALSConfig()
      ALSConfig(
        interactions = ints.getOrElse(Nil),
        iterations = iterations.getOrElse(d.iterations),
        factors = factors.getOrElse(d.factors),
        userReg = userReg.getOrElse(d.userReg),
        itemReg = itemReg.getOrElse(d.itemReg),
        store = store.getOrElse(HnswConfig()),
        selector = selector.getOrElse(Selector.AcceptSelector())
      )
    }
  )

  implicit val alsConfigEncoder: Encoder[ALSConfig] = deriveEncoder

}
