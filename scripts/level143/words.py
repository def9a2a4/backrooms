"""Level 143's words, emblems and flags. Sign tokens like "[house]" or "[notice]" in the
buildings are filled from these lists (MaceWords: a random line; "house" is
"<housetypes> of~<titles> <nouns>~- n -"). City names are a prefix and a suffix line of the
theme's lists, joined as written: a suffix starting with a space is a separate word.
"""

WORDS = {
    # city names, per theme
    "timber_prefix": """Ash Elm Hollow Ox Brack Thorn Wend Mill Crow Fen Hazel Bram Lark Wil Kettle Rook Har Stone Ivy
                        Marrow Pell Ember Low Fallow Sallow Wick Cold Mead Holt Dun""".split(),
    "timber_suffix": ["ford", "wick", "stead", "bury", "holm", "ley", "worth", "combe", "by", "thorpe", "field",
                      "mere", "ham", "ton", "brook", "gate", " Green", " Cross", " Market"],
    "ochre_prefix": """San Vel Mira Cal Oro Terra Alba Sol Port Bel Cas Ara Lum Ver Ros Pal Cor Dal Esta Mon""".split(),
    "ochre_suffix": ["ona", "ella", "ar", "ino", "esa", "ada", "ozza", "ana", "ero", "anza", "ia", "eto", " Alta",
                     " Vieja", " Nova", " del Sol"],
    "ashen_prefix": """Grey Cinder Lamp Ash Pale Soot Hush Vesper Mourn Candle Night Slate Raven Gloam Dusk Wax
                       Ember Murk Still Shroud""".split(),
    "ashen_suffix": ["hold", "gate", "mere", "moor", "wick", "fall", "spire", "keep", "reach", "hollow", "barrow",
                     "deep", "vale", " Bell", " Watch"],
    # streets (MaceCity: "<adjectives> <roadtypes>")
    "adjectives": """Quiet Long Narrow Crooked Old New Upper Lower Middle Copper Candle Chapel Mill Bell Lantern
                     Weaver Cooper Tanner Baker Mason Glover Chandler Market Well Church Orchard Meadow Brook
                     Hollow Vacant Empty Patient Familiar Second Same Other Last First Plan Draft Sample Model
                     Pending Unlit Unnamed""".split(),
    "roadtypes": ["Street", "Lane", "Row", "Way", "Walk", "Close", "Yard", "Court", "Passage", "Alley", "Hill",
                  "Rise", "End", "Gate", "Steps"],
    # house names
    "housetypes": ["House", "Home", "Residence", "Dwelling", "Lodging", "Rooms"],
    "titles": ["the", "the late", "the absent", "Mister", "Mistress", "Widow", "Old", "Young", "the expected",
               "the intended", "a"],
    "nouns": """Cooper Fletcher Thatcher Weaver Mason Turner Glover Tanner Miller Baker Draper Carter Webb Hale
                Ashby Morrow Pell Quill Rook Sallow Tamsin Vane Whitlock Yarrow Occupant Tenant Resident Family
                Household""".split(),
    "surnames": """Cooper Fletcher Thatcher Weaver Mason Turner Glover Tanner Miller Baker Draper Carter Webb Hale
                   Ashby Morrow Pell Quill Rook Sallow Vane Whitlock Yarrow Abbot Brindle Coyle Dunmore Elkin
                   Fairweather Gage Harrow Ingram Jessop Keel Lisle""".split(),
    "tavernadj": ["Empty", "Last", "Quiet", "Open", "Patient", "Unlit", "Drowsy", "Crooked", "Golden", "Silver",
                  "Painted", "Sleeping", "Second", "Waiting", "Hollow"],
    "tavernnoun": ["Lantern", "Tankard", "Door", "Chair", "Kettle", "Bell", "Key", "Candle", "Window", "Stair",
                   "Guest", "Table", "Lamb", "Hare", "Crow"],
    "epitaph": [
        "Here lies~a resident~of this city", "Rest~undisturbed", "Moved away~and stayed", "Gone~ahead",
        "Planned for~a long life", "In memory of~the occupant", "Name to be~added later",
        "Returned~to the plan", "Asleep~at last", "Expected~any day now", "Lot 14~reserved",
        "Beloved~tenant", "We kept~the lights on",
    ],
    "monument": [
        "To the~founders~of this city", "Erected~in the~first year", "To those~who will~live here",
        "In honour~of the~surveyors", "This city~is complete", "Lest we~forget the~way out",
    ],
    "notice": [
        "Doors are to~be closed~after dark.~- The Council",
        "Has anyone~seen the people~at number 7?",
        "Census results~posted inside.~Total: 0",
        "Lost: one street.~Answers to~Mill Lane.",
        "Market day~is every day~until further~notice",
        "Rooms to let.~Enquire within.~No one within.",
        "Please do not~walk on the~unfinished~roads",
        "Lamps are lit~at dusk. Do~not ask by~whom.",
        "Residents~arriving soon.~Keep the beds~made.",
        "Council meeting~postponed~again",
        "Found: a key.~It fits every~door here.",
        "The bell will~ring when~someone moves~in.",
        "If you are~reading this~you are early",
        "Map of the~city removed~for accuracy",
        "Wells are~deep. Do not~count the~rungs.",
        "Have you~seen the~edge of town?",
    ],
    # buildings_extra.py
    "barracks": [
        "Garrison~at full~strength~(0)", "Recruits~wanted.~Any at all.", "Night watch~rota:~to be filled",
        "No drill~today.~Or yesterday.", "Lights out~at dusk~for everyone",
    ],
    "clock": [
        "Correct~time kept~here", "The clock~is right~twice a day~somewhere", "Please do not~set your watch~by this",
        "Wound daily~by the~keeper", "It is later~than the~clock says",
    ],
    "playbill": [
        "Tonight:~The Arrival~All seats~free", "Now showing:~An Empty Room~in Three Acts",
        "Tonight:~The Tenants~(postponed)", "Rehearsal~in progress~Do not~applaud",
        "Matinee daily~Audience~wanted", "Tonight:~A Full House~(a fantasy)",
    ],
    "school": [
        "Lessons begin~when the~children~arrive", "Register:~0 present~0 absent", "Today's lesson:~counting~the houses",
        "School~House~Enrolment open", "Quiet please~Class~in session",
    ],
    "lamplighter": [
        "Lamps lit~at dusk.~Every one.", "Lamplighter~Out on~rounds", "Oil, wicks~and patience",
        "The lamps~are never~out", "Rounds~begin at~the Square",
    ],
    "guild": ["Weavers'", "Masons'", "Coopers'", "Chandlers'", "Glovers'", "Drapers'", "Surveyors'", "Lamplighters'",
              "Clockmakers'", "Locksmiths'", "Mapmakers'", "Bellfounders'"],
    "shrine": [
        "Keep the~flame lit~for those~yet to come", "Light one~for the~household~expected",
        "The fire~is tended~by someone", "Warm your~hands. It~is always~cold here.",
    ],
}

