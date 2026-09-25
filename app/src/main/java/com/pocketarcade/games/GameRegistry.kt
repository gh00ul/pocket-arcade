package com.pocketarcade.games

import com.pocketarcade.games.claw.ClawMachineGame
import com.pocketarcade.games.coinpusher.CoinPusherGame
import com.pocketarcade.games.hoops.HoopsGame
import com.pocketarcade.games.skeeball.SkeeBallGame
import com.pocketarcade.games.whackamole.WhackAMoleGame

/**
 * The one place machines are registered. The hall lays out one cabinet per entry, in this
 * order, two per row from the back of the hall toward the entrance. Add a new [MiniGame]
 * here and it appears in the arcade with its own cabinet, prompt and high-score table.
 */
object GameRegistry {
    fun createAll(): List<MiniGame> = listOf(
        ClawMachineGame(),
        WhackAMoleGame(),
        SkeeBallGame(),
        HoopsGame(),
        CoinPusherGame(),
    )
}
