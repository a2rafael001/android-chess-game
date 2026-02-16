package com.example.chesslab

import android.content.SharedPreferences
import android.graphics.drawable.TransitionDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.example.chesslab.databinding.ActivityChessTableBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.math.abs
import kotlin.random.Random

class GameActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_PLAYER_NAME = "player_name"
        const val EXTRA_LOAD_SAVED_GAME = "load_saved_game"
    }

    private data class GameSnapshot(
        val boardTags: List<Int?>,
        val isWhiteTurn: Boolean,
        val whiteCaptured: List<Int>,
        val blackCaptured: List<Int>,
        val moveHistory: List<Move>,
        val whiteKingMoved: Boolean,
        val blackKingMoved: Boolean,
        val whiteRookAMoved: Boolean,
        val whiteRookHMoved: Boolean,
        val blackRookAMoved: Boolean,
        val blackRookHMoved: Boolean,
        val enPassantTarget: Pair<Int, Int>?
    )

    private data class CandidateMove(val fromRow: Int, val fromCol: Int, val toRow: Int, val toCol: Int)

    private lateinit var binding: ActivityChessTableBinding
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var db: AppDatabase

    private lateinit var piecesGrid: GridLayout
    private var selected: Pair<Int, Int>? = null
    private var gameMode: String? = null
    private var playerName: String? = null
    private var isWhiteTurn = true

    private var whiteKingMoved = false
    private var blackKingMoved = false
    private var whiteRookAMoved = false
    private var whiteRookHMoved = false
    private var blackRookAMoved = false
    private var blackRookHMoved = false
    private var enPassantTarget: Pair<Int, Int>? = null

    private val undoStack = mutableListOf<GameSnapshot>()

    private val whiteCapturedPieces = mutableListOf<Int>()
    private val blackCapturedPieces = mutableListOf<Int>()
    private val moveHistory = mutableListOf<Move>()
    private lateinit var moveHistoryAdapter: MoveHistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChessTableBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getDatabase(this)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        gameMode = intent.getStringExtra(EXTRA_MODE)
        playerName = intent.getStringExtra(EXTRA_PLAYER_NAME)

        binding.selfProfile.background = getDrawable(R.drawable.highlight_transition)
        binding.opponentProfile.background = getDrawable(R.drawable.highlight_transition)

        setupToolbar()
        setupPlayerProfiles()
        setupActionButtons()
        setupMoveHistory()
        applyCoordinateSetting()

        initBoard()
        initPiecesLayer()

        if (intent.getBooleanExtra(EXTRA_LOAD_SAVED_GAME, false)) {
            loadSavedGame()
        } else {
            placeStartPosition()
        }

        Stats.incrementGamesToday(this)
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.setNavigationIcon(R.drawable.ic_settings_24)
        binding.toolbar.setNavigationOnClickListener { showGameMenuDialog() }
    }

    private fun setupPlayerProfiles() {
        binding.selfName.text = playerName
        when (gameMode) {
            "bot" -> binding.opponentName.text = "Chess Bot (1000)"
            "friend" -> binding.opponentName.text = "Friend"
        }
    }

    private fun setupActionButtons() {
        binding.btnRestart.setOnClickListener { placeStartPosition() }
        binding.btnHint.setOnClickListener { showHint() }
        binding.btnUndo.setOnClickListener { undoMove() }
        binding.btnDraw.setOnClickListener { offerDraw() }
    }

    private fun setupMoveHistory() {
        moveHistoryAdapter = MoveHistoryAdapter(moveHistory)
        binding.rvMoveHistory.adapter = moveHistoryAdapter
    }

    private fun applyCoordinateSetting() {
        val showCoordinates = sharedPreferences.getBoolean("show_coordinates", true)
        val visibility = if (showCoordinates) View.VISIBLE else View.GONE
        binding.topLetters.visibility = visibility
        binding.bottomLetters.visibility = visibility
        binding.leftNumbers.visibility = visibility
        binding.rightNumbers.visibility = visibility
    }

    private fun showGameMenuDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_pause_title)
            .setMessage(R.string.dialog_pause_message)
            .setNegativeButton(R.string.menu_exit_to_menu) { _, _ -> finish() }
            .setNeutralButton(R.string.menu_save_game) { _, _ -> saveGame() }
            .setPositiveButton(R.string.menu_return_to_game) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun initBoard() {
        val board = binding.tableBoard
        board.removeAllViews()
        for (i in 0..7) {
            val row = TableRow(this)
            row.layoutParams = TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            for (j in 0..7) {
                val cell = View(this)
                cell.setBackgroundResource(if ((i + j) % 2 == 0) R.drawable.cell_light else R.drawable.cell_dark)
                row.addView(cell, TableRow.LayoutParams(0, TableRow.LayoutParams.MATCH_PARENT, 1f))
            }
            board.addView(row)
        }
    }

    private fun initPiecesLayer() {
        piecesGrid = GridLayout(this).apply {
            rowCount = 8; columnCount = 8
            layoutParams = android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT)
        }
        binding.boardHolder.addView(piecesGrid)

        for (r in 0 until 8) for (c in 0 until 8) {
            val iv = ImageView(this).apply {
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = GridLayout.LayoutParams(GridLayout.spec(r, 1f), GridLayout.spec(c, 1f)).apply {
                    width = 0; height = 0
                    val m = (4 * resources.displayMetrics.density).toInt()
                    setMargins(m, m, m, m)
                }
                setOnClickListener { onCellClicked(r, c) }
            }
            piecesGrid.addView(iv)
        }
    }

    private fun cellAt(row: Int, col: Int): ImageView {
        return piecesGrid.getChildAt(row * 8 + col) as ImageView
    }

    private fun placeStartPosition() {
        deselectPiece()
        (0 until piecesGrid.childCount).forEach {
            val cell = piecesGrid.getChildAt(it) as ImageView
            cell.setImageDrawable(null); cell.tag = null
            cell.background = null
        }

        whiteCapturedPieces.clear()
        blackCapturedPieces.clear()
        updateCapturedPiecesUI()

        moveHistory.clear()
        moveHistoryAdapter.notifyDataSetChanged()
        undoStack.clear()

        for (c in 0 until 8) {
            cellAt(6, c).setImageResource(R.drawable.piece_w_pawn).also { cellAt(6, c).tag = R.drawable.piece_w_pawn }
            cellAt(1, c).setImageResource(R.drawable.piece_b_pawn).also { cellAt(1, c).tag = R.drawable.piece_b_pawn }
        }
        val whiteBack = intArrayOf(R.drawable.piece_w_rook, R.drawable.piece_w_knight, R.drawable.piece_w_bishop, R.drawable.piece_w_queen, R.drawable.piece_w_king, R.drawable.piece_w_bishop, R.drawable.piece_w_knight, R.drawable.piece_w_rook)
        val blackBack = intArrayOf(R.drawable.piece_b_rook, R.drawable.piece_b_knight, R.drawable.piece_b_bishop, R.drawable.piece_b_queen, R.drawable.piece_b_king, R.drawable.piece_b_bishop, R.drawable.piece_b_knight, R.drawable.piece_b_rook)
        for (c in 0 until 8) {
            cellAt(7, c).setImageResource(whiteBack[c]).also { cellAt(7, c).tag = whiteBack[c] }
            cellAt(0, c).setImageResource(blackBack[c]).also { cellAt(0, c).tag = blackBack[c] }
        }

        selected = null
        isWhiteTurn = true
        whiteKingMoved = false
        blackKingMoved = false
        whiteRookAMoved = false
        whiteRookHMoved = false
        blackRookAMoved = false
        blackRookHMoved = false
        enPassantTarget = null
        updateTurnHighlight()
    }

    private fun onCellClicked(row: Int, col: Int) {
        val pieceResId = cellAt(row, col).tag as? Int
        val isMyPiece = pieceResId?.let { isWhitePieceRes(it) == isWhiteTurn } ?: false

        if (selected == null) {
            if (isMyPiece) selectPiece(row, col)
        } else {
            val (sr, sc) = selected!!
            if (sr == row && sc == col) {
                deselectPiece()
                return
            }
            if (isMyPiece) {
                selectPiece(row, col)
                return
            }

            if (isValidMove(sr, sc, row, col, isWhiteTurn)) {
                pushUndoSnapshot()
                movePiece(sr, sc, row, col)
            } else {
                showToast(R.string.invalid_move)
            }
        }
    }

    private fun movePiece(fromRow: Int, fromCol: Int, toRow: Int, toCol: Int) {
        val from = cellAt(fromRow, fromCol)
        val to = cellAt(toRow, toCol)
        val movingPiece = from.tag as Int
        val movingPieceName = resources.getResourceEntryName(movingPiece)

        val wasEnPassantCapture = movingPieceName.endsWith("pawn") && to.tag == null && fromCol != toCol && enPassantTarget == (toRow to toCol)
        val capturedRes: Int? = if (wasEnPassantCapture) cellAt(fromRow, toCol).tag as? Int else to.tag as? Int
        val notation = getChessNotation(fromRow, fromCol, toRow, toCol, movingPiece, capturedRes)

        if (wasEnPassantCapture) {
            val capturedCell = cellAt(fromRow, toCol)
            capturedCell.setImageDrawable(null)
            capturedCell.tag = null
        }

        capturedRes?.let {
            if (isWhiteTurn) blackCapturedPieces.add(it) else whiteCapturedPieces.add(it)
            updateCapturedPiecesUI()
            updateCastlingRightsOnCapture(it, toRow, toCol, wasEnPassantCapture)
        }

        to.setImageDrawable(from.drawable)
        to.tag = from.tag
        from.setImageDrawable(null)
        from.tag = null

        // Castling move: king moved two squares, move rook too
        if (movingPieceName.endsWith("king") && abs(toCol - fromCol) == 2) {
            if (toCol == 6) {
                val rookFrom = cellAt(fromRow, 7)
                val rookTo = cellAt(fromRow, 5)
                rookTo.setImageDrawable(rookFrom.drawable)
                rookTo.tag = rookFrom.tag
                rookFrom.setImageDrawable(null)
                rookFrom.tag = null
            } else if (toCol == 2) {
                val rookFrom = cellAt(fromRow, 0)
                val rookTo = cellAt(fromRow, 3)
                rookTo.setImageDrawable(rookFrom.drawable)
                rookTo.tag = rookFrom.tag
                rookFrom.setImageDrawable(null)
                rookFrom.tag = null
            }
        }

        updateCastlingFlagsAfterMove(movingPiece, fromRow, fromCol)

        enPassantTarget = if (movingPieceName.endsWith("pawn") && abs(fromRow - toRow) == 2) {
            ((fromRow + toRow) / 2) to fromCol
        } else {
            null
        }

        deselectPiece()
        updateMoveHistory(notation)

        val pieceName = resources.getResourceEntryName(to.tag as Int)
        val promotionRank = if (isWhiteTurn) 0 else 7
        if (pieceName.endsWith("pawn") && toRow == promotionRank) {
            promotePawn(toRow, toCol)
        } else {
            changeTurn()
        }
    }

    private fun promotePawn(row: Int, col: Int) {
        if (gameMode == "bot" && !isWhiteTurn) {
            val newPiece = R.drawable.piece_b_queen
            cellAt(row, col).setImageResource(newPiece)
            cellAt(row, col).tag = newPiece
            changeTurn()
            return
        }

        val promotionPieces = if (isWhiteTurn) {
            arrayOf(R.drawable.piece_w_queen, R.drawable.piece_w_rook, R.drawable.piece_w_bishop, R.drawable.piece_w_knight)
        } else {
            arrayOf(R.drawable.piece_b_queen, R.drawable.piece_b_rook, R.drawable.piece_b_bishop, R.drawable.piece_b_knight)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.pawn_promotion_title)
            .setItems(R.array.promotion_piece_names) { _, which ->
                val newPiece = promotionPieces[which]
                cellAt(row, col).setImageResource(newPiece)
                cellAt(row, col).tag = newPiece
                changeTurn()
            }
            .setCancelable(false)
            .show()
    }

    private fun changeTurn() {
        isWhiteTurn = !isWhiteTurn
        updateTurnHighlight()

        if (isKingInCheck(isWhiteTurn)) {
            if (isCheckmate(isWhiteTurn)) {
                showGameOverDialog(!isWhiteTurn)
            } else {
                showToast(R.string.check)
            }
            return
        }

        if (isStalemate(isWhiteTurn)) {
            showGameOverDialog(null)
            return
        }

        if (gameMode == "bot" && !isWhiteTurn) {
            makeBotMove()
        }
    }

    private fun makeBotMove() {
        val bestMove = findBestMove(false)
        bestMove?.let {
            pushUndoSnapshot()
            movePiece(it.fromRow, it.fromCol, it.toRow, it.toCol)
        }
    }

    private fun allLegalMoves(forWhite: Boolean): List<CandidateMove> {
        return (0..63).flatMap { i ->
            val r = i / 8
            val c = i % 8
            val piece = cellAt(r, c).tag as? Int
            if (piece == null || isWhitePieceRes(piece) != forWhite) {
                emptyList()
            } else {
                (0..63).mapNotNull { j ->
                    val tr = j / 8
                    val tc = j % 8
                    if (isValidMove(r, c, tr, tc, forWhite)) CandidateMove(r, c, tr, tc) else null
                }
            }
        }
    }

    private fun isValidMove(fromRow: Int, fromCol: Int, toRow: Int, toCol: Int, forWhite: Boolean): Boolean {
        val fromCell = cellAt(fromRow, fromCol)
        if (fromCell.tag == null) return false

        if (!isMoveTheoreticallyPossible(fromRow, fromCol, toRow, toCol, forWhite)) return false

        val fromTag = fromCell.tag as Int
        val fromName = resources.getResourceEntryName(fromTag)
        val toCell = cellAt(toRow, toCol)

        // Simulate move (incl. en passant and castling rook movement)
        val oldFrom = fromCell.tag
        val oldTo = toCell.tag

        var enPassantCapturedTag: Any? = null
        var enPassantCapturedCell: ImageView? = null
        if (fromName.endsWith("pawn") && oldTo == null && fromCol != toCol && enPassantTarget == (toRow to toCol)) {
            enPassantCapturedCell = cellAt(fromRow, toCol)
            enPassantCapturedTag = enPassantCapturedCell.tag
            enPassantCapturedCell.tag = null
        }

        var rookFromCell: ImageView? = null
        var rookToCell: ImageView? = null
        var rookFromTag: Any? = null
        var rookToTag: Any? = null
        if (fromName.endsWith("king") && abs(toCol - fromCol) == 2) {
            if (toCol == 6) {
                rookFromCell = cellAt(fromRow, 7)
                rookToCell = cellAt(fromRow, 5)
            } else if (toCol == 2) {
                rookFromCell = cellAt(fromRow, 0)
                rookToCell = cellAt(fromRow, 3)
            }
            if (rookFromCell != null && rookToCell != null) {
                rookFromTag = rookFromCell.tag
                rookToTag = rookToCell.tag
                rookToCell.tag = rookFromCell.tag
                rookFromCell.tag = null
            }
        }

        toCell.tag = oldFrom
        fromCell.tag = null

        val kingInCheck = isKingInCheck(forWhite)

        fromCell.tag = oldFrom
        toCell.tag = oldTo
        if (enPassantCapturedCell != null) {
            enPassantCapturedCell.tag = enPassantCapturedTag
        }
        if (rookFromCell != null && rookToCell != null) {
            rookFromCell.tag = rookFromTag
            rookToCell.tag = rookToTag
        }

        return !kingInCheck
    }

    private fun isMoveTheoreticallyPossible(fromRow: Int, fromCol: Int, toRow: Int, toCol: Int, forWhite: Boolean): Boolean {
        if (fromRow == toRow && fromCol == toCol) return false

        val fromCell = cellAt(fromRow, fromCol)
        val toCell = cellAt(toRow, toCol)
        val pieceResId = fromCell.tag as? Int ?: return false
        val pieceName = resources.getResourceEntryName(pieceResId)
        val pieceColorIsWhite = isWhitePieceRes(pieceResId)

        if (pieceColorIsWhite != forWhite) return false

        (toCell.tag as? Int)?.let {
            if (isWhitePieceRes(it) == pieceColorIsWhite) return false
        }

        val dr = abs(fromRow - toRow)
        val dc = abs(fromCol - toCol)

        when {
            pieceName.endsWith("pawn") -> {
                val forward = if (pieceColorIsWhite) -1 else 1
                val startRow = if (pieceColorIsWhite) 6 else 1
                val targetIsEmpty = toCell.tag == null

                if (fromCol == toCol) {
                    if (fromRow + forward == toRow && targetIsEmpty) return true
                    if (fromRow == startRow && fromRow + 2 * forward == toRow && targetIsEmpty && cellAt(fromRow + forward, fromCol).tag == null) return true
                } else if (dc == 1 && fromRow + forward == toRow) {
                    if (!targetIsEmpty) return true
                    if (targetIsEmpty && enPassantTarget == (toRow to toCol)) return true
                }
                return false
            }
            pieceName.endsWith("rook") -> {
                if (dr != 0 && dc != 0) return false
                if (dr == 0) {
                    for (c in (minOf(fromCol, toCol) + 1) until maxOf(fromCol, toCol)) if (cellAt(fromRow, c).tag != null) return false
                } else {
                    for (r in (minOf(fromRow, toRow) + 1) until maxOf(fromRow, toRow)) if (cellAt(r, fromCol).tag != null) return false
                }
                return true
            }
            pieceName.endsWith("knight") -> {
                return (dr == 2 && dc == 1) || (dr == 1 && dc == 2)
            }
            pieceName.endsWith("bishop") -> {
                if (dr != dc) return false
                val rStep = if (toRow > fromRow) 1 else -1
                val cStep = if (toCol > fromCol) 1 else -1
                var r = fromRow + rStep
                var c = fromCol + cStep
                while (r != toRow) {
                    if (cellAt(r, c).tag != null) return false
                    r += rStep; c += cStep
                }
                return true
            }
            pieceName.endsWith("queen") -> {
                val isRookMove = dr == 0 || dc == 0
                val isBishopMove = dr == dc
                if (!isRookMove && !isBishopMove) return false
                if (isRookMove) {
                    if (dr == 0) {
                        for (c in (minOf(fromCol, toCol) + 1) until maxOf(fromCol, toCol)) if (cellAt(fromRow, c).tag != null) return false
                    } else {
                        for (r in (minOf(fromRow, toRow) + 1) until maxOf(fromRow, toRow)) if (cellAt(r, fromCol).tag != null) return false
                    }
                } else {
                    val rStep = if (toRow > fromRow) 1 else -1
                    val cStep = if (toCol > fromCol) 1 else -1
                    var r = fromRow + rStep
                    var c = fromCol + cStep
                    while (r != toRow) {
                        if (cellAt(r, c).tag != null) return false
                        r += rStep; c += cStep
                    }
                }
                return true
            }
            pieceName.endsWith("king") -> {
                if (dr <= 1 && dc <= 1) return true
                if (dr == 0 && dc == 2) return canCastle(fromRow, fromCol, toCol, pieceColorIsWhite)
                return false
            }
        }
        return false
    }

    private fun canCastle(row: Int, fromCol: Int, toCol: Int, isWhite: Boolean): Boolean {
        if (fromCol != 4) return false
        if (isWhite && whiteKingMoved) return false
        if (!isWhite && blackKingMoved) return false
        if (isKingInCheck(isWhite)) return false

        return if (toCol == 6) {
            val rookCol = 7
            val rook = cellAt(row, rookCol).tag as? Int ?: return false
            if (!resources.getResourceEntryName(rook).endsWith("rook")) return false
            if (isWhite && whiteRookHMoved) return false
            if (!isWhite && blackRookHMoved) return false
            if (cellAt(row, 5).tag != null || cellAt(row, 6).tag != null) return false
            !isSquareAttacked(row, 5, !isWhite) && !isSquareAttacked(row, 6, !isWhite)
        } else if (toCol == 2) {
            val rookCol = 0
            val rook = cellAt(row, rookCol).tag as? Int ?: return false
            if (!resources.getResourceEntryName(rook).endsWith("rook")) return false
            if (isWhite && whiteRookAMoved) return false
            if (!isWhite && blackRookAMoved) return false
            if (cellAt(row, 1).tag != null || cellAt(row, 2).tag != null || cellAt(row, 3).tag != null) return false
            !isSquareAttacked(row, 3, !isWhite) && !isSquareAttacked(row, 2, !isWhite)
        } else false
    }

    private fun isSquareAttacked(targetRow: Int, targetCol: Int, byWhite: Boolean): Boolean {
        return (0..63).any { i ->
            val r = i / 8
            val c = i % 8
            val resId = cellAt(r, c).tag as? Int ?: return@any false
            if (isWhitePieceRes(resId) != byWhite) return@any false
            isPieceAttackingSquare(r, c, targetRow, targetCol, resId)
        }
    }

    private fun isPieceAttackingSquare(fromRow: Int, fromCol: Int, targetRow: Int, targetCol: Int, pieceResId: Int): Boolean {
        if (fromRow == targetRow && fromCol == targetCol) return false
        val pieceName = resources.getResourceEntryName(pieceResId)
        val dr = targetRow - fromRow
        val dc = targetCol - fromCol
        val adr = abs(dr)
        val adc = abs(dc)

        return when {
            pieceName.endsWith("pawn") -> {
                val forward = if (isWhitePieceRes(pieceResId)) -1 else 1
                dr == forward && adc == 1
            }
            pieceName.endsWith("knight") -> (adr == 2 && adc == 1) || (adr == 1 && adc == 2)
            pieceName.endsWith("bishop") -> adr == adc && isDiagonalPathClear(fromRow, fromCol, targetRow, targetCol)
            pieceName.endsWith("rook") -> (adr == 0 || adc == 0) && isStraightPathClear(fromRow, fromCol, targetRow, targetCol)
            pieceName.endsWith("queen") ->
                ((adr == 0 || adc == 0) && isStraightPathClear(fromRow, fromCol, targetRow, targetCol)) ||
                    (adr == adc && isDiagonalPathClear(fromRow, fromCol, targetRow, targetCol))
            pieceName.endsWith("king") -> adr <= 1 && adc <= 1
            else -> false
        }
    }

    private fun isStraightPathClear(fromRow: Int, fromCol: Int, toRow: Int, toCol: Int): Boolean {
        if (fromRow == toRow) {
            for (c in (minOf(fromCol, toCol) + 1) until maxOf(fromCol, toCol)) {
                if (cellAt(fromRow, c).tag != null) return false
            }
        } else {
            for (r in (minOf(fromRow, toRow) + 1) until maxOf(fromRow, toRow)) {
                if (cellAt(r, fromCol).tag != null) return false
            }
        }
        return true
    }

    private fun isDiagonalPathClear(fromRow: Int, fromCol: Int, toRow: Int, toCol: Int): Boolean {
        val rStep = if (toRow > fromRow) 1 else -1
        val cStep = if (toCol > fromCol) 1 else -1
        var r = fromRow + rStep
        var c = fromCol + cStep
        while (r != toRow) {
            if (cellAt(r, c).tag != null) return false
            r += rStep
            c += cStep
        }
        return true
    }

    private fun isKingInCheck(isWhiteKing: Boolean): Boolean {
        val kingResId = if (isWhiteKing) R.drawable.piece_w_king else R.drawable.piece_b_king
        val kingPos = (0..63)
            .find { (cellAt(it / 8, it % 8).tag as? Int) == kingResId }
            ?.let { it / 8 to it % 8 }
            ?: return true

        return isSquareAttacked(kingPos.first, kingPos.second, !isWhiteKing)
    }

    private fun hasAnyValidMoves(isWhitePlayer: Boolean): Boolean {
        return (0..63).any { i ->
            val r = i / 8; val c = i % 8
            (cellAt(r, c).tag as? Int)?.let {
                val isMyPiece = isWhitePieceRes(it) == isWhitePlayer
                if (isMyPiece) {
                    (0..63).any { j -> isValidMove(r, c, j / 8, j % 8, isWhitePlayer) }
                } else false
            } ?: false
        }
    }

    private fun isCheckmate(isWhitePlayer: Boolean): Boolean {
        return isKingInCheck(isWhitePlayer) && !hasAnyValidMoves(isWhitePlayer)
    }

    private fun isStalemate(isWhitePlayer: Boolean): Boolean {
        return !isKingInCheck(isWhitePlayer) && !hasAnyValidMoves(isWhitePlayer)
    }

    private fun showGameOverDialog(winnerIsWhite: Boolean?) {
        val title: Int
        val message: Int
        val result: String

        when (winnerIsWhite) {
            true -> {
                title = R.string.game_over_checkmate_title
                message = R.string.game_over_checkmate_white_wins
                result = if (playerName == binding.selfName.text.toString()) "Win" else "Loss"
            }
            false -> {
                title = R.string.game_over_checkmate_title
                message = R.string.game_over_checkmate_black_wins
                result = if (playerName == binding.selfName.text.toString()) "Loss" else "Win"
            }
            null -> {
                title = R.string.game_over_stalemate_title
                message = R.string.game_over_stalemate_body
                result = "Draw"
            }
        }

        lifecycleScope.launch {
            val gameRecord = GameRecord(
                playerName = playerName ?: getString(R.string.guest_user_name),
                opponentName = binding.opponentName.text.toString(),
                result = result,
                date = Date()
            )
            db.gameRecordDao().insert(gameRecord)
            db.savedGameDao().deleteSavedGame(playerName ?: getString(R.string.guest_user_name))
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.game_over_new_game) { _, _ -> placeStartPosition() }
            .setNegativeButton(R.string.game_over_exit_menu) { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun selectPiece(row: Int, col: Int) {
        deselectPiece()
        cellAt(row, col).setBackgroundResource(R.drawable.sel_bg)
        selected = row to col

        for (r in 0..7) {
            for (c in 0..7) {
                if (isValidMove(row, col, r, c, isWhiteTurn)) {
                    val targetCell = cellAt(r, c)
                    if (targetCell.tag == null) {
                        targetCell.setImageResource(R.drawable.move_dot)
                    } else {
                        targetCell.setBackgroundResource(R.drawable.capture_bg)
                    }
                }
            }
        }
    }

    private fun deselectPiece() {
        selected?.let { (r, c) -> cellAt(r, c).background = null }
        selected = null

        for (i in 0 until piecesGrid.childCount) {
            val cell = piecesGrid.getChildAt(i) as ImageView
            if (cell.tag == null) {
                cell.setImageDrawable(null)
            }
            cell.background = null
        }
    }

    private fun updateTurnHighlight() {
        val selfTransition = binding.selfProfile.background as TransitionDrawable
        val opponentTransition = binding.opponentProfile.background as TransitionDrawable

        if (isWhiteTurn) {
            selfTransition.startTransition(200)
            opponentTransition.reverseTransition(200)
        } else {
            selfTransition.reverseTransition(200)
            opponentTransition.startTransition(200)
        }
    }

    private fun updateCapturedPiecesUI() {
        binding.selfCapturedPieces.text = blackCapturedPieces.joinToString("") { getUnicodePiece(it) }
        binding.opponentCapturedPieces.text = whiteCapturedPieces.joinToString("") { getUnicodePiece(it) }
    }

    private fun getUnicodePiece(resId: Int): String {
        return when (resId) {
            R.drawable.piece_w_pawn, R.drawable.piece_b_pawn -> "♙"
            R.drawable.piece_w_rook, R.drawable.piece_b_rook -> "♖"
            R.drawable.piece_w_knight, R.drawable.piece_b_knight -> "♘"
            R.drawable.piece_w_bishop, R.drawable.piece_b_bishop -> "♗"
            R.drawable.piece_w_queen, R.drawable.piece_b_queen -> "♕"
            R.drawable.piece_w_king, R.drawable.piece_b_king -> "♔"
            else -> ""
        }
    }

    private fun getChessNotation(fromRow: Int, fromCol: Int, toRow: Int, toCol: Int, pieceResId: Int, capturedPieceResId: Int?): String {
        val pieceName = resources.getResourceEntryName(pieceResId)

        if (pieceName.endsWith("king") && abs(toCol - fromCol) == 2) {
            return if (toCol == 6) "O-O" else "O-O-O"
        }

        val pieceChar = when {
            pieceName.endsWith("knight") -> "N"
            pieceName.endsWith("bishop") -> "B"
            pieceName.endsWith("rook") -> "R"
            pieceName.endsWith("queen") -> "Q"
            pieceName.endsWith("king") -> "K"
            else -> ""
        }
        val from = "${'a' + fromCol}${8 - fromRow}"
        val to = "${'a' + toCol}${8 - toRow}"
        val capture = if (capturedPieceResId != null) "x" else ""
        return "$pieceChar$from$capture$to"
    }

    private fun updateMoveHistory(notation: String) {
        if (isWhiteTurn) {
            val moveNumber = moveHistory.size + 1
            moveHistory.add(Move(moveNumber, notation, null))
            moveHistoryAdapter.notifyItemInserted(moveHistory.size - 1)
        } else {
            val lastMove = moveHistory.lastOrNull()
            if (lastMove == null) {
                moveHistory.add(Move(1, "", notation))
                moveHistoryAdapter.notifyItemInserted(moveHistory.size - 1)
            } else {
                val newMove = lastMove.copy(blackMove = notation)
                moveHistory[moveHistory.size - 1] = newMove
                moveHistoryAdapter.notifyItemChanged(moveHistory.size - 1)
            }
        }
        binding.rvMoveHistory.scrollToPosition(moveHistory.size - 1)
    }

    private fun showToast(resId: Int, arg: String? = null) {
        Toast.makeText(this, getString(resId, arg), Toast.LENGTH_SHORT).show()
    }

    private fun showHint() {
        val best = findBestMove(isWhiteTurn)
        if (best == null) {
            showToast(R.string.hint_no_moves)
            return
        }

        deselectPiece()
        cellAt(best.fromRow, best.fromCol).setBackgroundResource(R.drawable.sel_bg)
        val target = cellAt(best.toRow, best.toCol)
        if (target.tag == null) {
            target.setImageResource(R.drawable.move_dot)
        } else {
            target.setBackgroundResource(R.drawable.capture_bg)
        }

        val piece = cellAt(best.fromRow, best.fromCol).tag as Int
        val captured = cellAt(best.toRow, best.toCol).tag as? Int
        val notation = getChessNotation(best.fromRow, best.fromCol, best.toRow, best.toCol, piece, captured)
        Toast.makeText(this, getString(R.string.hint_best_move, notation), Toast.LENGTH_SHORT).show()
    }

    private fun offerDraw() {
        if (gameMode == "bot") {
            val accepted = Random.nextInt(100) < 35
            if (accepted) {
                showGameOverDialog(null)
            } else {
                showToast(R.string.draw_declined)
            }
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.draw_offer_title)
            .setMessage(R.string.draw_offer_message)
            .setPositiveButton(R.string.yes) { _, _ -> showGameOverDialog(null) }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private fun undoMove() {
        val snapshot = undoStack.removeLastOrNull()
        if (snapshot == null) {
            showToast(R.string.undo_nothing_to_undo)
            return
        }

        restoreSnapshot(snapshot)
        showToast(R.string.undo_done)
    }

    private fun saveGame() {
        lifecycleScope.launch {
            val boardState = serializeBoardState()
            val savedGame = SavedGame(
                playerName = playerName ?: getString(R.string.guest_user_name),
                opponentName = binding.opponentName.text.toString(),
                boardState = boardState,
                isWhiteTurn = isWhiteTurn,
                whiteCapturedPieces = whiteCapturedPieces.joinToString(","),
                blackCapturedPieces = blackCapturedPieces.joinToString(","),
                savedAt = Date()
            )
            db.savedGameDao().saveGame(savedGame)
            showToast(R.string.game_saved)
        }
    }

    private fun loadSavedGame() {
        lifecycleScope.launch {
            val savedGame = db.savedGameDao().getSavedGame(playerName ?: getString(R.string.guest_user_name))
            if (savedGame != null) {
                applySerializedBoardState(savedGame.boardState)
                isWhiteTurn = savedGame.isWhiteTurn
                whiteCapturedPieces.clear()
                whiteCapturedPieces.addAll(savedGame.whiteCapturedPieces.split(",").mapNotNull { it.toIntOrNull() })
                blackCapturedPieces.clear()
                blackCapturedPieces.addAll(savedGame.blackCapturedPieces.split(",").mapNotNull { it.toIntOrNull() })
                updateCapturedPiecesUI()
                updateTurnHighlight()
            } else {
                placeStartPosition()
            }
        }
    }

    private fun serializeBoardState(): String {
        val board = (0 until piecesGrid.childCount).joinToString(",") {
            (piecesGrid.getChildAt(it) as ImageView).tag?.toString() ?: "0"
        }
        val meta = listOf(
            "wkm=${if (whiteKingMoved) 1 else 0}",
            "bkm=${if (blackKingMoved) 1 else 0}",
            "wra=${if (whiteRookAMoved) 1 else 0}",
            "wrh=${if (whiteRookHMoved) 1 else 0}",
            "bra=${if (blackRookAMoved) 1 else 0}",
            "brh=${if (blackRookHMoved) 1 else 0}",
            "ep=${enPassantTarget?.let { "${it.first}:${it.second}" } ?: "-"}"
        ).joinToString(";")
        return "$board|$meta"
    }

    private fun applySerializedBoardState(serialized: String) {
        val (boardPart, metaPart) = serialized.split("|", limit = 2).let {
            it[0] to it.getOrNull(1)
        }

        val boardState = boardPart.split(",")
        (0 until piecesGrid.childCount).forEach { i ->
            val tag = boardState.getOrNull(i)?.toIntOrNull()
            if (tag != null && tag != 0) {
                (piecesGrid.getChildAt(i) as ImageView).setImageResource(tag)
                (piecesGrid.getChildAt(i) as ImageView).tag = tag
            } else {
                (piecesGrid.getChildAt(i) as ImageView).setImageDrawable(null)
                (piecesGrid.getChildAt(i) as ImageView).tag = null
            }
        }

        whiteKingMoved = false
        blackKingMoved = false
        whiteRookAMoved = false
        whiteRookHMoved = false
        blackRookAMoved = false
        blackRookHMoved = false
        enPassantTarget = null

        metaPart?.split(";")?.forEach { part ->
            val (k, v) = part.split("=", limit = 2).let { it[0] to it.getOrNull(1).orEmpty() }
            when (k) {
                "wkm" -> whiteKingMoved = v == "1"
                "bkm" -> blackKingMoved = v == "1"
                "wra" -> whiteRookAMoved = v == "1"
                "wrh" -> whiteRookHMoved = v == "1"
                "bra" -> blackRookAMoved = v == "1"
                "brh" -> blackRookHMoved = v == "1"
                "ep" -> enPassantTarget = v.takeIf { it != "-" }?.split(":", limit = 2)?.let {
                    val r = it.getOrNull(0)?.toIntOrNull()
                    val c = it.getOrNull(1)?.toIntOrNull()
                    if (r != null && c != null) r to c else null
                }
            }
        }
    }

    private fun pushUndoSnapshot() {
        val boardTags = (0 until piecesGrid.childCount).map {
            (piecesGrid.getChildAt(it) as ImageView).tag as? Int
        }
        undoStack.add(
            GameSnapshot(
                boardTags = boardTags,
                isWhiteTurn = isWhiteTurn,
                whiteCaptured = whiteCapturedPieces.toList(),
                blackCaptured = blackCapturedPieces.toList(),
                moveHistory = moveHistory.toList(),
                whiteKingMoved = whiteKingMoved,
                blackKingMoved = blackKingMoved,
                whiteRookAMoved = whiteRookAMoved,
                whiteRookHMoved = whiteRookHMoved,
                blackRookAMoved = blackRookAMoved,
                blackRookHMoved = blackRookHMoved,
                enPassantTarget = enPassantTarget
            )
        )
    }

    private fun restoreSnapshot(snapshot: GameSnapshot) {
        snapshot.boardTags.forEachIndexed { i, tag ->
            val cell = piecesGrid.getChildAt(i) as ImageView
            if (tag == null || tag == 0) {
                cell.setImageDrawable(null)
                cell.tag = null
            } else {
                cell.setImageResource(tag)
                cell.tag = tag
            }
            cell.background = null
        }

        selected = null
        isWhiteTurn = snapshot.isWhiteTurn

        whiteCapturedPieces.clear()
        whiteCapturedPieces.addAll(snapshot.whiteCaptured)
        blackCapturedPieces.clear()
        blackCapturedPieces.addAll(snapshot.blackCaptured)

        moveHistory.clear()
        moveHistory.addAll(snapshot.moveHistory)
        moveHistoryAdapter.notifyDataSetChanged()

        whiteKingMoved = snapshot.whiteKingMoved
        blackKingMoved = snapshot.blackKingMoved
        whiteRookAMoved = snapshot.whiteRookAMoved
        whiteRookHMoved = snapshot.whiteRookHMoved
        blackRookAMoved = snapshot.blackRookAMoved
        blackRookHMoved = snapshot.blackRookHMoved
        enPassantTarget = snapshot.enPassantTarget

        updateCapturedPiecesUI()
        updateTurnHighlight()
    }

    private fun updateCastlingFlagsAfterMove(movedPieceRes: Int, fromRow: Int, fromCol: Int) {
        when (movedPieceRes) {
            R.drawable.piece_w_king -> whiteKingMoved = true
            R.drawable.piece_b_king -> blackKingMoved = true
            R.drawable.piece_w_rook -> {
                if (fromRow == 7 && fromCol == 0) whiteRookAMoved = true
                if (fromRow == 7 && fromCol == 7) whiteRookHMoved = true
            }
            R.drawable.piece_b_rook -> {
                if (fromRow == 0 && fromCol == 0) blackRookAMoved = true
                if (fromRow == 0 && fromCol == 7) blackRookHMoved = true
            }
        }
    }

    private fun updateCastlingRightsOnCapture(capturedRes: Int, toRow: Int, toCol: Int, wasEnPassantCapture: Boolean) {
        if (wasEnPassantCapture) return
        when (capturedRes) {
            R.drawable.piece_w_rook -> {
                if (toRow == 7 && toCol == 0) whiteRookAMoved = true
                if (toRow == 7 && toCol == 7) whiteRookHMoved = true
            }
            R.drawable.piece_b_rook -> {
                if (toRow == 0 && toCol == 0) blackRookAMoved = true
                if (toRow == 0 && toCol == 7) blackRookHMoved = true
            }
        }
    }

    private fun findBestMove(forWhite: Boolean): CandidateMove? {
        val moves = allLegalMoves(forWhite)
        if (moves.isEmpty()) return null

        val scored = moves.map { it to evaluateCandidateMove(it, forWhite) }
        val bestScore = scored.maxOf { it.second }
        val topMoves = scored.filter { it.second == bestScore }.map { it.first }
        return topMoves.randomOrNull()
    }

    private fun evaluateCandidateMove(move: CandidateMove, movingWhite: Boolean): Int {
        val fromCell = cellAt(move.fromRow, move.fromCol)
        val toCell = cellAt(move.toRow, move.toCol)
        val movingResId = fromCell.tag as? Int ?: return Int.MIN_VALUE
        val movingName = resources.getResourceEntryName(movingResId)

        val wasEnPassantCapture = movingName.endsWith("pawn") && toCell.tag == null && move.fromCol != move.toCol && enPassantTarget == (move.toRow to move.toCol)
        val capturedRes = if (wasEnPassantCapture) cellAt(move.fromRow, move.toCol).tag as? Int else toCell.tag as? Int

        val oldFrom = fromCell.tag
        val oldTo = toCell.tag

        var enPassantCapturedCell: ImageView? = null
        var enPassantCapturedOldTag: Any? = null
        if (wasEnPassantCapture) {
            enPassantCapturedCell = cellAt(move.fromRow, move.toCol)
            enPassantCapturedOldTag = enPassantCapturedCell.tag
            enPassantCapturedCell.tag = null
        }

        var rookFromCell: ImageView? = null
        var rookToCell: ImageView? = null
        var rookFromOldTag: Any? = null
        var rookToOldTag: Any? = null
        if (movingName.endsWith("king") && abs(move.toCol - move.fromCol) == 2) {
            if (move.toCol == 6) {
                rookFromCell = cellAt(move.fromRow, 7)
                rookToCell = cellAt(move.fromRow, 5)
            } else if (move.toCol == 2) {
                rookFromCell = cellAt(move.fromRow, 0)
                rookToCell = cellAt(move.fromRow, 3)
            }
            if (rookFromCell != null && rookToCell != null) {
                rookFromOldTag = rookFromCell.tag
                rookToOldTag = rookToCell.tag
                rookToCell.tag = rookFromCell.tag
                rookFromCell.tag = null
            }
        }

        toCell.tag = oldFrom
        fromCell.tag = null

        val givesCheck = isKingInCheck(!movingWhite)
        val isPromotion = movingName.endsWith("pawn") && (if (movingWhite) move.toRow == 0 else move.toRow == 7)
        val destinationAttacked = isSquareAttacked(move.toRow, move.toCol, !movingWhite)

        fromCell.tag = oldFrom
        toCell.tag = oldTo
        if (enPassantCapturedCell != null) enPassantCapturedCell.tag = enPassantCapturedOldTag
        if (rookFromCell != null && rookToCell != null) {
            rookFromCell.tag = rookFromOldTag
            rookToCell.tag = rookToOldTag
        }

        val movingValue = pieceValue(movingResId)
        val captureValue = capturedRes?.let { pieceValue(it) } ?: 0

        return ChessHeuristics.moveScore(
            captureValue = captureValue,
            givesCheck = givesCheck,
            isPromotion = isPromotion,
            toRow = move.toRow,
            toCol = move.toCol,
            isDestinationAttacked = destinationAttacked,
            movingPieceValue = movingValue
        )
    }

    private fun pieceValue(resId: Int): Int {
        return ChessHeuristics.pieceValue(resources.getResourceEntryName(resId))
    }

    private fun isWhitePieceRes(resId: Int): Boolean {
        return resources.getResourceEntryName(resId).contains("_w_")
    }

    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.slide_in_left, R.anim.slide_out_right)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
    }
}
