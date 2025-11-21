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

class GameActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_PLAYER_NAME = "player_name"
        const val EXTRA_LOAD_SAVED_GAME = "load_saved_game"
    }

    private lateinit var binding: ActivityChessTableBinding
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var db: AppDatabase

    private lateinit var piecesGrid: GridLayout
    private var selected: Pair<Int, Int>? = null
    private var gameMode: String? = null
    private var playerName: String? = null
    private var isWhiteTurn = true

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
        binding.btnHint.setOnClickListener { showToast(R.string.toast_hint_soon) }
        binding.btnUndo.setOnClickListener { showToast(R.string.toast_undo_soon) }
        binding.btnDraw.setOnClickListener { showToast(R.string.toast_draw_soon) }
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
        updateTurnHighlight()
    }

    private fun onCellClicked(row: Int, col: Int) {
        val pieceResId = cellAt(row, col).tag as? Int
        val isMyPiece = pieceResId?.let { resources.getResourceEntryName(it).contains("_w_") == isWhiteTurn } ?: false

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
                movePiece(sr, sc, row, col)
            } else {
                showToast(R.string.invalid_move)
            }
        }
    }

    private fun movePiece(fromRow: Int, fromCol: Int, toRow: Int, toCol: Int) {
        val from = cellAt(fromRow, fromCol)
        val to = cellAt(toRow, toCol)

        val notation = getChessNotation(fromRow, fromCol, toRow, toCol, from.tag as Int, to.tag as? Int)

        (to.tag as? Int)?.let {
            if (isWhiteTurn) blackCapturedPieces.add(it) else whiteCapturedPieces.add(it)
            updateCapturedPiecesUI()
        }

        to.setImageDrawable(from.drawable)
        to.tag = from.tag
        from.setImageDrawable(null)
        from.tag = null
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
            showGameOverDialog(null) // null winner for draw
            return
        }

        if (gameMode == "bot" && !isWhiteTurn) {
            makeBotMove()
        }
    }

    private fun makeBotMove() {
        val allMyPieces = (0..63).mapNotNull { i ->
            val r = i / 8; val c = i % 8
            (cellAt(r, c).tag as? Int)?.let { resId ->
                if (resources.getResourceEntryName(resId).contains("_b_")) r to c else null
            }
        }

        val possibleMoves = allMyPieces.flatMap { (r, c) ->
            (0..63).mapNotNull { i ->
                val tr = i / 8; val tc = i % 8
                if (isValidMove(r, c, tr, tc, false)) listOf(r, c, tr, tc) else null
            }
        }

        val captureMoves = possibleMoves.filter { cellAt(it[2], it[3]).tag != null }

        val move = captureMoves.ifEmpty { possibleMoves }.randomOrNull()
        move?.let { 
            movePiece(it[0], it[1], it[2], it[3])
        }
    }

    private fun isValidMove(fromRow: Int, fromCol: Int, toRow: Int, toCol: Int, forWhite: Boolean): Boolean {
        val fromCell = cellAt(fromRow, fromCol)
        if (fromCell.tag == null) return false

        if (!isMoveTheoreticallyPossible(fromRow, fromCol, toRow, toCol, forWhite)) return false

        val toCell = cellAt(toRow, toCol)
        val fromTag = fromCell.tag
        val toTag = toCell.tag
        toCell.tag = fromTag
        fromCell.tag = null

        val kingInCheck = isKingInCheck(forWhite)

        fromCell.tag = fromTag
        toCell.tag = toTag

        return !kingInCheck
    }

    private fun isMoveTheoreticallyPossible(fromRow: Int, fromCol: Int, toRow: Int, toCol: Int, forWhite: Boolean): Boolean {
        val fromCell = cellAt(fromRow, fromCol)
        val toCell = cellAt(toRow, toCol)
        val pieceResId = fromCell.tag as? Int ?: return false
        val pieceName = resources.getResourceEntryName(pieceResId)
        val pieceColorIsWhite = pieceName.contains("_w_")

        if (pieceColorIsWhite != forWhite) return false

        (toCell.tag as? Int)?.let {
            if (resources.getResourceEntryName(it).contains("_w_") == pieceColorIsWhite) return false
        }

        val dr = abs(fromRow - toRow)
        val dc = abs(fromCol - toCol)

        when {
            pieceName.endsWith("pawn") -> {
                val forward = if (pieceColorIsWhite) -1 else 1
                val startRow = if (pieceColorIsWhite) 6 else 1
                val targetIsEmpty = toCell.tag == null

                if (fromCol == toCol) { // Move forward
                    if (fromRow + forward == toRow && targetIsEmpty) return true
                    if (fromRow == startRow && fromRow + 2 * forward == toRow && targetIsEmpty && cellAt(fromRow + forward, fromCol).tag == null) return true
                } else if (dc == 1 && fromRow + forward == toRow && !targetIsEmpty) { // Capture
                    return true
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
                } else { // isBishopMove
                    val rStep = if (toRow > fromRow) 1 else -1
                    val cStep = if (toCol > fromCol) 1 else -1
                    var r = fromRow + rStep; var c = fromCol + cStep
                    while (r != toRow) {
                        if (cellAt(r, c).tag != null) return false
                        r += rStep; c += cStep
                    }
                }
                return true
            }
            pieceName.endsWith("king") -> {
                return dr <= 1 && dc <= 1
            }
        }
        return false
    }

    private fun isKingInCheck(isWhiteKing: Boolean): Boolean {
        val kingResId = if (isWhiteKing) R.drawable.piece_w_king else R.drawable.piece_b_king
        val kingPos = (0..63).find { (cellAt(it / 8, it % 8).tag as? Int) == kingResId }?.let { it / 8 to it % 8 } ?: return true

        val opponentIsWhite = !isWhiteKing
        return (0..63).any { i ->
            val r = i / 8; val c = i % 8
            (cellAt(r, c).tag as? Int)?.let {
                val isOpponentPiece = resources.getResourceEntryName(it).contains("_w_") == opponentIsWhite
                if (isOpponentPiece) {
                    isMoveTheoreticallyPossible(r, c, kingPos.first, kingPos.second, opponentIsWhite)
                } else false
            } ?: false
        }
    }

    private fun hasAnyValidMoves(isWhitePlayer: Boolean): Boolean {
        return (0..63).any { i ->
            val r = i / 8; val c = i % 8
            (cellAt(r, c).tag as? Int)?.let {
                val isMyPiece = resources.getResourceEntryName(it).contains("_w_") == isWhitePlayer
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
            val lastMove = moveHistory.last()
            val newMove = lastMove.copy(blackMove = notation)
            moveHistory[moveHistory.size - 1] = newMove
            moveHistoryAdapter.notifyItemChanged(moveHistory.size - 1)
        }
        binding.rvMoveHistory.scrollToPosition(moveHistory.size - 1)
    }

    private fun showToast(resId: Int, arg: String? = null) {
        Toast.makeText(this, getString(resId, arg), Toast.LENGTH_SHORT).show()
    }
    
    private fun saveGame() {
        lifecycleScope.launch {
            val boardState = (0 until piecesGrid.childCount).joinToString(",") { 
                (piecesGrid.getChildAt(it) as ImageView).tag?.toString() ?: "0"
            }
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
                val boardState = savedGame.boardState.split(",")
                (0 until piecesGrid.childCount).forEach { i ->
                    val tag = boardState[i].toIntOrNull()
                    if (tag != null && tag != 0) {
                        (piecesGrid.getChildAt(i) as ImageView).setImageResource(tag)
                        (piecesGrid.getChildAt(i) as ImageView).tag = tag
                    } else {
                        (piecesGrid.getChildAt(i) as ImageView).setImageDrawable(null)
                        (piecesGrid.getChildAt(i) as ImageView).tag = null
                    }
                }
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