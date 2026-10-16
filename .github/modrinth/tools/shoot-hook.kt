package xyz.kkaii.steamed.fabric

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory

class SteamedFabric : ModInitializer, ClientModInitializer {
    private val logger = LoggerFactory.getLogger("steamed")

    override fun onInitialize() {
    }

    override fun onInitializeClient() {
        logger.info("init steamworks client")
        Thread {
            val mc = net.minecraft.client.Minecraft.getInstance()
            fun shot(tag: String) { mc.execute { net.minecraft.client.Screenshot.grab(mc, false); logger.info("DEMO-SHOT {}", tag) }; Thread.sleep(1500) }
            fun ints(u: java.util.UUID) = listOf(u.mostSignificantBits ushr 32, u.mostSignificantBits and 0xffffffffL, u.leastSignificantBits ushr 32, u.leastSignificantBits and 0xffffffffL).joinToString(",") { it.toInt().toString() }
            while (mc.level == null || mc.player == null) Thread.sleep(500)
            Thread.sleep(2000)
            val demo = xyz.kkaii.steamed.DemoData
            demo.enabled = true
            val kai = demo.people[1]; val sam = demo.people[2]; val ari = demo.people[3]
            val server = mc.singleplayerServer!!
            fun run(vararg cmds: String) = server.execute { val src = server.createCommandSourceStack(); cmds.forEach { server.commands.performPrefixedCommand(src, it) } }
            run("time set ${System.getProperty("demo.time", "11000")}", "weather clear", "gamerule advance_time false", "gamerule send_command_feedback false", "gamerule spawn_mobs false", "tp @p 320 150 734")
            Thread.sleep(5000)
            val yRef = java.util.concurrent.atomic.AtomicInteger()
            server.execute { yRef.set(server.overworld().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 320, 734)) }
            Thread.sleep(500)
            val y = yRef.get()
            logger.info("DEMO ground y={}", y)
            fun man(p: xyz.kkaii.steamed.DemoData.Person, x: Double, z: Double, yaw: Int, pitch: Int, item: String, pose: String = "standing") =
                "summon mannequin $x $y $z {UUID:[I;${ints(p.uuid)}],profile:{id:[I;${ints(p.uuid)}]},hide_description:1b,CustomName:\"${p.name}\",CustomNameVisible:1b,Rotation:[${yaw}f,${pitch}f],pose:\"$pose\",Tags:[\"demo\"],equipment:{mainhand:{id:\"minecraft:$item\",count:1}}}"
            run(
                "kill @e[type=minecraft:mannequin]", "kill @e[tag=demo]", "kill @e[type=minecraft:item]",
                "fill 314 $y 729 326 ${y + 4} 743 air",
                "fill 314 ${y - 1} 729 326 ${y - 1} 743 grass_block replace #minecraft:dirt",
                
                "setblock 320 $y 735 campfire",
                "setblock 317 $y 737 oak_fence", "setblock 317 ${y + 1} 737 lantern",
                "setblock 323 $y 736 crafting_table", "setblock 324 $y 736 chest[facing=north]",
                "fill 318 $y 741 318 ${y + 2} 741 oak_log", "fill 324 $y 741 324 ${y + 2} 741 oak_log",
                "fill 319 $y 741 323 $y 741 oak_planks", "fill 319 ${y + 1} 741 321 ${y + 1} 741 oak_planks",
                "setblock 322 ${y + 1} 740 scaffolding",
                "fill 315 $y 732 316 $y 733 short_grass", "setblock 325 $y 732 poppy", "setblock 315 $y 739 dandelion", "setblock 325 $y 738 cornflower",
            )
            Thread.sleep(1500)
            run(
                man(kai, 318.4, 734.4, -112, 14, "iron_pickaxe"),
                man(sam, 322.4, 734.6, 120, 10, "bread"),
                man(ari, 322.7, 739.4, 15, 12, "oak_planks"),
                "summon wolf 316.9 $y 734.2 {Owner:[I;${ints(kai.uuid)}],Sitting:1b,Rotation:[165f,0f],Tags:[\"demo\"],PersistenceRequired:1b}",
                "gamemode spectator @p", "tp @p 320.5 ${y + 0.2} 730.2 0 7",
            )
            xyz.kkaii.steamed.SteamPlayers::class.java.getDeclaredField("uuids").apply { isAccessible = true }.set(null, setOf(kai.uuid, ari.uuid))
            Thread.sleep(6000)
            mc.execute { mc.gui.hud.chat.clearMessages(true) }
            Thread.sleep(1000)
            shot("scene")
            mc.execute { mc.gui.hud.chat.clearMessages(true) }
            xyz.kkaii.steamed.Steamed.onMapsAvailable(kai.id, listOf("squaremap" to 41234))
            Thread.sleep(2500)
            shot("maplink")
            xyz.kkaii.steamed.Steamed.matchmaking!!.createLobby(com.codedisaster.steamworks.SteamMatchmaking.LobbyType.FriendsOnly, 20)
            Thread.sleep(5000)
            mc.execute { mc.gui.hud.chat.clearMessages(true); mc.setScreenAndShow(xyz.kkaii.steamed.screen.SteamLobbyScreen(null)) }
            Thread.sleep(3000)
            shot("lobby")
            mc.execute { mc.setScreenAndShow(xyz.kkaii.steamed.screen.SteamFriendListScreen(mc.gui.screen())) }
            Thread.sleep(3000)
            shot("invite")
            mc.execute { xyz.kkaii.steamed.Steamed.leaveLobby(); mc.gui.setScreen(null); mc.gui.hud.chat.clearMessages(true) }
            Thread.sleep(1500)
            xyz.kkaii.steamed.Steamed.onLobbyInvite(com.codedisaster.steamworks.SteamID.createFromNativeHandle(109775240000000999L), kai.id)
            Thread.sleep(300)
            mc.execute { mc.gui.hud.chat.clearMessages(true); mc.setScreenAndShow(xyz.kkaii.steamed.screen.SteamLobbyScreen(null)) }
            Thread.sleep(1200)
            shot("invited")
            mc.execute { mc.setScreenAndShow(xyz.kkaii.steamed.screen.SteamConnectingScreen(null, kai.name)) }
            Thread.sleep(1500)
            shot("connecting")
            logger.info("STEAMED-SHOT done")
        }.apply { isDaemon = true }.start()
    }
}
