package com.pocketarcade.hub

import com.pocketarcade.data.DecorStyle
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.SpriteFX
import com.pocketarcade.engine.r3d.Blend
import com.pocketarcade.engine.r3d.BoxFaces
import com.pocketarcade.engine.r3d.Model
import com.pocketarcade.engine.r3d.ModelBuilder
import com.pocketarcade.engine.r3d.Texture
import com.pocketarcade.games.CabinetShape
import kotlin.math.sqrt

/** Builds the 3D models of everything standing in the hall. */
object HubModels {

    /**
     * A cabinet whose footprint is ([x0], [z0])–([x1], [z1]); the front faces +z. The screen quad
     * samples [CabinetSkin.screen], which the scene repaints every frame.
     */
    fun cabinet(skin: CabinetSkin, x0: Float, z0: Float, x1: Float, z1: Float): Model {
        val b = ModelBuilder()
        val f = z1
        when (skin.shape) {
            CabinetShape.UPRIGHT -> {
                val h = 52f
                sidePanels(b, skin, x0, z0, x1, f, h)
                b.box(x0 + 2f, 0f, z0, x1 - 2f, 22f, f, BoxFaces(front = skin.lowerFront.full, top = skin.panelTop.full))
                b.box(x0 + 2f, 22f, z0, x1 - 2f, 44f, f - 6f, BoxFaces(front = skin.bezel.full))
                screen(b, skin, x0 + 4f, x1 - 4f, 25f, 41f, f - 5.9f)
                b.box(x0 + 2f, 44f, z0, x1 - 2f, h, f - 1f, BoxFaces(front = skin.marquee.full, top = skin.top.full, frontEmissive = 1.15f))
            }
            CabinetShape.WIDE -> {
                val h = 56f
                sidePanels(b, skin, x0, z0, x1, f, h)
                b.box(x0 + 2f, 0f, z0, x1 - 2f, 20f, f, BoxFaces(front = skin.lowerFront.full, top = skin.panelTop.full))
                b.box(x0 + 2f, 20f, z0, x1 - 2f, 46f, f - 4f, BoxFaces(front = skin.bezel.full))
                screen(b, skin, x0 + 4f, x1 - 4f, 22f, 44f, f - 3.9f)
                b.quad(
                    x0 + 3f, 45f, f - 3.6f, x1 - 3f, 45f, f - 3.6f, x1 - 3f, 21f, f - 3.6f, x0 + 3f, 21f, f - 3.6f,
                    skin.glassShine.full, 0f, 0f, 1f, blend = Blend.ALPHA,
                )
                b.box(x0 + 2f, 46f, z0, x1 - 2f, h, f - 2f, BoxFaces(front = skin.marquee.full, top = skin.top.full, frontEmissive = 1.15f))
            }
            CabinetShape.LANE -> {
                val rail = skin.railTex!!.full
                b.box(x0, 0f, z0, x0 + 4f, 14f, f, BoxFaces(front = skin.sideEdge.full, left = skin.sideArt.full, top = rail, right = rail))
                b.box(x1 - 4f, 0f, z0, x1, 14f, f, BoxFaces(front = skin.sideEdge.full, right = skin.sideArt.full, top = rail, left = rail))
                // The lane ramps up from the console toward the target.
                val ny = 1f
                val nz = 8f / 44f
                val l = sqrt(ny * ny + nz * nz)
                b.quad(
                    x0 + 4f, 18f, z0 + 10f, x1 - 4f, 18f, z0 + 10f, x1 - 4f, 10f, f - 8f, x0 + 4f, 10f, f - 8f,
                    skin.laneTex!!.full, 0f, ny / l, nz / l,
                )
                b.box(x0, 0f, f - 8f, x1, 14f, f, BoxFaces(front = skin.consoleFront!!.full, top = skin.top.full, left = rail, right = rail))
                b.box(x0, 0f, z0, x1, 40f, z0 + 10f, BoxFaces(front = skin.backboard!!.full, left = skin.sideArt.full, right = skin.sideArt.full, top = skin.top.full))
                screen(b, skin, x0 + 5f, x1 - 5f, 24f, 37f, z0 + 10.1f)
                b.box(x0, 40f, z0, x1, 50f, z0 + 8f, BoxFaces(front = skin.marquee.full, top = skin.top.full, frontEmissive = 1.15f))
            }
            CabinetShape.TABLE -> {
                val rail = skin.railTex!!.full
                // The table: a body, the playing surface and a low rail round it.
                b.box(x0, 0f, z0 + 8f, x1, 16f, f, BoxFaces(front = skin.lowerFront.full, left = skin.sideEdge.full, right = skin.sideEdge.full))
                b.quad(x0 + 3f, 16.5f, z0 + 10f, x1 - 3f, 16.5f, z0 + 10f, x1 - 3f, 16.5f, f - 3f, x0 + 3f, 16.5f, f - 3f, skin.tableTop!!.full, 0f, 1f, 0f, emissive = 0.9f)
                b.box(x0, 16f, z0 + 8f, x0 + 3f, 19f, f, BoxFaces(front = rail, top = rail, right = rail))
                b.box(x1 - 3f, 16f, z0 + 8f, x1, 19f, f, BoxFaces(front = rail, top = rail, left = rail))
                b.box(x0 + 3f, 16f, f - 3f, x1 - 3f, 19f, f, BoxFaces(front = rail, top = rail))
                b.box(x0 + 3f, 16f, z0 + 8f, x1 - 3f, 19f, z0 + 10f, BoxFaces(top = rail))
                // Scoreboard on a post at the far end.
                b.box(x0 + 3f, 16f, z0, x1 - 3f, 44f, z0 + 6f, BoxFaces(front = skin.bezel.full, left = skin.sideEdge.full, right = skin.sideEdge.full, top = skin.top.full))
                screen(b, skin, x0 + 5f, x1 - 5f, 27f, 41f, z0 + 6.1f)
                b.box(x0 + 1f, 44f, z0, x1 - 1f, 52f, z0 + 7f, BoxFaces(front = skin.marquee.full, top = skin.top.full, frontEmissive = 1.15f))
            }
        }
        return b.build()
    }

