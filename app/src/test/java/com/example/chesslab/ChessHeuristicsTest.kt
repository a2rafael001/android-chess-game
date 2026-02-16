package com.example.chesslab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChessHeuristicsTest {

    @Test
    fun pieceValue_returnsExpectedValues() {
        assertEquals(1, ChessHeuristics.pieceValue("piece_w_pawn"))
        assertEquals(3, ChessHeuristics.pieceValue("piece_b_knight"))
        assertEquals(3, ChessHeuristics.pieceValue("piece_w_bishop"))
        assertEquals(5, ChessHeuristics.pieceValue("piece_b_rook"))
        assertEquals(9, ChessHeuristics.pieceValue("piece_w_queen"))
        assertEquals(100, ChessHeuristics.pieceValue("piece_b_king"))
        assertEquals(0, ChessHeuristics.pieceValue("unknown_piece"))
    }

    @Test
    fun moveScore_rewardsGoodTacticalMove() {
        val safeCaptureScore = ChessHeuristics.moveScore(
            captureValue = 9,
            givesCheck = true,
            isPromotion = false,
            toRow = 3,
            toCol = 3,
            isDestinationAttacked = false,
            movingPieceValue = 3
        )

        val blunderScore = ChessHeuristics.moveScore(
            captureValue = 0,
            givesCheck = false,
            isPromotion = false,
            toRow = 0,
            toCol = 0,
            isDestinationAttacked = true,
            movingPieceValue = 9
        )

        assertTrue(safeCaptureScore > blunderScore)
    }

    @Test
    fun moveScore_rewardsPromotion() {
        val regularPawnPush = ChessHeuristics.moveScore(
            captureValue = 0,
            givesCheck = false,
            isPromotion = false,
            toRow = 1,
            toCol = 0,
            isDestinationAttacked = false,
            movingPieceValue = 1
        )

        val promotingMove = ChessHeuristics.moveScore(
            captureValue = 0,
            givesCheck = false,
            isPromotion = true,
            toRow = 0,
            toCol = 0,
            isDestinationAttacked = false,
            movingPieceValue = 1
        )

        assertTrue(promotingMove > regularPawnPush)
    }
}