# Emblems over the north gate: rows top to bottom; "#" the city's precious block, letters below.
EMBLEM_ART = {
    "key": ("..##...", ".#..#..", ".#..#..", "..##...", "...#...", "...##..", "...#...", "...##.."),
    "eye": (".......", "..aaa..", ".a###a.", "a##b##a", ".a###a.", "..aaa..", "......."),
    "door": (".aaaaa.", ".a###a.", ".a###a.", ".a#b#a.", ".a###a.", ".a###a.", ".aaaaa."),
    "moon": ("..###..", ".##....", "##.....", "##.....", "##.....", ".##....", "..###.."),
    "lantern": ("...a...", "..aaa..", ".a###a.", ".a#b#a.", ".a###a.", "..aaa..", "..a.a.."),
    "bell": ("...#...", "..###..", "..###..", ".#####.", ".#####.", "#######", "...b..."),
    "tower": ("#.#.#..", "#####..", ".###...", ".#b#...", ".###...", ".###...", "#####.."),
    "spiral": ("#######", "#.....#", "#.###.#", "#.#.#.#", "#.#...#", "#.#####", "#......"),
}
EMBLEM_BLOCKS = {
    "timber": {"a": "minecraft:dark_oak_planks", "b": "minecraft:black_wool"},
    "ochre": {"a": "minecraft:orange_terracotta", "b": "minecraft:black_wool"},
    "ashen": {"a": "minecraft:polished_blackstone", "b": "minecraft:light_blue_stained_glass"},
}
EMBLEMS_BY_STYLE = {
    "timber": ("key", "eye", "door", "bell", "tower"),
    "ochre": ("key", "door", "moon", "lantern", "spiral"),
    "ashen": ("eye", "moon", "lantern", "bell", "spiral"),
}


def emblem(art, blocks):
    """MaceCity's emblem format: tab-separated block states, -1 for the precious block."""
    rows = []
    for line in art:
        rows.append("\t".join("-1" if c == "#" else blocks[c] if c in blocks else "minecraft:air" for c in line))
    return "\n".join(rows) + "\n"


# Tower flags: wool colour slots per cell (MaceCity shuffles which colour each digit is).
FLAGS = {
    "border": ("0000000000", "0111111110", "0111111110", "0111111110", "0111111110", "0000000000"),
    "chevron": ("1000000000", "1100000000", "1110000000", "1110000000", "1100000000", "1000000000"),
    "diagonal": ("0000000111", "0000011112", "0001111222", "0111122222", "1111222222", "1122222222"),
    "canton": ("1110000000", "1110000000", "1110000000", "0000000000", "2222222222", "2222222222"),
    "stripes": ("0000000000", "1111111111", "0000000000", "1111111111", "0000000000", "1111111111"),
    "pale": ("0001111000", "0001111000", "0001111000", "0001111000", "0001111000", "0001111000"),
    "saltire": ("1000000001", "0110000110", "0001111000", "0001111000", "0110000110", "1000000001"),
    "dot": ("0000000000", "0000110000", "0001111000", "0001111000", "0000110000", "0000000000"),
}
