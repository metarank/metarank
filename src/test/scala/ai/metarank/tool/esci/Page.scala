package ai.metarank.tool.esci

import io.circe.generic.semiauto.deriveCodec
import io.circe.{Codec, Decoder, DecodingFailure, Encoder, Json, JsonObject}

sealed trait Page {
  def asin: String
  def locale: String
  def template: String
}

object Page {
  val colors = Map(
    "Black"             -> List("black"),
    "White"             -> List("white"),
    "Multicolor"        -> List("multicolor"),
    "Blue"              -> List("blue"),
    "Silver"            -> List("silver"),
    "Red"               -> List("red"),
    "Green"             -> List("green"),
    "Brown"             -> List("brown"),
    "Grey"              -> List("grey"),
    "Pink"              -> List("pink"),
    "Gray"              -> List("grey"),
    "Multi"             -> List("multicolor"),
    "Yellow"            -> List("yellow"),
    "Gold"              -> List("gold"),
    "Purple"            -> List("purple"),
    "Orange"            -> List("orange"),
    "Stainless Steel"   -> List("silver"),
    "Beige"             -> List("beige"),
    "black"             -> List("black"),
    "Assorted"          -> List("multicolor"),
    "Rose Gold"         -> List("gold"),
    "Chrome"            -> List("silver"),
    "Multicolored"      -> List("multicolor"),
    "Matte Black"       -> List("black"),
    "Bronze"            -> List("bronze"),
    "Multi-colored"     -> List("multicolor"),
    "Navy"              -> List("navy"),
    "Multi Color"       -> List("multicolor"),
    "Warm White"        -> List("white"),
    "Navy Blue"         -> List("blue"),
    "BLACK"             -> List("black"),
    "Teal"              -> List("teal"),
    "Brushed Nickel"    -> List("silver"),
    "Multi-color"       -> List("multicolor"),
    "Dark Grey"         -> List("grey"),
    "Cream"             -> List("beige"),
    "white"             -> List("white"),
    "Charcoal"          -> List("black"),
    "Colorful"          -> List("multicolor"),
    "Copper"            -> List("brown"),
    "Aqua"              -> List("blue"),
    "Light Blue"        -> List("blue"),
    "Rainbow"           -> List("multicolor"),
    "Multicoloured"     -> List("multicolor"),
    "Wood"              -> List("wood"),
    "Turquoise"         -> List("blue"),
    "Dark Gray"         -> List("grey"),
    "Multi_color"       -> List("multicolor"),
    "Brown/a"           -> List("brown"),
    "Burgundy"          -> List("brown"),
    "Silver, Black"     -> List("silver", "black"),
    "Dark Brown"        -> List("brown"),
    "Black/White"       -> List("black", "white"),
    "Light Grey"        -> List("grey"),
    "Espresso"          -> List("brown"),
    "Oil Rubbed Bronze" -> List("brown")
  )

  val materials = Map(
    "Plastic"                                   -> List("plastic"),
    "Stainless Steel"                           -> List("metal", "steel"),
    "Polyester"                                 -> List("plastic"),
    "Metal"                                     -> List("metal"),
    "Paper"                                     -> List("paper"),
    "Wood"                                      -> List("wood"),
    "Aluminum"                                  -> List("metal"),
    "Cotton"                                    -> List("fabric"),
    "Vinyl"                                     -> List("plastic"),
    "Alloy Steel"                               -> List("metal"),
    "Glass"                                     -> List("glass"),
    "Silicone"                                  -> List("silicone"),
    "Rubber"                                    -> List("rubber"),
    "Nylon"                                     -> List("fabric"),
    "Ceramic"                                   -> List("ceramic"),
    "Polypropylene"                             -> List("plastic"),
    "Leather"                                   -> List("leather"),
    "Microfiber"                                -> List("microfiber"),
    "Acrylic"                                   -> List("plastic"),
    "Polyvinyl Chloride"                        -> List("plastic"),
    "Fabric"                                    -> List("fabric"),
    "Synthetic"                                 -> List("fabric"),
    "Thermoplastic Polyurethane"                -> List("plastic"),
    "Resin"                                     -> List("rubber"),
    "Steel"                                     -> List("metal"),
    "Acrylonitrile Butadiene Styrene"           -> List("plastic"),
    "Brass"                                     -> List("metal"),
    "Tempered Glass"                            -> List("glass"),
    "Faux Leather"                              -> List("leather"),
    "Polyester, Polyester Blend"                -> List("plastic"),
    "Canvas"                                    -> List("fabric"),
    "Polycarbonate"                             -> List("plastic"),
    "Engineered Wood"                           -> List("wood"),
    "Plush"                                     -> List("fabric"),
    "Polycarbonate, Thermoplastic Polyurethane" -> List("plastic"),
    "Cardboard"                                 -> List("paper"),
    "Copper"                                    -> List("metal"),
    "Iron"                                      -> List("metal"),
    "Neoprene"                                  -> List("plastic"),
    "Plastic, Metal"                            -> List("plastic", "metal"),
    "Bamboo"                                    -> List("wood"),
    "Cast Iron"                                 -> List("metal"),
    "Silk"                                      -> List("fabric"),
    "Porcelain"                                 -> List("ceramic"),
    "Polyethylene"                              -> List("plastic"),
    "Cardstock"                                 -> List("paper"),
    "Polyester & Polyester Blend"               -> List("plastic"),
    "PVC"                                       -> List("plastic"),
    "Carbon Steel"                              -> List("metal"),
    "Polyurethane"                              -> List("plastic"),
    "Linen"                                     -> List("fabric"),
    "Velvet"                                    -> List("fabric"),
    "Ethylene Vinyl Acetate"                    -> List("plastic"),
    "Crystal"                                   -> List("glass"),
    "Wood, Metal"                               -> List("wood", "metal"),
    "Zinc"                                      -> List("metal"),
    "Fiberglass"                                -> List("plastic"),
    "Satin"                                     -> List("fabric"),
    "Gel"                                       -> List("plastic"),
    "ABS"                                       -> List("plastic"),
    "Stone"                                     -> List("stone"),
    "Carbon Fiber"                              -> List("plastic"),
    "Silicone, Rubber"                          -> List("rubber"),
    "Stainless Steel, Plastic"                  -> List("metal", "plastic"),
    "Plastic, Acrylonitrile Butadiene Styrene"  -> List("plastic"),
    "Faux Leather, Leather"                     -> List("leather"),
    "Wool"                                      -> List("fabric"),
    "Alloy Steel, Metal"                        -> List("metal"),
    "Glass, Metal"                              -> List("glass", "metal"),
    "plush"                                     -> List("fabric"),
    "Fleece"                                    -> List("fabric"),
    "plastic"                                   -> List("plastic"),
    "Silver"                                    -> List("metal"),
    "Silicone, Plastic"                         -> List("plastic"),
    "TPU"                                       -> List("plastic"),
    "Chrome Vanadium Steel"                     -> List("metal"),
    "Natural Rubber"                            -> List("rubber"),
    "Flannel"                                   -> List("fabric")
  )

