package com.pocketarcade.games

import com.pocketarcade.games.airhockey.AirHockeyGame
import com.pocketarcade.games.claw.ClawMachineGame
import com.pocketarcade.games.coinpusher.CoinPusherGame
import com.pocketarcade.games.fishing.FishingGame
import com.pocketarcade.games.hoops.HoopsGame
import com.pocketarcade.games.pinball.PinballGame
import com.pocketarcade.games.racer.RacerGame
import com.pocketarcade.games.shooter.ShooterGame
import com.pocketarcade.games.skeeball.SkeeBallGame
import com.pocketarcade.games.stacker.StackerGame
import com.pocketarcade.games.whackamole.WhackAMoleGame

/**
 * The one place machines are registered. The hall gives each entry a bank of cabinets: the
 * bank for its [CabinetShape] in HubLayout.slots, or else the next spare bank (the build fails
 * loudly when none is left). Add a new [MiniGame] here and it appears in the arcade with its
 * own cabinets, prompts and high-score table.
 */
object GameRegistry {
    fun createAll(): List<MiniGame> = listOf(
        ClawMachineGame(),
        WhackAMoleGame(),
        SkeeBallGame(),
        HoopsGame(),
        CoinPusherGame(),
        AirHockeyGame(),
        RacerGame(),
        StackerGame(),
        ShooterGame(),
        PinballGame(),
        FishingGame(),
    )
}
