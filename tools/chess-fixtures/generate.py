#!/usr/bin/env python3
"""Generates the reference data for :core:chess tests with python-chess.

Usage (from the repository root, in an environment with `pip install chess`):

    python tools/chess-fixtures/generate.py core/chess/src/test/resources/fixtures

Output is deterministic: a fixed seed, and gzip written without a timestamp.

games.tsv.gz  Random games, standard and Chess960. A line starting with "#" opens a game:
              "#<TAB>standard|chess960<TAB>start FEN". Every other line is one move:
              UCI, SAN, FEN after the move, flags ("+" check, "#" mate, "s" stalemate,
              "i" insufficient material; "-" if none).
chess960.txt  White's back rank for each of the 960 starting positions, by index.
"""
import gzip
import io
import random
import sys
from pathlib import Path

import chess

SEED = 20261112
STANDARD_GAMES = 70
CHESS960_GAMES = 50
MAX_PLIES = 110

# Positions that reach rarely seen rules quickly: promotions, en passant pins, underpromotion.
EXTRA_STARTS = [
    "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
    "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
    "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1",
    "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8",
    "8/PPPPPPPP/8/2k5/8/2K5/pppppppp/8 w - - 0 1",
    "4k3/8/8/8/8/8/8/R3K2R w KQ - 0 1",
    "n1n5/PPPk4/8/8/8/8/4Kppp/5N1N b - - 0 1",
]


def flags(board: chess.Board) -> str:
    result = ""
    if board.is_checkmate():
        result += "#"
    elif board.is_check():
        result += "+"
    if board.is_stalemate():
        result += "s"
    if board.is_insufficient_material():
        result += "i"
    return result or "-"


def play_game(rng: random.Random, board: chess.Board, out: io.StringIO) -> None:
    variant = "chess960" if board.chess960 else "standard"
    out.write(f"#\t{variant}\t{board.fen(en_passant='fen')}\n")
    for _ in range(MAX_PLIES):
        moves = list(board.legal_moves)
        if not moves:
            break
        # Castling, en passant and promotions are rare in random play; favour them.
        special = [m for m in moves if board.is_castling(m) or board.is_en_passant(m) or m.promotion]
        move = rng.choice(special) if special and rng.random() < 0.5 else rng.choice(moves)
        uci = board.uci(move)
        san = board.san(move)
        board.push(move)
        out.write(f"{uci}\t{san}\t{board.fen(en_passant='fen')}\t{flags(board)}\n")


def main() -> None:
    target = Path(sys.argv[1])
    target.mkdir(parents=True, exist_ok=True)
    rng = random.Random(SEED)

    out = io.StringIO()
    for _ in range(STANDARD_GAMES):
        play_game(rng, chess.Board(), out)
    for fen in EXTRA_STARTS:
        for _ in range(4):
            play_game(rng, chess.Board(fen), out)
    for _ in range(CHESS960_GAMES):
        play_game(rng, chess.Board.from_chess960_pos(rng.randrange(960)), out)
    with open(target / "games.tsv.gz", "wb") as raw:
        with gzip.GzipFile(fileobj=raw, mode="wb", mtime=0) as gz:
            gz.write(out.getvalue().encode("utf-8"))

    with open(target / "chess960.txt", "w", encoding="utf-8") as f:
        for index in range(960):
            board = chess.Board.from_chess960_pos(index)
            f.write("".join(board.piece_at(square).symbol() for square in range(8)) + "\n")


if __name__ == "__main__":
    main()