    private fun sidePanels(b: ModelBuilder, skin: CabinetSkin, x0: Float, z0: Float, x1: Float, f: Float, h: Float) {
        b.box(x0, 0f, z0, x0 + 2f, h, f, BoxFaces(front = skin.sideEdge.full, left = skin.sideArt.full, top = skin.sideEdge.full))
        b.box(x1 - 2f, 0f, z0, x1, h, f, BoxFaces(front = skin.sideEdge.full, right = skin.sideArt.full, top = skin.sideEdge.full))
    }

    private fun screen(b: ModelBuilder, skin: CabinetSkin, xa: Float, xb: Float, ya: Float, yb: Float, z: Float) {
        b.quad(xa, yb, z, xb, yb, z, xb, ya, z, xa, ya, z, skin.screen.full, 0f, 0f, 1f, emissive = 1.05f)
    }

    fun counter(): Model = ModelBuilder()
        .box(64f, 0f, 78f, 160f, 18f, 96f, BoxFaces(front = HubTextures.counterFront.full, top = HubTextures.counterTop.full, left = HubTextures.counterSide.full, right = HubTextures.counterSide.full))
        .box(66f, 18f, 80f, 104f, 30f, 92f, BoxFaces(front = HubTextures.glassCaseFront.full, top = HubTextures.glassCaseTop.full, left = HubTextures.glassCaseTop.full, right = HubTextures.glassCaseTop.full))
        .box(120f, 18f, 80f, 158f, 30f, 92f, BoxFaces(front = HubTextures.glassCaseFront.full, top = HubTextures.glassCaseTop.full, left = HubTextures.glassCaseTop.full, right = HubTextures.glassCaseTop.full))
        .box(106f, 18f, 84f, 118f, 24f, 90f, BoxFaces(front = HubTextures.darkMetal.full, top = HubTextures.darkMetal.full))
        .build()

