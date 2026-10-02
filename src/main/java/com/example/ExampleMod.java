package net.fabricmc.example;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix4f;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class ExampleMod implements ClientModInitializer {
    private final Map<String, BlockPos> serverDatabase = new HashMap<>();
    private final String path = "config/corleone_saved_coords.txt";
    private String currentServerID = "Unknown";
    private BlockPos activeWaypoint = null;
    private int tickCount = 0;

    @Override
    public void onInitializeClient() {
        if (Files.exists(Paths.get(path))) {
            try (BufferedReader r = new BufferedReader(new FileReader(path))) {
                String l; while ((l = r.readLine()) != null) {
                    String[] p = l.split(",");
                    if (p.length == 4) serverDatabase.put(p[0], new BlockPos(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])));
                }
            } catch (Exception ignored) {}
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null || client.player == null) return;
            if (tickCount++ >= 40) { 
                tickCount = 0;
                try {
                    Scoreboard sb = client.world.getScoreboard();
                    ScoreboardObjective obj = sb.getObjectiveForSlot(Scoreboard.getDisplaySlotFromName("sidebar"));
                    String id = sb.getScoreboardEntries(obj).stream()
                        .map(e -> e.getOwner()).filter(line -> line.contains("mini") || line.contains("meg"))
                        .findFirst().orElse("Unknown").trim().replaceAll("§.", "");
                    if (!id.equals(currentServerID)) {
                        currentServerID = id;
                        activeWaypoint = serverDatabase.get(currentServerID);
                    }
                } catch (Exception e) { currentServerID = "Unknown"; }
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("savecorleone").executes(ctx -> {
                if (currentServerID.equals("Unknown")) {
                    ctx.getSource().sendFeedback(Text.of("§cNot on a valid lobby instance!"));
                    return 0;
                }
                BlockPos pos = ctx.getSource().getPlayer().getBlockPos();
                serverDatabase.put(currentServerID, pos);
                activeWaypoint = pos;
                try (PrintWriter w = new PrintWriter(new FileWriter(path))) {
                    for (Map.Entry<String, BlockPos> entry : serverDatabase.entrySet()) {
                        BlockPos p = entry.getValue();
                        w.printf("%s,%d,%d,%d%n", entry.getKey(), p.getX(), p.getY(), p.getZ());
                    }
                } catch (Exception ignored) {}
                ctx.getSource().sendFeedback(Text.of("§a[Corleone Mod] Saved location for: §6" + currentServerID));
                return 1;
            }));
        });

        WorldRenderEvents.END.register(context -> {
            if (activeWaypoint == null) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;
            MatrixStack matrices = context.matrixStack();
            matrices.push();
            matrices.translate(activeWaypoint.getX() - context.camera().getPos().x + 0.5, activeWaypoint.getY() - context.camera().getPos().y + 2.0, activeWaypoint.getZ() - context.camera().getPos().z + 0.5);
            matrices.multiply(client.getEntityRenderDispatcher().getRotation());
            matrices.scale(-0.025f, -0.025f, 0.025f);
            Matrix4f posMatrix = matrices.peek().getPositionMatrix();
            TextRenderer tr = client.textRenderer;
            String t = "§b§lBoss Corleone §7(" + currentServerID + ")";
            tr.draw(t, -tr.getWidth(t) / 2f, 0, 0xFFFFFF, false, posMatrix, context.consumers(), TextRenderer.TextLayerType.SEE_THROUGH, 0, 15728880);
            matrices.pop();
        });
    }
						}
