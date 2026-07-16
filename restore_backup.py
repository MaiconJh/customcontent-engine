#!/usr/bin/env python3
"""
Restaurador de backup do Project Manager & SV Patch.

Lê um arquivo JSON no formato de exportação AI (schema_version: 2,
report_type: "project_export_ai") e restaura todos os arquivos listados
na seção "files", recriando a estrutura de diretórios a partir do caminho
relativo e do conteúdo armazenado.

Uso:
    python restore_backup.py backup.json [--dest DIR] [--force] [--dry-run]

Opções:
    --dest DIR      Diretório onde restaurar os arquivos (padrão: diretório atual)
    --force          Sobrescrever arquivos existentes sem perguntar
    --dry-run        Apenas simular a restauração, sem escrever nada
    --verbose        Mostrar informações detalhadas

Exemplo:
    python restore_backup.py report_latest_backup.json --dest ./restored_project
"""

import argparse
import json
import sys
import os
from pathlib import Path
import logging

def setup_logging(verbose):
    level = logging.DEBUG if verbose else logging.INFO
    logging.basicConfig(
        level=level,
        format='%(asctime)s - %(levelname)s - %(message)s',
        datefmt='%Y-%m-%d %H:%M:%S'
    )

def load_backup(file_path):
    """Carrega e valida o arquivo JSON de backup."""
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            data = json.load(f)
    except json.JSONDecodeError as e:
        logging.error(f"Erro ao decodificar JSON: {e}")
        sys.exit(1)
    except FileNotFoundError:
        logging.error(f"Arquivo não encontrado: {file_path}")
        sys.exit(1)

    # Validação básica do schema
    if data.get('schema_version') != 2:
        logging.warning("Schema version diferente de 2, pode não ser compatível.")
    if data.get('report_type') != 'project_export_ai':
        logging.warning("Report type não é 'project_export_ai', pode não ser compatível.")

    files = data.get('files')
    if not isinstance(files, list):
        logging.error("Campo 'files' não é uma lista ou está ausente.")
        sys.exit(1)

    return data

def restore_files(data, dest_root, force=False, dry_run=False):
    """Restaura os arquivos listados no backup para o diretório de destino."""
    files = data.get('files', [])
    total = len(files)
    logging.info(f"Preparando restauração de {total} arquivos.")

    project_path = data.get('meta', {}).get('project_path', '')
    if project_path:
        logging.info(f"Projeto original: {project_path}")

    dest_root = Path(dest_root).resolve()
    logging.info(f"Destino: {dest_root}")

    restored_count = 0
    skipped_count = 0

    for entry in files:
        rel_path = entry.get('path')
        if not rel_path:
            logging.warning("Entrada sem 'path', ignorada.")
            continue

        content = entry.get('content', '')
        # Se for None, tratar como string vazia
        if content is None:
            content = ''

        # Se for binário, pode estar vazio ou codificado; assumimos que é texto
        # e o conteúdo já está como string no JSON.

        target_path = dest_root / rel_path

        # Verifica se já existe e se deve sobrescrever
        if target_path.exists() and not force:
            logging.warning(f"Arquivo já existe: {target_path}. Use --force para sobrescrever.")
            skipped_count += 1
            continue

        if dry_run:
            logging.info(f"[DRY-RUN] Criaria: {target_path} ({len(content)} bytes)")
            restored_count += 1
            continue

        try:
            # Cria diretório pai se não existir
            target_path.parent.mkdir(parents=True, exist_ok=True)

            # Escreve o conteúdo (se vazio, cria arquivo vazio)
            with open(target_path, 'w', encoding='utf-8') as f:
                f.write(content)

            logging.debug(f"Restaurado: {target_path}")
            restored_count += 1

        except Exception as e:
            logging.error(f"Erro ao escrever {target_path}: {e}")
            # Continua com os próximos

    logging.info(f"Restauração concluída: {restored_count} arquivos restaurados, {skipped_count} ignorados/erros.")


def main():
    parser = argparse.ArgumentParser(
        description="Restaura arquivos a partir de um backup JSON do Project Manager & SV Patch.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Exemplos:
  python restore_backup.py backup.json
  python restore_backup.py backup.json --dest ./restored --force
  python restore_backup.py backup.json --dry-run
        """
    )
    parser.add_argument('backup_file', help='Caminho para o arquivo JSON de backup')
    parser.add_argument('--dest', default='.', help='Diretório destino (padrão: diretório atual)')
    parser.add_argument('--force', action='store_true', help='Sobrescrever arquivos existentes')
    parser.add_argument('--dry-run', action='store_true', help='Simular restauração sem escrever')
    parser.add_argument('--verbose', action='store_true', help='Mostrar informações detalhadas')

    args = parser.parse_args()

    setup_logging(args.verbose)

    data = load_backup(args.backup_file)
    restore_files(data, args.dest, args.force, args.dry_run)


if __name__ == '__main__':
    main()