    fun tokenMachine(p: Prop): Model = ModelBuilder()
        .box(p.x0, 0f, p.z0, p.x1, 34f, p.z1, BoxFaces(front = HubTextures.tokenFront.full, left = HubTextures.goldSide.full, right = HubTextures.goldSide.full, top = HubTextures.goldSide.full))
        .box(p.x0, 34f, p.z0, p.x1, 42f, p.z1, BoxFaces(front = HubTextures.tokenSign.full, top = HubTextures.goldSide.full, left = HubTextures.goldSide.full, right = HubTextures.goldSide.full, frontEmissive = 1.1f))
        .build()

    fun soda(p: Prop): Model = ModelBuilder()
        .box(p.x0, 0f, p.z0, p.x1, 42f, p.z1, BoxFaces(front = HubTextures.sodaFront.full, left = HubTextures.redSide.full, right = HubTextures.redSide.full, top = HubTextures.redSide.full))
        .quad(p.x0 + 3f, 32f, p.z1 + 0.1f, p.x0 + 15f, 32f, p.z1 + 0.1f, p.x0 + 15f, 12f, p.z1 + 0.1f, p.x0 + 3f, 12f, p.z1 + 0.1f, HubTextures.sodaPanel.full, 0f, 0f, 1f, emissive = 1.2f)
        .build()

    fun bench(p: Prop): Model {
        val wood = HubTextures.wood.region(wrap = true)
        val metal = HubTextures.darkMetal.full
        return ModelBuilder()
            .box(p.x0, 5f, p.z0 + 2f, p.x1, 8f, p.z1, BoxFaces(front = wood, top = wood, left = wood, right = wood, texelsPerUnit = 2f))
            .box(p.x0, 8f, p.z0, p.x1, 16f, p.z0 + 2f, BoxFaces(front = wood, top = wood, left = wood, right = wood, texelsPerUnit = 2f))
            .box(p.x0 + 2f, 0f, p.z1 - 3f, p.x0 + 4f, 5f, p.z1 - 1f, BoxFaces(front = metal, left = metal, right = metal))
            .box(p.x1 - 4f, 0f, p.z1 - 3f, p.x1 - 2f, 5f, p.z1 - 1f, BoxFaces(front = metal, left = metal, right = metal))
            .build()
    }

    fun trash(p: Prop): Model = ModelBuilder()
        .cylinder(p.centerX, (p.z0 + p.z1) / 2f, 0f, 14f, 5f, 10, HubTextures.trashSide.full, HubTextures.trashLid.full)
        .build()

    fun entranceWall(widthUnits: Float, z: Float): Model {
        val wall = HubTextures.entranceWall.region(wrap = true)
        return ModelBuilder()
            .box(16f, 0f, z, widthUnits - 16f, 8f, z + 16f, BoxFaces(front = wall, top = wall, back = wall, texelsPerUnit = 2f))
            .build()
    }

    /** Box-shaped decorations use their 2x artwork as the front face. */
    fun decorBox(style: DecorStyle, p: Prop, front: Texture): Model {
        val side = when (style) {
            DecorStyle.JUKEBOX -> HubTextures.solid(8, 8, Pal.PURPLE)
            DecorStyle.FISH_TANK -> HubTextures.solid(8, 8, Pal.DARKBROWN)
            else -> HubTextures.solid(8, 8, Pal.DARKBROWN)
        }
        val top = HubTextures.solid(8, 8, Pal.shade(Pal.BROWN, 0.8f))
        return ModelBuilder()
            .box(p.x0, 0f, p.z0, p.x1, p.height, p.z1, BoxFaces(front = front.full, left = side.full, right = side.full, top = top.full, frontEmissive = if (style == DecorStyle.JUKEBOX) 0.95f else 0f))
            .build()
    }

    /** Scale2x (no bevel, no new outline) for artwork used flat on a box face. */
    fun boxFront(style: DecorStyle): Texture = Texture.of(SpriteFX.scale2x(DecorArt.canvas(style)))
}
