"""Genera una copia N veces más grande del dataset oficial para medir escalabilidad.

Cada repetición desplaza el identificador de la primera columna (+10000 por copia) para que
las filas no se rechacen como duplicadas; el resto de errores del dataset se conserva.
Uso: python3 benchmark/generate_scaled_dataset.py <factor> <directorio_salida>
"""
import csv
import pathlib
import sys

SOURCE = pathlib.Path(__file__).resolve().parent.parent / "src/main/resources/data"
FILES = ["movimientos_financieros_diarios.csv", "intereses_trimestrales.csv", "estados_financieros_anuales.csv"]


def main(factor: int, out_dir: pathlib.Path) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    for name in FILES:
        with open(SOURCE / name, encoding="utf-8") as f:
            header, *rows = list(csv.reader(f))
        with open(out_dir / name, "w", encoding="utf-8", newline="") as f:
            writer = csv.writer(f, lineterminator="\n")
            writer.writerow(header)
            for copy in range(factor):
                for row in rows:
                    row = list(row)
                    if row[0].isdigit():
                        row[0] = str(int(row[0]) + copy * 10000)
                    writer.writerow(row)


if __name__ == "__main__":
    main(int(sys.argv[1]), pathlib.Path(sys.argv[2]))
