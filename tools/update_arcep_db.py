#!/usr/bin/env python3
"""
tools/update_arcep_db.py
Script d'automatisation pour télécharger, valider et compiler les fichiers officiels
de l'ARCEP (MAJNUM et identifiants_CE) dans la base SQLite locale de l'application.

Usage:
    python3 tools/update_arcep_db.py [--output app/src/main/assets/arcep_data.db]
"""

import argparse
import csv
import datetime
import hashlib
import io
import os
import sqlite3
import sys
import urllib.request

MAJNUM_URL = "https://extranet.arcep.fr/uploads/MAJNUM.csv"
CE_URL = "https://extranet.arcep.fr/uploads/identifiants_CE.csv"
DEFAULT_OUTPUT = "app/src/main/assets/arcep_data.db"


def fetch_url(url: str):
    """Télécharge le fichier avec en-têtes HTTP et calcule son hash SHA-256."""
    print(f"[*] Téléchargement : {url} ...")
    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": "Mozilla/5.0 (Android; ArcepOperateur/1.0; Build-Tool)"
        }
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        last_modified = resp.headers.get("Last-Modified", "")
        content = resp.read()
    
    sha256 = hashlib.sha256(content).hexdigest()
    print(f"    -> Reçu : {len(content):,} octets, SHA-256 : {sha256[:16]}... (Last-Modified: {last_modified})")
    return content, sha256, last_modified


def decode_csv_content(content_bytes: bytes) -> str:
    """Décode le contenu en gérant latin-1, cp1252 et utf-8."""
    for enc in ("utf-8", "cp1252", "latin-1"):
        try:
            return content_bytes.decode(enc)
        except UnicodeDecodeError:
            continue
    return content_bytes.decode("latin-1", errors="replace")


