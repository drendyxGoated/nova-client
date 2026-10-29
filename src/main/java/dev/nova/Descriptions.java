package dev.nova;

import java.util.HashMap;
import java.util.Map;

public final class Descriptions {
    private Descriptions() {}
    private static final Map<String, String> D = new HashMap<>();

    private static final String RAW = """
KillAura|Attacks nearby targets with eased, mouse-like aim and random timing
PlayerESP|Boxes, names and health bars on other players
EntityESP|Boxes for mobs, animals, villagers, crystals, boats
StorageESP|Highlights chests, barrels, shulkers, hoppers
Tracers|Lines from your crosshair to targets
StashFinder|Scores chunks by storage blocks to find stashes and bases
Fullbright|Night vision, always
AutoSprint|Sprints for you while moving forward
TriggerBot|Attacks when your crosshair is on a target
AutoTotem|Keeps a totem in your offhand
AutoEat|Eats when hungry
Reach|Extends entity and block reach
Flight|Fly in survival (with anti-kick)
Speed|Faster ground movement
Jesus|Float on water
Spider|Climb walls
NoFall|Cancels fall damage
Step|Walk up full blocks
AutoWalk|Holds forward for you
AntiAFK|Small random actions so you don't get kicked
AutoTool|Picks the best tool for the block you mine
Zoom|Zooms your view
AimAssist|Gently pulls your aim to nearby targets
AutoWeapon|Swaps to a sword or axe when you attack
Sneak|Holds sneak
Bhop|Bunny hop
AutoJump|Jumps over blocks in your way
Jetpack|Hold jump to rise
HighJump|Jump much higher
LowGravity|Moon gravity
Glide|Slows your fall
ElytraFly|Controlled elytra flight
FastLadder|Climb ladders faster
AutoRespawn|Respawns instantly
ChestStealer|Moves all items out of an open chest
AutoMine|Holds the attack button
MiddleClickPearl|Middle-click throws an ender pearl
ItemESP|Shows dropped items, valuables highlighted
OreESP|Shows ores, spawners and portals through walls
Trail|Draws your path
LowHealthWarning|Red screen border when you're low
AutoLog|Disconnects on low health or nearby players
PlayerAlert|Chat alert when a player enters range
DeathLogger|Saves and marks where you died
Panic|Turns every module off
HUD|Coordinates, FPS, ping, speed, totem count
Radar|Minimap of nearby entities
TargetHUD|Name and health of the closest target
ArmorHUD|Armor icons with durability
Freecam|Fly the camera while your body stays put
SusChunkFinder|Flags chunks with redstone, torches, glass and other built blocks
SpawnerESP|Spots and alerts on spawners
SellBot|Runs your sell command on a timer
AutoMiner|Digs tunnels or a route to X/Y/Z. Command: /krypton mine x y z
Keystrokes|WASD overlay
PotionHUD|Active potion effects
BaseBuilder|Builds a blueprint (bunker, panic room, platform, wall)
Surround|Obsidian on all four sides of your feet
Scaffold|Places blocks under you as you walk
Eagle|Auto-sneaks at block edges
Parkour|Jumps at block edges
AutoReplenish|Refills low hotbar stacks from your inventory
AutoArmor|Equips armor into empty slots
AutoDrop|Throws away junk items
HoleESP|Shows safe bedrock/obsidian holes
AutoCommand|Runs a command on a timer (/rtp, /home...)
AutoFarm|Harvests mature crops in reach and replants
Waypoints|Labels in the world for saved spots. /krypton waypoint add <name>
Search|Highlights any blocks you pick: /krypton esp add <block>
BlockEntityESP|Beds, beacons, enchanting tables, furnaces, signs...
ChunkBorders|Shows the edges of the chunk you're in
Nearby|List of nearby players with health and distance
AutoMend|Throws XP bottles at your feet to repair armor
VehicleFly|Faster boats/horses and flying vehicles
Nuker|Mines the nearest matching block in reach
Macro|Sends a message or command on a timer: /krypton macro <sec> <text>
""";

    static {
        for (String line : RAW.split("\n")) {
            String[] a = line.split("\\|", 2);
            if (a.length == 2) D.put(a[0].trim(), a[1].trim());
        }
    }

    public static String get(String name) { return D.get(name); }
}
