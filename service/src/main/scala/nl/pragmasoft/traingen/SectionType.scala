package nl.pragmasoft.traingen

import nl.pragmasoft.traingen.GroupType.*

enum SectionType:
  case Warmup, Combo, Calisthenics, Filler, Close

enum GroupType:
  case split, together

object SectionType:
  def group(s: SectionType): GroupType = s match
    case SectionType.Warmup       => split
    case SectionType.Combo        => split
    case SectionType.Calisthenics => together
    case SectionType.Filler       => split
    case SectionType.Close        => together
