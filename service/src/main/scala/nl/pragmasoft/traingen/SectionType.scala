package nl.pragmasoft.traingen

import nl.pragmasoft.traingen.GroupType.*

enum SectionType:
  case warmup, combo, calisthenics, filler, close

enum GroupType:
  case split, together

object SectionType:
  def group(s: SectionType): GroupType = s match
    case SectionType.warmup       => split
    case SectionType.combo        => split
    case SectionType.calisthenics => together
    case SectionType.filler       => split
    case SectionType.close        => together