  val weightPattern = """([0-9]?\.?[0-9]+)\s+([a-zA-Z]+)""".r
  val pricePattern  = """\$([0-9]+\.[0-9]+)""".r

  case class BrokenPage(asin: String, locale: String, error: String, template: String = "") extends Page
  sealed trait ContentPage extends Page {
    def title: String
    def stars: String
    def ratings: String
    def category: List[String]
    def reviews: List[Review]
    def desc: String
    def attr: Map[String, String]
    def formats: Map[String, String]
    def template: String
    def price: String

    def starsNumeric: Option[Double] = ProductInfo.parseStars(stars)
    def ratingsNumeric: Option[Int]  = ProductInfo.parseRatings(ratings)
    def color: Option[List[String]]  = attr.get("Color").flatMap(rawName => colors.get(rawName))
    def weight: Option[Double] = attr.get("Item Weight").flatMap {
      case weightPattern(num, "Pounds")    => num.toDoubleOption.map(_ * 453.0)
      case weightPattern(num, "Ounces")    => num.toDoubleOption.map(_ * 28.0)
      case weightPattern(num, "Grams")     => num.toDoubleOption
      case weightPattern(num, "Kilograms") => num.toDoubleOption.map(_ * 1000.0)
      case _                               => None
    }
    def material: Option[List[String]] = attr.get("Material").flatMap(rawName => materials.get(rawName))
    def priceNumeric: Option[Double] = price match {
      case pricePattern(num) => num.toDoubleOption
      case _                 => None
    }
  }
  case class Review(stars: String, title: String, date: String, text: String)

  case class BookPage(
      locale: String,
      asin: String,
      template: String,
      title: String,
      subtitle: String,
      author: String,
      stars: String,
      ratings: String,
      price: String,
      category: List[String],
      reviews: List[Review],
      desc: String,
      attr: Map[String, String],
      formats: Map[String, String],
      review: String,
      img: String
  ) extends ContentPage
  case class ProductPage(
      locale: String,
      asin: String,
      title: String,
      stars: String,
      ratings: String,
      category: List[String],
      attrs: Map[String, String],
      bullets: List[String],
      description: String,
      info: Map[String, String],
      reviews: List[Review],
      price: String,
      formats: Map[String, String],
      template: String,
      image: String
  ) extends ContentPage {
    override def desc: String              = description
    override def attr: Map[String, String] = attrs
  }

  implicit val brokenCodec: Codec[BrokenPage] = deriveCodec
  implicit val bookCodec: Codec[BookPage]     = deriveCodec
  implicit val reviewCodec: Codec[Review]     = deriveCodec
  implicit val prodPage: Codec[ProductPage]   = deriveCodec

  implicit val pageEncoder: Encoder[Page] = Encoder.instance {
    case p: BookPage    => bookCodec(p).deepMerge(tpe("book"))
    case p: BrokenPage  => brokenCodec(p).deepMerge(tpe("error"))
    case p: ProductPage => prodPage(p).deepMerge(tpe("product"))
  }

  implicit val pageDecoder: Decoder[Page] = Decoder.instance(c =>
    for {
      tpe <- c.downField("type").as[String]
      page <- tpe match {
        case "book"    => bookCodec.tryDecode(c)
        case "error"   => brokenCodec.tryDecode(c)
        case "product" => prodPage.tryDecode(c)
        case _         => Left(DecodingFailure("nope", c.history))
      }
    } yield {
      page
    }
  )

  def tpe(t: String) = Json.fromJsonObject(JsonObject.fromMap(Map("type" -> Json.fromString(t))))
}
