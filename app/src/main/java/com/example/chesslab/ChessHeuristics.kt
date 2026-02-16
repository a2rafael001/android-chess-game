package com.example.chesslab

object ChessHeuristics {
    fun pieceValue(pieceName: String): Int {
        return when {
            pieceName.endsWith("pawn") -> 1
            pieceName.endsWith("knight") || pieceName.endsWith("bishop") -> 3
            pieceName.endsWith("rook") -> 5
            pieceName.endsWith("queen") -> 9
            pieceName.endsWith("king") -> 100
            else -> 0
        }
    }

    fun moveScore(
        captureValue: Int,
        givesCheck: Boolean,
        isPromotion: Boolean,
        toRow: Int,
        toCol: Int,
        isDestinationAttacked: Boolean,
        movingPieceValue: Int
    ): Int {
        val centerBonus = if (toRow in 2..5 && toCol in 2..5) 1 else 0
        val checkBonus = if (givesCheck) 2 else 0
        val promotionBonus = if (isPromotion) 6 else 0
        val safetyPenalty = if (isDestinationAttacked) (movingPieceValue / 2).coerceAtLeast(1) else 0
        return captureValue + centerBonus + checkBonus + promotionBonus - safetyPenalty
    }
}
