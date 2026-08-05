#!/usr/bin/env python3
"""Build backend/src/main/resources/golf_clubs.json.

Sources, both free and keyless:
  - no.wikipedia.org "Liste over golfbaner i Norge" — club names, hole counts, coordinates
  - ws.geonorge.no (Kartverket) — municipality and county from those coordinates

One entry per CLUB, not per course: a club with an 18-hole and a 9-hole course is one
entry whose `holes` is the largest course it runs. Run this again when the club list
changes (about once a year), then review the diff before committing.

Usage:  python3 scripts/build-club-list.py [--limit N] [--no-geocode] [--out PATH]
"""

import argparse
import json
import re
import sys
import time
import unicodedata
import urllib.parse
import urllib.request

WIKI_API = ("https://no.wikipedia.org/w/api.php?action=parse"
            "&page=Liste_over_golfbaner_i_Norge&prop=wikitext&format=json&formatversion=2")
GEONORGE_POINT = "https://ws.geonorge.no/adresser/v1/punktsok?lat={lat}&lon={lon}&radius={radius}&treffPerSide=1"
GEONORGE_KOMMUNE = "https://ws.geonorge.no/kommuneinfo/v1/kommuner/{nr}"


def get(url, timeout=30):
    req = urllib.request.Request(url, headers={"User-Agent": "golfjakten-club-list/1.0"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.load(r)


def strip_markup(cell):
    cell = re.sub(r"<ref[^>]*?/>", "", cell)
    cell = re.sub(r"<ref.*?</ref>", "", cell, flags=re.S)
    cell = re.sub(r"<!--.*?-->", "", cell, flags=re.S)
    cell = re.sub(r"\[\[([^\]|]*\|)?([^\]]*)\]\]", r"\2", cell)
    cell = re.sub(r"\[https?://\S+\s+([^\]]*)\]", r"\1", cell)
    cell = re.sub(r"https?://\S+", "", cell)
    cell = cell.replace("'''", "").replace("''", "")
    return cell.strip()


def slugify(name):
    s = name.lower()
    for a, b in (("ø", "o"), ("æ", "ae"), ("å", "aa")):
        s = s.replace(a, b)
    s = unicodedata.normalize("NFD", s)
    s = "".join(c for c in s if unicodedata.category(c) != "Mn")
    s = re.sub(r"\bgolfklubb\b", "gk", s)
    s = re.sub(r"[^a-z0-9]+", "-", s).strip("-")
    return re.sub(r"-+", "-", s)


CLOSED = re.compile(r"nedlagt|stengt|opphørt|avviklet", re.I)

# Real clubs the Wikipedia list omits, confirmed by hand against the courses already in the
# database. Coordinates come from those existing rows. Without this, a rebuild would drop
# them again — Solastranden is an 18-hole club and was the first course in the database.
# `holes` is left None where the source is unverified rather than guessed.
EXTRA_CLUBS = [
    {"name": "Solastranden Golfklubb", "lat": 58.8758048, "lon": 5.6103164, "holes": None},
    {"name": "Farsund Golfklubb", "lat": 58.0737631, "lon": 6.7716306, "holes": None},
    {"name": "Hasvik Golfklubb", "lat": 70.4874399, "lon": 22.1416628, "holes": None},
    {"name": "Lindesnes Golfklubb", "lat": 58.0436174, "lon": 7.1342185, "holes": None},
]


def clean_club_name(name):
    """"Alta Golfklubb / Alta Golfpark AS" is one club; the company half is not its name."""
    name = re.sub(r"\(.*?\)", " ", name)          # "(pay and play)", "(nedlagt?)"
    # An alias tacked on after a comma is not the club's name, e.g.
    # "Trondheim Golfklubb, IL Tempo Golf & Country Club".
    name = name.split(",", 1)[0]
    # The club and the company that runs it are separated by a slash, in either order:
    # "Alta Golfklubb / Alta Golfpark AS" but also "Haga Golf AS / Haga Golfklubb".
    # Prefer whichever side names a klubb; fall back to the first.
    parts = [p.strip() for p in name.split("/") if p.strip()]
    name = next((p for p in parts if re.search(r"klubb", p, re.I)), parts[0] if parts else name)
    name = re.sub(r"\s+(AS|A/S|ASA)$", "", name.strip(), flags=re.I)
    name = re.sub(r"\s+", " ", name).strip(" ,-")
    # The list capitalises inconsistently ("Hakadal Golfklubb" vs "Hakadal golfklubb").
    return re.sub(r"\bgolfklubb\b", "Golfklubb", name, flags=re.I)


def parse_rows(wikitext):
    """Yield (course_name, club_name, holes, lat, lon, closed) for each row of the course table."""
    start = wikitext.find("!Banenavn")
    if start == -1:
        sys.exit("could not find the course table — has the Wikipedia page changed?")
    table = wikitext[start:wikitext.find("\n|}", start)]

    for raw in table.split("\n|-")[1:]:
        coord = re.search(r"\{\{koord\|([\d.]+)\|N\|([\d.]+)\|E", raw)
        if not coord:
            continue
        body = re.sub(r"\{\{koord.*?\}\}", "", raw, flags=re.S)
        cells = [strip_markup(c) for c in re.split(r"\n\||\|\|", body)]
        cells = [c for c in cells if c is not None]
        if len(cells) < 3:
            continue

        course, club = cells[1], cells[2]
        holes = None
        for value in cells[3:6]:
            digits = re.sub(r"\D", "", value or "")
            if digits:
                holes = max(holes or 0, int(digits))
        if not club:
            continue
        # "Bruk" (the last cell) and the club name both carry closure notes.
        closed = bool(CLOSED.search(club) or CLOSED.search(" ".join(cells[6:])))
        yield course, clean_club_name(club), holes, float(coord.group(1)), float(coord.group(2)), closed


def geocode(lat, lon):
    """Municipality and county from Kartverket, widening the radius until an address is found."""
    for radius in (2000, 6000, 15000):
        try:
            hits = get(GEONORGE_POINT.format(lat=lat, lon=lon, radius=radius)).get("adresser") or []
        except Exception:
            return None, None
        if hits:
            nr = hits[0].get("kommunenummer")
            navn = hits[0].get("kommunenavn", "").title()
            try:
                fylke = get(GEONORGE_KOMMUNE.format(nr=nr)).get("fylkesnavn")
            except Exception:
                fylke = None
            return navn, fylke
    return None, None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--limit", type=int, help="only process the first N clubs (for a quick check)")
    ap.add_argument("--no-geocode", action="store_true", help="skip Kartverket lookups")
    ap.add_argument("--out", default="backend/src/main/resources/golf_clubs.json")
    args = ap.parse_args()

    wikitext = get(WIKI_API)["parse"]["wikitext"]

    clubs = {}
    skipped_closed = []
    for course, club, holes, lat, lon, closed in parse_rows(wikitext):
        if closed:
            skipped_closed.append(club)
            continue
        # A club fronting several courses keeps one entry: biggest course wins, and its
        # coordinates come with it so the map pin sits on the main course. Keyed
        # case-insensitively because the source capitalises the same club both ways.
        key = club.casefold()
        current = clubs.get(key)
        if current is None or (holes or 0) > (current["holes"] or 0):
            clubs[key] = {"name": club, "lat": lat, "lon": lon, "holes": holes, "course": course}

    for extra in EXTRA_CLUBS:
        key = extra["name"].casefold()
        if key not in clubs:
            clubs[key] = {"name": extra["name"], "lat": extra["lat"], "lon": extra["lon"],
                          "holes": extra["holes"], "course": extra["name"]}

    entries = []
    for i, (_key, data) in enumerate(sorted(clubs.items())):
        club = data["name"]
        if args.limit and i >= args.limit:
            break
        municipality, county = (None, None) if args.no_geocode else geocode(data["lat"], data["lon"])
        entries.append({
            "clubId": slugify(club),
            "name": club,
            "lat": data["lat"],
            "lon": data["lon"],
            "municipality": municipality,
            "county": county,
            "holes": data["holes"],
        })
        if not args.no_geocode:
            time.sleep(0.2)  # be polite to a free public API
        print(f"  {i + 1:3d}. {club} — {municipality or '?'}, {county or '?'} ({data['holes'] or '?'} hull)",
              file=sys.stderr)

    slugs = [e["clubId"] for e in entries]
    dupes = {s for s in slugs if slugs.count(s) > 1}
    if dupes:
        print(f"\nDUPLICATE clubIds, fix by hand before committing: {sorted(dupes)}", file=sys.stderr)

    missing = [e["name"] for e in entries if not e["county"] or not e["holes"]]
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(entries, f, ensure_ascii=False, indent=2)
        f.write("\n")

    print(f"\nwrote {len(entries)} clubs to {args.out}", file=sys.stderr)
    if skipped_closed:
        print(f"skipped {len(skipped_closed)} closed: {', '.join(sorted(set(skipped_closed)))}", file=sys.stderr)
    if missing:
        print(f"{len(missing)} need a human: {', '.join(missing[:12])}"
              f"{' …' if len(missing) > 12 else ''}", file=sys.stderr)


if __name__ == "__main__":
    main()
