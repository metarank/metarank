package ai.metarank.main

object Logo {
  def raw =
    """
      |                __                              __    
      |  _____   _____/  |______ ____________    ____ |  | __
      | /     \_/ __ \   __\__  \\_  __ \__  \  /    \|  |/ /
      ||  Y Y  \  ___/|  |  / __ \|  | \// __ \|   |  \    < 
      ||__|_|  /\___  >__| (____  /__|  (____  /___|  /__|_ \
      |      \/     \/          \/           \/     \/     \/""".stripMargin
  def lines = raw.split("\n").toList

}
