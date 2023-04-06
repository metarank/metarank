package ai.metarank.tool.esci

import ai.metarank.tool.esci.GenerateLLMTraining.TitleDesc
import ai.metarank.tool.esci.Page.{BookPage, ProductPage}

case class ProductInfo(
    asin: String,
    title: String,
    description: String,
    brand: String,
    color: String,
    bullets: String,
    stars: Option[Double],
    ratings: Option[Int],
    categories: List[String],
    template: String
) {
  def asText() = TitleDesc(title, description, brand, color, bullets)
}

object ProductInfo {
  case class ProductInfoOrig(
      asin: String,
      title: String,
      description: String,
      bullets: Option[String],
      brand: Option[String],
      color: Option[String],
      locale: String
  ) {
    def asText() = TitleDesc(title, description, brand.getOrElse(""), color.getOrElse(""), bullets.getOrElse(""))
  }
  def apply(page: BookPage): ProductInfo = {
    ProductInfo(
      asin = page.asin,
      title = page.title,
      description = page.desc.split(' ').take(512).mkString(" "),
      stars = parseStars(page.stars),
      ratings = parseRatings(page.ratings),
      categories = page.category,
      template = page.template,
      brand = "",
      color = "",
      bullets = ""
    )
  }
  def apply(page: ProductPage): ProductInfo = {
    ProductInfo(
      asin = page.asin,
      title = page.title,
      description = page.description.split(' ').take(512).mkString(" "),
      stars = parseStars(page.stars),
      ratings = parseRatings(page.ratings),
      categories = page.category,
      template = page.template,
      brand = page.attrs.getOrElse("Brand", ""),
      color = page.attrs.getOrElse("Color", ""),
      bullets = page.bullets.mkString(" ")
    )
  }

  val starsPattern = "([0-9])\\.([0-9]) out of 5 stars".r
  def parseStars(stars: String) = stars match {
    case starsPattern(a, b) =>
      (a.toIntOption, b.toIntOption) match {
        case (Some(i), Some(m)) => Some(i.toDouble + m.toDouble / 10.0)
        case _                  => None
      }
    case _ => None
  }

  val ratingPattern1 = "([0-9]+) ratings".r
  val ratingPattern2 = "([0-9]+),([0-9]+) ratings".r
  def parseRatings(r: String) = r match {
    case ratingPattern1(a) => a.toIntOption
    case ratingPattern2(a, b) =>
      (a.toIntOption, b.toIntOption) match {
        case (Some(i), Some(m)) => Some(i * 1000 + m)
        case _                  => None
      }
    case _ => None
  }
}