def build_database(output_path: str):
    print("=" * 60)
    print("  Génération de la base de données SQLite officielle ARCEP")
    print("=" * 60)

    # 1. Téléchargements
    ce_bytes, ce_hash, ce_mod = fetch_url(CE_URL)
    maj_bytes, maj_hash, maj_mod = fetch_url(MAJNUM_URL)

    # Récupérer les notes utilisateurs existantes si la base existe déjà
    existing_notes = []
    if os.path.exists(output_path):
        try:
            old_conn = sqlite3.connect(output_path)
            old_c = old_conn.cursor()
            old_c.execute("SELECT phone_number, is_favorite, is_spam, user_tag, user_note, updated_at FROM call_notes")
            existing_notes = old_c.fetchall()
            old_conn.close()
            print(f"[i] {len(existing_notes)} notes utilisateurs conservées de la base précédente.")
        except Exception:
            pass

    # 2. Préparation base SQLite temporaire
    os.makedirs(os.path.dirname(os.path.abspath(output_path)), exist_ok=True)
    temp_db = output_path + ".tmp"
    if os.path.exists(temp_db):
        os.remove(temp_db)

    conn = sqlite3.connect(temp_db)
    c = conn.cursor()
    c.execute("PRAGMA page_size = 4096")
    c.execute("PRAGMA synchronous = OFF")

    # Schéma
    c.execute("""
    CREATE TABLE operators (
        code TEXT PRIMARY KEY,
        name TEXT NOT NULL,
        siret TEXT,
        rcs TEXT,
        address TEXT,
        declaration_date TEXT
    )
    """)

    c.execute("""
    CREATE TABLE number_ranges (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        ezabpqm TEXT NOT NULL,
        tranche_debut TEXT NOT NULL,
        tranche_fin TEXT NOT NULL,
        operator_code TEXT NOT NULL,
        operator_name TEXT NOT NULL,
        territory TEXT,
        attribution_date TEXT
    )
    """)

    c.execute("""
    CREATE TABLE call_notes (
        phone_number TEXT PRIMARY KEY,
        is_favorite INTEGER DEFAULT 0,
        is_spam INTEGER DEFAULT 0,
        user_tag TEXT,
        user_note TEXT,
        updated_at INTEGER
    )
    """)

    c.execute("""
    CREATE TABLE arcep_metadata (
        key TEXT PRIMARY KEY,
        value TEXT NOT NULL
    )
    """)

    # 3. Insertion des opérateurs (identifiants_CE.csv)
    ce_text = decode_csv_content(ce_bytes)
    ce_reader = csv.DictReader(io.StringIO(ce_text), delimiter=';')
    operators_dict = {}
    op_count = 0

    for r in ce_reader:
        code = r.get("CODE_OPERATEUR", "").strip()
        if not code:
            continue
        name = r.get("IDENTITE_OPERATEUR", "").strip()
        siret = r.get("SIRET_ACTEUR", "").strip()
        rcs = r.get("RCS_ACTEUR", "").strip()
        address = r.get("ADRESSE_COMPLETE_ACTEUR", "").strip()
        dec_date = r.get("DATE_DECLARATION_OPERATEUR", "").strip()

        operators_dict[code] = name
        c.execute(
            "INSERT OR REPLACE INTO operators VALUES (?, ?, ?, ?, ?, ?)",
            (code, name, siret, rcs, address, dec_date)
        )
        op_count += 1

    print(f"[✓] {op_count:,} opérateurs insérés depuis identifiants_CE.")

    # 4. Insertion des tranches de numérotation (MAJNUM.csv)
    maj_text = decode_csv_content(maj_bytes)
    maj_reader = csv.DictReader(io.StringIO(maj_text), delimiter=';')
    range_count = 0
    latest_attr_date = ""

    for r in maj_reader:
        ez = r.get("EZABPQM", "").strip()
        debut = r.get("Tranche_Debut", "").strip()
        fin = r.get("Tranche_Fin", "").strip()
        code = r.get("Mnémo", r.get("Mn\xe9mo", "")).strip()
        op_name = operators_dict.get(code, code)
        territory = r.get("Territoire", "").strip()
        attr_date = r.get("Date_Attribution", "").strip()

        if attr_date and attr_date > latest_attr_date:
            latest_attr_date = attr_date

        c.execute(
            "INSERT INTO number_ranges (ezabpqm, tranche_debut, tranche_fin, operator_code, operator_name, territory, attribution_date) "
            "VALUES (?, ?, ?, ?, ?, ?, ?)",
            (ez, debut, fin, code, op_name, territory, attr_date)
        )
        range_count += 1

    print(f"[✓] {range_count:,} tranches de numérotation insérées depuis MAJNUM.")

    # 5. Restauration des notes utilisateurs existantes
    for note in existing_notes:
        c.execute(
            "INSERT OR REPLACE INTO call_notes VALUES (?, ?, ?, ?, ?, ?)",
            note
        )

    # 6. Création des métadonnées de version
    now_iso = datetime.datetime.now(datetime.timezone.utc).strftime("%d/%m/%Y %H:%M UTC")
    version_label = datetime.datetime.now(datetime.timezone.utc).strftime("%d/%m/%Y")
    if maj_mod:
        version_label = maj_mod

    metadata = [
        ("version_date", version_label),
        ("ce_version_date", ce_mod),
        ("generated_at", now_iso),
        ("ranges_count", str(range_count)),
        ("operators_count", str(op_count)),
        ("latest_attribution_date", latest_attr_date),
        ("majnum_sha256", maj_hash),
        ("ce_sha256", ce_hash),
        ("source_majnum_url", MAJNUM_URL),
        ("source_ce_url", CE_URL)
    ]
    c.executemany("INSERT INTO arcep_metadata VALUES (?, ?)", metadata)

    # 7. Création des index pour requêtes rapides
    print("[*] Création des index SQLite...")
    c.execute("CREATE INDEX idx_tranche ON number_ranges (tranche_debut, tranche_fin)")
    c.execute("CREATE INDEX idx_ezabpqm ON number_ranges (ezabpqm)")
    c.execute("CREATE INDEX idx_op_code ON number_ranges (operator_code)")
    conn.commit()

    print("[*] Optimisation SQLite (VACUUM)...")
    c.execute("VACUUM")
    conn.close()

    # Remplacement atomique
    if os.path.exists(output_path):
        os.remove(output_path)
    os.rename(temp_db, output_path)

    final_size_mb = os.path.getsize(output_path) / (1024 * 1024)
    print(f"[SUCCESS] Base ARCEP générée avec succès : {output_path}")
    print(f"          Taille finale : {final_size_mb:.2f} Mo")
    print(f"          Tranches : {range_count:,} | Opérateurs : {op_count:,} | Version : {version_label}")


def main():
    parser = argparse.ArgumentParser(description="Mise à jour et compilation de la base SQLite ARCEP")
    parser.add_argument("--output", default=DEFAULT_OUTPUT, help=f"Chemin de sortie de la base SQLite (défaut: {DEFAULT_OUTPUT})")
    args = parser.parse_args()

    build_database(args.output)


if __name__ == "__main__":
    main()
